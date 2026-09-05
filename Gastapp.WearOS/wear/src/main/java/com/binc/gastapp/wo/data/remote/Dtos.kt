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

/** Desvinculacion. El reloj solo manda su propio deviceId. */
@Serializable
data class DeviceRevokeRequest(val deviceId: String)

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

/** Un gasto ya registrado en el servidor, para pintarlo en la lista del reloj. */
@Serializable
data class DeviceDaySpendingDto(
    val spendingId: String,
    val title: String,
    val categoryName: String? = null,
    val amount: Double,
    /** ISO-8601 en UTC. */
    val occurredAt: String
)

/**
 * Un gasto capturado en el reloj, tal como viaja por Bluetooth al telefono.
 *
 * Lleva el gasto entero y no solo lo justo para la notificacion: el telefono lo
 * inserta en su base local, asi aparece en su lista aunque no haya internet.
 *
 * El spendingId es el mismo que sube el reloj al API. Tanto Device/Expenses como
 * SyncAllData hacen upsert por ese id, asi que no se duplica.
 */
@Serializable
data class WearExpensePayload(
    val spendingId: String,
    val amount: Double,
    val title: String,
    val categoryId: String? = null,
    /**
     * Ya compuesta aqui, con el "Agregado desde mi ...". Viaja hecha porque si el
     * telefono gana la carrera al subir el gasto, el servidor ve que ya existe y no
     * vuelve a escribirla.
     */
    val description: String? = null,
    /** ISO-8601 en UTC. */
    val occurredAt: String
)

/**
 * Lo que empuja el telefono por la Data Layer con el estado del dia.
 *
 * Reutiliza DeviceDaySpendingDto a proposito: es la misma forma que devuelve
 * GET /Device/Expenses, asi el mapeo a Room es el mismo venga de donde venga.
 */
@Serializable
data class WearTodayPayload(
    val total: Double = 0.0,
    val count: Int = 0,
    val spendings: List<DeviceDaySpendingDto> = emptyList()
)

@Serializable
data class DeviceSummaryResponse(
    val period: String,
    val total: Double,
    val count: Int,
    val currency: String = "MXN"
)
