package com.binc.gastapp.wear

import com.binc.gastapp.core.remote.parseApiInstant
import com.binc.gastapp.core.wear.WearExpensePayload
import com.binc.gastapp.core.wear.WearPaths
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.repository.DeviceRepository
import com.binc.gastapp.data.repository.DeviceResult
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.money.roundHalfEven
import com.binc.gastapp.ui.format.MexicoLocale
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/** Que paso con un gasto que llego del reloj. */
sealed interface WatchExpenseResult {
    data object Imported : WatchExpenseResult

    /** Ya estaba (el reloj lo reenvio o ya habia llegado por la sincronizacion). */
    data object AlreadyThere : WatchExpenseResult

    /** Sin cuenta en el telefono: el gasto llegara por la sincronizacion al iniciar sesion. */
    data object NoAccount : WatchExpenseResult
}

/**
 * RegistrarGastoAsync de GastappWearListenerService (MAUI): inserta en Room el gasto que
 * capturo el reloj, CON SU spendingId. Asi aparece en la lista aunque el telefono no
 * tenga internet; el reloj lo sube por su cuenta y el telefono tambien (SyncAllData), y
 * el servidor hace upsert por ese id: no se duplica.
 */
@Singleton
class WatchExpenseImporter @Inject constructor(
    private val db: GastappDatabase,
    private val users: UserRepository,
    private val spendings: SpendingRepository,
    private val clock: Clock,
) {
    suspend fun import(payload: WearExpensePayload): WatchExpenseResult {
        // Incluye los borrados: un gasto que se borro aqui no revive porque el reloj lo reenvie.
        if (db.spendingDao().get(payload.spendingId) != null) return WatchExpenseResult.AlreadyThere
        val user = users.getUser() ?: return WatchExpenseResult.NoAccount

        // La categoria del reloj si sigue siendo del usuario; si no, "Sin categoria" (la pone
        // SpendingRepository al recibirla vacia). Nunca se descarta un gasto por la categoria.
        val categoryId = payload.categoryId
            ?.let { db.categoryDao().get(it) }
            ?.takeIf { !it.isDeleted && it.userId == user.userId }
            ?.categoryId
            .orEmpty()

        spendings.save(
            Spending(
                spendingId = payload.spendingId,
                amount = BigDecimal.valueOf(payload.amount).roundHalfEven(2),
                date = occurredAtLocal(payload.occurredAt),
                userId = user.userId,
                categoryId = categoryId,
                title = payload.title.ifBlank { "Gasto desde el reloj" },
                description = payload.description,
                // El reloj no maneja tarjetas: los mismos valores que pone el API para los
                // gastos que entran por Device/Expenses.
                isCreditCard = false,
                creditCardId = null,
                paymentMethod = PaymentMethods.CASH,
            ),
        )
        return WatchExpenseResult.Imported
    }

    /** El reloj manda UTC; en el telefono los gastos van en hora local. Ilegible = ahora. */
    private fun occurredAtLocal(text: String): LocalDateTime =
        runCatching { LocalDateTime.ofInstant(parseApiInstant(text), clock.zone) }
            .getOrElse { LocalDateTime.now(clock) }
            .withNano(0)
}

/** "$85 · Café" o "$85.50" (NotificarAsync de MAUI: sin centavos si el monto es entero). */
fun watchExpenseText(payload: WearExpensePayload): String {
    val amount = BigDecimal.valueOf(payload.amount)
    val format = NumberFormat.getCurrencyInstance(MexicoLocale).apply {
        val whole = amount.stripTrailingZeros().scale() <= 0
        minimumFractionDigits = if (whole) 0 else 2
        maximumFractionDigits = if (whole) 0 else 2
    }
    val money = format.format(amount)
    val title = payload.title.trim()
    return if (title.isEmpty()) money else "$money · $title"
}

/**
 * VincularAsync de MAUI: el reloj manda su codigo por Bluetooth y aqui se reenvia a
 * Device/Link con el token del telefono, sin que el usuario teclee nada.
 */
@Singleton
class WatchPairing @Inject constructor(
    private val devices: DeviceRepository,
    private val events: WearEvents,
) {
    /** [WearPaths.PAIR_OK] o el motivo corto que el reloj muestra tal cual. */
    suspend fun pair(userCode: String): String {
        if (userCode.isBlank()) return "Código vacío"
        // Por debajo de los 60 s que espera el reloj: si Render tarda mas, vale mas devolver
        // un motivo a tiempo que una respuesta que ya nadie escucha.
        val result = withTimeoutOrNull(MaxWaitMillis) { devices.link(userCode.trim()) }
            ?: return "El servidor tardó demasiado"
        return when (result) {
            is DeviceResult.Ok -> {
                events.emit(WearEvent.Linked(result.value.deviceName))
                events.emit(WearEvent.DevicesChanged)
                WearPaths.PAIR_OK
            }
            DeviceResult.NoSession -> "Inicia sesión en el teléfono"
            // Los mismos casos que el dialogo de teclear el codigo.
            is DeviceResult.Failed -> when (result.httpCode) {
                null -> "Sin conexión"
                429 -> "Demasiados intentos"
                400 -> "Código no válido o expirado"
                401 -> "Sesión caducada en el teléfono"
                404 -> "Falta actualizar el servidor"
                else -> "Error del servidor (${result.httpCode})"
            }
        }
    }

    private companion object {
        const val MaxWaitMillis = 55_000L
    }
}
