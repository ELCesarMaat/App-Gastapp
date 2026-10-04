package com.binc.gastapp.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.money.dividedBy
import com.binc.gastapp.domain.money.roundHalfEven
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.domain.spendings.CategoryTotal
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.navigation.ExplorePeriodRoute
import com.binc.gastapp.ui.summary.Shortcut
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Resumen del rango elegido. */
data class RangeSummary(
    val start: LocalDate,
    val end: LocalDate,
    val total: BigDecimal,
    val dailyAverage: BigDecimal,
    val movements: Int,
    /** Lo de cada dia hasta hoy (las barras de la grafica). */
    val days: List<Pair<LocalDate, BigDecimal>>,
    val highestDay: Pair<LocalDate, BigDecimal>?,
    /** Las 3 categorias con mas gasto. */
    val topCategories: List<CategoryTotal>,
) {
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
}

data class ExploreUiState(
    val today: LocalDate,
    val start: LocalDate?,
    val end: LocalDate?,
    /** Meses que se pueden recorrer: desde el del gasto mas viejo hasta el actual. */
    val months: List<YearMonth>,
    /** Nivel de gasto de cada dia (0 a 3) para los puntos del calendario. */
    val levels: Map<LocalDate, Int> = emptyMap(),
    val shortcuts: List<Pair<Shortcut, Pair<LocalDate, LocalDate>>> = emptyList(),
    val summary: RangeSummary? = null,
) {
    val hasRange: Boolean get() = start != null && end != null
}

/**
 * Explorar periodo: el reemplazo del SfCalendar de MAUI con el diseno del demo
 * (atajos, calendario con la intensidad de gasto de cada dia, resumen del rango con
 * grafica y categorias, y "Ver N dias", que aplica el rango en Resumen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExplorePeriodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    users: UserRepository,
    categories: CategoryRepository,
    private val spendings: SpendingRepository,
    clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExplorePeriodRoute>()
    private val today = LocalDate.now(clock)
    private val selection = MutableStateFlow(LocalDate.parse(route.start) to LocalDate.parse(route.end) as LocalDate?)

    /** Desde el mes del gasto mas viejo (o hace 3 meses si hay poco) hasta el actual. */
    private val months = spendings.observeFirstDay().map { first ->
        val current = YearMonth.from(today)
        val selected = YearMonth.from(LocalDate.parse(route.start))
        val oldest = listOfNotNull(first?.let(YearMonth::from), selected, current.minusMonths(2)).min()
        generateSequence(oldest) { it.plusMonths(1) }.takeWhile { it <= current }.toList()
    }.distinctUntilChanged()

    private val levels = months.flatMapLatest { list ->
        spendings.observeDailyTotals(list.first().atDay(1), today).map { totals ->
            val byDay = totals.associate { it.day to it.totalWithoutCardPurchases }
            val max = byDay.values.maxOrNull()?.takeIf { it.signum() > 0 } ?: return@map emptyMap()
            byDay.mapValues { (_, amount) -> intensityLevel(amount, max) }
        }
    }

    private val summary = selection.flatMapLatest { (start, end) ->
        if (end == null) {
            flowOf(null)
        } else {
            combine(
                spendings.observeDailyTotals(start, end),
                spendings.observeCategoryTotals(start, end),
                categories.observeAll().map(::CategoryDirectory),
            ) { totals, categoryTotals, directory ->
                val byDay = totals.associate { it.day to it.totalWithoutCardPurchases }
                val total = byDay.values.sumOfMoney { it }
                val shownDays = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end && it <= today }.toList()
                val elapsed = shownDays.size.coerceAtLeast(1)
                RangeSummary(
                    start = start,
                    end = end,
                    total = total,
                    dailyAverage = (total dividedBy elapsed).roundHalfEven(2),
                    movements = totals.sumOf { it.spendingCount },
                    days = shownDays.map { it to (byDay[it] ?: BigDecimal.ZERO) },
                    highestDay = byDay.entries.filter { it.value.signum() > 0 }.maxByOrNull { it.value }?.toPair(),
                    topCategories = directory.mergeTotals(categoryTotals).filter { it.amount.signum() > 0 }.take(3),
                )
            }
        }
    }

    val state: StateFlow<ExploreUiState> = combine(selection, months, levels, users.observeUser(), summary) { (start, end), m, l, user, s ->
        ExploreUiState(
            today = today,
            start = start,
            end = end,
            months = m,
            levels = l,
            shortcuts = Shortcut.entries.map { it to it.range(user, today) },
            summary = s,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExploreUiState(
            today = today,
            start = LocalDate.parse(route.start),
            end = LocalDate.parse(route.end),
            months = listOf(YearMonth.from(today)),
        ),
    )

    /**
     * Elegir un dia: sin seleccion o con un rango completo se empieza uno nuevo; si el
     * dia es anterior al inicio, se vuelve el nuevo inicio; si no, es el fin.
     */
    fun onDayClick(date: LocalDate) {
        if (date.isAfter(today)) return
        val (start, end) = selection.value
        selection.value = when {
            end != null -> date to null
            date.isBefore(start) -> date to null
            else -> start to date
        }
    }

    fun onShortcut(shortcut: Shortcut, range: Pair<LocalDate, LocalDate>) {
        selection.value = range.first to range.second
    }

    companion object {
        /** Como en el demo: menos de una cuarta parte del dia mas alto es 1, mas de la mitad es 3. */
        fun intensityLevel(amount: BigDecimal, max: BigDecimal): Int {
            if (amount.signum() <= 0) return 0
            val ratio = amount.toDouble() / max.toDouble()
            return when {
                ratio < 0.25 -> 1
                ratio < 0.55 -> 2
                else -> 3
            }
        }
    }
}
