package com.binc.gastapp.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.ui.navigation.CategoryDetailRoute
import com.binc.gastapp.ui.spending.SpendingItem
import com.binc.gastapp.ui.spending.spendingItems
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Los gastos de un dia dentro del detalle de categoria. */
data class DayGroup(val day: LocalDate, val total: BigDecimal, val items: List<SpendingItem>)

data class CategoryDetailUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val start: LocalDate,
    val end: LocalDate,
    val today: LocalDate,
    val total: BigDecimal = BigDecimal.ZERO,
    val count: Int = 0,
    val groups: List<DayGroup> = emptyList(),
)

/**
 * Gastos de una categoria en un periodo (CategoryDetailViewModel de MAUI): total,
 * cuantos movimientos y la lista por dia, del mas reciente al mas viejo. Sin compras
 * con tarjeta, igual que el total de la categoria en Ahorros.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    categories: CategoryRepository,
    private val spendings: SpendingRepository,
    cards: CreditCardRepository,
    clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CategoryDetailRoute>()
    private val start = LocalDate.parse(route.start)
    private val end = LocalDate.parse(route.end)
    private val today = LocalDate.now(clock)

    private val directory = categories.observeAll().map(::CategoryDirectory)

    val state: StateFlow<CategoryDetailUiState> = combine(
        directory.map { it.idsFor(route.categoryId) }.distinctUntilChanged()
            .flatMapLatest { ids -> spendings.observeCategorySpendings(ids, start, end) },
        directory,
        cards.observeCards(),
    ) { list, dir, cardList ->
        val items = spendingItems(list, dir, cardList)
        CategoryDetailUiState(
            loaded = true,
            name = dir.nameOf(route.categoryId),
            start = start,
            end = end,
            today = today,
            total = items.sumOfMoney { it.spending.amount },
            count = items.size,
            groups = items.groupBy { it.spending.date.toLocalDate() }
                .map { (day, dayItems) -> DayGroup(day, dayItems.sumOfMoney { it.spending.amount }, dayItems) }
                .sortedByDescending { it.day },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailUiState(start = start, end = end, today = today))

    fun delete(spendingId: String, onDeleted: () -> Unit) {
        viewModelScope.launch { if (spendings.delete(spendingId)) onDeleted() }
    }

    fun restore(spendingId: String) {
        viewModelScope.launch { spendings.restore(spendingId) }
    }
}
