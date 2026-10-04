package com.binc.gastapp.core.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * El API de Gastapp, solo con lo que usa la app nativa (anexo B del plan). Las
 * altas, ediciones y borrados de gastos, tarjetas y suscripciones van todos por
 * [syncAllData], que ya hace upsert y aplica los borrados.
 *
 * Los parametros sueltos van en la query string con el mismo nombre que les pone Refit
 * en MAUI (?Email=&Code=, ?categoryId=). El token va en cada llamada que lo pide, como
 * el [Authorize] de Refit: asi queda a la vista que llamadas son anonimas.
 *
 * Las rutas son relativas: la base termina en ".../api/".
 */
interface GastappApi {

    // ---------------------------------------------------------------- Sesion

    @POST("User/Login")
    suspend fun login(@Body body: LoginRequest): AllUserDataDto

    @POST("User/RefreshToken")
    suspend fun refreshToken(@Header("Authorization") authorization: String): TokenDto

    @POST("User/CreateUser")
    suspend fun createUser(@Body body: CreateUserRequest): CreateUserResponse

    @POST("User/EmailVerification/request")
    suspend fun requestEmailVerification(@Query("Email") email: String): Boolean

    @POST("User/EmailVerification/verify")
    suspend fun verifyEmail(@Query("Email") email: String, @Query("Code") code: String): Boolean

    @POST("User/PasswordReset/request")
    suspend fun requestPasswordReset(@Query("Email") email: String): Boolean

    @POST("User/PasswordReset/verify")
    suspend fun verifyPasswordReset(@Query("email") email: String, @Query("code") code: String): Boolean

    /**
     * Ojo: la contrasena nueva viaja en la URL porque asi la espera el API, y las URL
     * quedan en los logs de Render (anexo B). Se corrige en API y app a la vez despues
     * de la migracion.
     */
    @POST("User/PasswordReset/confirm")
    suspend fun confirmPasswordReset(
        @Query("email") email: String,
        @Query("code") code: String,
        @Query("newPassword") newPassword: String,
    ): Boolean

    @POST("User/PasswordReset/temporary")
    suspend fun generateTemporaryPassword(@Query("email") email: String): TemporaryPasswordResponse

    // ---------------------------------------------------------------- Datos

    @POST("Spendings/SyncAllData")
    suspend fun syncAllData(@Header("Authorization") authorization: String, @Body body: SyncDataDto): Boolean

    /** Gastos vigentes del servidor, para bajar los que se crearon en otro lado (el reloj). */
    @GET("Spendings/GetSpendings")
    suspend fun getSpendings(@Header("Authorization") authorization: String): List<SpendingDto>

    /** Sus gastos pasan a "Sin categoria" en el servidor. 404 = ya no existe alla. */
    @POST("Spendings/DeleteCategory")
    suspend fun deleteCategory(@Header("Authorization") authorization: String, @Query("categoryId") categoryId: String): Boolean

    // ---------------------------------------------------------------- Dispositivos y version

    @POST("Device/Link")
    suspend fun linkDevice(@Header("Authorization") authorization: String, @Body body: LinkDeviceRequest): LinkDeviceResponse

    @GET("Device/List")
    suspend fun getDevices(@Header("Authorization") authorization: String): List<DeviceDto>

    @POST("Device/Revoke")
    suspend fun revokeDevice(@Header("Authorization") authorization: String, @Body body: RevokeDeviceRequest): Boolean

    /** Anonimo. 404 = no hay release publicado. */
    @GET("App/LatestVersion")
    suspend fun getLatestVersion(): AppLatestVersionDto
}

/** Valor del encabezado Authorization. */
fun bearer(token: String): String = "Bearer $token"
