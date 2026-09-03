package com.binc.gastapp.wo.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.data.local.DaySpendingEntity
import com.binc.gastapp.wo.data.local.ExpenseEntity
import com.binc.gastapp.wo.data.local.SummaryEntity
import com.binc.gastapp.wo.data.wear.ChannelTest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** Una linea de la lista del dia. */
data class SpendingRow(
    val id: String,
    val title: String,
    val amount: Double,
    val occurredAt: Long,
    /** Capturado en el reloj y todavia sin subir. */
    val pending: Boolean
)

data class HomeState(
    val total: Double = 0.0,
    val count: Int = 0,
    val rows: List<SpendingRow> = emptyList(),
    /** Gastos locales sin subir, incluidos los de dias anteriores. */
    val pending: Int = 0,
    /** Rechazados de forma permanente. Nada los reintenta salvo el boton de sincronizar. */
    val failed: Int = 0,
    val refreshing: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val unlinking: Boolean = false,
    /** Prueba del canal Bluetooth con el telefono. Temporal, para validar la Fase 0. */
    val testingChannel: Boolean = false,
    val channelMessage: String? = null
) {
    val allSynced: Boolean get() = pending == 0 && failed == 0
}

/**
 * Estado de la pantalla principal y de la de opciones. Lee siempre de la base local
 * (asi la lista aparece al instante y sin red) y sale a la API solo para refrescar.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GastappApp

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var mensajeJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                app.repository.observeSummary(),
                app.repository.observeDaySpendings(),
                app.repository.observeUnsynced(),
                app.repository.observeFailed()
            ) { resumen, delDia, pendientes, fallidos ->
                construir(resumen, delDia, pendientes, fallidos)
            }.collect { datos ->
                _state.update { actual ->
                    actual.copy(
                        total = datos.total,
                        count = datos.count,
                        rows = datos.rows,
                        pending = datos.pending,
                        failed = datos.failed
                    )
                }
            }
        }
    }

    /** Refresco silencioso al abrir la app: la cache ya se ve, esto solo la pone al dia. */
    fun refresh() {
        if (_state.value.refreshing) return

        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }

            // Una por una y no en el mismo runCatching: si fallaba la primera, la
            // segunda ni se ejecutaba. Eso escondia el 401 con el que el reloj se
            // entera de que lo revocaron desde el telefono, y por eso seguia
            // mostrandose como vinculado.
            runCatching { app.repository.refreshDayExpenses() }
                .onFailure { Log.i(TAG, "No se pudieron refrescar los gastos: ${it.message}") }

            runCatching { app.repository.refreshSummary() }
                .onFailure { Log.i(TAG, "No se pudo refrescar el resumen: ${it.message}") }

            _state.update { it.copy(refreshing = false) }
        }
    }

    /**
     * Sincronizacion a peticion del usuario. A diferencia del SyncWorker, aqui si se
     * espera el resultado: el usuario esta mirando la pantalla y pidio saber.
     */
    fun syncNow() {
        if (_state.value.syncing) return

        viewModelScope.launch {
            mensajeJob?.cancel()
            _state.update { it.copy(syncing = true, syncMessage = null) }

            val resultado = runCatching {
                // El usuario pidio reintentar: los dados por perdidos vuelven a la cola.
                app.repository.retryFailed()

                val subidos = app.repository.pushPending()
                app.repository.refreshDayExpenses()
                app.repository.refreshSummary()
                subidos
            }

            val mensaje = resultado.fold(
                onSuccess = { subidos ->
                    when (subidos) {
                        0 -> "Todo estaba al día"
                        1 -> "1 gasto enviado"
                        else -> "$subidos gastos enviados"
                    }
                },
                onFailure = {
                    Log.i(TAG, "Sincronizacion manual fallida: ${it.message}")
                    "No se pudo conectar"
                }
            )

            _state.update { it.copy(syncing = false, syncMessage = mensaje) }

            mensajeJob = launch {
                delay(MILIS_MENSAJE)
                _state.update { it.copy(syncMessage = null) }
            }
        }
    }

    /**
     * Prueba el canal con el telefono: manda un ping y espera el pong. Es temporal,
     * para validar la Fase 0; se quita cuando el canal ya se use de verdad.
     */
    fun testChannel() {
        if (_state.value.testingChannel) return

        viewModelScope.launch {
            mensajeJob?.cancel()
            _state.update { it.copy(testingChannel = true, channelMessage = null) }

            val mensaje = when (val resultado = app.phoneChannel.test()) {
                is ChannelTest.Ok -> "OK · ${resultado.deviceName} · ${resultado.millis} ms"
                ChannelTest.NoPhone -> "Sin teléfono emparejado"
                is ChannelTest.NoReply -> "${resultado.deviceName} no responde"
                is ChannelTest.Failure -> resultado.message
            }

            _state.update { it.copy(testingChannel = false, channelMessage = mensaje) }

            mensajeJob = launch {
                delay(MILIS_MENSAJE_CANAL)
                _state.update { it.copy(channelMessage = null) }
            }
        }
    }

    /**
     * Desvincula el reloj.
     *
     * El trabajo vive en [GastappApp.unlinkDevice] y no aqui: en un reloj la pantalla
     * se apaga en segundos y con ella se cancelaria este viewModelScope a media
     * desvinculacion. Al apagar GastappApp.sessionActive la MainActivity vuelve sola
     * a la pantalla de emparejamiento.
     */
    fun unlink() {
        if (_state.value.unlinking) return

        _state.update { it.copy(unlinking = true, syncMessage = null) }
        app.unlinkDevice()
    }

    private fun construir(
        resumen: SummaryEntity?,
        delDia: List<DaySpendingEntity>,
        pendientes: List<ExpenseEntity>,
        fallidos: Int
    ): HomeState {
        val inicioDelDia = inicioDelDiaLocal()

        // La cache puede haber quedado de ayer. Vale mas mostrar cero que un dato viejo
        // que el usuario leeria como el de hoy.
        val delDiaVigentes = delDia.filter { it.occurredAt >= inicioDelDia }
        val resumenVigente = resumen?.takeIf { it.updatedAt >= inicioDelDia }

        // Un pendiente puede haberse subido ya y no estar todavia en la cache del dia:
        // descartarlo por id evita que la misma compra salga dos veces.
        val yaEnServidor = delDiaVigentes.mapTo(mutableSetOf()) { it.spendingId }
        val pendientesDeHoy = pendientes.filter {
            it.occurredAt >= inicioDelDia && it.id !in yaEnServidor
        }

        val filas = buildList {
            delDiaVigentes.forEach {
                add(
                    SpendingRow(
                        id = it.spendingId,
                        title = it.categoryName?.takeIf { nombre -> nombre.isNotBlank() } ?: it.title,
                        amount = it.amount,
                        occurredAt = it.occurredAt,
                        pending = false
                    )
                )
            }
            pendientesDeHoy.forEach {
                add(
                    SpendingRow(
                        id = it.id,
                        title = it.title,
                        amount = it.amount,
                        occurredAt = it.occurredAt,
                        pending = true
                    )
                )
            }
        }.sortedByDescending { it.occurredAt }

        // El resumen del servidor no cuenta lo que aun no sube. Sumarlo aqui evita que
        // el total contradiga a la lista que esta justo debajo.
        return HomeState(
            total = (resumenVigente?.total ?: 0.0) + pendientesDeHoy.sumOf { it.amount },
            count = (resumenVigente?.count ?: 0) + pendientesDeHoy.size,
            rows = filas,
            pending = pendientes.size,
            failed = fallidos
        )
    }

    private fun inicioDelDiaLocal(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val TAG = "GastappHome"
        const val MILIS_MENSAJE = 3500L

        // El resultado del canal es mas largo de leer que "3 gastos enviados".
        const val MILIS_MENSAJE_CANAL = 6000L
    }
}
