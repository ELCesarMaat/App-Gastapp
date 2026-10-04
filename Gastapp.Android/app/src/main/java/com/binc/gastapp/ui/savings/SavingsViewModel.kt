package com.binc.gastapp.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.domain.cards.savesPendingLevel
import com.binc.gastapp.domain.cards.savesPendingText
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.domain.savings.BudgetHealth
import com.binc.gastapp.domain.savings.BudgetStatus
import com.binc.gastapp.domain.savings.budgetStatus
import com.binc.gastapp.domain.savings.categoryPercentages
import com.binc.gastapp.domain.spendings.CategoryTotal
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.summary.PeriodSelection
import com.binc.gastapp.ui.summary.ResolvedPeriod
import com.binc.gastapp.ui.summary.currentPeriodNaturalEnd
import com.binc.gastapp.ui.summary.resolvePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una categoria en la distribucion del periodo. */
data class CategoryShare(val total: CategoryTotal, val percent: BigDecimal) {
    val ratio: Float get() = (percent.toFloat() / 100f).coerceIn(0f, 1f)
}

/** Tarjeta con deuda en "Tarjetas por pagar" (umbral de 5 dias, no 3 como en Mis tarjetas). */
data class PendingCard(val summary: CardSummary, val statusText: String, val level: StatusLevel)

data class SavingsUiState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val period: ResolvedPeriod? = null,
    /** Dias del periodo de pago completo (el actual "termina hoy" para los totales). */
    val naturalDayCount: Int = 0,
    val totalSpending: BigDecimal = BigDecimal.ZERO,
    val budget: BudgetStatus? = null,
    val categories: List<CategoryShare> = emptyList(),
    val pendingCards: List<PendingCard> = emptyList(),
) {
    val isPastPeriod: Boolean get() = (period?.selection as? PeriodSelection.Paid)?.offset?.let { it > 0 } ?: false
    val topCategory: CategoryShare? get() = categories.firstOrNull()

    /** Dias que ya pasaron del periodo (en el actual, contando hoy). */
    val elapsedDays: Int get() = period?.dayCount ?: 0
}

/**
 * Ahorros (SavesViewModel de MAUI): total gastado del periodo, salud del presupuesto,
 * cuanto queda, promedio diario, categoria principal, tarjetas por pagar con "Registrar
 * pago" y la distribucion por categoria. Todo con el criterio del periodo: las compras
 * con tarjeta no cuentan, cuenta el pago.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsViewModel @Inject constructor(
    users: UserRepository,
    categories: CategoryRepository,
    spendings: SpendingRepository,
    private val cards: CreditCardRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))
    private val offset = MutableStateFlow(0)
    private val directory = categories.observeAll().map(::CategoryDirectory)

    private val pendingCards = today.flatMapLatest { cards.observeSummaries(it) }.map { summaries ->
        summaries.filter { it.totalDebt.signum() > 0 }
            .sortedBy { it.daysUntilPayment }
            .map { PendingCard(it, savesPendingText(it.daysUntilPayment), savesPendingLevel(it.daysUntilPayment)) }
    }

    val state: StateFlow<SavingsUiState> = combine(offset, users.observeUser(), today) { o, user, t -> Triple(o, user, t) }
        .distinctUntilChanged()
        .flatMapLatest { (o, user, t) ->
            val period = resolvePeriod(PeriodSelection.Paid(o), user, t)
            combine(spendings.observeCategoryTotals(period.start, period.end), directory, pendingCards) { totals, dir, pending ->
                val merged = dir.mergeTotals(totals)
                val total = merged.sumOfMoney { it.amount }
                val percents = categoryPercentages(merged.map { it.amount })
                val natural = if (o == 0) currentPeriodNaturalEnd(user, t) else period.end
                SavingsUiState(
                    loaded = true,
                    today = t,
                    period = period,
                    naturalDayCount = ChronoUnit.DAYS.between(period.start, natural).toInt() + 1,
                    totalSpending = total,
                    budget = user?.let { budgetStatus(it.salary, it.percentSave, total, period.dayCount) },
                    categories = merged.zip(percents) { category, percent -> CategoryShare(category, percent) },
                    pendingCards = pending,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsUiState(today = today.value))

    fun previousPeriod() {
        offset.value += 1
    }

    fun nextPeriod() {
        if (offset.value > 0) offset.value -= 1
    }

    fun refreshToday() {
        val now = LocalDate.now(clock)
        if (now != today.value) today.value = now
    }

    /** Registrar pago desde "Tarjetas por pagar". */
    fun registerPayment(summary: CardSummary, amount: BigDecimal, onDone: () -> Unit) {
        viewModelScope.launch {
            cards.registerPayment(summary.card, amount)
            onDone()
        }
    }
}

/** Salud del periodo con su texto y su color de estado (los textos de MAUI, con acentos). */
data class HealthInfo(val label: String, val message: String, val level: StatusLevel)

fun healthInfo(health: BudgetHealth): HealthInfo = when (health) {
    BudgetHealth.HEALTHY -> HealthInfo(
        "Saludable",
        "Tus gastos siguen bajo control y todavía tienes margen para el resto del periodo.",
        StatusLevel.OK,
    )
    BudgetHealth.STABLE -> HealthInfo(
        "Estable",
        "Vas bien, pero ya consumiste buena parte del presupuesto disponible.",
        StatusLevel.WARNING,
    )
    BudgetHealth.TIGHT -> HealthInfo(
        "Ajustada",
        "Estás muy cerca del límite. Cualquier gasto extra puede desbalancear tu periodo.",
        StatusLevel.WARNING,
    )
    BudgetHealth.CRITICAL -> HealthInfo(
        "Crítica",
        "Ya rebasaste tu límite ideal. Conviene pausar gastos no esenciales.",
        StatusLevel.CRITICAL,
    )
}
