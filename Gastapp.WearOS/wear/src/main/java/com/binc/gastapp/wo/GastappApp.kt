package com.binc.gastapp.wo

import android.app.Application
import com.binc.gastapp.wo.data.ExpenseRepository
import com.binc.gastapp.wo.data.auth.PairingRepository
import com.binc.gastapp.wo.data.auth.TokenStore
import com.binc.gastapp.wo.data.local.AppDatabase
import com.binc.gastapp.wo.data.remote.DeviceCategoryDto
import com.binc.gastapp.wo.data.remote.WearExpensePayload
import com.binc.gastapp.wo.data.remote.WearTodayPayload
import com.binc.gastapp.wo.data.wear.PhoneChannel
import com.binc.gastapp.wo.tile.ExpenseTileService
import androidx.wear.tiles.TileService
import com.binc.gastapp.wo.data.remote.GastappApi
import com.binc.gastapp.wo.data.remote.NetworkModule
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias hecho a mano. El proyecto es chico y no justifica Hilt.
 */
class GastappApp : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var api: GastappApi
        private set

    lateinit var repository: ExpenseRepository
        private set

    lateinit var pairingRepository: PairingRepository
        private set

    /** Canal con la app del telefono por Bluetooth (Wearable Data Layer). */
    lateinit var phoneChannel: PhoneChannel
        private set

    /** Se vuelve false cuando el refresh falla: el reloj quedo desvinculado. */
    val sessionActive = MutableStateFlow(true)

    /**
     * true justo despues de una desvinculacion a mano. Sirve para NO salir corriendo a
     * pedir un codigo nuevo: el usuario pidio soltar el reloj, no volver a vincularlo,
     * y hacerlo dejaba la pantalla en "Preparando..." mientras el API despertaba.
     */
    val justUnlinked = MutableStateFlow(false)

    /**
     * Para trabajo que NO debe morir con la pantalla. En un reloj la Activity se
     * destruye en cuanto bajas la muñeca, y con ella cualquier viewModelScope.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        tokenStore = TokenStore(this)
        api = NetworkModule.create(tokenStore) { sessionActive.value = false }

        val db = AppDatabase.get(this)
        repository = ExpenseRepository(
            api,
            db.expenseDao(),
            db.categoryDao(),
            db.summaryDao(),
            db.daySpendingDao()
        )
        pairingRepository = PairingRepository(api, tokenStore)
        phoneChannel = PhoneChannel(this)
    }

    /**
     * Desvincula el reloj.
     *
     * Regla: NADA de red por delante del borrado local. Ya fallo dos veces por eso, y
     * el sintoma siempre fue el mismo: el usuario pulsa, la llamada se queda esperando
     * al API, y el reloj sigue vinculado.
     *
     * El orden es memoria -> disco -> red, y cada paso de disco o red va con su propio
     * limite de tiempo para que ninguno pueda dejar la desvinculacion a medias.
     */
    fun unlinkDevice(): Job = appScope.launch {
        Log.i(TAG, "Desvinculacion solicitada.")

        // Paso 1, en memoria y sin poder fallar: esto es lo que devuelve la pantalla
        // al emparejamiento. En cuanto se ejecuta, para el usuario ya esta desvinculado.
        sessionActive.value = false
        justUnlinked.value = true

        // Hay que leer las credenciales antes de borrarlas: son las que autentican la
        // revocacion en el servidor. Con limite, porque toca DataStore y AndroidKeyStore.
        val credenciales = withTimeoutOrNull(MILIS_DISCO) {
            runCatching { pairingRepository.readCredentials() }.getOrNull()
        }

        // Paso 2, disco.
        borrarSesionLocal()
        refrescarTile()

        // Paso 3, red. Puramente "por si sale": el reloj ya esta desvinculado y aqui
        // solo se evita dejarlo colgado en la lista del telefono.
        if (credenciales == null) {
            Log.w(TAG, "Sin credenciales guardadas: no se revoca en el servidor.")
        } else {
            val revocado = withTimeoutOrNull(MILIS_RED) {
                pairingRepository.revokeOnServer(credenciales)
            } ?: false
            Log.i(TAG, "Revocado en el servidor: $revocado")
        }

        // Avisar al telefono para que refresque su lista sin tener que recargarla a
        // mano. Va al final: es cortesia, no parte de la desvinculacion.
        withTimeoutOrNull(MILIS_RED) { phoneChannel.notifyUnlinked() }
    }

    /**
     * Aplica el estado del dia que empujo el telefono.
     *
     * Va en el appScope porque lo llama un servicio que el sistema puede parar en
     * cuanto vuelve de su callback.
     */
    fun applyPushedToday(payload: WearTodayPayload): Job = appScope.launch {
        runCatching { repository.applyPushedToday(payload) }
            .onSuccess {
                Log.i(TAG, "Dia actualizado desde el telefono: ${payload.count} gasto(s).")
                refrescarTile()
            }
            .onFailure { Log.w(TAG, "No se pudo aplicar el dia empujado: ${it.message}") }
    }

    /**
     * Pide al sistema que vuelva a dibujar el tile.
     *
     * Hace falta aunque los datos ya esten en la base: el tile solo se redibuja por su
     * cuenta cada 15 minutos (setFreshnessIntervalMillis), asi que sin esto los datos
     * llegaban al instante pero seguian sin verse.
     */
    fun refrescarTile() {
        runCatching {
            TileService.getUpdater(this).requestUpdate(ExpenseTileService::class.java)
        }.onFailure { Log.i(TAG, "No se pudo refrescar el tile: ${it.message}") }
    }

    /** Aplica las categorias que empujo el telefono. */
    fun applyPushedCategories(categorias: List<DeviceCategoryDto>): Job = appScope.launch {
        runCatching { repository.applyPushedCategories(categorias) }
            .onSuccess { Log.i(TAG, "Categorias actualizadas desde el telefono: ${categorias.size}.") }
            .onFailure { Log.w(TAG, "No se pudieron aplicar las categorias: ${it.message}") }
    }

    /**
     * Avisa al telefono de un gasto recien capturado.
     *
     * Vive aqui y no en QuickAddActivity porque esa pantalla se cierra a los dos
     * segundos: su scope moriria a mitad del envio.
     */
    fun notifyExpenseToPhone(payload: WearExpensePayload): Job = appScope.launch {
        withTimeoutOrNull(MILIS_RED) { phoneChannel.notifyExpense(payload) }
    }

    /**
     * Se revoco este reloj desde el telefono. Solo hay que soltar la sesion local: el
     * servidor ya lo hizo por su lado.
     *
     * Antes esto solo se detectaba cuando una llamada al API rebotaba un 401, asi que
     * el reloj podia pasarse horas creyendose vinculado.
     */
    fun onRevokedRemotely(deviceId: String): Job = appScope.launch {
        val propio = withTimeoutOrNull(MILIS_DISCO) {
            runCatching { tokenStore.readDeviceId() }.getOrNull()
        }

        // Con varios relojes en la cuenta, el aviso llega a todos: cada uno decide
        // mirando si el revocado es el suyo.
        if (propio == null || propio != deviceId) {
            Log.i(TAG, "Revocacion ajena ($deviceId), no es este reloj.")
            return@launch
        }

        Log.i(TAG, "El telefono revoco este reloj.")
        sessionActive.value = false
        justUnlinked.value = true
        borrarSesionLocal()

        // El tile muestra el estado de la sesion: sin esto seguiria enseñando el total
        // de una cuenta que ya no esta vinculada.
        refrescarTile()
    }

    /** Borrado local con limite y NonCancellable, para que nunca quede a medias. */
    private suspend fun borrarSesionLocal() {
        withContext(NonCancellable) {
            withTimeoutOrNull(MILIS_DISCO) {
                tokenStore.clear()
                repository.clearAllLocalData()
            } ?: Log.w(TAG, "El borrado local tardo demasiado; la sesion ya esta apagada.")
        }
        Log.i(TAG, "Sesion local borrada.")
    }

    private companion object {
        const val TAG = "GastappPairing"
        const val MILIS_DISCO = 3_000L
        const val MILIS_RED = 60_000L
    }
}
