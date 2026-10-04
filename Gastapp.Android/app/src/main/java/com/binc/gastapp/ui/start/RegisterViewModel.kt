package com.binc.gastapp.ui.start

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
import com.binc.gastapp.data.prefs.RegisterDraft
import com.binc.gastapp.data.prefs.RegisterDraftStore
import com.binc.gastapp.data.session.NewAccount
import com.binc.gastapp.data.session.SessionRepository
import com.binc.gastapp.data.session.SessionResult
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.components.toggledPayDays
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.parseAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pasos del asistente. El correo se confirma ANTES de pedir datos personales, como en MAUI. */
enum class RegisterStep(@StringRes val title: Int, @StringRes val description: Int) {
    Account(R.string.register_account_title, R.string.register_account_description),
    EmailCode(R.string.register_code_title, R.string.register_code_description),
    Name(R.string.register_name_title, R.string.register_name_description),
    BirthDate(R.string.register_birth_title, R.string.register_birth_description),
    Salary(R.string.register_salary_title, R.string.register_salary_description),
}

data class RegisterUiState(
    val step: RegisterStep = RegisterStep.Account,
    // Paso 1: acceso. Los errores solo se muestran despues de escribir en el campo.
    val email: String = "",
    val confirmEmail: String = "",
    val password: String = "",
    val passwordHidden: Boolean = true,
    @StringRes val emailError: Int? = null,
    @StringRes val confirmEmailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    // Paso 2: codigo.
    val emailCode: String = "",
    val emailCodeError: String? = null,
    /** Desde cuando se puede pedir otro codigo (60 s despues de mandar uno, como MAUI). */
    val resendAvailableAt: Instant? = null,
    /** A que correo se mando el ultimo codigo. */
    val codeSentTo: String? = null,
    /** Correo ya confirmado con el codigo; null si no. */
    val verifiedEmail: String? = null,
    // Paso 3.
    val name: String = "",
    @StringRes val nameError: Int? = null,
    // Paso 4.
    val birthDate: LocalDate? = null,
    // Paso 5.
    val incomeTypeId: Int = IncomeTypes.WEEKLY,
    /** Dia de la semana de pago en modo semanal (0 = domingo, como .NET). */
    val weekPayDay: Int = 0,
    /** Dias del mes de pago, en el orden en que se tocaron (quincenal: hasta 2; mensual: 1). */
    val monthPayDays: List<Int> = emptyList(),
    val salaryText: String = "",
    val percentSaveText: String = "",
    /**
     * El registro se retomo de un borrador: la contrasena no se guarda en ningun lado,
     * asi que se vuelve a pedir en el ultimo paso.
     */
    val askPasswordAgain: Boolean = false,
    val busy: Boolean = false,
    /** Mensaje de un solo uso (Snackbar). */
    val message: String? = null,
    /** La cuenta quedo creada: "Bienvenido" va en un Toast porque la pantalla se va. */
    val welcomeName: String? = null,
) {
    val stepIndex: Int get() = step.ordinal
    val stepCount: Int get() = RegisterStep.entries.size
    val progress: Float get() = (stepIndex + 1f) / stepCount
    val isLastStep: Boolean get() = step == RegisterStep.Salary
    val emailVerified: Boolean get() = verifiedEmail != null && verifiedEmail == email.trim()

    /** Ya se mando un codigo a este correo hace menos de un minuto: sigue sirviendo. */
    fun codeStillFresh(now: Instant): Boolean =
        codeSentTo == email.trim() && resendAvailableAt != null && now < resendAvailableAt

    val salary: BigDecimal? get() = parseAmountInput(salaryText)

    /** MAUI topa el porcentaje entre 0 y 99; vacio cuenta como 0. */
    val percentSave: BigDecimal?
        get() = if (percentSaveText.isBlank()) BigDecimal.ZERO else parseAmountInput(percentSaveText)

    /** "Estarías ahorrando $X por período". */
    fun savingText(strings: Strings): String? {
        val salary = salary ?: return null
        val percent = percentSave ?: return null
        if (salary.signum() <= 0) return null
        return strings.get(R.string.saving_text, formatMoney(salary.multiply(percent).divide(BigDecimal(100))))
    }

    @get:StringRes
    val salaryError: Int? get() = if (salaryText.isBlank()) null else RegisterRules.salaryError(salary)

    @get:StringRes
    val percentSaveError: Int? get() = RegisterRules.percentSaveError(percentSave)

    /** CanContinue de MAUI, paso por paso. */
    val canContinue: Boolean
        get() = !busy && when (step) {
            RegisterStep.Account -> RegisterRules.emailError(email) == null &&
                RegisterRules.confirmEmailError(confirmEmail, email) == null &&
                RegisterRules.passwordError(password) == null
            RegisterStep.EmailCode -> emailCode.trim().length == 6
            RegisterStep.Name -> RegisterRules.nameError(name) == null
            RegisterStep.BirthDate -> birthDate != null
            RegisterStep.Salary -> RegisterRules.salaryError(salary) == null &&
                RegisterRules.percentSaveError(percentSave) == null &&
                (!askPasswordAgain || RegisterRules.passwordError(password) == null)
        }

    /** Dias de pago que se mandan al API (SaveUser de MAUI, con sus valores por defecto). */
    fun payDays(): Pair<Int?, Int?> = when (incomeTypeId) {
        IncomeTypes.WEEKLY -> weekPayDay to null
        IncomeTypes.BIWEEKLY -> (monthPayDays.minOrNull() ?: 15) to (if (monthPayDays.size > 1) monthPayDays.max() else 30)
        else -> (monthPayDays.minOrNull() ?: 15) to null
    }
}

/**
 * Registro en 5 pasos (RegisterViewModel de MAUI). El avance se guarda como borrador en
 * DataStore para retomarlo si se cierra la app, sobre todo despues de confirmar el
 * correo; la contrasena nunca se guarda.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val draftStore: RegisterDraftStore,
    private val strings: Strings,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    /** Rango de fechas de nacimiento: el de los pickers de MAUI (de 1900 a hace 3 anios). */
    val birthDateRange: ClosedRange<LocalDate> =
        LocalDate.of(1900, 1, 1)..LocalDate.of(LocalDate.now(clock).year - 3, 12, 31)

    init {
        viewModelScope.launch { restoreDraft() }
    }

    // ------------------------------------------------------------ campos

    fun onEmailChange(value: String) = _state.update {
        it.copy(
            email = value,
            emailError = RegisterRules.emailError(value),
            // El de confirmacion se revalida contra el nuevo correo si ya se escribio.
            confirmEmailError = if (it.confirmEmail.isEmpty()) null else RegisterRules.confirmEmailError(it.confirmEmail, value),
        )
    }

    fun onConfirmEmailChange(value: String) = _state.update {
        it.copy(confirmEmail = value, confirmEmailError = RegisterRules.confirmEmailError(value, it.email))
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, passwordError = RegisterRules.passwordError(value))
    }

    fun togglePasswordVisibility() = _state.update { it.copy(passwordHidden = !it.passwordHidden) }

    fun onEmailCodeChange(value: String) {
        val digits = value.filter(Char::isDigit).take(6)
        _state.update { it.copy(emailCode = digits, emailCodeError = null) }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = RegisterRules.nameError(value)) }

    fun onBirthDateChange(value: LocalDate) {
        if (value in birthDateRange) _state.update { it.copy(birthDate = value) }
    }

    fun onIncomeTypeChange(incomeTypeId: Int) = _state.update {
        if (it.incomeTypeId == incomeTypeId) return@update it
        // Se muestran preseleccionados los dias que MAUI usaba si no se elegia ninguno.
        val days = when (incomeTypeId) {
            IncomeTypes.BIWEEKLY -> it.monthPayDays.take(2).ifEmpty { listOf(15, 30) }.let { d -> if (d.size == 1) d + 30 else d }
            IncomeTypes.MONTHLY -> it.monthPayDays.take(1).ifEmpty { listOf(15) }
            else -> it.monthPayDays
        }
        it.copy(incomeTypeId = incomeTypeId, monthPayDays = days.distinct())
    }

    fun onWeekPayDayChange(day: Int) = _state.update { it.copy(weekPayDay = day.coerceIn(0, 6)) }

    /** Quincenal: hasta 2 dias (el tercero desplaza al mas viejo). Mensual: solo 1. */
    fun onMonthPayDayToggle(day: Int) = _state.update {
        val limit = if (it.incomeTypeId == IncomeTypes.BIWEEKLY) 2 else 1
        it.copy(monthPayDays = toggledPayDays(it.monthPayDays, day, limit))
    }

    fun onSalaryChange(value: String) = _state.update { it.copy(salaryText = value) }

    /** MAUI topa el porcentaje: mas de 99 se queda en 99. */
    fun onPercentSaveChange(value: String) = _state.update {
        val parsed = parseAmountInput(value)
        it.copy(percentSaveText = if (parsed != null && parsed > BigDecimal(99)) "99" else value)
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------ navegacion

    /** Siguiente (Next de MAUI). Devuelve sin hacer nada si el paso no esta completo. */
    fun next() {
        val current = _state.value
        if (!current.canContinue) {
            if (current.step == RegisterStep.Salary && !current.busy) {
                _state.update { it.copy(message = strings.get(R.string.register_review_fields)) }
            }
            return
        }
        when (current.step) {
            RegisterStep.Account -> viewModelScope.launch {
                when {
                    // Si el correo ya estaba confirmado y no cambio, no se gasta otro codigo.
                    current.emailVerified -> goTo(RegisterStep.Name)
                    // Se regreso al paso 1 y se siguio sin cambiar el correo: el codigo de hace
                    // un momento sirve (y el API rechazaria otro antes de 60 s).
                    current.codeStillFresh(clock.instant()) -> goTo(RegisterStep.EmailCode)
                    sendCode(isResend = false) -> goTo(RegisterStep.EmailCode)
                }
            }
            RegisterStep.EmailCode -> viewModelScope.launch {
                if (verifyCode()) goTo(RegisterStep.Name)
            }
            RegisterStep.Name -> viewModelScope.launch { goTo(RegisterStep.BirthDate) }
            RegisterStep.BirthDate -> viewModelScope.launch { goTo(RegisterStep.Salary) }
            RegisterStep.Salary -> viewModelScope.launch { createAccount() }
        }
    }

    /**
     * Anterior. Ya con el correo confirmado no se regresa al paso del codigo: ese codigo
     * ya se consumio. Devuelve false en el primer paso (la pantalla se cierra).
     */
    fun previous(): Boolean {
        val current = _state.value
        if (current.busy) return true
        val target = when (current.step) {
            RegisterStep.Account -> return false
            RegisterStep.Name -> if (current.emailVerified) RegisterStep.Account else RegisterStep.EmailCode
            else -> RegisterStep.entries[current.stepIndex - 1]
        }
        viewModelScope.launch { goTo(target) }
        return true
    }

    fun resendCode() {
        val current = _state.value
        val availableAt = current.resendAvailableAt
        if (current.busy || (availableAt != null && clock.instant() < availableAt)) return
        _state.update { it.copy(emailCode = "") }
        viewModelScope.launch { sendCode(isResend = true) }
    }

    /** El borrador se guarda antes de cambiar de paso: si la app se cierra ahi, ya quedo. */
    private suspend fun goTo(step: RegisterStep) {
        persistDraft(_state.value.copy(step = step))
        _state.update { it.copy(step = step) }
    }

    // ------------------------------------------------------------ API

    private suspend fun sendCode(isResend: Boolean): Boolean {
        _state.update { it.copy(busy = true, emailCodeError = null) }
        val result = sessions.requestEmailVerification(_state.value.email)
        _state.update { it.copy(busy = false) }
        return when (result) {
            SessionResult.Ok -> {
                if (isResend) _state.update { it.copy(message = strings.get(R.string.new_code_sent)) }
                // Evita que se pida un codigo nuevo cada segundo.
                _state.update {
                    it.copy(
                        resendAvailableAt = clock.instant().plusSeconds(CodeResendCooldownSeconds),
                        codeSentTo = it.email.trim(),
                    )
                }
                true
            }
            is SessionResult.Failed -> {
                _state.update {
                    // En el paso de acceso no hay donde mostrar el error: va en el Snackbar.
                    if (it.step == RegisterStep.Account) it.copy(message = result.message)
                    else it.copy(emailCodeError = result.message)
                }
                false
            }
        }
    }

    private suspend fun verifyCode(): Boolean {
        val current = _state.value
        val code = current.emailCode.trim()
        if (code.length != 6) {
            _state.update { it.copy(emailCodeError = strings.get(R.string.error_code_six_digits)) }
            return false
        }
        _state.update { it.copy(busy = true, emailCodeError = null) }
        val result = sessions.verifyEmail(current.email, code)
        return when (result) {
            SessionResult.Ok -> {
                _state.update { it.copy(busy = false, verifiedEmail = it.email.trim(), emailCode = "") }
                true
            }
            is SessionResult.Failed -> {
                _state.update { it.copy(busy = false, emailCodeError = result.message) }
                false
            }
        }
    }

    private suspend fun createAccount() {
        val s = _state.value
        val salary = s.salary ?: return
        val percent = s.percentSave ?: return
        val birthDate = s.birthDate ?: return
        val (firstPayDay, secondPayDay) = s.payDays()

        _state.update { it.copy(busy = true) }
        val result = sessions.createAccount(
            NewAccount(
                name = s.name,
                email = s.email,
                password = s.password,
                birthDate = birthDate,
                salary = salary,
                percentSave = percent,
                incomeTypeId = s.incomeTypeId,
                firstPayDay = firstPayDay,
                secondPayDay = secondPayDay,
            ),
        )
        when (result) {
            // La compuerta de sesion cambia a la app en cuanto el usuario aparece en Room.
            SessionResult.Ok -> _state.update { it.copy(busy = false, welcomeName = s.name.trim()) }
            is SessionResult.Failed -> _state.update { it.copy(busy = false, message = result.message) }
        }
    }

    // ------------------------------------------------------------ borrador

    private suspend fun persistDraft(s: RegisterUiState) {
        if (s.email.isBlank()) return
        val (firstPayDay, secondPayDay) = s.payDays()
        draftStore.save(
            RegisterDraft(
                step = s.stepIndex,
                email = s.email.trim(),
                emailVerified = s.emailVerified,
                name = s.name,
                birthDate = s.birthDate?.toString(),
                incomeTypeId = s.incomeTypeId,
                firstPayDay = firstPayDay,
                secondPayDay = secondPayDay,
                salaryText = s.salaryText,
                percentSaveText = s.percentSaveText,
            ),
        )
    }

    /**
     * Solo se regresa al paso donde iba si el correo ya estaba confirmado; si no, se
     * deja capturar de nuevo porque el codigo pudo haber expirado.
     */
    private suspend fun restoreDraft() {
        val draft = draftStore.load() ?: return
        if (draft.email.isBlank()) return
        val birthDate = try {
            draft.birthDate?.let(LocalDate::parse)?.takeIf { it in birthDateRange }
        } catch (_: DateTimeParseException) {
            null
        }
        val incomeTypeId = draft.incomeTypeId.takeIf { it in IncomeTypes.WEEKLY..IncomeTypes.MONTHLY } ?: IncomeTypes.WEEKLY
        val step = if (draft.emailVerified) {
            RegisterStep.entries[draft.step.coerceIn(RegisterStep.Name.ordinal, RegisterStep.Salary.ordinal)]
        } else {
            RegisterStep.Account
        }
        val monthDays = when (incomeTypeId) {
            IncomeTypes.WEEKLY -> emptyList()
            else -> listOfNotNull(draft.firstPayDay, draft.secondPayDay).filter { it in 1..31 }.distinct()
        }
        _state.update {
            it.copy(
                step = step,
                email = draft.email,
                confirmEmail = draft.email,
                verifiedEmail = if (draft.emailVerified) draft.email else null,
                name = draft.name,
                birthDate = birthDate,
                incomeTypeId = incomeTypeId,
                weekPayDay = if (incomeTypeId == IncomeTypes.WEEKLY) (draft.firstPayDay ?: 0).coerceIn(0, 6) else 0,
                monthPayDays = monthDays,
                salaryText = draft.salaryText,
                percentSaveText = draft.percentSaveText,
                askPasswordAgain = draft.emailVerified,
                message = if (step != RegisterStep.Account) strings.get(R.string.register_resumed) else null,
            )
        }
    }
}
