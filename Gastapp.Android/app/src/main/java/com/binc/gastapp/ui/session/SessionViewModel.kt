package com.binc.gastapp.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.local.DevSampleData
import com.binc.gastapp.data.session.SessionRepository
import com.binc.gastapp.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Compuerta de sesion, en el ViewModel de la Activity (ver GastappApp):
 *  - Sin datos en el telefono, o si el servidor respondio 401: pantalla de inicio.
 *  - Con datos (sesion vigente o vencida por tiempo): la app. Con sesion vencida se usa
 *    lo local y el inicio de sesion se abre desde el aviso de Ajustes o de Resumen
 *    ([requestLogin]), con opcion de volver sin entrar.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val sampleData: DevSampleData,
) : ViewModel() {

    /** null mientras se lee la base y DataStore: se queda el splash. */
    val state: StateFlow<SessionState?> = sessions.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _loginRequest = MutableStateFlow<LoginRequest?>(null)

    /** Inicio de sesion pedido con la sesion vencida; null si no se ha pedido. */
    val loginRequest: StateFlow<LoginRequest?> = _loginRequest.asStateFlow()

    fun requestLogin(email: String?) {
        _loginRequest.value = LoginRequest(email.orEmpty())
    }

    fun dismissLogin() {
        _loginRequest.value = null
    }

    /** Ya se entro: la compuerta deja pasar a la app. */
    fun onLoggedIn() {
        _loginRequest.value = null
    }

    fun logout() {
        viewModelScope.launch {
            sessions.logout()
            _loginRequest.value = null
        }
    }

    /** Solo debug: entra con la muestra de la Fase 2 (sin token: la app la trata como sesion vencida). */
    fun useSample() {
        viewModelScope.launch { sampleData.load() }
    }
}

/** Con que correo abrir el inicio de sesion. */
data class LoginRequest(val email: String)
