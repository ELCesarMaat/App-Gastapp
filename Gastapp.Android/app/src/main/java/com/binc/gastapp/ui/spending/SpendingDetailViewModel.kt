package com.binc.gastapp.ui.spending

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.binc.gastapp.R
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.domain.cards.installmentOn
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.money.dividedBy
import com.binc.gastapp.domain.money.roundHalfEven
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.navigation.SpendingDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que muestra el detalle (DetailViewModel de MAUI). */
data class SpendingDetail(
    val spending: Spending,
    val categoryName: String,
    val paymentText: String,
    /** "Banco - Tarjeta" si fue con tarjeta. */
    val cardText: String?,
    /** "Plan MSI a 6 meses · $500.00/mes (Cuota 1 de 6)". */
    val msiText: String?,
) {
    val isCardPurchase: Boolean get() = spending.isCreditCard
    val isCardPayment: Boolean get() = !spending.isCreditCard && spending.creditCardId != null
}

sealed interface SpendingDetailState {
    data object Loading : SpendingDetailState

    /** Se borro (desde aqui o desde otro lado): la pantalla se cierra. */
    data object Gone : SpendingDetailState

    data class Shown(val detail: SpendingDetail) : SpendingDetailState
}

/**
 * Detalle de un gasto: pantalla de solo lectura con el boton Editar (decision del
 * usuario: se queda igual que en MAUI). Se actualiza sola al editarlo.
 */
@HiltViewModel
class SpendingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val spendings: SpendingRepository,
    categories: CategoryRepository,
    cards: CreditCardRepository,
    private val strings: Strings,
    private val clock: Clock,
) : ViewModel() {

    val spendingId: String = savedStateHandle.toRoute<SpendingDetailRoute>().spendingId

    val state: StateFlow<SpendingDetailState> = combine(
        spendings.observe(spendingId),
        categories.observeAll(),
        cards.observeCards(),
    ) { spending, cats, cardList ->
        if (spending == null || spending.isDeleted) SpendingDetailState.Gone
        else SpendingDetailState.Shown(buildDetail(spending, CategoryDirectory(cats), cardList, strings, LocalDate.now(clock)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingDetailState.Loading)

    fun restore(spendingId: String) {
        viewModelScope.launch { spendings.restore(spendingId) }
    }
}

fun buildDetail(spending: Spending, categories: CategoryDirectory, cards: List<CreditCard>, strings: Strings, today: LocalDate): SpendingDetail {
    val card = spending.creditCardId?.let { id -> cards.firstOrNull { it.creditCardId == id } }
    val cardText = when {
        spending.creditCardId == null -> null
        card == null -> strings.get(R.string.payment_method_credit_card)
        card.bankName.isBlank() -> card.cardName
        else -> strings.get(R.string.detail_card_name, card.bankName, card.cardName)
    }
    val msiText = if (spending.isMsi) {
        val installments = maxOf(1, spending.totalInstallments)
        val monthly = if (spending.installmentMonthlyAmount.signum() > 0) spending.installmentMonthlyAmount
        else (spending.amount dividedBy installments).roundHalfEven(2)
        // La que va hoy (avanza con los cortes); sin la tarjeta, la guardada. Una ya pagada
        // se queda en la ultima.
        val current = card?.let { spending.installmentOn(it.cutOffDay, today) } ?: spending.currentInstallment
        strings.get(
            R.string.detail_msi_plan,
            spending.totalInstallments,
            formatMoney(monthly),
            current.coerceAtMost(spending.totalInstallments),
            spending.totalInstallments,
        )
    } else {
        null
    }
    return SpendingDetail(
        spending = spending,
        categoryName = categories.nameOf(spending.categoryId),
        paymentText = strings.get(
            if (!spending.isCreditCard && spending.creditCardId != null) R.string.detail_card_payment else paymentLongLabel(spending),
        ),
        cardText = cardText,
        msiText = msiText,
    )
}
