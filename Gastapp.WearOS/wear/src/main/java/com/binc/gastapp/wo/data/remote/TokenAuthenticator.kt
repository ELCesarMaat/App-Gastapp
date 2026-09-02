package com.binc.gastapp.wo.data.remote

import com.binc.gastapp.wo.data.auth.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Renueva el access token cuando la API responde 401.
 *
 * El servidor ROTA el refresh token en cada uso: el anterior queda invalidado de
 * inmediato. Por eso el refresh esta serializado con un Mutex y ademas se comprueba
 * si otra peticion ya lo renovo mientras se esperaba el lock. Sin esas dos cosas,
 * dos peticiones que caducan a la vez lanzarian dos refresh con el mismo token, el
 * segundo recibiria 401, y el reloj se desvincularia sin motivo.
 */
class TokenAuthenticator(
    private val apiProvider: () -> GastappApi,
    private val tokenStore: TokenStore,
    private val onSessionLost: () -> Unit
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Un solo reintento. Si la respuesta ya venia de un reintento, rendirse.
        if (response.priorResponse != null) return null

        val tokenUsado = response.request.header("Authorization")

        return runBlocking {
            mutex.withLock {
                // Otra peticion pudo haber refrescado mientras esperabamos el lock.
                val actual = tokenStore.accessToken
                if (actual != null && tokenUsado != "Bearer $actual") {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $actual")
                        .build()
                }

                val refresh = tokenStore.readRefreshToken() ?: return@withLock null

                val respuesta = runCatching {
                    apiProvider().refresh(DeviceRefreshRequest(refresh))
                }.getOrNull()

                val nuevos = respuesta?.body()
                if (respuesta?.isSuccessful == true && nuevos != null) {
                    tokenStore.save(nuevos)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${nuevos.accessToken}")
                        .build()
                } else {
                    // 401 al refrescar: el dispositivo fue revocado, o el refresh token
                    // ya se habia rotado. En ambos casos la sesion se perdio.
                    tokenStore.clear()
                    onSessionLost()
                    null
                }
            }
        }
    }
}
