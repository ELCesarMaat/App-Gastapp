package com.binc.gastapp.wo.data.remote

import com.binc.gastapp.wo.data.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Agrega el access token a las peticiones autenticadas.
 *
 * Las rutas de emparejamiento se saltan a proposito: en ese momento todavia no hay
 * credenciales, y mandar un token vencido solo confundiria al servidor.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (esRutaDeEmparejamiento(request.url.encodedPath)) {
            return chain.proceed(request)
        }

        val token = tokenStore.accessToken ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }

    private fun esRutaDeEmparejamiento(path: String): Boolean =
        path.endsWith("/Device/Code", ignoreCase = true) ||
            path.endsWith("/Device/Token", ignoreCase = true) ||
            path.endsWith("/Device/Refresh", ignoreCase = true)
}
