package com.binc.gastapp.wo.ui.pairing

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.data.auth.PollResult
import com.binc.gastapp.wo.data.wear.ChannelTest
import com.binc.gastapp.wo.data.wear.PairingRelay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PairingState {
    data object Idle : PairingState
    data object RequestingCode : PairingState
    data class ShowingCode(
        val userCode: String,
        val secondsLeft: Int
    ) : PairingState
    data object Expired : PairingState
    /** Se acaba de desvincular a mano y se espera a que el usuario decida. */
    data object Unlinked : PairingState
    data object Success : PairingState
    data class Error(val message: String) : PairingState
}

/** Estado de la prueba del canal Bluetooth. Temporal, para validar la Fase 0. */
data class ChannelState(
    val testing: Boolean = false,
    val message: String? = null
)

class PairingViewModel(app: Application) : AndroidViewModel(app) {

    private val gastapp = app as GastappApp
    private val pairing = gastapp.pairingRepository

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _channelState = MutableStateFlow(ChannelState())
    val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

    /**
     * Que esta haciendo el telefono con el codigo. null = no hay nada que contar y la
     * pantalla se comporta como siempre: el usuario teclea el codigo.
     */
    private val _autoPairStatus = MutableStateFlow<String?>(null)
    val autoPairStatus: StateFlow<String?> = _autoPairStatus.asStateFlow()

    private var autoPairJob: Job? = null

    /**
     * Prueba el canal con el telefono desde esta pantalla.
     *
     * Tambien esta en Opciones, pero ahi solo se llega con el reloj ya vinculado, y
     * justo tras instalar no hay sesion. Ademas es donde hara falta en la Fase 1,
     * cuando el codigo viaje por aqui en vez de teclearse.
     */
    fun testChannel() {
        if (_channelState.value.testing) return

        viewModelScope.launch {
            _channelState.value = ChannelState(testing = true)

            val mensaje = when (val resultado = gastapp.phoneChannel.test()) {
                is ChannelTest.Ok -> "OK · ${resultado.deviceName} · ${resultado.millis} ms"
                ChannelTest.NoPhone -> "Sin teléfono emparejado"
                is ChannelTest.NoReply -> "${resultado.deviceName} no responde"
                is ChannelTest.Failure -> resultado.message
            }

            _channelState.value = ChannelState(testing = false, message = mensaje)
        }
    }

    private var deviceCode: String? = null
    private var expiresAtMillis: Long = 0
    private var intervalSeconds: Int = 5
    private var pollingJob: Job? = null
    private var countdownJob: Job? = null

    fun start() {
        if (_state.value is PairingState.ShowingCode) {
            // Se regresa a la pantalla con un codigo todavia vigente: solo reanudar.
            resumePolling()
            return
        }
        requestCode()
    }

    /** Pantalla de reposo tras desvincular: sin sondeo y sin tocar la red. */
    fun showUnlinked() {
        cancelJobs()
        deviceCode = null
        _state.value = PairingState.Unlinked
    }

    fun requestCode() {
        cancelJobs()
        _state.value = PairingState.RequestingCode

        viewModelScope.launch {
            try {
                val respuesta = pairing.requestCode(nombreDelReloj())
                deviceCode = respuesta.deviceCode
                intervalSeconds = respuesta.interval
                expiresAtMillis = System.currentTimeMillis() + respuesta.expiresIn * 1000L

                Log.i(TAG, "Mostrando en pantalla: '${respuesta.userCode}'")
                _state.value = PairingState.ShowingCode(respuesta.userCode, respuesta.expiresIn)
                resumePolling()
                pedirVinculacionAlTelefono(respuesta.userCode)
            } catch (e: Exception) {
                // La primera peticion puede tardar casi un minuto si la API estaba
                // dormida en el plan gratuito de Render.
                Log.e(TAG, "No se pudo pedir el codigo: ${e.javaClass.simpleName}: ${e.message}", e)
                _state.value = PairingState.Error("No se pudo conectar. Intenta de nuevo.")
            }
        }
    }

    /**
     * Le pasa el codigo al telefono para que vincule sin que haya que teclearlo.
     *
     * Corre en paralelo al sondeo y no lo sustituye: si el telefono vincula, el sondeo
     * recoge los tokens igual que si el codigo se hubiera tecleado. Por eso, si esto
     * falla, no pasa nada mas que seguir viendo el codigo en pantalla.
     */
    private fun pedirVinculacionAlTelefono(userCode: String) {
        autoPairJob?.cancel()
        _autoPairStatus.value = null

        autoPairJob = viewModelScope.launch {
            when (val resultado = gastapp.phoneChannel.requestPairing(userCode)) {
                PairingRelay.Linked -> {
                    Log.i(TAG, "El telefono vinculo el reloj.")
                    _autoPairStatus.value = "Vinculando..."
                }

                // Sin telefono a la vista: ni se menciona, el codigo ya esta en pantalla.
                PairingRelay.NotDelivered -> Log.i(TAG, "Sin telefono para vincular solo.")

                PairingRelay.NoAnswer -> {
                    Log.i(TAG, "El telefono no contesto a tiempo.")
                    _autoPairStatus.value = "Teclea el código en el teléfono"
                }

                is PairingRelay.Rejected -> {
                    Log.w(TAG, "El telefono rechazo la vinculacion: ${resultado.message}")
                    _autoPairStatus.value = resultado.message
                }
            }
        }
    }

    /** Se llama desde onStart. Reanuda el sondeo tras apagarse la pantalla. */
    fun resumePolling() {
        val codigo = deviceCode ?: return
        if (pollingJob?.isActive == true) return
        if (System.currentTimeMillis() >= expiresAtMillis) {
            _state.value = PairingState.Expired
            return
        }

        countdownJob = viewModelScope.launch {
            while (isActive) {
                val restante = ((expiresAtMillis - System.currentTimeMillis()) / 1000).toInt()
                val actual = _state.value
                if (actual !is PairingState.ShowingCode) break
                if (restante <= 0) {
                    _state.value = PairingState.Expired
                    break
                }
                _state.value = actual.copy(secondsLeft = restante)
                delay(1000)
            }
        }

        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalSeconds * 1000L)

                if (System.currentTimeMillis() >= expiresAtMillis) {
                    _state.value = PairingState.Expired
                    break
                }

                when (val resultado = pairing.poll(codigo)) {
                    PollResult.Linked -> {
                        // Reabre la sesion sin reiniciar la app. Hace falta cuando se
                        // vuelve a vincular tras desvincular a mano o tras perder el
                        // refresh token: sin esto MainActivity se quedaria en esta
                        // misma pantalla aunque ya haya credenciales.
                        gastapp.sessionActive.value = true
                        _state.value = PairingState.Success
                        terminarEmparejamiento()
                        break
                    }
                    PollResult.Pending -> Unit
                    // El servidor pide bajar el ritmo: subir el intervalo, como en RFC 8628.
                    PollResult.SlowDown -> {
                        intervalSeconds += 5
                        Log.i(TAG, "slow_down: el intervalo local sube a ${intervalSeconds}s")
                    }
                    PollResult.Expired -> {
                        _state.value = PairingState.Expired
                        terminarEmparejamiento()
                        break
                    }
                    PollResult.Denied -> {
                        _state.value = PairingState.Error("Vinculación rechazada.")
                        terminarEmparejamiento()
                        break
                    }
                    // Un fallo de red no cancela el emparejamiento: se sigue sondeando.
                    is PollResult.Failure -> Unit
                }
            }
        }
    }

    /**
     * Cierra el emparejamiento cuando el codigo ya no sirve, sea porque vinculo o
     * porque caduco.
     *
     * Sin esto quedaba un sondeo zombi: el bucle salia pero el deviceCode seguia
     * guardado, y el onStart siguiente (la Activity se recrea al cambiar de pantalla)
     * arrancaba otro sondeo con un codigo ya consumido, que el servidor contestaba
     * con expired_token.
     */
    private fun terminarEmparejamiento() {
        countdownJob?.cancel()
        countdownJob = null
        autoPairJob?.cancel()
        autoPairJob = null
        deviceCode = null
    }

    /**
     * Se llama desde onStop. Si el usuario baja la muñeca la pantalla se apaga, y
     * seguir sondeando solo gastaria bateria.
     */
    fun pausePolling() {
        pollingJob?.cancel()
        countdownJob?.cancel()
        pollingJob = null
        countdownJob = null
    }

    private fun cancelJobs() {
        pausePolling()
        // El codigo viejo ya no sirve, y con el sobra el veredicto que trajera.
        autoPairJob?.cancel()
        autoPairJob = null
        _autoPairStatus.value = null
        deviceCode = null
    }

    private companion object {
        const val TAG = "GastappPairing"
    }

    private fun nombreDelReloj(): String {
        val modelo = Build.MODEL?.trim().orEmpty()
        return if (modelo.isBlank()) "Reloj Wear OS" else modelo
    }

    override fun onCleared() {
        pausePolling()
        super.onCleared()
    }
}
