package com.binc.gastapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.domain.model.User
import com.binc.gastapp.domain.savings.savingsAmountFromPercent
import com.binc.gastapp.domain.savings.savingsPercentFromAmount
import com.binc.gastapp.ui.components.toggledPayDays
import com.binc.gastapp.ui.components.weekDayName
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.formatPercent
import com.binc.gastapp.ui.format.parseAmountInput
import com.binc.gastapp.ui.format.toInputText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Como va el guardado automatico (ProfileSettingsStatusText de MAUI). */
sealed interface SaveStatus {
    data object Idle : SaveStatus

    data object Pending : SaveStatus

    data object Saved : SaveStatus

    data object UpToDate : SaveStatus

    /** No se guarda hasta corregirlo. */
    data class Invalid(val message: String) : SaveStatus
}

fun SaveStatus.text(strings: Strings): String = when (this) {
    SaveStatus.Idle -> strings.get(R.string.save_status_idle)
    SaveStatus.Pending -> strings.get(R.string.save_status_pending)
    SaveStatus.Saved -> strings.get(R.string.save_status_saved)
    SaveStatus.UpToDate -> strings.get(R.string.save_status_up_to_date)
    is SaveStatus.Invalid -> message
}

/**
 * Perfil (ProfileViewModel de MAUI): datos de la cuenta y el formulario de ingreso y
 * ahorro, que se guarda solo. Los textos de los campos son la fuente de verdad, como los
 * `*Input` de MAUI; todo lo demas sale de ellos.
 */
data class ProfileUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val email: String? = null,
    val birthDate: LocalDate? = null,
    val incomeTypeId: Int = IncomeTypes.MONTHLY,
    /** Semanal: dia de la semana con la numeracion de .NET (0 = domingo); null si falta. */
    val weekPayDay: Int? = null,
    /** Quincenal (hasta 2) o mensual (1), en el orden en que se tocaron. */
    val monthPayDays: List<Int> = emptyList(),
    val salaryText: String = "",
    val percentText: String = "",
    val amountText: String = "",
    /** Meta de ahorro como porcentaje o como monto fijo (SavingsModeIsPercent). */
    val byPercent: Boolean = true,
    val saveStatus: SaveStatus = SaveStatus.Idle,
) {
    val salary: BigDecimal get() = parseAmountInput(salaryText) ?: BigDecimal.ZERO
    private val amount: BigDecimal get() = parseAmountInput(amountText) ?: BigDecimal.ZERO

    /** Lo que se guarda: el tecleado, o el que sale del monto fijo (4 decimales, como MAUI). */
    val percent: BigDecimal
        get() = if (byPercent) parseAmountInput(percentText) ?: BigDecimal.ZERO else savingsPercentFromAmount(amount, salary)

    val estimatedSavings: BigDecimal
        get() = if (byPercent) savingsAmountFromPercent(salary, percent) else amount

    val estimatedSpendable: BigDecimal get() = salary - estimatedSavings

    fun displayName(strings: Strings): String = name.trim().ifEmpty { strings.get(R.string.your_profile) }

    /** Iniciales de las dos primeras palabras ("GU" si no hay nombre, como MAUI). */
    val initials: String
        get() = name.trim().split(' ').filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "GU" }

    /** Dias que se guardan en FirstPayDay y SecondPayDay. */
    val payDays: Pair<Int?, Int?>
        get() = when (incomeTypeId) {
            IncomeTypes.WEEKLY -> weekPayDay to null
            IncomeTypes.BIWEEKLY -> monthPayDays.minOrNull() to monthPayDays.takeIf { it.size > 1 }?.max()
            else -> monthPayDays.firstOrNull() to null
        }

    fun incomeSummary(strings: Strings): String =
        if (salary.signum() > 0) strings.get(R.string.income_per_period, formatMoney(salary)) else strings.get(R.string.income_pending)

    fun goalSummary(strings: Strings): String = if (percent.signum() > 0) {
        strings.get(R.string.goal_summary, percentLabel(percent), formatMoney(estimatedSavings))
    } else {
        strings.get(R.string.goal_pending)
    }

    /** BuildCurrentPayScheduleSummary de MAUI. */
    fun scheduleSummary(strings: Strings): String {
        val (first, second) = payDays
        return when (incomeTypeId) {
            IncomeTypes.WEEKLY -> first?.let { strings.get(R.string.schedule_weekly, weekDayName(it)) }
                ?: strings.get(R.string.schedule_weekly_pending)
            IncomeTypes.BIWEEKLY -> if (first != null && second != null) {
                strings.get(R.string.schedule_biweekly, first, second)
            } else {
                strings.get(R.string.schedule_biweekly_pending)
            }
            else -> first?.let { strings.get(R.string.schedule_monthly, it) } ?: strings.get(R.string.schedule_monthly_pending)
        }
    }

    /** "Equivale al 12.5% de tu sueldo", solo con monto fijo. Mismo formato que la meta del resumen. */
    fun computedPercentInfo(strings: Strings): String? =
        if (!byPercent && percent.signum() > 0) strings.get(R.string.computed_percent_info, percentLabel(percent)) else null

    /** ValidateFinancialSettings de MAUI, con sus mensajes y en su orden. */
    fun validationError(strings: Strings): String? {
        val (first, second) = payDays
        return when {
            salary.signum() <= 0 -> strings.get(R.string.error_salary)
            percent < BigDecimal.ZERO || percent > BigDecimal(99) ->
                strings.get(if (byPercent) R.string.error_percent_range else R.string.error_amount_range)
            incomeTypeId == IncomeTypes.WEEKLY && first == null -> strings.get(R.string.error_weekly_day)
            incomeTypeId == IncomeTypes.BIWEEKLY && (first == null || second == null) -> strings.get(R.string.error_biweekly_days)
            incomeTypeId == IncomeTypes.MONTHLY && first == null -> strings.get(R.string.error_monthly_day)
            else -> null
        }
    }

    /** Lo que se compara para saber si hay algo nuevo que guardar (BuildSnapshot de MAUI). */
    internal fun snapshot(): String {
        val (first, second) = payDays
        return profileKey(incomeTypeId, first, second, salary, percent)
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val settingsStore: SettingsStore,
    private val strings: Strings,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** Lo ultimo que se cargo o se guardo: si el formulario es igual, no hay cambios. */
    private var savedSnapshot: String? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val byPercent = settingsStore.settings.first().savingsModeIsPercent
            users.observeUser().filterNotNull().collect { onUser(it, byPercent) }
        }
    }

    private fun onUser(user: User, byPercent: Boolean) {
        val current = _state.value
        if (!current.loaded) {
            load(user, byPercent)
            return
        }
        _state.update { it.copy(name = user.name, email = user.email, birthDate = user.birthDate) }
        // Cambio que llega de fuera (otro inicio de sesion): solo pisa el formulario si
        // no hay ediciones a medias.
        val incoming = user.snapshot()
        val editing = current.snapshot() != savedSnapshot || saveJob?.isActive == true
        if (incoming != savedSnapshot && !editing) load(user, current.byPercent)
    }

    /** LoadSettingsFromUser de MAUI. Un tipo de ingreso desconocido se trata como mensual el dia 1. */
    private fun load(user: User, byPercent: Boolean) {
        val known = user.incomeTypeId in IncomeTypes.WEEKLY..IncomeTypes.MONTHLY
        val incomeTypeId = if (known) user.incomeTypeId else IncomeTypes.MONTHLY
        val monthDays = when {
            !known -> listOf(1)
            incomeTypeId == IncomeTypes.BIWEEKLY -> listOfNotNull(user.firstPayDay, user.secondPayDay)
            incomeTypeId == IncomeTypes.MONTHLY -> listOfNotNull(user.firstPayDay)
            else -> emptyList()
        }.filter { it in 1..31 }.distinct()

        _state.value = ProfileUiState(
            loaded = true,
            name = user.name,
            email = user.email,
            birthDate = user.birthDate,
            incomeTypeId = incomeTypeId,
            weekPayDay = user.firstPayDay?.takeIf { incomeTypeId == IncomeTypes.WEEKLY && it in 0..6 },
            monthPayDays = monthDays,
            salaryText = user.salary.toInputText(),
            percentText = user.percentSave.toPercentText(),
            amountText = savingsAmountFromPercent(user.salary, user.percentSave).toAmountText(),
            byPercent = byPercent,
        )
        savedSnapshot = user.snapshot()
    }

    // ------------------------------------------------------------ frecuencia

    fun onIncomeTypeChange(incomeTypeId: Int) {
        if (_state.value.incomeTypeId == incomeTypeId) return
        _state.update {
            // Mensual se queda con un dia; quincenal conserva los que hubiera (si falta uno, se pide).
            val days = if (incomeTypeId == IncomeTypes.MONTHLY) it.monthPayDays.take(1) else it.monthPayDays
            it.copy(incomeTypeId = incomeTypeId, monthPayDays = days)
        }
        queueSave()
    }

    fun onWeekPayDayChange(day: Int) {
        _state.update { it.copy(weekPayDay = day.coerceIn(0, 6)) }
        queueSave()
    }

    fun onMonthPayDayToggle(day: Int) {
        _state.update {
            val limit = if (it.incomeTypeId == IncomeTypes.BIWEEKLY) 2 else 1
            it.copy(monthPayDays = toggledPayDays(it.monthPayDays, day, limit))
        }
        queueSave()
    }

    // ------------------------------------------------------------ sueldo y ahorro

    /** Con monto fijo se recalcula el porcentaje; con porcentaje, el monto (OnSalaryInputChanged). */
    fun onSalaryChange(text: String) {
        _state.update {
            val next = it.copy(salaryText = text)
            if (it.byPercent) next.copy(amountText = savingsAmountFromPercent(next.salary, next.percent).toAmountText())
            else next.copy(percentText = next.percent.toPercentText())
        }
        queueSave()
    }

    fun onPercentChange(text: String) {
        _state.update {
            val next = it.copy(percentText = text)
            next.copy(amountText = savingsAmountFromPercent(next.salary, next.percent).toAmountText())
        }
        queueSave()
    }

    fun onAmountChange(text: String) {
        _state.update {
            val next = it.copy(amountText = text)
            next.copy(percentText = next.percent.toPercentText())
        }
        queueSave()
    }

    /** SelectSavingsMode de MAUI: cambia como se captura la meta, sin cambiarla. */
    fun onSavingsModeChange(byPercent: Boolean) {
        val current = _state.value
        if (current.byPercent == byPercent) return
        _state.update {
            if (byPercent) {
                it.copy(byPercent = true, percentText = it.percent.toPercentText())
            } else {
                it.copy(byPercent = false, amountText = savingsAmountFromPercent(it.salary, it.percent).toAmountText())
            }
        }
        viewModelScope.launch { settingsStore.setSavingsModeIsPercent(byPercent) }
    }

    // ------------------------------------------------------------ guardado automatico

    /** QueueAutoSaveProfileSettings de MAUI: espera 700 ms sin cambios y guarda. */
    private fun queueSave() {
        val current = _state.value
        if (!current.loaded) return
        saveJob?.cancel()
        val error = current.validationError(strings)
        when {
            error != null -> _state.update { it.copy(saveStatus = SaveStatus.Invalid(error)) }
            current.snapshot() == savedSnapshot -> _state.update { it.copy(saveStatus = SaveStatus.UpToDate) }
            else -> {
                _state.update { it.copy(saveStatus = SaveStatus.Pending) }
                saveJob = viewModelScope.launch {
                    delay(AutoSaveDelayMillis)
                    save()
                }
            }
        }
    }

    private suspend fun save() {
        val current = _state.value
        current.validationError(strings)?.let { error ->
            _state.update { it.copy(saveStatus = SaveStatus.Invalid(error)) }
            return
        }
        val snapshot = current.snapshot()
        if (snapshot == savedSnapshot) return
        val user = users.getUser() ?: return
        val (first, second) = current.payDays
        users.saveProfile(
            user.copy(
                salary = current.salary,
                percentSave = current.percent,
                incomeTypeId = current.incomeTypeId,
                firstPayDay = first,
                secondPayDay = second,
            ),
        )
        savedSnapshot = snapshot
        // Si se siguio editando mientras se guardaba, ese cambio ya tiene su propio guardado en cola.
        if (_state.value.snapshot() == snapshot) _state.update { it.copy(saveStatus = SaveStatus.Saved) }
    }

    companion object {
        const val AutoSaveDelayMillis = 700L
    }
}

private fun User.snapshot(): String = profileKey(incomeTypeId, firstPayDay, secondPayDay, salary, percentSave)

/** Sin ceros a la derecha: 15000 y 15000.00 son el mismo sueldo. */
private fun profileKey(incomeTypeId: Int, first: Int?, second: Int?, salary: BigDecimal, percent: BigDecimal): String =
    listOf(incomeTypeId, first, second, salary.stripTrailingZeros().toPlainString(), percent.stripTrailingZeros().toPlainString())
        .joinToString("|")

/** "33.3333" o "20": hasta 4 decimales, los que guarda la meta. Vacio si es 0. */
private fun BigDecimal.toPercentText(): String = setScale(4, RoundingMode.HALF_EVEN).toInputText()

private fun BigDecimal.toAmountText(): String = setScale(2, RoundingMode.HALF_EVEN).toInputText()

/** "20%" o "33.33%". */
internal fun percentLabel(percent: BigDecimal): String = formatPercent(percent)
