package com.binc.gastapp.wo.data.remote

import com.binc.gastapp.wo.BuildConfig
import com.binc.gastapp.wo.data.auth.TokenStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * 90 segundos, no 30.
     *
     * La API vive en el plan gratuito de Render, que apaga el servicio por inactividad.
     * El arranque en frio tarda 50 segundos o mas, asi que con timeouts cortos la
     * primera peticion del dia fallaria siempre.
     */
    private const val TIMEOUT_LECTURA_SEGUNDOS = 90L
    private const val TIMEOUT_CONEXION_SEGUNDOS = 30L

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(tokenStore: TokenStore, onSessionLost: () -> Unit): GastappApi {
        // El Authenticator necesita la propia API para poder refrescar. Se resuelve
        // con un proveedor perezoso para romper la dependencia circular.
        lateinit var api: GastappApi

        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_CONEXION_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_LECTURA_SEGUNDOS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_LECTURA_SEGUNDOS, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator({ api }, tokenStore, onSessionLost))
            .build()

        api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GastappApi::class.java)

        return api
    }

    /** Lee el campo "error" de un 400 de emparejamiento (formato RFC 8628). */
    fun parseDeviceError(cuerpo: String?): String? {
        if (cuerpo.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<DeviceErrorResponse>(cuerpo).error }.getOrNull()
    }
}
