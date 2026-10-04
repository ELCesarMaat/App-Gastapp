package com.binc.gastapp.ui.spending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.spendings.msiMonthlyInstallment
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.parseAmountInput
import com.binc.gastapp.ui.format.toInputText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Que se abre en el formulario de gasto. */
sealed interface SpendingFormRequest {
    /** Gasto nuevo en [date]. Desde Mis tarjetas llega con la tarjeta (y MSI) ya elegidas. */
    data class New(val date: LocalDate, val creditCardId: String? = null, val msi: Boolean = false) : SpendingFormRequest

    data class Edit(val spendingId: String) : SpendingFormRequest
}

/** Plazos de MSI de MAUI. */
val MsiTerms = listOf(3, 6, 9, 12, 18, 24)

/** Aviso antes de borrar una categoria. */
data class CategoryDeletePrompt(val category: Category, val spendingCount: Int) {
    /** Los textos de NewSpendingViewModel.DeleteCategory. */
    fun message(strings: Strings): String = if (spendingCount > 0) {
        strings.plural(
            R.plurals.delete_category_in_use,
            spendingCount,
            category.categoryName,
            spendingCount,
            strings.get(R.string.default_category),
        )
    } else {
        strings.get(R.string.delete_category_confirm, category.categoryName)
    }
}

data class SpendingFormState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val amountText: String = "",
    val amountError: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val defaultCategoryId: String? = null,
    /** Todas las "Sin categoria" de la cuenta (puede haber dos, hallazgo 13). */
    val defaultCategoryIds: Set<String> = emptySet(),
    val paymentMethod: String = PaymentMethods.CASH,
    val cards: List<CreditCard> = emptyList(),
    val selectedCardId: String? = null,
    val isMsi: Boolean = false,
    val installments: Int = 3,
    val title: String = "",
    val description: String = "",
    val date: LocalDate,
    val today: LocalDate,
    val newCategoryOpen: Boolean = false,
    val newCategoryName: String = "",
    val newCategoryError: String? = null,
    val deletePrompt: CategoryDeletePrompt? = null,
    /** Error que no es de un campo (sin tarjeta elegida, gasto que ya no existe...). */
    val error: String? = null,
    val saving: Boolean = false,
) {
    val isCreditCard: Boolean get() = paymentMethod == PaymentMethods.CREDIT_CARD

    val amount: BigDecimal? get() = parseAmountInput(amountText)

    /** Solo se ofrece borrar una categoria propia, nunca "Sin categoria". */
    val canDeleteSelectedCategory: Boolean
        get() = selectedCategoryId != null && selectedCategoryId != defaultCategoryId

    /** "Pagarás $X al mes durante N meses." (UpdateMsiPreview de MAUI). */
    fun msiPreview(strings: Strings): String {
        val value = amount
        return if (value != null && value.signum() > 0) {
            strings.get(R.string.msi_preview_amount, formatMoney(msiMonthlyInstallment(value, installments)), installments)
        } else {
            strings.get(R.string.msi_preview_plain, installments)
        }
    }
}

/** Lo que paso al guardar o borrar, para el aviso de la pantalla de abajo. */
sealed interface SpendingFormResult {
    data class Saved(val spending: Spending, val isNew: Boolean) : SpendingFormResult

    data class Deleted(val spendingId: String) : SpendingFormResult
}

/**
 * Formulario de gasto (NewSpendingViewModel de MAUI con el diseno del demo): alta y
 * edicion, las 4 formas de pago, tarjeta, MSI, crear y borrar categorias y la fecha
 * (sin futuro).
 */
@HiltViewModel
class SpendingFormViewModel @Inject constructor(
    private val spendings: SpendingRepository,
    private val categories: CategoryRepository,
    private val cards: CreditCardRepository,
    private val strings: Strings,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SpendingFormState(date = LocalDate.now(clock), today = LocalDate.now(clock)))
    val state: StateFlow<SpendingFormState> = _state.asStateFlow()

    /** El gasto que se edita, tal como estaba (conserva hora, cuota actual y sincronizacion). */
    private var editing: Spending? = null
    private var listsJob: Job? = null
    private var openedFor: SpendingFormRequest? = null

    /** Prepara el formulario. Llamarlo de nuevo con la misma peticion no reinicia lo escrito. */
    fun open(request: SpendingFormRequest) {
        if (openedFor == request) return
        openedFor = request
        editing = null
        val today = LocalDate.now(clock)
        _state.value = SpendingFormState(date = today, today = today)

        listsJob?.cancel()
        listsJob = viewModelScope.launch {
            // Categorias y tarjetas en vivo: una categoria creada aqui aparece al instante.
            combine(categories.observeAll(), cards.observeCards()) { cats, cardList -> cats to cardList }
                .collect { (cats, cardList) ->
                    val directory = CategoryDirectory(cats)
                    _state.update { s ->
                        val picker = directory.pickerCategories
                        s.copy(
                            categories = picker,
                            defaultCategoryId = directory.primaryDefaultId,
                            defaultCategoryIds = directory.defaultIds,
                            cards = cardList,
                            // Si la elegida desaparecio (se borro), queda "Sin categoria".
                            selectedCategoryId = s.selectedCategoryId?.takeIf { id -> picker.any { it.categoryId == id } }
                                ?: if (s.loaded) directory.primaryDefaultId else s.selectedCategoryId,
                            // La tarjeta de un gasto que se edita se respeta aunque ya este borrada.
                            selectedCardId = s.selectedCardId?.takeIf { id ->
                                cardList.any { it.creditCardId == id } || id == editing?.creditCardId
                            } ?: cardList.firstOrNull()?.creditCardId,
                        )
                    }
                }
        }

        viewModelScope.launch {
            when (request) {
                is SpendingFormRequest.New -> loadNew(request, today)
                is SpendingFormRequest.Edit -> loadEdit(request.spendingId, today)
            }
        }
    }

    private suspend fun loadNew(request: SpendingFormRequest.New, today: LocalDate) {
        val default = categories.ensureDefault()
        val directory = CategoryDirectory(categories.getAll())
        val card = request.creditCardId
        _state.update {
            it.copy(
                loaded = true,
                date = request.date.coerceAtMost(today),
                selectedCategoryId = directory.displayId(default.categoryId),
                paymentMethod = if (card != null) PaymentMethods.CREDIT_CARD else PaymentMethods.CASH,
                selectedCardId = card ?: it.selectedCardId,
                isMsi = card != null && request.msi,
            )
        }
    }

    private suspend fun loadEdit(spendingId: String, today: LocalDate) {
        val spending = spendings.get(spendingId)?.takeUnless { it.isDeleted }
        if (spending == null) {
            _state.update { it.copy(loaded = true, error = strings.get(R.string.error_spending_gone)) }
            return
        }
        editing = spending
        val directory = CategoryDirectory(categories.getAll())
        val paymentMethod = spending.paymentMethod.ifBlank { if (spending.isCreditCard) PaymentMethods.CREDIT_CARD else PaymentMethods.CASH }
        _state.update {
            it.copy(
                loaded = true,
                isEdit = true,
                amountText = spending.amount.toInputText(),
                selectedCategoryId = directory.displayId(spending.categoryId),
                paymentMethod = paymentMethod,
                selectedCardId = spending.creditCardId ?: it.selectedCardId,
                isMsi = spending.isMsi,
                installments = if (spending.totalInstallments > 1) spending.totalInstallments else 3,
                title = spending.title,
                description = normalizeDescription(spending.description),
                date = spending.date.toLocalDate(),
                today = today,
            )
        }
    }

    // ------------------------------------------------------------ campos

    fun onAmountChange(value: String) = _state.update { it.copy(amountText = value, amountError = null) }

    fun onCategorySelect(categoryId: String) = _state.update { it.copy(selectedCategoryId = categoryId) }

    fun onPaymentMethodSelect(method: String) = _state.update { it.copy(paymentMethod = method, error = null) }

    fun onCardSelect(creditCardId: String) = _state.update { it.copy(selectedCardId = creditCardId, error = null) }

    fun onMsiChange(enabled: Boolean) = _state.update { it.copy(isMsi = enabled) }

    fun onInstallmentsSelect(months: Int) = _state.update { it.copy(installments = months) }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    /** No se registran gastos a futuro. */
    fun onDateChange(date: LocalDate) {
        val today = LocalDate.now(clock)
        if (!date.isAfter(today)) _state.update { it.copy(date = date, today = today) }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------ categorias

    fun toggleNewCategory() = _state.update {
        it.copy(newCategoryOpen = !it.newCategoryOpen, newCategoryName = "", newCategoryError = null)
    }

    fun onNewCategoryNameChange(value: String) = _state.update { it.copy(newCategoryName = value, newCategoryError = null) }

    /** SaveNewCategory de MAUI, con sus mensajes. */
    fun saveNewCategory() {
        val s = _state.value
        val name = s.newCategoryName.trim()
        val error = when {
            name.isEmpty() -> strings.get(R.string.error_category_name_empty)
            s.categories.any { it.categoryName.equals(name, ignoreCase = true) } -> strings.get(R.string.error_category_exists)
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(newCategoryError = error) }
            return
        }
        viewModelScope.launch {
            val created = categories.create(name)
            _state.update {
                it.copy(selectedCategoryId = created.categoryId, newCategoryOpen = false, newCategoryName = "", newCategoryError = null)
            }
        }
    }

    /** Primero se cuenta cuantos gastos tiene, para avisar (CountActiveSpendingsByCategory). */
    fun requestDeleteSelectedCategory() {
        val s = _state.value
        val category = s.categories.firstOrNull { it.categoryId == s.selectedCategoryId } ?: return
        if (!s.canDeleteSelectedCategory) return
        viewModelScope.launch {
            val count = categories.countActiveSpendings(category.categoryId)
            _state.update { it.copy(deletePrompt = CategoryDeletePrompt(category, count)) }
        }
    }

    fun dismissDeletePrompt() = _state.update { it.copy(deletePrompt = null) }

    fun confirmDeleteCategory() {
        val prompt = _state.value.deletePrompt ?: return
        _state.update { it.copy(deletePrompt = null) }
        viewModelScope.launch {
            if (categories.delete(prompt.category.categoryId)) {
                _state.update { it.copy(selectedCategoryId = it.defaultCategoryId) }
            } else {
                _state.update { it.copy(error = strings.get(R.string.error_category_delete)) }
            }
        }
    }

    // ------------------------------------------------------------ guardar y borrar

    /**
     * SaveSpending de MAUI. Devuelve el resultado por [onDone] solo si se guardo; si
     * falta algo, deja el error en el estado y no llama.
     */
    fun save(onDone: (SpendingFormResult) -> Unit) {
        val s = _state.value
        if (s.saving || !s.loaded) return
        val amount = s.amount
        if (amount == null || amount.signum() <= 0) {
            _state.update { it.copy(amountError = strings.get(R.string.error_amount_positive)) }
            return
        }
        val categoryId = s.selectedCategoryId
        if (categoryId == null) {
            _state.update { it.copy(error = strings.get(R.string.error_select_category)) }
            return
        }
        val knownCard = s.cards.any { it.creditCardId == s.selectedCardId } ||
            (s.selectedCardId != null && s.selectedCardId == editing?.creditCardId)
        if (s.isCreditCard && !knownCard) {
            _state.update { it.copy(error = strings.get(R.string.error_select_card)) }
            return
        }

        val isCard = s.isCreditCard
        val isMsi = isCard && s.isMsi
        val totalInstallments = if (isMsi) maxOf(1, s.installments) else 1
        val original = editing
        // Una "Sin categoria" duplicada se muestra como la principal: si no se cambio de
        // categoria, el gasto se queda en la suya en vez de moverse.
        val keepOriginalDefault = original != null && original.categoryId in s.defaultCategoryIds && categoryId == s.defaultCategoryId
        val categoryToSave = if (keepOriginalDefault) original!!.categoryId else categoryId
        // La hora del gasto: la original al editar; la de ahora al crear (como SelectedTime de MAUI).
        val time = original?.date?.toLocalTime() ?: LocalTime.now(clock).withNano(0)

        val spending = (original ?: Spending(spendingId = UUID.randomUUID().toString(), amount = amount, date = s.date.atTime(time))).copy(
            amount = amount,
            date = s.date.atTime(time),
            categoryId = categoryToSave,
            title = s.title.trim(),
            description = normalizeDescription(s.description),
            isCreditCard = isCard,
            creditCardId = if (isCard) s.selectedCardId else null,
            paymentMethod = s.paymentMethod,
            isMsi = isMsi,
            totalInstallments = totalInstallments,
            currentInstallment = original?.currentInstallment ?: 1,
            installmentMonthlyAmount = if (isMsi) msiMonthlyInstallment(amount, totalInstallments) else amount,
        )

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = spendings.save(spending)
            _state.update { it.copy(saving = false) }
            openedFor = null
            onDone(SpendingFormResult.Saved(saved, isNew = original == null))
        }
    }

    fun delete(onDone: (SpendingFormResult) -> Unit) {
        val original = editing ?: return
        viewModelScope.launch {
            if (spendings.delete(original.spendingId)) {
                openedFor = null
                onDone(SpendingFormResult.Deleted(original.spendingId))
            }
        }
    }

    /** Se cerro sin guardar: la proxima vez se empieza de cero. */
    fun closed() {
        openedFor = null
        listsJob?.cancel()
    }
}

/** NormalizeDescription de MAUI: vacia o "*SIN DESCRIPCION*" se guarda como "". */
fun normalizeDescription(description: String?): String {
    val trimmed = description?.trim().orEmpty()
    return if (trimmed.equals("*SIN DESCRIPCION*", ignoreCase = true)) "" else trimmed
}
