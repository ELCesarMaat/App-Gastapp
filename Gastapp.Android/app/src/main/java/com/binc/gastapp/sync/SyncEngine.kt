package com.binc.gastapp.sync

import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.binc.gastapp.R
import com.binc.gastapp.core.remote.ApiResult
import com.binc.gastapp.core.remote.GastappApi
import com.binc.gastapp.core.remote.SyncDataDto
import com.binc.gastapp.core.remote.apiCall
import com.binc.gastapp.core.remote.bearer
import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.CreditCardEntity
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.PendingCounts
import com.binc.gastapp.data.local.SpendingEntity
import com.binc.gastapp.data.local.SubscriptionEntity
import com.binc.gastapp.data.local.UserEntity
import com.binc.gastapp.data.prefs.SessionStore
import com.binc.gastapp.data.remote.toDto
import com.binc.gastapp.data.remote.toInfoDto
import com.binc.gastapp.data.remote.translateServerMessage
import com.binc.gastapp.data.session.SessionGuard
import com.binc.gastapp.ui.format.Strings
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Como termino una sincronizacion; el SyncWorker lo traduce a exito, reintento o fallo. */
sealed interface SyncOutcome {
    data class Done(val pushed: Int, val pulled: Int) : SyncOutcome

    /** Sin token vigente: no se llama al API y lo pendiente se queda para despues. */
    data object NoSession : SyncOutcome

    /** 401: la sesion se cerro y la app manda a iniciar sesion. */
    data object Unauthorized : SyncOutcome

    /** Sin red, timeout o 5xx: vale la pena reintentar. */
    data class Retry(val reason: String) : SyncOutcome

    /** El API rechazo los datos (400) o respondio algo ilegible: reintentar no lo arregla. */
    data class Failed(val reason: String) : SyncOutcome
}

/** Lo que Ajustes muestra de la nube (CloudSyncStatusSummary de MAUI, mas el ultimo resultado). */
data class SyncRunState(
    val running: Boolean = false,
    val lastSuccessAt: Instant? = null,
    val lastError: String? = null,
)

/**
 * La sincronizacion offline-first, en el orden de App.xaml.cs (MAUI):
 *  1. (Completa) Refrescar el token.
 *  2. Push: un solo SyncAllData con todo lo pendiente, borrados incluidos.
 *  3. Categorias borradas: DeleteCategory una por una (el API no recibe su IsDeleted).
 *  4. (Completa) Pull: GetSpendings e insertar solo los gastos que faltan.
 *
 * La completa corre al abrir la app; la normal, despues de cada escritura.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: GastappApi,
    private val db: GastappDatabase,
    private val guard: SessionGuard,
    private val sessionStore: SessionStore,
    private val writer: LocalDataWriter,
    /** Los errores que Ajustes muestra, en el idioma del telefono. */
    private val strings: Strings,
    private val clock: Clock,
) {
    /** Una sola sincronizacion a la vez, aunque WorkManager y un boton coincidan. */
    private val mutex = Mutex()

    private val _runState = MutableStateFlow(SyncRunState())
    val runState: StateFlow<SyncRunState> = _runState.asStateFlow()

    /** Pendientes por tipo, en vivo. */
    fun observePending(): Flow<PendingCounts> = db.syncDao().observePendingCounts()

    suspend fun sync(full: Boolean): SyncOutcome = mutex.withLock {
        _runState.update { it.copy(running = true) }
        val outcome = try {
            run(full)
        } finally {
            _runState.update { it.copy(running = false) }
        }
        _runState.update {
            when (outcome) {
                is SyncOutcome.Done -> it.copy(lastSuccessAt = clock.instant(), lastError = null)
                is SyncOutcome.Retry -> it.copy(lastError = outcome.reason)
                is SyncOutcome.Failed -> it.copy(lastError = outcome.reason)
                SyncOutcome.Unauthorized -> it.copy(lastError = strings.get(R.string.sync_session_invalid))
                SyncOutcome.NoSession -> it
            }
        }
        outcome
    }

    private suspend fun run(full: Boolean): SyncOutcome {
        var token = guard.validToken() ?: return SyncOutcome.NoSession

        if (full) {
            when (val refreshed = apiCall { api.refreshToken(bearer(token)) }) {
                is ApiResult.Success -> {
                    token = refreshed.value.tokenValue
                    val expiresAt = refreshed.value.tokenExpiration ?: clock.instant().plus(Duration.ofDays(1))
                    sessionStore.save(token, expiresAt)
                }
                else -> return refreshed.failure(R.string.sync_action_refresh)
            }
        }

        // Si el API rechaza el envio (400), reintentar no sirve, pero eso no debe impedir
        // que bajen los gastos del reloj: se sigue con el pull y al final se reporta.
        var rejected: SyncOutcome.Failed? = null
        var pushed = 0
        when (val push = push(token)) {
            is Step.Ok -> pushed = push.count
            is Step.Stop -> if (push.outcome is SyncOutcome.Failed) rejected = push.outcome else return push.outcome
        }

        // Solo si el push paso: los gastos de la categoria ya tienen que haberse movido alla.
        if (rejected == null) {
            when (val deleted = pushDeletedCategories(token)) {
                is Step.Ok -> Unit
                is Step.Stop -> if (deleted.outcome is SyncOutcome.Failed) rejected = deleted.outcome else return deleted.outcome
            }
        }

        val pulled = if (full) {
            when (val remote = apiCall { api.getSpendings(bearer(token)) }) {
                is ApiResult.Success -> writer.insertMissingSpendings(remote.value)
                else -> return remote.failure(R.string.sync_action_download)
            }
        } else {
            0
        }

        return rejected ?: SyncOutcome.Done(pushed = pushed, pulled = pulled)
    }

    /** SyncData de App.xaml.cs: todo lo pendiente en un solo SyncAllData. */
    private suspend fun push(token: String): Step {
        val snapshot = db.withTransaction {
            Snapshot(
                user = db.userDao().pendingSync(),
                categories = db.categoryDao().pendingSync(),
                creditCards = db.creditCardDao().pendingSync(),
                subscriptions = db.subscriptionDao().pendingSync(),
                spendings = db.spendingDao().pendingSync(),
            )
        }
        val body = SyncDataDto(
            user = snapshot.user?.toInfoDto(),
            categories = snapshot.categories.map { it.toDto() },
            spendings = snapshot.spendings.map { it.toDto(clock.zone) },
            creditCards = snapshot.creditCards.map { it.toDto() },
            subscriptions = snapshot.subscriptions.map { it.toDto() },
        )
        // El API responde 400 a un envio vacio.
        if (body.isEmpty) return Step.Ok(0)

        return when (val result = apiCall { api.syncAllData(bearer(token), body) }) {
            is ApiResult.Success -> if (result.value) {
                markSynced(snapshot)
                Step.Ok(snapshot.size)
            } else {
                Step.Stop(SyncOutcome.Failed(strings.get(R.string.sync_server_rejected)))
            }
            else -> Step.Stop(result.failure(R.string.sync_action_upload))
        }
    }

    /**
     * Marca como subido solo lo que no cambio mientras se subia. Si el usuario edito un
     * gasto a media sincronizacion, la version nueva se queda pendiente y sube despues;
     * marcarla a ciegas la daria por subida sin que el servidor la conozca.
     */
    private suspend fun markSynced(snapshot: Snapshot) = db.withTransaction {
        snapshot.user?.let { sent -> if (db.userDao().get() == sent) db.userDao().upsert(sent.copy(isSynced = true)) }
        snapshot.categories.forEach { sent ->
            if (db.categoryDao().get(sent.categoryId) == sent) db.categoryDao().upsert(sent.copy(isSynced = true))
        }
        snapshot.creditCards.forEach { sent ->
            if (db.creditCardDao().get(sent.creditCardId) == sent) db.creditCardDao().upsert(sent.copy(isSynced = true))
        }
        snapshot.subscriptions.forEach { sent ->
            if (db.subscriptionDao().get(sent.subscriptionId) == sent) db.subscriptionDao().upsert(sent.copy(isSynced = true))
        }
        snapshot.spendings.forEach { sent ->
            if (db.spendingDao().get(sent.spendingId) == sent) db.spendingDao().upsert(sent.copy(isSynced = true))
        }
    }

    /**
     * Va despues del push: sus gastos ya se movieron a "Sin categoria" localmente y ese
     * cambio ya subio. Cuando el API confirma (o responde 404: alla ya no existe), se
     * borra de verdad. Un 400 es que alla es la categoria por defecto y no se deja
     * borrar: se restaura aqui para no quedar distintos.
     */
    private suspend fun pushDeletedCategories(token: String): Step {
        val deleted = db.categoryDao().pendingDeletion()
        for (category in deleted) {
            when (val result = apiCall { api.deleteCategory(bearer(token), category.categoryId) }) {
                is ApiResult.Success -> db.categoryDao().delete(category.categoryId)
                is ApiResult.HttpError -> when (result.code) {
                    404 -> db.categoryDao().delete(category.categoryId)
                    400 -> db.categoryDao().restoreDeleted(category.categoryId)
                    else -> return Step.Stop(result.failure(R.string.sync_action_delete_category))
                }
                else -> return Step.Stop(result.failure(R.string.sync_action_delete_category))
            }
        }
        return Step.Ok(deleted.size)
    }

    /** Traduce un error del API. Un 401 cierra la sesion; nada mas lo hace. */
    private suspend fun ApiResult<*>.failure(@StringRes actionId: Int): SyncOutcome {
        val action = strings.get(actionId)
        return when (this) {
            is ApiResult.HttpError -> when {
                isUnauthorized -> {
                    guard.onUnauthorized()
                    SyncOutcome.Unauthorized
                }
                code >= 500 || code == 408 || code == 429 -> SyncOutcome.Retry(strings.get(R.string.sync_server_error, code, action))
                else -> SyncOutcome.Failed(
                    message?.let { strings.get(R.string.sync_failed_with_message, action, translateServerMessage(strings, it)) }
                        ?: strings.get(R.string.sync_failed_with_code, action, code),
                )
            }
            is ApiResult.NetworkError -> SyncOutcome.Retry(strings.get(R.string.sync_no_connection, action))
            is ApiResult.InvalidResponse -> SyncOutcome.Failed(strings.get(R.string.sync_invalid_response, action))
            is ApiResult.Success -> SyncOutcome.Failed(strings.get(R.string.sync_failed, action))
        }
    }

    private data class Snapshot(
        val user: UserEntity?,
        val categories: List<CategoryEntity>,
        val creditCards: List<CreditCardEntity>,
        val subscriptions: List<SubscriptionEntity>,
        val spendings: List<SpendingEntity>,
    ) {
        val size: Int get() = (if (user != null) 1 else 0) + categories.size + creditCards.size + subscriptions.size + spendings.size
    }

    private sealed interface Step {
        data class Ok(val count: Int) : Step
        data class Stop(val outcome: SyncOutcome) : Step
    }
}
