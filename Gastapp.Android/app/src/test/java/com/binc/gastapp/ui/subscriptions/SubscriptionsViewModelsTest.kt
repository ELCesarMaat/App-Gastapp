package com.binc.gastapp.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Subscription
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Suscripciones y su formulario con Room en memoria (hoy: 2 oct 2026). */
class SubscriptionsViewModelsTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.listViewModel(): SubscriptionsViewModel {
        val vm = SubscriptionsViewModel(subscriptions, cards, categories, clock)
        vm.state.launchIn(backgroundScope)
        return vm
    }

    private fun formViewModel(id: String? = null) = SubscriptionFormViewModel(
        SavedStateHandle(if (id == null) emptyMap() else mapOf("subscriptionId" to id)),
        subscriptions,
        cards,
        categories,
        clock,
    )

    private suspend fun seed() {
        seedBasics()
        subscriptions.save(
            Subscription("netflix", "Netflix", BigDecimal("219"), LocalDate.of(2026, 1, 3), paymentMethod = PaymentMethods.CREDIT_CARD, creditCardId = "card-1"),
        )
        subscriptions.save(Subscription("gym", "Gimnasio", BigDecimal("600"), LocalDate.of(2026, 1, 10), billingCycle = BillingCycles.QUARTERLY))
        subscriptions.save(
            Subscription("prueba", "Disney", BigDecimal("150"), LocalDate.of(2026, 10, 9), isTrial = true, trialEndDate = LocalDate.of(2026, 10, 9)),
        )
        subscriptions.save(Subscription("pausada", "Revista", BigDecimal("99"), LocalDate.of(2026, 1, 1), isActive = false))
    }

    @Test
    fun `los totales no cuentan pausadas ni pruebas y el chip lo explica`() = runTest {
        seed()
        val state = listViewModel().state.awaitUntil { it.loaded && it.summaries.size == 4 }

        // 219 al mes + 600 trimestral (200 al mes).
        assertEquals(0, state.monthlyTotal.compareTo(BigDecimal("419")))
        assertEquals(0, state.yearlyTotal.compareTo(BigDecimal("5028")))
        assertEquals("2 activas · 1 en prueba · 1 pausada", state.countsText)
        assertEquals("Siguiente cobro: Netflix · \$219.00 · mañana", state.nextChargeText)
    }

    @Test
    fun `registrar cobro crea el gasto en la tarjeta y avisa si ya estaba`() = runTest {
        seed()
        val vm = listViewModel()
        val netflix = vm.state.awaitUntil { it.summaries.size == 4 }.summaries.first { it.subscription.subscriptionId == "netflix" }
        assertFalse(netflix.isCurrentCycleCharged)

        val done = CompletableDeferred<Unit>()
        vm.registerCharge(netflix, BigDecimal("219")) { done.complete(Unit) }
        done.await()

        val charged = vm.state.awaitUntil { s -> s.summaries.first { it.subscription.subscriptionId == "netflix" }.isCurrentCycleCharged }
            .summaries.first { it.subscription.subscriptionId == "netflix" }
        assertTrue(charged.lastChargeText { it.toString() }.startsWith("Ya registraste el cobro"))
        val spending = db.spendingDao().pendingSync().single { it.title.contains("Netflix") }
        assertTrue(spending.isCreditCard)
        assertEquals("card-1", spending.creditCardId)
        assertEquals(21_900L, spending.amountCents)
    }

    @Test
    fun `pausar, reanudar y eliminar`() = runTest {
        seed()
        val vm = listViewModel()
        val gym = vm.state.awaitUntil { it.summaries.size == 4 }.summaries.first { it.subscription.subscriptionId == "gym" }

        val paused = CompletableDeferred<String>()
        vm.toggleActive(gym) { paused.complete(it) }
        assertEquals("Gimnasio quedó en pausa.", paused.await())
        vm.state.awaitUntil { it.countsText == "1 activa · 1 en prueba · 2 pausadas" }

        val deleted = CompletableDeferred<Unit>()
        vm.delete(gym) { deleted.complete(Unit) }
        deleted.await()
        vm.state.awaitUntil { it.summaries.size == 3 }
    }

    @Test
    fun `el formulario valida y muestra las fechas antes de guardar`() = runTest {
        seedBasics()
        val vm = formViewModel()
        vm.state.awaitUntil { it.loaded }
        assertEquals("card-1", vm.state.value.creditCardId)
        assertEquals("cat-default", vm.state.value.categoryId)

        vm.save { error("no debia guardar") }
        assertEquals("Ingresa el nombre del servicio (Ej. Netflix, Spotify, gimnasio).", vm.state.value.error)
        vm.onServiceNameChange("Spotify")
        vm.save { error("no debia guardar") }
        assertEquals("Ingresa cuánto te cobran en cada periodo.", vm.state.value.error)

        vm.onAmountChange("129")
        vm.onFirstChargeDateChange(LocalDate.of(2026, 9, 15))
        assertEquals("Próximo cobro: 15/oct/2026  ·  Después: 15/nov/2026", vm.state.value.chargePreview)
        assertEquals("Equivale a \$129.00 al mes  ·  \$1,548.00 al año", vm.state.value.monthlyPreview)

        vm.onTrialChange(true)
        vm.onTrialEndDateChange(LocalDate.of(2026, 9, 1))
        vm.save { error("no debia guardar") }
        assertEquals("La prueba gratis no puede terminar en una fecha que ya pasó.", vm.state.value.error)

        vm.onTrialChange(false)
        vm.onPaymentMethodSelect(PaymentMethods.DEBIT)
        val message = CompletableDeferred<String>()
        vm.save { message.complete(it) }
        assertEquals("Spotify agregado a tus suscripciones.", message.await())
        val saved = subscriptions.getAll().single()
        assertNull("Sin credito no se guarda la tarjeta", saved.creditCardId)
        assertNull(saved.trialEndDate)
        assertEquals(LocalDate.of(2026, 9, 15), saved.firstChargeDate)
    }

    @Test
    fun `editar conserva la pausa y una Sin categoria duplicada`() = runTest {
        seedBasics()
        db.categoryDao().upsert(CategoryEntity("cat-default-2", userId, "Sin categoría", isSynced = true))
        subscriptions.save(
            Subscription("s1", "Netflix", BigDecimal("219"), LocalDate.of(2026, 1, 3), isActive = false, categoryId = "cat-default-2"),
        )
        val vm = formViewModel("s1")
        val state = vm.state.awaitUntil { it.loaded && it.isEdit }
        assertEquals("cat-default", state.categoryId)

        vm.onAmountChange("249")
        val message = CompletableDeferred<String>()
        vm.save { message.complete(it) }
        assertEquals("Suscripción actualizada.", message.await())
        val saved = subscriptions.get("s1")!!
        assertFalse(saved.isActive)
        assertEquals("cat-default-2", saved.categoryId)
        assertEquals(0, saved.amount.compareTo(BigDecimal("249")))
    }
}
