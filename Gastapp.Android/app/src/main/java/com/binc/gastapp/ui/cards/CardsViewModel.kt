package com.binc.gastapp.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.domain.cards.sortedByPaymentUrgency
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.ui.category.CategoryDirectory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardsUiState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val summaries: List<CardSummary> = emptyList(),
    /** Nombres de categoria para las compras del ciclo. */
    val categories: CategoryDirectory = CategoryDirectory(emptyList()),
) {
    val hasCards: Boolean get() = summaries.isNotEmpty()
    val totalDebt: BigDecimal get() = summaries.sumOfMoney { it.totalDebt }
    val totalAvailable: BigDecimal get() = summaries.sumOfMoney { it.availableCredit }
    val totalLimit: BigDecimal get() = summaries.sumOfMoney { it.creditLimit }
    val totalCurrentCycle: BigDecimal get() = summaries.sumOfMoney { it.currentCycleAmount }

    /** Uso combinado del credito (0 a 1), para la barra del resumen global. */
    val combinedUsage: Float
        get() = if (totalLimit.signum() > 0) (totalDebt.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Mis tarjetas (CreditCardsViewModel de MAUI): resumen global, una tarjeta a la vez con
 * su ciclo, sus MSI y sus compras del ciclo, y las acciones de cada una. El formulario
 * de alta y edicion es otra pantalla (CardFormScreen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardsViewModel @Inject constructor(
    private val cards: CreditCardRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))

    /**
     * Orden del carrusel (ids), fijado al abrir la pantalla: la mas proxima a vencer
     * primero. No se reordena al pagar; si no, la tarjeta pagada se iria al final y el
     * carrusel se quedaria mostrando otra. Las tarjetas nuevas se agregan al final.
     */
    private var cardOrder: List<String> = emptyList()

    val state: StateFlow<CardsUiState> = combine(
        today.flatMapLatest { cards.observeSummaries(it) },
        categories.observeAll().map(::CategoryDirectory),
        today,
    ) { summaries, directory, t ->
        CardsUiState(loaded = true, today = t, summaries = inStableOrder(summaries), categories = directory)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardsUiState(today = today.value))

    private fun inStableOrder(summaries: List<CardSummary>): List<CardSummary> {
        val byId = summaries.associateBy { it.card.creditCardId }
        val kept = cardOrder.filter { it in byId }
        val added = summaries.sortedByPaymentUrgency().map { it.card.creditCardId }.filterNot { it in kept }
        cardOrder = kept + added
        return cardOrder.map(byId::getValue)
    }

    fun refreshToday() {
        val now = LocalDate.now(clock)
        if (now != today.value) today.value = now
    }

    fun registerPayment(summary: CardSummary, amount: BigDecimal, isStatementPayment: Boolean = false, onDone: () -> Unit) {
        viewModelScope.launch {
            cards.registerPayment(summary.card, amount, isStatementPayment)
            onDone()
        }
    }

    /** onDone recibe false si el saldo ya era ese (no se crea nada). */
    fun adjustBalance(summary: CardSummary, newBalance: BigDecimal, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(cards.adjustBalance(summary.card, summary.totalDebt, newBalance) != null) }
    }

    fun delete(summary: CardSummary, onDone: () -> Unit) {
        viewModelScope.launch { if (cards.delete(summary.card.creditCardId)) onDone() }
    }
}
