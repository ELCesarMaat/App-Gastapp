package com.binc.gastapp.wo.data.auth

import com.binc.gastapp.wo.data.remote.DeviceCodeRequest
import com.binc.gastapp.wo.data.remote.DeviceCodeResponse
import com.binc.gastapp.wo.data.remote.DeviceTokenRequest
import com.binc.gastapp.wo.data.remote.GastappApi
import com.binc.gastapp.wo.data.remote.NetworkModule

/** Resultado de un sondeo, en los terminos de RFC 8628. */
sealed interface PollResult {
    data object Linked : PollResult
    data object Pending : PollResult
    data object SlowDown : PollResult
    data object Expired : PollResult
    data object Denied : PollResult
    data class Failure(val message: String) : PollResult
}

class PairingRepository(
    private val api: GastappApi,
    private val tokenStore: TokenStore
) {

    suspend fun requestCode(deviceName: String): DeviceCodeResponse =
        api.requestCode(DeviceCodeRequest(deviceName = deviceName))

    /**
     * Un sondeo. El llamador controla el intervalo y sube 5 segundos cuando recibe
     * [PollResult.SlowDown].
     */
    suspend fun poll(deviceCode: String): PollResult {
        val respuesta = try {
            api.pollToken(DeviceTokenRequest(deviceCode))
        } catch (e: Exception) {
            // Sin red o la API despertando: no es un fallo de emparejamiento.
            return PollResult.Failure(e.message ?: "Sin conexión")
        }

        val tokens = respuesta.body()
        if (respuesta.isSuccessful && tokens != null) {
            tokenStore.save(tokens)
            return PollResult.Linked
        }

        val error = NetworkModule.parseDeviceError(respuesta.errorBody()?.string())
        return when (error) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "expired_token" -> PollResult.Expired
            "access_denied" -> PollResult.Denied
            else -> PollResult.Failure("No se pudo vincular")
        }
    }

    suspend fun hasSession(): Boolean = tokenStore.hasSession()

    suspend fun clearSession() = tokenStore.clear()
}
