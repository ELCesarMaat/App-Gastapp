package com.binc.gastapp.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
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
import com.binc.gastapp.ui.format.AppLocale
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.whenText
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
    fun countsText(strings: Strings): String {
        val active = summaries.count { it.countsTowardTotals }
        val trial = summaries.count { it.isTrialActive }
        val paused = summaries.count { !it.subscription.isActive }
        return buildList {
            add(strings.plural(R.plurals.subscriptions_active, active))
            if (trial > 0) add(strings.plural(R.plurals.subscriptions_trial, trial))
            if (paused > 0) add(strings.plural(R.plurals.subscriptions_paused, paused))
        }.joinToString(" · ")
    }

    fun nextChargeText(strings: Strings): String = upcoming.firstOrNull()?.let {
        strings.get(R.string.next_charge_text, it.serviceName, formatMoney(it.amount), it.whenText(strings).lowercase(AppLocale.locale))
    } ?: strings.get(R.string.no_upcoming_charges)
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
    private val strings: Strings,
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
                    strings.get(
                        if (activate) R.string.subscription_resumed else R.string.subscription_paused_message,
                        subscription.serviceName,
                    ),
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
