package com.binc.gastapp.data.repository

import com.binc.gastapp.core.remote.ApiResult
import com.binc.gastapp.core.remote.DeviceDto
import com.binc.gastapp.core.remote.GastappApi
import com.binc.gastapp.core.remote.LinkDeviceRequest
import com.binc.gastapp.core.remote.LinkDeviceResponse
import com.binc.gastapp.core.remote.RevokeDeviceRequest
import com.binc.gastapp.core.remote.apiCall
import com.binc.gastapp.core.remote.bearer
import com.binc.gastapp.data.session.SessionGuard
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de una llamada de dispositivos, con el mensaje listo para mostrarse. */
sealed interface DeviceResult<out T> {
    data class Ok<T>(val value: T) : DeviceResult<T>

    /** Sin token vigente: no se llama al API. */
    data object NoSession : DeviceResult<Nothing>

    data class Failed(val message: String) : DeviceResult<Nothing>
}

/**
 * Relojes vinculados a la cuenta (la seccion de dispositivos de SettingsViewModel en
 * MAUI). Todo es en linea: no hay copia local, la lista es la del servidor.
 *
 * Avisarle al reloj por Bluetooth que se le quito el acceso (/gastapp/revoked) y la
 * vinculacion automatica que manda el reloj son de la Fase 5.4 (Data Layer).
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val api: GastappApi,
    private val guard: SessionGuard,
) {

    suspend fun list(): DeviceResult<List<DeviceDto>> =
        authorized({ api.getDevices(it) }) { "No se pudo consultar tus dispositivos. Revisa tu conexión." }

    /**
     * Vincula el reloj que muestra [userCode] ("K7M-2QX"). El API ignora guiones y
     * mayusculas; responde 400 si el codigo no existe o vencio y 429 tras muchos intentos.
     */
    suspend fun link(userCode: String): DeviceResult<LinkDeviceResponse> =
        authorized({ api.linkDevice(it, LinkDeviceRequest(userCode.trim())) }) { failure ->
            when {
                failure is ApiResult.HttpError && failure.code == 429 ->
                    failure.message ?: "Demasiados intentos. Espera 15 minutos e intenta de nuevo."
                failure is ApiResult.HttpError && failure.code == 400 ->
                    "El código no es válido o ya expiró. Revisa el que muestra tu reloj."
                failure is ApiResult.NetworkError -> "No se pudo vincular el reloj. Revisa tu conexión."
                else -> "No se pudo vincular el reloj. Intenta de nuevo."
            }
        }

    suspend fun revoke(deviceId: String): DeviceResult<Unit> {
        val token = guard.validToken() ?: return DeviceResult.NoSession
        val result = apiCall { api.revokeDevice(bearer(token), RevokeDeviceRequest(deviceId)) }
        // 404: ya no existe en el servidor, que es justo lo que se queria.
        if (result is ApiResult.HttpError && result.code == 404) return DeviceResult.Ok(Unit)
        return when (val outcome = result.toDeviceResult { "No se pudo desvincular el dispositivo. Revisa tu conexión." }) {
            is DeviceResult.Ok -> DeviceResult.Ok(Unit)
            is DeviceResult.Failed -> DeviceResult.Failed(outcome.message)
            DeviceResult.NoSession -> DeviceResult.NoSession
        }
    }

    /** Llama al API con el token vigente; sin token no hay llamada. */
    private suspend fun <T> authorized(
        call: suspend (authorization: String) -> T,
        failureMessage: (ApiResult<*>) -> String,
    ): DeviceResult<T> {
        val token = guard.validToken() ?: return DeviceResult.NoSession
        return apiCall { call(bearer(token)) }.toDeviceResult(failureMessage)
    }

    /**
     * Un 401 invalida la sesion (SessionGuard), igual que en la sincronizacion: la app
     * manda a iniciar sesion.
     */
    private suspend fun <T> ApiResult<T>.toDeviceResult(failureMessage: (ApiResult<*>) -> String): DeviceResult<T> =
        when (this) {
            is ApiResult.Success -> DeviceResult.Ok(value)
            is ApiResult.HttpError -> if (isUnauthorized) {
                guard.onUnauthorized()
                DeviceResult.Failed("Tu sesión expiró. Inicia sesión de nuevo.")
            } else {
                DeviceResult.Failed(failureMessage(this))
            }
            else -> DeviceResult.Failed(failureMessage(this))
        }
}
