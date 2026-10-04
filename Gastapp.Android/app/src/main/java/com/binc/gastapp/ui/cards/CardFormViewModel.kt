package com.binc.gastapp.ui.cards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.domain.cards.PendingMsiPurchase
import com.binc.gastapp.domain.cards.calculateCycleDates
import com.binc.gastapp.domain.cards.inUseCardMovements
import com.binc.gastapp.domain.cards.lastCutOffDate
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.ui.format.dayMonth
import com.binc.gastapp.ui.format.formatDecimal
import com.binc.gastapp.ui.format.parseAmountInput
import com.binc.gastapp.ui.format.toInputText
import com.binc.gastapp.ui.navigation.CardFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Colores de tarjeta de MAUI: esmeralda, azul real, ambar, violeta, grafito y rubi. */
val CardColors = listOf("#126E63", "#1A73E8", "#D97706", "#7C3AED", "#1F2937", "#E11D48")

/** Plazos de las compras a MSI previas (MAUI ofrece hasta 36). */
val PreviousMsiTerms = listOf(3, 6, 9, 12, 18, 24, 36)

/** Lo que se esta capturando en la hoja "Compra a meses". */
data class MsiDraft(
    val title: String = "",
    val monthlyText: String = "",
    val totalInstallments: Int = 12,
    val paidInstallments: Int = 0,
    val error: String? = null,
) {
    val monthly: BigDecimal get() = parseAmountInput(monthlyText) ?: BigDecimal.ZERO
    val remainingInstallments: Int get() = maxOf(0, totalInstallments - paidInstallments)
    val hasPreview: Boolean get() = monthly.signum() > 0
    val remainingText: String get() = "Te faltan $remainingInstallments de $totalInstallments mensualidades"
    val detailText: String get() = "Sumará ${money0(monthly.multiply(BigDecimal(remainingInstallments)))} a la deuda de esta tarjeta"
}

data class CardFormState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val cardName: String = "",
    val bankName: String = "",
    val lastFour: String = "",
    val creditLimitText: String = "",
    val colorHex: String = CardColors.first(),
    val cutOffDay: Int = 15,
    val paymentDay: Int = 5,
    // Tarjeta en uso (solo al dar de alta).
    val hasExistingBalance: Boolean = false,
    val totalUsedText: String = "",
    val currentCycleText: String = "",
    /** El saldo ya salio en un estado de cuenta: pertenece al corte pendiente de pago. */
    val balanceAlreadyCut: Boolean = true,
    val hasActiveMsi: Boolean = false,
    val msiPurchases: List<PendingMsiPurchase> = emptyList(),
    val msiDraft: MsiDraft? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val today: LocalDate,
) {
    val creditLimit: BigDecimal get() = parseAmountInput(creditLimitText) ?: BigDecimal.ZERO
    val totalUsed: BigDecimal get() = parseAmountInput(totalUsedText) ?: BigDecimal.ZERO
    private val msiDebt: BigDecimal get() = if (hasActiveMsi) msiPurchases.sumOfMoney { it.remainingAmount } else BigDecimal.ZERO
    private val cashDebt: BigDecimal get() = (totalUsed - msiDebt).max(BigDecimal.ZERO)

    /** Si los MSI rebasan el total capturado, el total real es el de los MSI. */
    val previewTotalDebt: BigDecimal get() = totalUsed.max(msiDebt)

    val showBreakdown: Boolean get() = hasExistingBalance && previewTotalDebt.signum() > 0
    val totalUsedLabel: String get() = money0(totalUsed)
    val msiDebtLabel: String get() = "−${money0(msiDebt)}"
    val msiCountLabel: String get() = if (msiPurchases.size == 1) "1 compra a meses" else "${msiPurchases.size} compras a meses"
    val cashDebtLabel: String get() = money0(cashDebt)

    val availablePreview: String?
        get() = if (showBreakdown && creditLimit.signum() > 0) {
            "Te quedarían ${money0((creditLimit - previewTotalDebt).max(BigDecimal.ZERO))} disponibles de ${money0(creditLimit)}"
        } else {
            null
        }

    val overLimit: Boolean get() = hasExistingBalance && creditLimit.signum() > 0 && previewTotalDebt > creditLimit

    val msiExceedsTotal: String?
        get() = if (hasExistingBalance && totalUsed.signum() > 0 && msiDebt > totalUsed) {
            "Tus compras a meses suman ${money0(msiDebt)}, más que los ${money0(totalUsed)} que capturaste como usado. Revisa las cantidades."
        } else {
            null
        }

    /** "1 compra · $1,500 al mes" */
    val msiSummary: String
        get() = if (msiPurchases.isEmpty()) "" else
            "${msiPurchases.size} compra${if (msiPurchases.size == 1) "" else "s"} · ${money0(msiPurchases.sumOfMoney { it.monthlyAmount })} al mes"

    /** Las fechas que salen de los dos dias, para verlas ANTES de guardar. */
    val cyclePreview: String
        get() {
            val (cutOff, payment) = calculateCycleDates(cutOffDay, paymentDay, today)
            val last = lastCutOffDate(cutOffDay, today)
            return "Último corte: ${dayMonth(last)}  ·  Próximo: ${dayMonth(cutOff)}  ·  Pago: ${dayMonth(payment)}"
        }

    /** Hay algo capturado que se perderia al salir (HasUnsavedCardData de MAUI). */
    val hasUnsavedData: Boolean
        get() = cardName.isNotBlank() || bankName.isNotBlank() || lastFour.isNotBlank() || creditLimitText.isNotBlank() ||
            totalUsedText.isNotBlank() || currentCycleText.isNotBlank() || msiPurchases.isNotEmpty()
}

/** Montos sin centavos, como el N0 de MAUI en este formulario. */
private fun money0(value: BigDecimal): String = "$" + formatDecimal(value, 0)

/**
 * Alta y edicion de tarjeta (el formulario de CreditCardsViewModel de MAUI). Al dar de
 * alta una tarjeta que ya se venia usando, registra su saldo de contado y sus compras a
 * MSI previas junto con la tarjeta, en una sola transaccion.
 */
@HiltViewModel
class CardFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cards: CreditCardRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.toRoute<CardFormRoute>().creditCardId
    private var original: CreditCard? = null

    private val _state = MutableStateFlow(CardFormState(today = LocalDate.now(clock)))
    val state: StateFlow<CardFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val card = editingId?.let { cards.get(it) }
            original = card
            _state.update {
                if (card == null) {
                    it.copy(loaded = true)
                } else {
                    it.copy(
                        loaded = true,
                        isEdit = true,
                        cardName = card.cardName,
                        bankName = card.bankName,
                        lastFour = card.lastFourDigits.orEmpty(),
                        creditLimitText = if (card.creditLimit.signum() > 0) card.creditLimit.toInputText() else "",
                        colorHex = card.colorHex.ifBlank { CardColors.first() },
                        cutOffDay = card.cutOffDay,
                        paymentDay = card.paymentDay,
                    )
                }
            }
        }
    }

    fun onCardNameChange(value: String) = _state.update { it.copy(cardName = value, error = null) }
    fun onBankNameChange(value: String) = _state.update { it.copy(bankName = value, error = null) }
    fun onLastFourChange(value: String) = _state.update { it.copy(lastFour = value.filter(Char::isDigit).take(4)) }
    fun onCreditLimitChange(value: String) = _state.update { it.copy(creditLimitText = value) }
    fun onColorSelect(hex: String) = _state.update { it.copy(colorHex = hex) }
    fun onCutOffDayChange(day: Int) = _state.update { it.copy(cutOffDay = day.coerceIn(1, 31)) }
    fun onPaymentDayChange(day: Int) = _state.update { it.copy(paymentDay = day.coerceIn(1, 31)) }
    fun onHasExistingBalanceChange(value: Boolean) = _state.update { it.copy(hasExistingBalance = value) }
    fun onTotalUsedChange(value: String) = _state.update { it.copy(totalUsedText = value) }
    fun onCurrentCycleChange(value: String) = _state.update { it.copy(currentCycleText = value) }
    fun onBalanceAlreadyCutChange(value: Boolean) = _state.update { it.copy(balanceAlreadyCut = value) }
    fun onHasActiveMsiChange(value: Boolean) = _state.update { it.copy(hasActiveMsi = value) }
    fun errorShown() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------ compras a MSI previas

    fun openMsiDraft() = _state.update { it.copy(msiDraft = MsiDraft()) }
    fun closeMsiDraft() = _state.update { it.copy(msiDraft = null) }

    fun updateMsiDraft(transform: (MsiDraft) -> MsiDraft) = _state.update { s ->
        val draft = s.msiDraft ?: return@update s
        val updated = transform(draft)
        // Lo pagado nunca llega al plazo completo (UpdatePaidInstallmentOptions de MAUI).
        s.copy(msiDraft = updated.copy(paidInstallments = updated.paidInstallments.coerceIn(0, maxOf(0, updated.totalInstallments - 1))))
    }

    /** AddMsiPurchase de MAUI. Devuelve true si se agrego (la hoja se cierra). */
    fun addMsiPurchase(): Boolean {
        val draft = _state.value.msiDraft ?: return false
        if (draft.monthly.signum() <= 0) {
            _state.update { it.copy(msiDraft = draft.copy(error = "Ingresa cuánto pagas cada mes por esta compra.")) }
            return false
        }
        val purchase = PendingMsiPurchase(
            title = draft.title.trim(),
            monthlyAmount = draft.monthly,
            paidInstallments = draft.paidInstallments.coerceIn(0, draft.totalInstallments - 1),
            totalInstallments = draft.totalInstallments,
        )
        _state.update { it.copy(msiPurchases = it.msiPurchases + purchase, msiDraft = null) }
        return true
    }

    fun removeMsiPurchase(index: Int) = _state.update {
        it.copy(msiPurchases = it.msiPurchases.filterIndexed { i, _ -> i != index })
    }

    // ------------------------------------------------------------ guardar

    /** SaveCard de MAUI. [onSaved] recibe el mensaje para el aviso. */
    fun save(onSaved: (String) -> Unit) {
        val s = _state.value
        if (s.saving) return
        val cardName = s.cardName.trim()
        val bankName = s.bankName.trim()
        val error = when {
            cardName.isEmpty() -> "Ingresa un nombre para la tarjeta (Ej. Oro, Nu, Platino)."
            bankName.isEmpty() -> "Ingresa el banco emisor (Ej. BBVA, Citibanamex, Santander)."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val card = (original ?: CreditCard(creditCardId = UUID.randomUUID().toString(), cutOffDay = s.cutOffDay, paymentDay = s.paymentDay)).copy(
            cardName = cardName,
            bankName = bankName,
            lastFourDigits = s.lastFour.trim().ifEmpty { null },
            cutOffDay = s.cutOffDay,
            paymentDay = s.paymentDay,
            creditLimit = s.creditLimit,
            colorHex = s.colorHex.ifBlank { CardColors.first() },
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (s.isEdit) {
                cards.save(card)
                onSaved("Tarjeta actualizada.")
            } else {
                val movements = if (s.hasExistingBalance) {
                    inUseCardMovements(
                        card = card,
                        totalUsed = s.totalUsed,
                        currentCycleDebt = parseAmountInput(s.currentCycleText) ?: BigDecimal.ZERO,
                        balanceAlreadyCut = s.balanceAlreadyCut,
                        msiPurchases = if (s.hasActiveMsi) s.msiPurchases else emptyList(),
                        now = LocalDateTime.now(clock),
                    )
                } else {
                    emptyList()
                }
                cards.createWithMovements(card, movements, categories.ensureDefault().categoryId)
                onSaved("Tarjeta agregada.")
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
