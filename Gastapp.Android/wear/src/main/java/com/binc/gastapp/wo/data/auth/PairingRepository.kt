package com.binc.gastapp.wo.data.auth

import android.util.Log
import com.binc.gastapp.wo.data.remote.DeviceCodeRequest
import com.binc.gastapp.wo.data.remote.DeviceCodeResponse
import com.binc.gastapp.wo.data.remote.DeviceRefreshRequest
import com.binc.gastapp.wo.data.remote.DeviceRevokeRequest
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

    suspend fun requestCode(deviceName: String): DeviceCodeResponse {
        val respuesta = api.requestCode(DeviceCodeRequest(deviceName = deviceName))

        // El userCode es justo lo que hay que comparar contra lo que se teclea en el
        // telefono, asi que registrarlo es lo util para diagnosticar. El deviceCode
        // NUNCA se registra: ese si es una credencial.
        Log.i(TAG, "Codigo recibido: '${respuesta.userCode}' | expira en ${respuesta.expiresIn}s | intervalo ${respuesta.interval}s")

        return respuesta
    }

    /**
     * Un sondeo. El llamador controla el intervalo y sube 5 segundos cuando recibe
     * [PollResult.SlowDown].
     */
    suspend fun poll(deviceCode: String): PollResult {
        val respuesta = try {
            api.pollToken(DeviceTokenRequest(deviceCode))
        } catch (e: Exception) {
            // Sin red o la API despertando: no es un fallo de emparejamiento.
            Log.w(TAG, "Sondeo fallo por red: ${e.javaClass.simpleName}: ${e.message}")
            return PollResult.Failure(e.message ?: "Sin conexión")
        }

        val tokens = respuesta.body()
        if (respuesta.isSuccessful && tokens != null) {
            Log.i(TAG, "Vinculado. deviceId=${tokens.deviceId} scopes=${tokens.scopes}")
            tokenStore.save(tokens)
            return PollResult.Linked
        }

        val cuerpo = respuesta.errorBody()?.string()
        val error = NetworkModule.parseDeviceError(cuerpo)
        Log.i(TAG, "Sondeo -> HTTP ${respuesta.code()} error='$error' cuerpo=$cuerpo")

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

    suspend fun readCredentials(): TokenStore.Credentials? = tokenStore.credentials()

    /**
     * Avisa al servidor de que este reloj ya no cuenta, para no dejarlo colgado en la
     * lista del telefono. Se llama con las credenciales capturadas ANTES del borrado
     * local, asi que se autentica a mano en vez de depender del TokenStore.
     *
     * Es siempre "por si sale": el reloj ya se desvinculo pase lo que pase aqui, y un
     * registro huerfano el usuario lo puede quitar desde el telefono.
     *
     * @return true si el servidor confirmo la revocacion.
     */
    suspend fun revokeOnServer(credentials: TokenStore.Credentials): Boolean {
        val resultado = runCatching {
            // El access token vive solo en memoria y ya se borro: hay que canjear el
            // refresh por uno nuevo antes de poder llamar a Revoke.
            val tokens = api.refresh(DeviceRefreshRequest(credentials.refreshToken)).body()
                ?: return@runCatching false

            api.revoke(
                authorization = "Bearer ${tokens.accessToken}",
                body = DeviceRevokeRequest(credentials.deviceId)
            ).isSuccessful
        }

        return resultado
            .onFailure { Log.w(TAG, "No se pudo revocar en el servidor: ${it.message}") }
            .getOrDefault(false)
    }

    private companion object {
        const val TAG = "GastappPairing"
    }
}
