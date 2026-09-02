package com.binc.gastapp.wo.ui.pairing

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.data.auth.PollResult
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
    data object Success : PairingState
    data class Error(val message: String) : PairingState
}

class PairingViewModel(app: Application) : AndroidViewModel(app) {

    private val pairing = (app as GastappApp).pairingRepository

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

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
            } catch (e: Exception) {
                // La primera peticion puede tardar casi un minuto si la API estaba
                // dormida en el plan gratuito de Render.
                Log.e(TAG, "No se pudo pedir el codigo: ${e.javaClass.simpleName}: ${e.message}", e)
                _state.value = PairingState.Error("No se pudo conectar. Intenta de nuevo.")
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
                        _state.value = PairingState.Success
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
                        break
                    }
                    PollResult.Denied -> {
                        _state.value = PairingState.Error("Vinculación rechazada.")
                        break
                    }
                    // Un fallo de red no cancela el emparejamiento: se sigue sondeando.
                    is PollResult.Failure -> Unit
                }
            }
        }
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
