package com.binc.gastapp.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.session.SessionRepository
import com.binc.gastapp.data.session.SessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordHidden: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    /** Ya se entro; la compuerta de sesion cambia a la app sola. */
    val loggedIn: Boolean = false,
)

/** StartPageViewModel de MAUI: la hoja de inicio de sesion. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** Correo que ya se conoce (sesion vencida, o recien cambio la contrasena). No pisa lo escrito. */
    fun prefillEmail(email: String) {
        if (email.isNotBlank() && _state.value.email.isBlank()) _state.update { it.copy(email = email) }
    }

    /** Correo con el que se acaba de cambiar la contrasena: reemplaza lo escrito. */
    fun useEmail(email: String) = _state.update { it.copy(email = email, password = "", error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun togglePasswordVisibility() = _state.update { it.copy(passwordHidden = !it.passwordHidden) }

    fun login() {
        val current = _state.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Ingresa tu correo y contraseña para continuar.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = sessions.login(current.email, current.password)) {
                SessionResult.Ok -> _state.update { LoginUiState(email = it.email, loggedIn = true) }
                is SessionResult.Failed -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }
}
