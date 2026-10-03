package com.binc.gastapp.wo.data.wear

import android.util.Log
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.data.remote.DeviceCategoryDto
import com.binc.gastapp.wo.data.remote.WearTodayPayload
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

/**
 * Recibe lo que manda la app del telefono.
 *
 * Es un servicio y no un listener temporal porque tiene que funcionar con la app del
 * reloj cerrada: Play Services lo arranca al llegar algo. Si la revocacion o los datos
 * del dia solo se escucharan con la pantalla abierta, el reloj seguiria mostrando
 * informacion vieja.
 */
class GastappWearListenerService : WearableListenerService() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        Log.i(TAG, "Mensaje del telefono: ${messageEvent.path}")

        when (messageEvent.path) {
            PhoneChannel.RUTA_REVOKED -> {
                val deviceId = String(messageEvent.data, Charsets.UTF_8)
                val app = applicationContext as GastappApp

                // El Job vive en el appScope de la Application, no en este servicio,
                // asi que sobrevive aunque el sistema lo pare al volver de aqui.
                app.onRevokedRemotely(deviceId)
            }
        }
    }

    /**
     * Datos empujados por el telefono. A diferencia de los mensajes, esto llega
     * tambien si el reloj estaba apagado o lejos: la Data Layer lo entrega al
     * reconectar.
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        val app = applicationContext as GastappApp

        // El buffer se cierra al volver de aqui, asi que hay que leerlo ya y no
        // dentro de la corrutina.
        val cargas = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { evento ->
                val ruta = evento.dataItem.uri.path ?: return@mapNotNull null
                val cuerpo = DataMapItem.fromDataItem(evento.dataItem)
                    .dataMap
                    .getString(CLAVE_JSON)
                    ?: return@mapNotNull null

                ruta to cuerpo
            }

        cargas.forEach { (ruta, cuerpo) ->
            Log.i(TAG, "Datos del telefono en $ruta (${cuerpo.length} car.)")

            when (ruta) {
                RUTA_HOY -> runCatching {
                    json.decodeFromString<WearTodayPayload>(cuerpo)
                }.onSuccess {
                    app.applyPushedToday(it)
                }.onFailure {
                    Log.w(TAG, "Payload del dia ilegible: ${it.message}")
                }

                RUTA_CATEGORIAS -> runCatching {
                    json.decodeFromString<List<DeviceCategoryDto>>(cuerpo)
                }.onSuccess {
                    app.applyPushedCategories(it)
                }.onFailure {
                    Log.w(TAG, "Payload de categorias ilegible: ${it.message}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "GastappCanal"

        /** Debe coincidir con lo que escribe WearChannel en el telefono. */
        const val CLAVE_JSON = "json"

        const val RUTA_HOY = "/gastapp/today"
        const val RUTA_CATEGORIAS = "/gastapp/categories"
    }
}
