package com.binc.gastapp.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.binc.gastapp.R
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SubscriptionRepository
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.subscriptions.monthlyEquivalent
import com.binc.gastapp.domain.subscriptions.nextChargeDate
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.dayMonthYearShort
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.parseAmountInput
import com.binc.gastapp.ui.format.toInputText
import com.binc.gastapp.ui.navigation.SubscriptionFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Periodicidades en el orden de MAUI. */
val BillingCycleOptions = listOf(
    BillingCycles.WEEKLY,
    BillingCycles.MONTHLY,
    BillingCycles.QUARTERLY,
    BillingCycles.SEMIANNUAL,
    BillingCycles.YEARLY,
)

/**
 * Formas de pago: las MISMAS cuatro del formulario de gasto, con las etiquetas que
 * muestra el detalle del gasto (si no, el detalle diria otra forma de pago).
 */
val SubscriptionPaymentOptions = listOf(
    PaymentMethods.CREDIT_CARD,
    PaymentMethods.DEBIT,
    PaymentMethods.TRANSFER,
    PaymentMethods.CASH,
)

/** Colores de suscripcion de MAUI (el violeta primero). */
val SubscriptionColors = listOf("#7C3AED", "#126E63", "#1A73E8", "#D97706", "#1F2937", "#E11D48")

private fun previewDate(date: LocalDate) = dayMonthYearShort(date)

data class SubscriptionFormState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val serviceName: String = "",
    val planName: String = "",
    val amountText: String = "",
    val billingCycle: String = BillingCycles.MONTHLY,
    val firstChargeDate: LocalDate,
    val paymentMethod: String = PaymentMethods.CREDIT_CARD,
    val cards: List<CreditCard> = emptyList(),
    val creditCardId: String? = null,
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    val defaultCategoryId: String? = null,
    /** Todas las "Sin categoria" (puede haber dos, hallazgo 13). */
    val defaultCategoryIds: Set<String> = emptySet(),
    val isTrial: Boolean = false,
    val trialEndDate: LocalDate,
    val colorHex: String = SubscriptionColors.first(),
    val notes: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val today: LocalDate,
) {
    val amount: BigDecimal get() = parseAmountInput(amountText) ?: BigDecimal.ZERO
    val isCreditCard: Boolean get() = paymentMethod == PaymentMethods.CREDIT_CARD

    /** Las fechas que salen de lo capturado, para verlas antes de guardar. */
    fun chargePreview(strings: Strings): String {
        val reference = if (isTrial && trialEndDate > today) trialEndDate else today
        val first = nextChargeDate(firstChargeDate, billingCycle, reference)
        val second = nextChargeDate(firstChargeDate, billingCycle, first.plusDays(1))
        return strings.get(R.string.charge_preview, previewDate(first), previewDate(second))
    }

    fun monthlyPreview(strings: Strings): String? = if (amount.signum() > 0) {
        val monthly = monthlyEquivalent(amount, billingCycle)
        strings.get(R.string.monthly_preview, formatMoney(monthly), formatMoney(monthly.multiply(BigDecimal(12))))
    } else {
        null
    }

    fun trialPreview(strings: Strings): String? = if (isTrial) strings.get(R.string.trial_preview, previewDate(trialEndDate)) else null

    val hasUnsavedData: Boolean
        get() = serviceName.isNotBlank() || planName.isNotBlank() || amountText.isNotBlank() || notes.isNotBlank()
}

/** Alta y edicion de una suscripcion (el formulario de SubscriptionsViewModel de MAUI). */
@HiltViewModel
class SubscriptionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subscriptions: SubscriptionRepository,
    cards: CreditCardRepository,
    categories: CategoryRepository,
    private val strings: Strings,
    clock: Clock,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.toRoute<SubscriptionFormRoute>().subscriptionId
    private var original: Subscription? = null
    private val today = LocalDate.now(clock)

    private val _state = MutableStateFlow(
        SubscriptionFormState(firstChargeDate = today, trialEndDate = today.plusDays(7), today = today),
    )
    val state: StateFlow<SubscriptionFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (cardList, cats) = combine(cards.observeCards(), categories.observeAll()) { a, b -> a to b }.first()
            val directory = CategoryDirectory(cats)
            val subscription = editingId?.let { subscriptions.get(it) }
            original = subscription
            _state.update { s ->
                val base = s.copy(
                    loaded = true,
                    cards = cardList,
                    categories = directory.pickerCategories,
                    creditCardId = cardList.firstOrNull()?.creditCardId,
                    categoryId = directory.primaryDefaultId,
                    defaultCategoryId = directory.primaryDefaultId,
                    defaultCategoryIds = directory.defaultIds,
                )
                if (subscription == null) {
                    base
                } else {
                    base.copy(
                        isEdit = true,
                        serviceName = subscription.serviceName,
                        planName = subscription.planName.orEmpty(),
                        amountText = subscription.amount.toInputText(),
                        billingCycle = subscription.billingCycle,
                        firstChargeDate = subscription.firstChargeDate,
                        paymentMethod = subscription.paymentMethod,
                        creditCardId = subscription.creditCardId ?: base.creditCardId,
                        categoryId = subscription.categoryId?.let(directory::displayId),
                        isTrial = subscription.isTrial,
                        trialEndDate = subscription.trialEndDate ?: today.plusDays(7),
                        colorHex = subscription.colorHex.ifBlank { SubscriptionColors.first() },
                        notes = subscription.notes.orEmpty(),
                    )
                }
            }
        }
    }

    fun onServiceNameChange(value: String) = _state.update { it.copy(serviceName = value, error = null) }
    fun onPlanNameChange(value: String) = _state.update { it.copy(planName = value) }
    fun onAmountChange(value: String) = _state.update { it.copy(amountText = value, error = null) }
    fun onBillingCycleSelect(value: String) = _state.update { it.copy(billingCycle = value) }
    fun onFirstChargeDateChange(value: LocalDate) = _state.update { it.copy(firstChargeDate = value) }
    fun onPaymentMethodSelect(value: String) = _state.update { it.copy(paymentMethod = value, error = null) }
    fun onCardSelect(value: String) = _state.update { it.copy(creditCardId = value, error = null) }
    fun onCategorySelect(value: String?) = _state.update { it.copy(categoryId = value) }
    fun onTrialChange(value: Boolean) = _state.update { it.copy(isTrial = value, error = null) }
    fun onTrialEndDateChange(value: LocalDate) = _state.update { it.copy(trialEndDate = value, error = null) }
    fun onColorSelect(value: String) = _state.update { it.copy(colorHex = value) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    /** SaveSubscription de MAUI, con sus mensajes. [onSaved] recibe el texto del aviso. */
    fun save(onSaved: (String) -> Unit) {
        val s = _state.value
        if (s.saving || !s.loaded) return
        val serviceName = s.serviceName.trim()
        val error = when {
            serviceName.isEmpty() -> strings.get(R.string.error_service_name)
            s.amount.signum() <= 0 -> strings.get(R.string.error_subscription_amount)
            s.isCreditCard && s.cards.none { it.creditCardId == s.creditCardId } ->
                strings.get(R.string.error_subscription_card)
            s.isTrial && s.trialEndDate < s.today -> strings.get(R.string.error_trial_past)
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val base = original ?: Subscription(
            subscriptionId = UUID.randomUUID().toString(),
            serviceName = serviceName,
            amount = s.amount,
            firstChargeDate = s.firstChargeDate,
        )
        // Al editar se conservan el estado (pausada), el ultimo cobro registrado y una
        // "Sin categoria" duplicada si no se cambio de categoria.
        val categoryId = original?.categoryId?.takeIf { it in s.defaultCategoryIds && s.categoryId == s.defaultCategoryId }
            ?: s.categoryId
        val subscription = base.copy(
            serviceName = serviceName,
            planName = s.planName.trim().ifEmpty { null },
            amount = s.amount,
            billingCycle = s.billingCycle,
            firstChargeDate = s.firstChargeDate,
            paymentMethod = s.paymentMethod,
            creditCardId = if (s.isCreditCard) s.creditCardId else null,
            categoryId = categoryId,
            isTrial = s.isTrial,
            trialEndDate = if (s.isTrial) s.trialEndDate else null,
            colorHex = s.colorHex,
            notes = s.notes.trim().ifEmpty { null },
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            subscriptions.save(subscription)
            _state.update { it.copy(saving = false) }
            onSaved(if (s.isEdit) strings.get(R.string.subscription_updated) else strings.get(R.string.subscription_added, serviceName))
        }
    }
}
