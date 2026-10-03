package com.binc.gastapp.wo.data.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageClient
import com.binc.gastapp.wo.data.remote.WearExpensePayload
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resultado de la prueba de canal, pensado para mostrarse tal cual en pantalla. */
sealed interface ChannelTest {
    data class Ok(val deviceName: String, val millis: Long) : ChannelTest
    data object NoPhone : ChannelTest
    data class NoReply(val deviceName: String) : ChannelTest
    data class Failure(val message: String) : ChannelTest
}

/**
 * Resultado de pedirle al telefono que vincule el reloj por su cuenta.
 *
 * [NotDelivered] no es un error: significa que hay que teclear el codigo a mano,
 * que es justo lo que sigue funcionando como respaldo.
 */
sealed interface PairingRelay {
    data object Linked : PairingRelay
    data object NotDelivered : PairingRelay
    data class Rejected(val message: String) : PairingRelay
    data object NoAnswer : PairingRelay
}

/**
 * Canal con la app del telefono por la Wearable Data Layer.
 *
 * Recordatorio para cuando esto deje de funcionar sin dar ningun error: la Data Layer
 * SOLO entrega entre apps con el mismo applicationId y la misma firma. Si alguien
 * cambia cualquiera de las dos cosas, los mensajes se pierden en silencio.
 */
class PhoneChannel(private val context: Context) {

    private val json = Json { encodeDefaults = true }

    /**
     * Manda un ping al telefono y espera el pong. Es la prueba de que el canal existe
     * de ida y de vuelta, no solo de que hay un dispositivo emparejado.
     */
    suspend fun test(): ChannelTest = withContext(Dispatchers.IO) {
        val nodos = try {
            Wearable.getNodeClient(context).connectedNodes.esperar()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron listar los nodos: ${e.message}")
            return@withContext ChannelTest.Failure(e.message ?: "Error al buscar el teléfono")
        }

        val destino = nodos.firstOrNull() ?: return@withContext ChannelTest.NoPhone

        val messageClient = Wearable.getMessageClient(context)
        val pong = CompletableDeferred<Unit>()

        // Listener temporal en vez de un WearableListenerService: la respuesta solo
        // interesa mientras dura la prueba.
        val listener = MessageClient.OnMessageReceivedListener { evento ->
            if (evento.path == RUTA_PONG) pong.complete(Unit)
        }
        messageClient.addListener(listener)

        try {
            val inicio = System.currentTimeMillis()
            messageClient.sendMessage(destino.id, RUTA_PING, ByteArray(0)).esperar()

            val llego = withTimeoutOrNull(MILIS_ESPERA) { pong.await() } != null
            val tardo = System.currentTimeMillis() - inicio

            Log.i(TAG, "Ping a '${destino.displayName}': respuesta=$llego en ${tardo}ms")

            if (llego) ChannelTest.Ok(destino.displayName, tardo)
            else ChannelTest.NoReply(destino.displayName)
        } catch (e: Exception) {
            // Lo tipico aqui: la app del telefono no esta instalada, o su
            // applicationId/firma no coinciden con los del reloj.
            Log.w(TAG, "No se pudo enviar el ping: ${e.message}")
            ChannelTest.Failure(e.message ?: "No se pudo enviar")
        } finally {
            messageClient.removeListener(listener)
        }
    }

    /**
     * Le pasa al telefono el codigo de emparejamiento para que llame el a Device/Link,
     * y espera su veredicto.
     *
     * El reloj sigue sondeando Device/Token por su cuenta mientras tanto, asi que si
     * esto sale bien la vinculacion se completa sola. Lo unico que aporta la respuesta
     * es poder decirle al usuario que fallo cuando falla.
     */
    suspend fun requestPairing(userCode: String): PairingRelay = withContext(Dispatchers.IO) {
        val destino = try {
            Wearable.getNodeClient(context).connectedNodes.esperar().firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron listar los nodos: ${e.message}")
            null
        } ?: return@withContext PairingRelay.NotDelivered

        val messageClient = Wearable.getMessageClient(context)
        val respuesta = CompletableDeferred<String>()

        val listener = MessageClient.OnMessageReceivedListener { evento ->
            if (evento.path == RUTA_PAIR_RESULT) {
                respuesta.complete(String(evento.data, Charsets.UTF_8))
            }
        }
        messageClient.addListener(listener)

        try {
            messageClient.sendMessage(
                destino.id,
                RUTA_PAIR,
                userCode.toByteArray(Charsets.UTF_8)
            ).esperar()

            Log.i(TAG, "Codigo enviado a '${destino.displayName}'. Esperando veredicto.")

            // Margen amplio a proposito: Device/Link lo llama el telefono contra
            // Render, que dormido tarda cerca de un minuto en despertar.
            val veredicto = withTimeoutOrNull(MILIS_VINCULACION) { respuesta.await() }

            when {
                veredicto == null -> PairingRelay.NoAnswer
                veredicto == RESULTADO_OK -> PairingRelay.Linked
                else -> PairingRelay.Rejected(veredicto)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo enviar el codigo: ${e.message}")
            PairingRelay.NotDelivered
        } finally {
            messageClient.removeListener(listener)
        }
    }

    /**
     * Manda al telefono un gasto recien capturado, entero.
     *
     * No va solo lo justo para la notificacion: el telefono inserta el gasto en su
     * base local, asi que aparece en su lista aunque ninguno de los dos tenga
     * internet. El gasto sube igual al API por su cuenta con el SyncWorker; son dos
     * caminos independientes y el servidor deduplica por spendingId.
     */
    suspend fun notifyExpense(payload: WearExpensePayload) {
        runCatching {
            val nodos = Wearable.getNodeClient(context).connectedNodes.esperar()
            if (nodos.isEmpty()) return@runCatching

            val cuerpo = json.encodeToString(WearExpensePayload.serializer(), payload)
                .toByteArray(Charsets.UTF_8)
            val messageClient = Wearable.getMessageClient(context)

            nodos.forEach { nodo ->
                messageClient.sendMessage(nodo.id, RUTA_EXPENSE, cuerpo).esperar()
            }

            Log.i(TAG, "Gasto avisado al telefono: ${payload.spendingId}")
        }.onFailure { Log.i(TAG, "No se pudo avisar del gasto: ${it.message}") }
    }

    /**
     * Avisa al telefono de que este reloj se acaba de desvincular, para que refresque
     * su lista de dispositivos sin que el usuario tenga que recargarla.
     *
     * No espera respuesta ni le importa fallar: la desvinculacion ya ocurrio.
     */
    suspend fun notifyUnlinked() {
        runCatching {
            val nodos = Wearable.getNodeClient(context).connectedNodes.esperar()
            val messageClient = Wearable.getMessageClient(context)

            nodos.forEach { nodo ->
                messageClient.sendMessage(nodo.id, RUTA_UNLINKED, ByteArray(0)).esperar()
            }

            Log.i(TAG, "Aviso de desvinculacion enviado a ${nodos.size} nodo(s).")
        }.onFailure { Log.i(TAG, "No se pudo avisar al telefono: ${it.message}") }
    }

    /**
     * Convierte una Task de Play Services en suspend. Se hace a mano para no arrastrar
     * kotlinx-coroutines-play-services por tres llamadas.
     */
    private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { valor -> cont.resume(valor) }
        addOnFailureListener { error -> cont.resumeWithException(error) }
        addOnCanceledListener { cont.cancel() }
    }

    companion object {
        private const val TAG = "GastappCanal"
        private const val MILIS_ESPERA = 5_000L
        private const val MILIS_VINCULACION = 60_000L

        const val RUTA_PING = "/gastapp/ping"
        const val RUTA_PONG = "/gastapp/pong"

        /** Reloj -> telefono, con el userCode como cuerpo. */
        const val RUTA_PAIR = "/gastapp/pair"

        /** Telefono -> reloj: "ok" o el motivo del fallo, para mostrarlo tal cual. */
        const val RUTA_PAIR_RESULT = "/gastapp/pair/result"

        const val RESULTADO_OK = "ok"

        /** Telefono -> reloj, con el deviceId revocado como cuerpo. */
        const val RUTA_REVOKED = "/gastapp/revoked"

        /** Reloj -> telefono, sin cuerpo: «me acabo de desvincular». */
        const val RUTA_UNLINKED = "/gastapp/unlinked"

        /** Reloj -> telefono, cuerpo WearExpensePayload en JSON. */
        const val RUTA_EXPENSE = "/gastapp/expense"
    }
}
