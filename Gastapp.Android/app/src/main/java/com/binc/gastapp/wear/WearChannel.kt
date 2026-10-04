package com.binc.gastapp.wear

import android.content.Context
import android.util.Log
import com.binc.gastapp.core.wear.WearPaths
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Lo que el telefono le manda al reloj por la Wearable Data Layer (WearChannel de MAUI).
 * Nada de esto es indispensable: si falla, el reloj se entera igual la proxima vez que
 * llame al API. Por eso ningun metodo lanza.
 */
interface WearChannel {
    /** Avisa a los relojes conectados que [deviceId] perdio el acceso. */
    suspend fun notifyRevoked(deviceId: String): Boolean

    /** Publica un DataItem con [json]; llega aunque el reloj este lejos (al reconectar). */
    suspend fun putData(path: String, json: String): Boolean

    /** Responde un mensaje al nodo que lo mando. */
    suspend fun reply(nodeId: String, path: String, body: String)
}

/**
 * Recordatorio: la entrega solo ocurre entre apps con el mismo applicationId y la misma
 * firma. Si deja de llegar nada sin ningun error, empieza por comprobar eso.
 */
@Singleton
class PlayServicesWearChannel @Inject constructor(
    @ApplicationContext private val context: Context,
) : WearChannel {

    override suspend fun notifyRevoked(deviceId: String): Boolean {
        if (deviceId.isBlank()) return false
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.i(TAG, "Sin relojes conectados a los que avisar.")
                return false
            }
            val messages = Wearable.getMessageClient(context)
            // A todos: con varios relojes en la cuenta, cada uno compara el deviceId con el
            // suyo y solo actua el que coincide.
            nodes.forEach { messages.sendMessage(it.id, WearPaths.REVOKED, deviceId.toByteArray(Charsets.UTF_8)).await() }
            Log.i(TAG, "Aviso de revocacion enviado a ${nodes.size} reloj(es).")
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo avisar al reloj: ${e.message}")
            false
        }
    }

    override suspend fun putData(path: String, json: String): Boolean = try {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString(WearPaths.DATA_KEY_JSON, json)
            dataMap.putLong(WearPaths.DATA_KEY_STAMP, System.currentTimeMillis())
        }.asPutDataRequest()
            // Sin urgente, la Data Layer puede tardar hasta media hora en entregarlo.
            .setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        Log.i(TAG, "Datos empujados a $path (${json.length} car.)")
        true
    } catch (e: Exception) {
        Log.w(TAG, "No se pudieron empujar datos a $path: ${e.message}")
        false
    }

    override suspend fun reply(nodeId: String, path: String, body: String) {
        try {
            Wearable.getMessageClient(context).sendMessage(nodeId, path, body.toByteArray(Charsets.UTF_8)).await()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo responder $path al reloj: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "GastappCanal"
    }
}
