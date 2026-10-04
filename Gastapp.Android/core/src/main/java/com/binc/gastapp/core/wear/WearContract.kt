package com.binc.gastapp.core.wear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Contrato de la Wearable Data Layer entre el telefono (:app) y el reloj (:wear). Vive
// aqui para que los dos usen exactamente las mismas rutas y los mismos payloads.
//
// Recordatorio para cuando deje de llegar algo sin ningun error: la Data Layer SOLO
// entrega entre apps con el mismo applicationId y la misma firma.

/** Rutas de mensajes y de DataItems. Todas empiezan con [BASE]. */
object WearPaths {
    const val BASE = "/gastapp"

    /** Reloj -> telefono, sin cuerpo. El telefono responde [PONG] al mismo nodo. */
    const val PING = "$BASE/ping"
    const val PONG = "$BASE/pong"

    /** Reloj -> telefono, con el userCode ("K7M-2QX") como cuerpo. */
    const val PAIR = "$BASE/pair"

    /** Telefono -> reloj: [PAIR_OK] o el motivo del fallo, para mostrarlo tal cual. */
    const val PAIR_RESULT = "$BASE/pair/result"
    const val PAIR_OK = "ok"

    /** Telefono -> reloj, con el deviceId revocado como cuerpo. */
    const val REVOKED = "$BASE/revoked"

    /** Reloj -> telefono, sin cuerpo: "me acabo de desvincular". */
    const val UNLINKED = "$BASE/unlinked"

    /** Reloj -> telefono, cuerpo [WearExpensePayload] en JSON. */
    const val EXPENSE = "$BASE/expense"

    /** DataItem del telefono con [WearTodayPayload]. */
    const val TODAY = "$BASE/today"

    /** DataItem del telefono con la lista de [DeviceCategoryDto]. */
    const val CATEGORIES = "$BASE/categories"

    /** Clave dentro del DataMap donde viaja el JSON. */
    const val DATA_KEY_JSON = "json"

    /**
     * Marca de tiempo en el DataMap. Sin esto, volver a poner un contenido identico no
     * cuenta como cambio y el reloj no recibe nada.
     */
    const val DATA_KEY_STAMP = "ts"
}

/** JSON de la Data Layer: camelCase (lo que ya mandaba MAUI) y tolerante a campos nuevos. */
val WearJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Una categoria tal como la conoce el reloj (tambien la devuelve GET /Device/Categories). */
@Serializable
data class DeviceCategoryDto(
    val categoryId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean,
)

/** Un gasto ya registrado, para pintarlo en la lista del reloj (forma de GET /Device/Expenses). */
@Serializable
data class DeviceDaySpendingDto(
    val spendingId: String,
    val title: String,
    val categoryName: String? = null,
    val amount: Double,
    /** ISO-8601 en UTC. */
    val occurredAt: String,
)

/**
 * Un gasto capturado en el reloj, tal como viaja por Bluetooth al telefono.
 *
 * Lleva el gasto entero y no solo lo justo para la notificacion: el telefono lo inserta
 * en su base local, asi aparece en su lista aunque no haya internet.
 *
 * El spendingId es el mismo que sube el reloj al API. Tanto Device/Expenses como
 * SyncAllData hacen upsert por ese id, asi que no se duplica.
 */
@Serializable
data class WearExpensePayload(
    val spendingId: String,
    val amount: Double,
    val title: String,
    /** Null si el reloj no supo clasificarlo; el telefono pone la suya por defecto. */
    val categoryId: String? = null,
    /**
     * Ya compuesta en el reloj, con el "Agregado desde mi ...". Viaja hecha porque si el
     * telefono gana la carrera al subir el gasto, el servidor ve que ya existe y no
     * vuelve a escribirla.
     */
    val description: String? = null,
    /** ISO-8601 en UTC. */
    val occurredAt: String,
)

/**
 * Lo que empuja el telefono con el estado del dia. Reutiliza [DeviceDaySpendingDto] a
 * proposito: es la misma forma que GET /Device/Expenses, asi el reloj aplica el mismo
 * mapeo venga de donde venga.
 */
@Serializable
data class WearTodayPayload(
    val total: Double = 0.0,
    val count: Int = 0,
    val spendings: List<DeviceDaySpendingDto> = emptyList(),
)
