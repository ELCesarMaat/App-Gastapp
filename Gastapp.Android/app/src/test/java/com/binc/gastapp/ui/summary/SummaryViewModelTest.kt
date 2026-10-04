package com.binc.gastapp.ui.summary

import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Resumen con Room en memoria. Usuario quincenal (1 y 16), hoy es el viernes 2 de
 * octubre de 2026: el periodo actual va del 1 al 2.
 */
class SummaryViewModelTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.viewModel(): SummaryViewModel {
        val vm = SummaryViewModel(users, categories, spendings, cards, subscriptions, strings, clock)
        // Alguien tiene que observar el estado (WhileSubscribed), como la pantalla.
        vm.state.launchIn(backgroundScope)
        return vm
    }

    private suspend fun seedDays() {
        seedBasics()
        val oct1 = LocalDate.of(2026, 10, 1)
        db.spendingDao().upsertAll(
            listOf(
                spending("hoy-efectivo", 10_000, at(today, 9)),
                // Compra con tarjeta: se lista pero no suma (decision del usuario, anexo F).
                spending("hoy-compra", 20_000, at(today, 10), isCreditCard = true, creditCardId = "card-1"),
                // Pago a la tarjeta: si suma.
                spending("hoy-pago", 5_000, at(today, 11), creditCardId = "card-1"),
                spending("ayer", 30_000, at(oct1, 13)),
                spending("septiembre", 8_000, at(LocalDate.of(2026, 9, 20), 18)),
                spending("borrado", 99_900, at(today, 8), isDeleted = true),
            ),
        )
    }

    @Test
    fun `el total del dia cuenta el pago a la tarjeta pero no la compra`() = runTest {
        seedDays()
        val state = viewModel().state.awaitUntil { it.loaded && it.dayItems.size == 3 }

        assertEquals(today, state.selectedDay)
        assertEquals(LocalDate.of(2026, 10, 1), state.period!!.start)
        assertEquals(BigDecimal("150.00"), state.dayTotal)
        assertEquals(BigDecimal("450.00"), state.periodTotal)
        assertEquals(listOf("hoy-efectivo", "hoy-compra", "hoy-pago"), state.dayItems.map { it.id })
        assertTrue(state.dayItems[1].isCardPurchase)
        assertTrue(state.dayItems[2].isCardPayment)
        // El texto de la fila ("Pago a tarjeta · Tarjeta card-1") lo arma la pantalla con estos datos.
        assertEquals("Tarjeta card-1", state.dayItems[2].cardName)
        assertEquals("Comida", state.dayItems[1].categoryName)
        assertEquals("Tarjeta card-1", state.dayItems[1].cardName)

        // La tira: todos los dias del periodo, con la barra respecto al dia mas alto.
        assertEquals(listOf(LocalDate.of(2026, 10, 1), today), state.days.map { it.date })
        assertEquals(1f, state.days[0].ratio)
        assertEquals(0.5f, state.days[1].ratio)
    }

    @Test
    fun `navegar de periodo elige el ultimo dia que ya paso y regresar lleva a hoy`() = runTest {
        seedDays()
        val vm = viewModel()
        vm.state.awaitUntil { it.loaded && it.period != null }

        vm.previousPeriod()
        var state = vm.state.awaitUntil { it.period?.start == LocalDate.of(2026, 9, 16) }
        assertEquals(LocalDate.of(2026, 9, 30), state.selectedDay)
        assertEquals(BigDecimal("80.00"), state.periodTotal)
        assertTrue(state.period!!.canGoNext)

        vm.selectDay(LocalDate.of(2026, 9, 20))
        state = vm.state.awaitUntil { it.selectedDay == LocalDate.of(2026, 9, 20) && it.dayItems.isNotEmpty() }
        assertEquals(listOf("septiembre"), state.dayItems.map { it.id })

        vm.goToToday()
        state = vm.state.awaitUntil { it.selectedDay == today && it.period?.start == LocalDate.of(2026, 10, 1) }
        assertFalse(state.period!!.canGoNext)

        // Un dia futuro no se puede elegir.
        vm.selectDay(today.plusDays(1))
        assertEquals(today, vm.state.value.selectedDay)
    }

    @Test
    fun `al guardar un gasto de otro periodo se salta a su dia`() = runTest {
        seedDays()
        val vm = viewModel()
        vm.state.awaitUntil { it.loaded && it.period != null }

        vm.showDay(LocalDate.of(2026, 9, 20))
        val state = vm.state.awaitUntil { it.selectedDay == LocalDate.of(2026, 9, 20) && it.dayItems.isNotEmpty() && it.periodTotal.signum() > 0 && it.period?.start?.monthValue == 9 }
        assertEquals(LocalDate.of(2026, 9, 16), state.period!!.start)
        assertEquals("Quincena anterior", state.period!!.label.text(strings))
    }

    @Test
    fun `el rango de Explorar periodo se aplica en Resumen`() = runTest {
        seedDays()
        val vm = viewModel()
        vm.state.awaitUntil { it.loaded && it.period != null }

        vm.applyRange(LocalDate.of(2026, 9, 20), today)
        val state = vm.state.awaitUntil { it.period?.start == LocalDate.of(2026, 9, 20) }
        assertEquals("Periodo personalizado", state.period!!.label.text(strings))
        assertEquals(13, state.days.size)
        assertEquals(BigDecimal("530.00"), state.periodTotal)
        assertEquals(today, state.selectedDay)
    }

    @Test
    fun `borrar y deshacer`() = runTest {
        seedDays()
        val vm = viewModel()
        vm.state.awaitUntil { it.dayItems.size == 3 }

        var deleted = false
        vm.deleteSpending("hoy-efectivo") { deleted = true }
        vm.state.awaitUntil { it.dayItems.size == 2 }
        assertTrue(deleted)
        assertEquals(BigDecimal("50.00"), vm.state.value.dayTotal)

        vm.restoreSpending("hoy-efectivo")
        vm.state.awaitUntil { it.dayItems.size == 3 }
    }

    @Test
    fun `los accesos muestran la tarjeta que vence primero y las suscripciones`() = runTest {
        seedDays()
        db.subscriptionDao().upsert(subscription("sub-1"))
        val state = viewModel().state.awaitUntil { it.cardChip != null && it.subscriptionsSubtitle?.contains("al mes") == true }

        // card-1: corte 5, pago 25. Debe 200 - 50 = 150 y vence en 23 dias.
        assertEquals("Tarjeta card-1 · vence en 23 días (25/oct)", state.cardChip!!.text)
        assertEquals(StatusLevel.OK, state.cardChip!!.level)
        // Servicio mensual de $199 anclado al 31 de enero: el proximo cobro es el 31 de octubre.
        assertEquals("\$199.00 al mes · Servicio sub-1 cobra en 29 días", state.subscriptionsSubtitle)
    }

    @Test
    fun `sin tarjetas el acceso invita a agregarlas`() = runTest {
        seedBasics()
        cards.delete("card-1")
        val state = viewModel().state.awaitUntil { it.loaded && it.cardsSubtitle?.startsWith("Agrega") == true }
        assertNull(state.cardChip)
        // null: la pantalla pone "Revisa cuánto pagas al mes y qué cobros vienen".
        assertNull(state.subscriptionsSubtitle)
    }
}
