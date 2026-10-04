package com.binc.gastapp.ui.savings

import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.domain.savings.BudgetHealth
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ahorros con Room en memoria. Usuario quincenal (1 y 16) con sueldo de $10,000 y
 * 15.3846 % de ahorro; hoy es el 2 de octubre de 2026.
 */
class SavingsViewModelTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.viewModel(): SavingsViewModel {
        val vm = SavingsViewModel(users, categories, spendings, cards, strings, clock)
        vm.state.launchIn(backgroundScope)
        return vm
    }

    private suspend fun seed() {
        seedBasics()
        db.spendingDao().upsertAll(
            listOf(
                spending("comida", 30_000, at(LocalDate.of(2026, 10, 1), 13)),
                spending("otro", 10_000, at(today, 9), categoryId = "cat-default"),
                // Compra con tarjeta: no cuenta en Ahorros; el pago si (MAUI, regla 16).
                spending("compra", 20_000, at(today, 10), isCreditCard = true, creditCardId = "card-1"),
                spending("pago", 5_000, at(today, 11), categoryId = "cat-default", creditCardId = "card-1"),
                spending("septiembre", 900_000, at(LocalDate.of(2026, 9, 20), 18)),
            ),
        )
    }

    @Test
    fun `presupuesto, salud, promedio y distribucion del periodo actual`() = runTest {
        seed()
        val state = viewModel().state.awaitUntil { it.loaded && it.budget != null && it.pendingCards.isNotEmpty() }

        assertEquals(LocalDate.of(2026, 10, 1), state.period!!.start)
        assertFalse(state.isPastPeriod)
        assertEquals(2, state.elapsedDays)
        assertEquals("Dia 2 de 15", 15, state.naturalDayCount)
        assertEquals(BigDecimal("450.00"), state.totalSpending)

        val budget = state.budget!!
        assertEquals(BigDecimal("5.32"), budget.percent)
        assertEquals(BudgetHealth.HEALTHY, budget.health)
        assertEquals(BigDecimal("225.00"), budget.dailyAverage)
        assertEquals(0, budget.remainingBudget.compareTo(BigDecimal("8011.54")))

        assertEquals(listOf("Comida", "Sin categoria"), state.categories.map { it.total.name })
        assertEquals(listOf(BigDecimal("66.7"), BigDecimal("33.3")), state.categories.map { it.percent })
        assertEquals("Comida", state.topCategory!!.total.name)

        // card-1 debe 200 - 50 = 150 y vence en 23 dias: verde (el ambar de Ahorros empieza a los 5).
        val pending = state.pendingCards.single()
        assertEquals(BigDecimal("150.00"), pending.summary.totalDebt)
        assertEquals("Vence en 23 días", pending.statusText)
        assertEquals(StatusLevel.OK, pending.level)
    }

    @Test
    fun `un periodo pasado dice cuanto se ahorro o cuanto se excedio`() = runTest {
        seed()
        val vm = viewModel()
        vm.state.awaitUntil { it.loaded && it.budget != null }

        vm.previousPeriod()
        val state = vm.state.awaitUntil { it.period?.start == LocalDate.of(2026, 9, 16) && it.budget != null }
        assertTrue(state.isPastPeriod)
        assertEquals(15, state.naturalDayCount)
        val budget = state.budget!!
        // Gasto 9,000 de un sueldo de 10,000: ahorro 1,000, pero ya paso el limite (8,461.54).
        assertFalse(budget.exceededSalary)
        assertEquals(0, budget.savedOrExceeded.compareTo(BigDecimal("1000")))
        assertEquals(BudgetHealth.CRITICAL, budget.health)
        assertEquals("Crítica", strings.get(healthInfo(budget.health).label))

        vm.nextPeriod()
        vm.nextPeriod()
        assertEquals(LocalDate.of(2026, 10, 1), vm.state.awaitUntil { it.period?.start == LocalDate.of(2026, 10, 1) }.period!!.start)
    }

    @Test
    fun `registrar pago crea el abono y la tarjeta deja de estar pendiente`() = runTest {
        seed()
        val vm = viewModel()
        val pending = vm.state.awaitUntil { it.pendingCards.isNotEmpty() }.pendingCards.single()

        val done = CompletableDeferred<Unit>()
        vm.registerPayment(pending.summary, BigDecimal("150.00")) { done.complete(Unit) }
        done.await()
        vm.state.awaitUntil { it.pendingCards.isEmpty() }

        val payment = db.spendingDao().pendingSync().single()
        assertEquals("Pago TDC - Tarjeta card-1", payment.title)
        assertEquals("Abono a tarjeta Banco", payment.description)
        assertFalse(payment.isCreditCard)
        assertEquals("card-1", payment.creditCardId)
        assertEquals(PaymentMethods.TRANSFER, payment.paymentMethod)
        assertEquals("cat-default", payment.categoryId)
        assertEquals(15_000L, payment.amountCents)
    }
}
