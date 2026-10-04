package com.binc.gastapp.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SubscriptionRepository
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.domain.subscriptions.SubscriptionSummary
import com.binc.gastapp.domain.subscriptions.UpcomingCharge
import com.binc.gastapp.domain.subscriptions.buildSubscriptionSummary
import com.binc.gastapp.domain.subscriptions.totalMonthlyCost
import com.binc.gastapp.domain.subscriptions.upcomingCharges
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.format.formatMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val summaries: List<SubscriptionSummary> = emptyList(),
    val upcoming: List<UpcomingCharge> = emptyList(),
) {
    val hasSubscriptions: Boolean get() = summaries.isNotEmpty()

    /** Solo lo que ya se esta pagando: ni pausadas ni en prueba gratis. */
    val monthlyTotal: BigDecimal get() = summaries.filter { it.countsTowardTotals }.sumOfMoney { it.monthlyEquivalent }
    val yearlyTotal: BigDecimal get() = summaries.filter { it.countsTowardTotals }.sumOfMoney { it.yearlyEquivalent }

    /** "2 activas · 1 en prueba · 1 pausada": explica por que el total no incluye todas. */
    val countsText: String
        get() {
            val active = summaries.count { it.countsTowardTotals }
            val trial = summaries.count { it.isTrialActive }
            val paused = summaries.count { !it.subscription.isActive }
            return buildString {
                append(if (active == 1) "1 activa" else "$active activas")
                if (trial > 0) append(" · $trial en prueba")
                if (paused > 0) append(" · $paused pausada${if (paused == 1) "" else "s"}")
            }
        }

    val nextChargeText: String
        get() = upcoming.firstOrNull()?.let {
            "Siguiente cobro: ${it.serviceName} · ${formatMoney(it.amount)} · ${it.whenText.lowercase()}"
        } ?: "Sin cobros programados en los próximos días."
}

/**
 * Suscripciones y membresias (SubscriptionsViewModel de MAUI): gasto recurrente al mes
 * y al anio, la lista con su estado, los proximos cobros a 45 dias y las acciones de
 * cada una (registrar cobro, pausar, editar, eliminar).
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    cards: CreditCardRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<SubscriptionsUiState> = combine(
        subscriptions.observeAll(),
        cards.observeCards(),
        categories.observeAll(),
        today,
    ) { list, cardList, cats, t ->
        val cardNames = cardList.associate { it.creditCardId to it.cardName }
        val directory = CategoryDirectory(cats)
        val categoryNames = cats.associate { it.categoryId to directory.nameOf(it.categoryId) }
        val monthly = totalMonthlyCost(list, t)
        SubscriptionsUiState(
            loaded = true,
            today = t,
            summaries = list.map { buildSubscriptionSummary(it, monthly, cardNames, categoryNames, t) },
            upcoming = upcomingCharges(list, t, cardNames, daysAhead = 45),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState(today = today.value))

    fun refreshToday() {
        val now = LocalDate.now(clock)
        if (now != today.value) today.value = now
    }

    /** Pausar o reanudar. [onDone] recibe el mensaje del aviso. */
    fun toggleActive(summary: SubscriptionSummary, onDone: (String) -> Unit) {
        val subscription = summary.subscription
        val activate = !subscription.isActive
        viewModelScope.launch {
            if (subscriptions.setActive(subscription.subscriptionId, activate)) {
                onDone(
                    if (activate) "${subscription.serviceName} vuelve a contar en tus totales."
                    else "${subscription.serviceName} quedó en pausa.",
                )
            }
        }
    }

    fun delete(summary: SubscriptionSummary, onDone: () -> Unit) {
        viewModelScope.launch { if (subscriptions.delete(summary.subscription.subscriptionId)) onDone() }
    }

    /** Registra el cobro como gasto (a la tarjeta si se paga con tarjeta). */
    fun registerCharge(summary: SubscriptionSummary, amount: BigDecimal, onDone: () -> Unit) {
        viewModelScope.launch {
            if (subscriptions.registerCharge(summary.subscription.subscriptionId, amount) != null) onDone()
        }
    }
}
