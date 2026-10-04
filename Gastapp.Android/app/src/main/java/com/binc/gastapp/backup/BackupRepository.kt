package com.binc.gastapp.backup

import androidx.room.withTransaction
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.R
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.domain.money.toCents
import com.binc.gastapp.sync.SyncScheduler
import com.binc.gastapp.ui.format.Strings
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/** Cuanto trae un respaldo: se muestra antes de restaurar y al terminar. */
data class BackupCounts(val spendings: Int, val creditCards: Int, val subscriptions: Int, val categories: Int)

/** Un respaldo ya leido y validado, listo para restaurarse si el usuario confirma. */
class BackupPreview internal constructor(
    internal val file: BackupFile,
) {
    val exportedAt: Instant get() = file.exportedAt
    val counts: BackupCounts
        get() = BackupCounts(
            spendings = file.spendings.size,
            creditCards = file.creditCards.count { !it.isDeleted },
            subscriptions = file.subscriptions.size,
            categories = file.categories.size,
        )
}

sealed interface BackupReadResult {
    data class Ok(val preview: BackupPreview) : BackupReadResult
    data class Invalid(val message: String) : BackupReadResult
}

/**
 * Respaldos JSON (BackupService de MAUI, que solo existia en debug: hallazgo 21).
 *
 * Exportar: todo lo vigente de la cuenta, suscripciones incluidas (el de MAUI no las
 * traia), en el formato versionado de BackupFormat.kt.
 *
 * Restaurar NO borra la base, a diferencia de MAUI: lo del respaldo vuelve a quedar como
 * estaba (si se edito o se borro despues, se recupera), y lo que se registro despues del
 * respaldo se queda. Todo lo restaurado queda pendiente de subir, asi que la nube tambien
 * vuelve a tenerlo. Solo se aceptan respaldos de la misma cuenta.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: GastappDatabase,
    private val categories: CategoryRepository,
    private val syncScheduler: SyncScheduler,
    private val strings: Strings,
    private val clock: Clock,
) {
    /** Escribe el respaldo en [output] (no lo cierra) y devuelve cuanto se guardo. */
    suspend fun export(output: OutputStream): BackupCounts {
        val file = db.withTransaction {
            val user = db.userDao().get() ?: throw IllegalStateException(strings.get(R.string.backup_no_account))
            val spendings = db.spendingDao().getAllActive()
            val subscriptions = db.subscriptionDao().getAll()
            val referencedCards = (spendings.mapNotNull { it.creditCardId } + subscriptions.mapNotNull { it.creditCardId }).toSet()
            BackupFile(
                exportedAt = clock.instant(),
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                user = user.toBackup(),
                incomeTypes = db.incomeTypeDao().getAll().map { it.toBackup() },
                categories = db.categoryDao().getAll().map { it.toBackup() },
                creditCards = db.creditCardDao().getAllIncludingDeleted()
                    .filter { !it.isDeleted || it.creditCardId in referencedCards }
                    .map { it.toBackup() },
                subscriptions = subscriptions.map { it.toBackup() },
                spendings = spendings.map { it.toBackup() },
            )
        }
        withContext(Dispatchers.IO) {
            output.write(BackupJson.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8))
            output.flush()
        }
        return BackupPreview(file).counts
    }

    /** Lee y valida [input] (no lo cierra). No toca la base. */
    suspend fun read(input: InputStream): BackupReadResult {
        val text = withContext(Dispatchers.IO) { input.readBytes().toString(Charsets.UTF_8) }
        val file = try {
            BackupJson.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return BackupReadResult.Invalid(strings.get(R.string.backup_not_a_backup))
        } catch (e: IllegalArgumentException) {
            // Un numero o una fecha con forma rara.
            return BackupReadResult.Invalid(strings.get(R.string.backup_not_a_backup))
        } catch (e: java.time.DateTimeException) {
            return BackupReadResult.Invalid(strings.get(R.string.backup_not_a_backup))
        }
        if (file.format != BACKUP_FORMAT) return BackupReadResult.Invalid(strings.get(R.string.backup_not_a_backup))
        if (file.version > BACKUP_VERSION) {
            return BackupReadResult.Invalid(strings.get(R.string.backup_newer_version))
        }
        val user = db.userDao().get() ?: return BackupReadResult.Invalid(strings.get(R.string.backup_sign_in))
        if (file.user.userId != user.userId) {
            val owner = file.user.email?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            return BackupReadResult.Invalid(strings.get(R.string.backup_other_account, owner))
        }
        return BackupReadResult.Ok(BackupPreview(file))
    }

    /** Restaura un respaldo ya validado por [read]. */
    suspend fun restore(preview: BackupPreview): BackupCounts {
        val file = preview.file
        db.withTransaction {
            val current = db.userDao().get() ?: throw IllegalStateException(strings.get(R.string.backup_no_account))
            check(current.userId == file.user.userId) { strings.get(R.string.backup_other_account_short) }
            val userId = current.userId

            // El usuario apunta a su tipo de ingreso con una llave RESTRICT: el catalogo primero.
            db.incomeTypeDao().upsertAll(file.incomeTypes.map { it.toEntity() })
            val incomeTypeId = file.user.incomeTypeId.takeIf { id -> db.incomeTypeDao().getAll().any { it.incomeTypeId == id } }
                ?: current.incomeTypeId
            db.userDao().upsert(
                current.copy(
                    name = file.user.name,
                    salaryCents = file.user.salary.toCents(),
                    percentSave = file.user.percentSave,
                    birthDate = file.user.birthDate,
                    incomeTypeId = incomeTypeId,
                    firstPayDay = file.user.firstPayDay,
                    secondPayDay = file.user.secondPayDay,
                    weekPayDay = file.user.weekPayDay,
                    isSynced = false,
                ),
            )

            db.categoryDao().upsertAll(file.categories.map { it.toEntity(userId) })
            val defaultCategoryId = categories.ensureDefaultFor(userId).categoryId
            db.creditCardDao().upsertAll(file.creditCards.map { it.toEntity(userId) })

            // Referencias que existen ahora (regla 12: una colgante truena la llave). Para
            // las categorias solo cuentan las vigentes: una borrada pendiente de avisar al
            // API se borra de verdad despues, y su cascada se llevaria el gasto.
            val cardIds = db.creditCardDao().allIds().toHashSet()
            val categoryIds = db.categoryDao().getAll().map { it.categoryId }.toHashSet()

            db.subscriptionDao().upsertAll(
                file.subscriptions.map {
                    it.toEntity(userId, it.creditCardId?.takeIf(cardIds::contains), it.categoryId?.takeIf(categoryIds::contains))
                },
            )
            db.spendingDao().upsertAll(
                file.spendings.map {
                    it.toEntity(userId, it.categoryId.takeIf(categoryIds::contains) ?: defaultCategoryId, it.creditCardId?.takeIf(cardIds::contains))
                },
            )
        }
        syncScheduler.requestSync()
        return preview.counts
    }

}
