package com.binc.gastapp.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.session.SessionRepository
import com.binc.gastapp.data.session.SessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResetStep { Email, Code, NewPassword, Done }

data class ForgotPasswordUiState(
    val step: ResetStep = ResetStep.Email,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordHidden: Boolean = true,
    val busy: Boolean = false,
    /** Desde cuando se puede pedir otro codigo; antes, "Reenviar codigo" espera. */
    val resendAvailableAt: Instant? = null,
    /** A que correo se mando el ultimo codigo. */
    val codeSentTo: String? = null,
    /** Mensaje de error del paso actual (StatusMessage de MAUI). */
    val error: String? = null,
    /** Aviso de un solo uso (Snackbar). */
    val message: String? = null,
)

/**
 * ForgetPasswordViewModel de MAUI: correo -> codigo -> contrasena nueva -> listo.
 *
 * La "contrasena temporal" del API (PasswordReset/temporary) no se ofrece, igual que en
 * MAUI: ninguna pantalla la usaba y cambia la contrasena de una cuenta con solo saber
 * su correo (hallazgo 19 del plan).
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onCodeChange(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun onNewPasswordChange(value: String) = _state.update { it.copy(newPassword = value, error = null) }

    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun togglePasswordVisibility() = _state.update { it.copy(passwordHidden = !it.passwordHidden) }

    fun messageShown() = _state.update { it.copy(message = null) }

    /**
     * Paso 1 y "Reenviar codigo" del paso 2. Despues de mandar uno hay que esperar 60 s,
     * como en el registro (MAUI aqui no tenia limite y el API ahora tambien lo exige).
     */
    fun sendCode() {
        val s = _state.value
        if (s.busy) return
        if (s.email.isBlank()) {
            _state.update { it.copy(error = "Ingresa tu correo para recibir el código de verificación.") }
            return
        }
        val waiting = s.resendAvailableAt?.let { clock.instant() < it } == true
        if (waiting && s.codeSentTo == s.email.trim()) {
            // Se regreso al paso 1 y se siguio con el mismo correo: el codigo de hace un
            // momento sigue sirviendo. En el paso 2 el boton ni siquiera esta activo.
            if (s.step == ResetStep.Email) _state.update { it.copy(step = ResetStep.Code) }
            return
        }
        run {
            when (val result = sessions.requestPasswordReset(s.email)) {
                SessionResult.Ok -> _state.update {
                    it.copy(
                        step = ResetStep.Code,
                        // Un codigo nuevo deja sin efecto el anterior.
                        code = if (s.step == ResetStep.Code) "" else it.code,
                        message = if (s.step == ResetStep.Code) "Te enviamos un código nuevo." else null,
                        resendAvailableAt = clock.instant().plusSeconds(CodeResendCooldownSeconds),
                        codeSentTo = s.email.trim(),
                    )
                }
                is SessionResult.Failed -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun verifyCode() {
        val s = _state.value
        if (s.busy) return
        if (s.code.isBlank()) {
            _state.update { it.copy(error = "Ingresa el código de verificación que recibiste en tu correo.") }
            return
        }
        run {
            when (val result = sessions.verifyPasswordReset(s.email, s.code)) {
                SessionResult.Ok -> _state.update { it.copy(step = ResetStep.NewPassword) }
                is SessionResult.Failed -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun resetPassword() {
        val s = _state.value
        if (s.busy) return
        ResetPasswordRules.error(s.newPassword, s.confirmPassword)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        run {
            when (val result = sessions.confirmPasswordReset(s.email, s.code, s.newPassword)) {
                SessionResult.Ok -> _state.update { it.copy(step = ResetStep.Done, newPassword = "", confirmPassword = "") }
                is SessionResult.Failed -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    /** Regresar un paso; false en el primero (la pantalla se cierra). */
    fun previous(): Boolean {
        val s = _state.value
        if (s.busy) return true
        val target = when (s.step) {
            ResetStep.Email, ResetStep.Done -> return false
            ResetStep.Code -> ResetStep.Email
            // El codigo ya se verifico: regresar al paso del codigo no sirve de nada.
            ResetStep.NewPassword -> ResetStep.Email
        }
        _state.update { it.copy(step = target, error = null, code = "") }
        return true
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
