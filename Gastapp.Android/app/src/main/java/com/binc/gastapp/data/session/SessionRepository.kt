package com.binc.gastapp.data.session

import com.binc.gastapp.core.remote.ApiResult
import com.binc.gastapp.core.remote.CreateUserRequest
import com.binc.gastapp.core.remote.GastappApi
import com.binc.gastapp.core.remote.LoginRequest
import com.binc.gastapp.core.remote.apiCall
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.prefs.RegisterDraftStore
import com.binc.gastapp.data.prefs.SessionStore
import com.binc.gastapp.sync.LocalDataWriter
import com.binc.gastapp.sync.SyncScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/** En que situacion esta la sesion; de aqui sale la pantalla de arranque. */
sealed interface SessionState {
    /** No hay datos en el telefono: pantalla de inicio de sesion. */
    data object LoggedOut : SessionState

    /** Hay datos y un token vigente: se usa la app y se sincroniza. */
    data class Active(val userName: String) : SessionState

    /**
     * Hay datos pero el token vencio o falta: se usa la app con lo local, sin expulsar,
     * y se avisa "Inicia sesion de nuevo para volver a sincronizar".
     */
    data class Expired(val userName: String, val email: String?) : SessionState

    /** El servidor respondio 401: se manda a iniciar sesion (los datos locales se quedan). */
    data class Revoked(val email: String?) : SessionState
}

/** Resultado de una accion de sesion, con el mensaje listo para mostrarse. */
sealed interface SessionResult {
    data object Ok : SessionResult
    data class Failed(val message: String) : SessionResult
}

/** Lo que se captura en el registro (CreateUserModel de MAUI). */
data class NewAccount(
    val name: String,
    val email: String,
    val password: String,
    val birthDate: LocalDate,
    val salary: BigDecimal,
    val percentSave: BigDecimal,
    val incomeTypeId: Int,
    val firstPayDay: Int?,
    val secondPayDay: Int?,
)

/**
 * Inicio de sesion, registro, recuperacion de contrasena y cierre de sesion. Port de
 * UserService (AddUserData), StartPageViewModel, RegisterViewModel y
 * ForgetPasswordViewModel de MAUI, sin la UI (esa es la Fase 4.1).
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: GastappApi,
    private val db: GastappDatabase,
    private val sessionStore: SessionStore,
    private val draftStore: RegisterDraftStore,
    private val guard: SessionGuard,
    private val writer: LocalDataWriter,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) {
    val state: Flow<SessionState> = combine(
        db.userDao().observe(),
        sessionStore.session,
        guard.revoked,
    ) { user, session, revoked ->
        when {
            user == null -> SessionState.LoggedOut
            revoked -> SessionState.Revoked(user.email)
            session != null && session.isValidAt(clock.instant()) -> SessionState.Active(user.name)
            else -> SessionState.Expired(user.name, user.email)
        }
    }.distinctUntilChanged()

    /**
     * Login: guarda el token y reemplaza la base local con todo lo del servidor (gastos,
     * categorias, tarjetas, suscripciones y tipos de ingreso). Si la base era de esta
     * misma cuenta, lo pendiente de subir se conserva (ver LocalDataWriter.replaceAll).
     */
    suspend fun login(email: String, password: String): SessionResult {
        val result = apiCall { api.login(LoginRequest(email.trim(), password)) }
        val data = when (result) {
            is ApiResult.Success -> result.value
            else -> return SessionResult.Failed(result.loginMessage())
        }

        // El token primero: asi, cuando aparece el usuario en Room, la sesion ya es vigente
        // y la pantalla pasa directo de "sin sesion" a "activa".
        sessionStore.save(data.token, data.tokenExpiration ?: fallbackExpiration())
        try {
            writer.replaceAll(data)
        } catch (e: Exception) {
            // La transaccion se revirtio. Sin usuario local no hay sesion que conservar;
            // entrar sin datos dejaria el perfil en ceros y sin poder guardar nada.
            if (db.userDao().get() == null) sessionStore.clear()
            return SessionResult.Failed("No se pudieron guardar tus datos en este dispositivo. Intenta iniciar sesión de nuevo.")
        }
        guard.clearRevoked()
        // Si se conservaron cambios pendientes, que suban ya.
        syncScheduler.requestSync()
        return SessionResult.Ok
    }

    // ---------------------------------------------------------------- Registro

    /** Paso del correo: manda el codigo de 6 digitos. El API avisa si el correo ya tiene cuenta. */
    suspend fun requestEmailVerification(email: String): SessionResult =
        apiCall { api.requestEmailVerification(email.trim()) }
            .toSessionResult("No pudimos enviar el código. Intenta de nuevo.")

    suspend fun verifyEmail(email: String, code: String): SessionResult =
        apiCall { api.verifyEmail(email.trim(), code.trim()) }
            .toSessionResult("Código inválido o expirado.")

    /**
     * Crea la cuenta y entra.
     *
     * Diferencia con MAUI: alla, despues de CreateUser, se armaba el usuario local a mano
     * y se creaba OTRA "Sin categoria" con un id nuevo (el API ya habia creado la suya),
     * asi que la cuenta quedaba con dos categorias por defecto. Aqui se inicia sesion con
     * las mismas credenciales y se baja la cuenta tal cual quedo en el servidor.
     */
    suspend fun createAccount(account: NewAccount): SessionResult {
        val request = CreateUserRequest(
            userId = UUID.randomUUID().toString(),
            salary = account.salary,
            percentSave = account.percentSave,
            name = account.name.trim(),
            email = account.email.trim(),
            password = account.password,
            birthDate = account.birthDate,
            incomeTypeId = account.incomeTypeId,
            firstPayDay = account.firstPayDay,
            secondPayDay = account.secondPayDay,
            weekPayDay = null,
        )
        val created = apiCall { api.createUser(request) }
        if (created !is ApiResult.Success) {
            return SessionResult.Failed(created.messageOr("No se pudo crear la cuenta."))
        }

        // La cuenta ya existe: el borrador sobra aunque falle el login de abajo.
        draftStore.clear()

        return when (val login = login(account.email, account.password)) {
            SessionResult.Ok -> SessionResult.Ok
            is SessionResult.Failed -> SessionResult.Failed(
                "Tu cuenta ya quedó creada, pero no pudimos entrar: ${login.message} Inicia sesión con tu correo y contraseña.",
            )
        }
    }

    // ---------------------------------------------------------------- Recuperar contrasena

    suspend fun requestPasswordReset(email: String): SessionResult =
        apiCall { api.requestPasswordReset(email.trim()) }
            .toSessionResult("No pudimos enviar el código. Intenta de nuevo.")

    suspend fun verifyPasswordReset(email: String, code: String): SessionResult =
        apiCall { api.verifyPasswordReset(email.trim(), code.trim()) }
            .toSessionResult("Código inválido o expirado.")

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): SessionResult =
        apiCall { api.confirmPasswordReset(email.trim(), code.trim(), newPassword) }
            .toSessionResult("No se pudo cambiar la contraseña.")

    /** Manda una contrasena temporal al correo. Devuelve el mensaje del API para mostrarlo. */
    suspend fun generateTemporaryPassword(email: String): SessionResult {
        val result = apiCall { api.generateTemporaryPassword(email.trim()) }
        return if (result is ApiResult.Success && result.value.message.isNotBlank()) {
            SessionResult.Ok
        } else {
            SessionResult.Failed(result.messageOr("No se pudo generar la contraseña temporal. Verifica que el correo esté registrado."))
        }
    }

    // ---------------------------------------------------------------- Cerrar sesion

    /**
     * Borra todo lo de la cuenta en el telefono: Room, el token y el borrador del
     * registro. Los ajustes del telefono (tema, recordatorios) se quedan.
     */
    suspend fun logout() {
        syncScheduler.cancelPending()
        withContext(Dispatchers.IO) { writer.clearAllTables() }
        sessionStore.clear()
        draftStore.clear()
        guard.clearRevoked()
    }

    /** Si el API no mando la expiracion, se asume un dia: lo bastante para no expulsar a nadie. */
    private fun fallbackExpiration(): Instant = clock.instant().plus(Duration.ofDays(1))
}

/** Los mensajes de StartPageViewModel.Login en MAUI. */
private fun ApiResult<*>.loginMessage(): String = when (this) {
    is ApiResult.HttpError -> when {
        code == 400 && !message.isNullOrBlank() -> message.orEmpty()
        code == 400 -> "Credenciales inválidas. Verifica tu correo y contraseña."
        else -> "Error del servidor: $code. Intenta más tarde."
    }
    is ApiResult.NetworkError -> NoConnectionMessage
    else -> "Ocurrió un error inesperado."
}

private fun ApiResult<*>.toSessionResult(fallback: String): SessionResult =
    if (this is ApiResult.Success) SessionResult.Ok else SessionResult.Failed(messageOr(fallback))

/** El texto del API si lo mando; si no hubo conexion, el aviso de conexion; si no, [fallback]. */
private fun ApiResult<*>.messageOr(fallback: String): String = when (this) {
    is ApiResult.HttpError -> message?.takeIf { it.isNotBlank() } ?: fallback
    is ApiResult.NetworkError -> NoConnectionMessage
    else -> fallback
}

private const val NoConnectionMessage = "Error de conexión. Verifica tu conexión a internet e intenta de nuevo."
