package com.binc.gastapp.wo.data.remote

import kotlinx.serialization.Serializable

// Contrato de /api/Device/*. Los nombres deben coincidir exactamente con los DTO
// de la API (Gastapp.Models/Models/DeviceDtos.cs), que serializa en camelCase.

@Serializable
data class DeviceCodeRequest(
    val deviceName: String,
    val platform: String = "wearos"
)

@Serializable
data class DeviceCodeResponse(
    /** Credencial bearer opaca. Nunca se muestra en pantalla ni en logs. */
    val deviceCode: String,
    /** Lo que el usuario teclea en el telefono, ya con guion: "K7M-2QX". */
    val userCode: String,
    val expiresIn: Int,
    val interval: Int
)

@Serializable
data class DeviceTokenRequest(val deviceCode: String)

@Serializable
data class DeviceRefreshRequest(val refreshToken: String)

@Serializable
data class DeviceTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val deviceId: String,
    val scopes: String
)

/** Cuerpo de los 400 de emparejamiento, en formato RFC 8628. */
@Serializable
data class DeviceErrorResponse(val error: String)

@Serializable
data class DeviceCategoryDto(
    val categoryId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean
)

@Serializable
data class DeviceExpenseDto(
    /** UUID generado en el reloj. Clave de idempotencia: nunca se regenera al reintentar. */
    val spendingId: String,
    val amount: Double,
    val title: String? = null,
    /** null deja que el servidor asigne la categoria por defecto del usuario. */
    val categoryId: String? = null,
    /** ISO-8601 en UTC, con Z. */
    val occurredAt: String,
    val rawInput: String? = null,
    val needsReview: Boolean = false
)

@Serializable
data class DeviceExpenseResult(
    val spendingId: String,
    /** false = ya existia. Para el reloj ambos casos son exito. */
    val created: Boolean
)

@Serializable
data class DeviceExpenseBatchResult(val results: List<DeviceExpenseResult>)

@Serializable
data class DeviceSummaryResponse(
    val period: String,
    val total: Double,
    val count: Int,
    val currency: String = "MXN"
)
