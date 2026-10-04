package com.binc.gastapp.core.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit

/** Configuracion de JSON del API. */
val ApiJson: Json = Json {
    // El API manda de mas (la entidad User completa, navegaciones de EF...).
    ignoreUnknownKeys = true
    // Un null donde hay valor por defecto toma el valor por defecto.
    coerceInputValues = true
    // Se manda todo, defaults y nulls incluidos, igual que System.Text.Json en MAUI.
    encodeDefaults = true
    explicitNulls = true
}

object ApiClient {
    /**
     * 120 segundos: el API vive en el plan gratuito de Render, que lo apaga por
     * inactividad, y el arranque en frio tarda un minuto o mas.
     */
    const val TIMEOUT_SECONDS = 120L

    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS + 30, TimeUnit.SECONDS)
        .build()

    /** [baseUrl] termina en "/api/". */
    fun create(baseUrl: String, client: OkHttpClient = okHttpClient()): GastappApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GastappApi::class.java)
}

/**
 * Resultado de una llamada, sin excepciones. La diferencia que importa (regla de la
 * Fase 3): solo un 401 explicito cierra la sesion; un 5xx, un timeout o la falta de red
 * nunca expulsan al usuario.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** El servidor respondio con error. [message] es el texto del API, si lo mando. */
    data class HttpError(val code: Int, val message: String?) : ApiResult<Nothing> {
        val isUnauthorized: Boolean get() = code == 401
    }

    /** No hubo respuesta: sin red, timeout, Render despertando... */
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>

    /** Hubo respuesta pero no se pudo leer: el contrato cambio. */
    data class InvalidResponse(val cause: Throwable) : ApiResult<Nothing>
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

/** Envuelve una llamada del API. Respeta la cancelacion de corrutinas. */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.HttpError(e.code(), extractApiMessage(e.response()?.errorBody()?.string()))
} catch (e: IOException) {
    ApiResult.NetworkError(e)
} catch (e: SerializationException) {
    ApiResult.InvalidResponse(e)
} catch (e: IllegalArgumentException) {
    // kotlinx.serialization lanza IllegalArgumentException en algunos errores de forma.
    ApiResult.InvalidResponse(e)
}

/**
 * ExtractApiMessage de MAUI: el API manda el error como texto plano ("Email en uso") o
 * como string JSON ("\"Email en uso\""). Se agregan { message } y { error } por si acaso.
 * Una pagina de error o un ProblemDetails no sirven para mostrarse: dan null.
 */
fun extractApiMessage(body: String?): String? {
    val content = body?.trim().orEmpty()
    if (content.isEmpty()) return null
    if (content.startsWith('"')) {
        return runCatching { ApiJson.decodeFromString<String>(content) }.getOrNull()?.takeIf { it.isNotBlank() }
    }
    if (content.startsWith('{')) {
        val obj = runCatching { ApiJson.parseToJsonElement(content) as? JsonObject }.getOrNull() ?: return null
        val value = (obj["message"] ?: obj["error"]) as? JsonPrimitive ?: return null
        return value.takeIf { it.isString }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }
    if (content.startsWith('<')) return null
    return content
}
