package com.binc.gastapp.wo.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

    @GET("Device/Categories")
    suspend fun categories(): List<DeviceCategoryDto>

    /** Acepta lotes de hasta 50. Es idempotente por spendingId. */
    @POST("Device/Expenses")
    suspend fun createExpenses(@Body body: List<DeviceExpenseDto>): DeviceExpenseBatchResult

    @GET("Device/Summary")
    suspend fun summary(
        @Query("period") period: String = "today",
        @Query("tzOffsetMinutes") tzOffsetMinutes: Int
    ): DeviceSummaryResponse
}
