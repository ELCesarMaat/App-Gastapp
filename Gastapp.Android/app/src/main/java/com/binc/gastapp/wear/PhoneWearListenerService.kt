package com.binc.gastapp.wear

import android.util.Log
import com.binc.gastapp.core.wear.WearExpensePayload
import com.binc.gastapp.core.wear.WearJson
import com.binc.gastapp.core.wear.WearPaths
import com.binc.gastapp.notifications.AppNotifier
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Recibe los mensajes del reloj (GastappWearListenerService de MAUI, mismas rutas).
 *
 * Play Services arranca este servicio al llegar un mensaje aunque la app este cerrada:
 * no hace falta socket ni FCM. onMessageReceived corre en un hilo secundario, y el
 * servicio sigue vivo mientras no regrese; por eso se espera con runBlocking en vez de
 * lanzar y olvidar: si se soltara, el proceso podria morir a media llamada a Device/Link
 * y el reloj se quedaria sin veredicto.
 *
 * La entrega SOLO ocurre entre apps con el mismo applicationId y la misma firma.
 */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    @Inject lateinit var channel: WearChannel

    @Inject lateinit var pairing: WatchPairing

    @Inject lateinit var importer: WatchExpenseImporter

    @Inject lateinit var notifier: AppNotifier

    @Inject lateinit var events: WearEvents

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.i(TAG, "Mensaje del reloj: ${messageEvent.path}")
        val source = messageEvent.sourceNodeId

        when (messageEvent.path) {
            // El reloj mide el viaje de ida y vuelta: confirma que el canal va en los dos sentidos.
            WearPaths.PING -> runBlocking { channel.reply(source, WearPaths.PONG, "") }

            WearPaths.PAIR -> runBlocking {
                val verdict = pairing.pair(messageEvent.data.toString(Charsets.UTF_8))
                Log.i(TAG, "Vinculacion automatica: $verdict")
                channel.reply(source, WearPaths.PAIR_RESULT, verdict)
            }

            // El reloj se desvinculo por su cuenta: si Ajustes esta abierto, recarga la lista.
            WearPaths.UNLINKED -> events.emit(WearEvent.DevicesChanged)

            WearPaths.EXPENSE -> runBlocking { registerExpense(messageEvent.data.toString(Charsets.UTF_8)) }
        }
    }

    private suspend fun registerExpense(body: String) {
        val payload = runCatching { WearJson.decodeFromString(WearExpensePayload.serializer(), body) }
            .onFailure { Log.w(TAG, "Aviso de gasto ilegible: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.spendingId.isNotBlank() }
            ?: return

        try {
            Log.i(TAG, "Gasto del reloj ${payload.spendingId}: ${importer.import(payload)}")
        } catch (e: Exception) {
            // Que falle no pierde el gasto: el reloj lo sube igual al API.
            Log.w(TAG, "No se pudo registrar el gasto del reloj: ${e.message}")
        }
        notifier.showWatchExpense(watchExpenseText(payload))
    }

    private companion object {
        const val TAG = "GastappCanal"
    }
}
