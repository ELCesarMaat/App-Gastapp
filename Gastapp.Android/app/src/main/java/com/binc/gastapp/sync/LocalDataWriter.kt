package com.binc.gastapp.sync

import androidx.room.withTransaction
import com.binc.gastapp.core.remote.AllUserDataDto
import com.binc.gastapp.core.remote.SpendingDto
import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.CreditCardEntity
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.IncomeTypeEntity
import com.binc.gastapp.data.local.SpendingEntity
import com.binc.gastapp.data.local.SubscriptionEntity
import com.binc.gastapp.data.local.UserEntity
import com.binc.gastapp.data.remote.toEntity
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.domain.model.IncomeTypes
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escribe en Room lo que baja del servidor: el login completo (AddUserData de MAUI) y
 * los gastos que faltan (PullRemoteSpendings).
 *
 * Regla 12 del anexo C: una referencia a una tarjeta o categoria que no existe
 * localmente truena la llave foranea y revierte la transaccion entera. Por eso cada
 * referencia se valida antes de insertar:
 *  - Tarjeta desconocida -> null (se conserva el gasto o la suscripcion).
 *  - Categoria desconocida de un gasto -> "Sin categoria" (el gasto exige categoria).
 *  - Categoria desconocida de una suscripcion -> null.
 */
@Singleton
class LocalDataWriter @Inject constructor(
    private val db: GastappDatabase,
    private val categories: CategoryRepository,
    private val clock: Clock,
) {
    /**
     * Reemplaza la base local con lo que devolvio el login.
     *
     * Diferencia con MAUI: si la base ya era de este mismo usuario (sesion vencida y
     * vuelta a iniciar), lo que estaba pendiente de subir NO se pierde: se vuelve a
     * escribir encima de lo del servidor, todavia como pendiente, y se sube en la
     * siguiente sincronizacion. MAUI borraba todo y esos cambios se perdian.
     *
     * Si la base era de otro usuario (o de la muestra de desarrollo), se descarta entera.
     */
    suspend fun replaceAll(data: AllUserDataDto) = db.withTransaction {
        val userId = data.user.userId
        val pending = if (db.userDao().get()?.userId == userId) collectPending() else Pending()

        clearAll()

        val incomes = data.incomes.map { it.toEntity() }.toMutableList()
        // El usuario apunta a su tipo de ingreso con una llave RESTRICT: si el catalogo
        // no vino completo, se completa con los conocidos para no tronar el login.
        if (incomes.none { it.incomeTypeId == data.user.incomeTypeId }) {
            incomes += IncomeTypeEntity(data.user.incomeTypeId, defaultIncomeName(data.user.incomeTypeId))
        }
        db.incomeTypeDao().upsertAll(incomes)
        if (pending.user != null && incomes.none { it.incomeTypeId == pending.user.incomeTypeId }) {
            db.incomeTypeDao().upsertAll(listOf(IncomeTypeEntity(pending.user.incomeTypeId, defaultIncomeName(pending.user.incomeTypeId))))
        }

        db.userDao().upsert(pending.user ?: data.user.toEntity())

        // Categorias: las del servidor y encima las pendientes (incluidas las borradas).
        db.categoryDao().upsertAll(data.categories.map { it.toEntity(userId) })
        db.categoryDao().upsertAll(pending.categories)
        val defaultCategoryId = categories.ensureDefaultFor(userId).categoryId

        // Tarjetas antes que gastos y suscripciones, que apuntan a ellas. Se incluyen las
        // borradas: sus gastos siguen viajando y apuntan a ellas.
        db.creditCardDao().upsertAll(data.creditCards.map { it.toEntity(userId) })
        db.creditCardDao().upsertAll(pending.creditCards)

        val cardIds = db.creditCardDao().allIds().toHashSet()
        val categoryIds = db.categoryDao().allIds().toHashSet()

        db.subscriptionDao().upsertAll(
            data.subscriptions.map { it.toEntity(userId, it.creditCardId.knownIn(cardIds), it.categoryId.knownIn(categoryIds)) },
        )
        db.subscriptionDao().upsertAll(
            pending.subscriptions.map { it.copy(creditCardId = it.creditCardId.knownIn(cardIds), categoryId = it.categoryId.knownIn(categoryIds)) },
        )

        db.spendingDao().upsertAll(data.spendings.map { it.toLocal(userId, cardIds, categoryIds, defaultCategoryId) })
        db.spendingDao().upsertAll(
            pending.spendings.map {
                it.copy(
                    creditCardId = it.creditCardId.knownIn(cardIds),
                    categoryId = it.categoryId.takeIf(categoryIds::contains) ?: defaultCategoryId,
                )
            },
        )
    }

    /**
     * PullRemoteSpendings: inserta SOLO los gastos del servidor que no existen aqui (los
     * del reloj, los de otro telefono). Nunca sobrescribe uno local: podria tener cambios
     * sin subir, y uno borrado aqui tampoco revive. Devuelve cuantos se agregaron.
     */
    suspend fun insertMissingSpendings(remote: List<SpendingDto>): Int = db.withTransaction {
        val user = db.userDao().get() ?: return@withTransaction 0
        val localIds = db.spendingDao().allIds().toHashSet()
        val missing = remote.filter { it.spendingId !in localIds }
        if (missing.isEmpty()) return@withTransaction 0

        val cardIds = db.creditCardDao().allIds().toHashSet()
        val categoryIds = db.categoryDao().allIds().toHashSet()
        val defaultCategoryId = categories.ensureDefaultFor(user.userId).categoryId

        db.spendingDao().upsertAll(missing.map { it.toLocal(user.userId, cardIds, categoryIds, defaultCategoryId) })
        missing.size
    }

    /** Cerrar sesion: no queda nada de la cuenta en el telefono. Va fuera de cualquier transaccion. */
    fun clearAllTables() = db.clearAllTables()

    private suspend fun clearAll() {
        // Usuario al final: su cascada se llevaria todo, pero asi el orden queda explicito
        // y ninguna llave NO ACTION (gasto -> tarjeta) se queda colgando a media consulta.
        db.spendingDao().deleteAll()
        db.subscriptionDao().deleteAll()
        db.creditCardDao().deleteAll()
        db.categoryDao().deleteAll()
        db.userDao().deleteAll()
        db.incomeTypeDao().deleteAll()
    }

    private suspend fun collectPending() = Pending(
        user = db.userDao().pendingSync(),
        categories = db.categoryDao().allPending(),
        creditCards = db.creditCardDao().pendingSync(),
        subscriptions = db.subscriptionDao().pendingSync(),
        spendings = db.spendingDao().pendingSync(),
    )

    private fun SpendingDto.toLocal(
        userId: String,
        cardIds: Set<String>,
        categoryIds: Set<String>,
        defaultCategoryId: String,
    ): SpendingEntity = toEntity(
        userId = userId,
        categoryId = categoryId.takeIf(categoryIds::contains) ?: defaultCategoryId,
        creditCardId = creditCardId.knownIn(cardIds),
        zone = clock.zone,
    )

    private fun String?.knownIn(ids: Set<String>): String? = this?.takeIf { it.isNotBlank() && it in ids }

    private fun defaultIncomeName(incomeTypeId: Int): String = when (incomeTypeId) {
        IncomeTypes.WEEKLY -> "Semanal"
        IncomeTypes.BIWEEKLY -> "Quincenal"
        IncomeTypes.MONTHLY -> "Mensual"
        else -> "Ingreso $incomeTypeId"
    }

    /** Lo que estaba sin subir antes de reemplazar la base. */
    private data class Pending(
        val user: UserEntity? = null,
        val categories: List<CategoryEntity> = emptyList(),
        val creditCards: List<CreditCardEntity> = emptyList(),
        val subscriptions: List<SubscriptionEntity> = emptyList(),
        val spendings: List<SpendingEntity> = emptyList(),
    )
}
