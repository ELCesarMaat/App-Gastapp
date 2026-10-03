package com.binc.gastapp.wo.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface GastappApi {

    // ---- Emparejamiento (sin autenticacion) ----

    @POST("Device/Code")
    suspend fun requestCode(@Body body: DeviceCodeRequest): DeviceCodeResponse

    /**
     * Devuelve Response<T> a proposito: el 400 lleva el campo "error" de RFC 8628
     * (authorization_pending, slow_down, expired_token, access_denied) y el reloj
     * necesita leerlo para decidir si sigue sondeando.
     */
    @POST("Device/Token")
    suspend fun pollToken(@Body body: DeviceTokenRequest): Response<DeviceTokenResponse>

    @POST("Device/Refresh")
    suspend fun refresh(@Body body: DeviceRefreshRequest): Response<DeviceTokenResponse>

    // ---- Autenticados con el access token del dispositivo ----

    /**
     * Desvincula este reloj en el servidor.
     *
     * Lleva el Authorization a mano porque se llama DESPUES de borrar la sesion local:
     * para entonces el AuthInterceptor ya no tiene token que poner. Devuelve Response<T>
     * porque un fallo aqui no cambia nada del lado del reloj, que ya se desvinculo.
     */
    @POST("Device/Revoke")
    suspend fun revoke(
        @Header("Authorization") authorization: String,
        @Body body: DeviceRevokeRequest
    ): Response<Unit>

    @GET("Device/Categories")
    suspend fun categories(): List<DeviceCategoryDto>

    /** Acepta lotes de hasta 50. Es idempotente por spendingId. */
    @POST("Device/Expenses")
    suspend fun createExpenses(@Body body: List<DeviceExpenseDto>): DeviceExpenseBatchResult

    /** Los gastos del periodo, ya consolidados en el servidor (reloj y telefono). */
    @GET("Device/Expenses")
    suspend fun dayExpenses(
        @Query("period") period: String = "today",
        @Query("tzOffsetMinutes") tzOffsetMinutes: Int,
        @Query("limit") limit: Int = 50
    ): List<DeviceDaySpendingDto>

    @GET("Device/Summary")
    suspend fun summary(
        @Query("period") period: String = "today",
        @Query("tzOffsetMinutes") tzOffsetMinutes: Int
    ): DeviceSummaryResponse
}
