package com.binc.gastapp.ui.explore

import androidx.lifecycle.SavedStateHandle
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import com.binc.gastapp.ui.summary.Shortcut
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule

class ExplorePeriodViewModelTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.viewModel(start: String = "2026-10-01", end: String = "2026-10-02"): ExplorePeriodViewModel {
        val vm = ExplorePeriodViewModel(SavedStateHandle(mapOf("start" to start, "end" to end)), users, categories, spendings, clock)
        vm.state.launchIn(backgroundScope)
        return vm
    }

    private suspend fun seed() {
        seedBasics()
        db.spendingDao().upsertAll(
            listOf(
                spending("a", 10_000, at(today, 9)),
                spending("b", 40_000, at(LocalDate.of(2026, 10, 1), 9), categoryId = "cat-default"),
                spending("compra", 90_000, at(today, 10), isCreditCard = true, creditCardId = "card-1"),
                spending("julio", 5_000, at(LocalDate.of(2026, 7, 15), 9)),
            ),
        )
    }

    @Test
    fun `resume el rango, sus categorias y los meses desde el gasto mas viejo`() = runTest {
        seed()
        val state = viewModel().state.awaitUntil { it.summary != null && it.months.size > 1 }

        assertEquals((7..10).map { YearMonth.of(2026, it) }, state.months)
        val summary = state.summary!!
        assertEquals(BigDecimal("500.00"), summary.total)
        assertEquals(BigDecimal("250.00"), summary.dailyAverage)
        // La compra con tarjeta se cuenta como movimiento pero no suma.
        assertEquals(3, summary.movements)
        assertEquals(LocalDate.of(2026, 10, 1) to BigDecimal("400.00"), summary.highestDay)
        assertEquals(listOf("Sin categoria", "Comida"), summary.topCategories.map { it.name })

        // Intensidad: el 1 de octubre es el dia mas alto (3); hoy es una cuarta parte (2).
        assertEquals(3, state.levels[LocalDate.of(2026, 10, 1)])
        assertEquals(2, state.levels[today])
        assertEquals(1, state.levels[LocalDate.of(2026, 7, 15)])
    }

    @Test
    fun `elegir dias arma el rango como en el demo`() = runTest {
        seed()
        val vm = viewModel()
        vm.state.awaitUntil { it.summary != null }

        // Con un rango completo, tocar un dia empieza uno nuevo.
        vm.onDayClick(LocalDate.of(2026, 9, 10))
        var state = vm.state.awaitUntil { it.end == null }
        assertEquals(LocalDate.of(2026, 9, 10), state.start)
        assertFalse(state.hasRange)
        assertNull(state.summary)

        // Un dia anterior al inicio se vuelve el nuevo inicio.
        vm.onDayClick(LocalDate.of(2026, 9, 5))
        assertEquals(LocalDate.of(2026, 9, 5), vm.state.awaitUntil { it.start == LocalDate.of(2026, 9, 5) }.start)

        // Los dias futuros no se pueden elegir.
        vm.onDayClick(today.plusDays(3))
        vm.onDayClick(LocalDate.of(2026, 9, 30))
        state = vm.state.awaitUntil { it.hasRange }
        assertEquals(LocalDate.of(2026, 9, 30), state.end)
        assertEquals(26, state.summary?.dayCount ?: vm.state.awaitUntil { it.summary != null }.summary!!.dayCount)
    }

    @Test
    fun `los atajos ponen su rango`() = runTest {
        seed()
        val vm = viewModel()
        val shortcuts = vm.state.awaitUntil { it.shortcuts.isNotEmpty() }.shortcuts
        val (shortcut, range) = shortcuts.first { it.first == Shortcut.LastMonth }
        vm.onShortcut(shortcut, range)
        val state = vm.state.awaitUntil { it.summary?.start == LocalDate.of(2026, 9, 1) }
        assertEquals(LocalDate.of(2026, 9, 30), state.end)
        assertEquals(BigDecimal.ZERO, state.summary!!.total.stripTrailingZeros())
    }

    @Test
    fun `nivel de intensidad`() {
        val max = BigDecimal("400")
        assertEquals(0, ExplorePeriodViewModel.intensityLevel(BigDecimal.ZERO, max))
        assertEquals(1, ExplorePeriodViewModel.intensityLevel(BigDecimal("99"), max))
        assertEquals(2, ExplorePeriodViewModel.intensityLevel(BigDecimal("100"), max))
        assertEquals(3, ExplorePeriodViewModel.intensityLevel(BigDecimal("220"), max))
    }
}
