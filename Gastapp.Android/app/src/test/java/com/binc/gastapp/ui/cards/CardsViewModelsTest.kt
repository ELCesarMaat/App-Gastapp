package com.binc.gastapp.ui.cards

import androidx.lifecycle.SavedStateHandle
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
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

/** Mis tarjetas y su formulario con Room en memoria (hoy: 2 oct 2026). */
class CardsViewModelsTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.cardsViewModel(): CardsViewModel {
        val vm = CardsViewModel(cards, categories, clock)
        vm.state.launchIn(backgroundScope)
        return vm
    }

    private fun formViewModel(id: String? = null) =
        CardFormViewModel(SavedStateHandle(if (id == null) emptyMap() else mapOf("creditCardId" to id)), cards, categories, strings, texts, clock)

    @Test
    fun `resumen global, pago y ajuste de saldo`() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("compra", 50_000, at(LocalDate.of(2026, 9, 20), 12), isCreditCard = true, creditCardId = "card-1"))
        val vm = cardsViewModel()
        val state = vm.state.awaitUntil { it.loaded && it.hasCards }

        assertEquals(BigDecimal("500.00"), state.totalDebt)
        assertEquals(0, state.totalLimit.compareTo(BigDecimal("20000")))
        assertEquals(0.025f, state.combinedUsage)
        val summary = state.summaries.single()

        val paid = CompletableDeferred<Unit>()
        vm.registerPayment(summary, BigDecimal("200.00")) { paid.complete(Unit) }
        paid.await()
        var updated = vm.state.awaitUntil { it.totalDebt.compareTo(BigDecimal("300")) == 0 }.summaries.single()

        // Ajustar a 0 crea un abono por la diferencia; ajustar al mismo saldo no crea nada.
        val adjusted = CompletableDeferred<Boolean>()
        vm.adjustBalance(updated, BigDecimal.ZERO) { adjusted.complete(it) }
        assertTrue(adjusted.await())
        updated = vm.state.awaitUntil { it.totalDebt.signum() == 0 }.summaries.single()
        val same = CompletableDeferred<Boolean>()
        vm.adjustBalance(updated, BigDecimal.ZERO) { same.complete(it) }
        assertFalse(same.await())

        val adjustment = db.spendingDao().pendingSync().first { it.title.startsWith("Ajuste") }
        assertEquals("Ajuste de saldo (Abono) - Tarjeta card-1", adjustment.title)
        assertEquals(30_000L, adjustment.amountCents)
        assertEquals(PaymentMethods.TRANSFER, adjustment.paymentMethod)
    }

    @Test
    fun `primero la mas proxima a vencer y al pagarla se queda en su lugar`() = runTest {
        seedBasics() // card-1: corte 5, pago 25 (vence el 25 de octubre)
        db.creditCardDao().upsert(card("card-2", cutOffDay = 20, paymentDay = 10))
        db.spendingDao().upsert(spending("c1", 30_000, at(LocalDate.of(2026, 9, 25), 12), isCreditCard = true, creditCardId = "card-1"))
        // Facturada en el corte del 20 de septiembre: vence el 10 de octubre.
        db.spendingDao().upsert(spending("c2", 50_000, at(LocalDate.of(2026, 9, 15), 12), isCreditCard = true, creditCardId = "card-2"))

        val vm = cardsViewModel()
        val state = vm.state.awaitUntil { it.summaries.size == 2 }
        assertEquals(listOf("card-2", "card-1"), state.summaries.map { it.card.creditCardId })
        assertEquals(LocalDate.of(2026, 10, 10), state.summaries.first().nextPaymentDueDate)

        // Un abono que no cubre el corte deja la fecha limite donde estaba.
        val first = state.summaries.first()
        assertTrue(first.paymentNeedsConfirmation(BigDecimal("200.00")))
        val partial = CompletableDeferred<Unit>()
        vm.registerPayment(first, BigDecimal("200.00")) { partial.complete(Unit) }
        partial.await()
        val stillDue = vm.state.awaitUntil { it.summaries.first().totalDebt.compareTo(BigDecimal("300")) == 0 }.summaries.first()
        assertEquals(LocalDate.of(2026, 10, 10), stillDue.nextPaymentDueDate)

        // Confirmado como el pago del mes: pasa al mes siguiente.
        val paid = CompletableDeferred<Unit>()
        vm.registerPayment(stillDue, BigDecimal("100.00"), isStatementPayment = true) { paid.complete(Unit) }
        paid.await()
        val after = vm.state.awaitUntil { it.summaries.first().nextPaymentDueDate == LocalDate.of(2026, 11, 10) }
        assertEquals("Mientras la pantalla esta abierta no se reordena", listOf("card-2", "card-1"), after.summaries.map { it.card.creditCardId })
        assertTrue(db.spendingDao().pendingSync().any { it.description == "Pago del mes · Abono a tarjeta Banco" })

        // Al volver a entrar ya va al final.
        val reopened = cardsViewModel().state.awaitUntil { it.summaries.size == 2 }
        assertEquals(listOf("card-1", "card-2"), reopened.summaries.map { it.card.creditCardId })
    }

    @Test
    fun `eliminar la tarjeta la quita de la lista`() = runTest {
        seedBasics()
        val vm = cardsViewModel()
        val summary = vm.state.awaitUntil { it.hasCards }.summaries.single()
        val done = CompletableDeferred<Unit>()
        vm.delete(summary) { done.complete(Unit) }
        done.await()
        vm.state.awaitUntil { it.loaded && !it.hasCards }
        assertTrue(db.creditCardDao().get("card-1")!!.isDeleted)
    }

    @Test
    fun `el formulario valida nombre y banco y edita sin tocar el saldo`() = runTest {
        seedBasics()
        val vm = formViewModel("card-1")
        val state = vm.state.awaitUntil { it.loaded }
        assertTrue(state.isEdit)
        assertEquals("Tarjeta card-1", state.cardName)
        assertEquals("20000", state.creditLimitText)
        assertEquals(5, state.cutOffDay)

        vm.onBankNameChange("  ")
        vm.save { error("no debia guardar") }
        assertEquals("Ingresa el banco emisor (Ej. BBVA, Citibanamex, Santander).", vm.state.value.error)

        vm.onBankNameChange("Nu")
        vm.onCutOffDayChange(14)
        vm.onColorSelect("#7C3AED")
        val message = CompletableDeferred<String>()
        vm.save { message.complete(it) }
        assertEquals("Tarjeta actualizada.", message.await())
        val row = db.creditCardDao().get("card-1")!!
        assertEquals("Nu", row.bankName)
        assertEquals(14, row.cutOffDay)
        assertEquals("#7C3AED", row.colorHex)
        assertFalse(row.isSynced)
    }

    @Test
    fun `una tarjeta en uso registra su saldo de contado y sus MSI previas`() = runTest {
        seedBasics()
        val vm = formViewModel()
        vm.state.awaitUntil { it.loaded }
        assertEquals("Último corte: 15/sep  ·  Próximo: 15/oct  ·  Pago: 05/oct", vm.state.value.cyclePreview(strings))

        vm.onCardNameChange("Oro")
        vm.onBankNameChange("BBVA")
        vm.onCreditLimitChange("10000")
        vm.onHasExistingBalanceChange(true)
        vm.onTotalUsedChange("6000")
        vm.onHasActiveMsiChange(true)
        vm.openMsiDraft()
        assertFalse("Sin mensualidad no se agrega", vm.addMsiPurchase())
        assertEquals("Ingresa cuánto pagas cada mes por esta compra.", vm.state.value.msiDraft!!.error)
        vm.updateMsiDraft { it.copy(title = "Laptop", monthlyText = "500", totalInstallments = 12, paidInstallments = 20) }
        assertEquals("Lo pagado se topa en el plazo - 1", 11, vm.state.value.msiDraft!!.paidInstallments)
        vm.updateMsiDraft { it.copy(paidInstallments = 4) }
        assertEquals("Te faltan 8 de 12 mensualidades", vm.state.value.msiDraft!!.remainingText(strings))
        assertTrue(vm.addMsiPurchase())

        val state = vm.state.value
        assertNull(state.msiDraft)
        assertEquals("\$6,000", state.totalUsedLabel)
        assertEquals("−\$4,000", state.msiDebtLabel)
        assertEquals("\$2,000", state.cashDebtLabel)
        assertEquals("Te quedarían \$4,000 disponibles de \$10,000", state.availablePreview(strings))
        assertFalse(state.overLimit)

        val message = CompletableDeferred<String>()
        vm.save { message.complete(it) }
        assertEquals("Tarjeta agregada.", message.await())

        val card = db.creditCardDao().getAll().first { it.cardName == "Oro" }
        val movements = db.spendingDao().pendingSync().filter { it.creditCardId == card.creditCardId }.sortedBy { it.amountCents }
        assertEquals(2, movements.size)
        val cash = movements[0]
        assertEquals("Saldo de contado - Oro", cash.title)
        assertEquals(200_000L, cash.amountCents)
        // Ya salio en el estado de cuenta: se fecha el dia del ultimo corte (15 de septiembre).
        assertEquals(LocalDateTime.of(2026, 9, 15, 0, 0), cash.date)
        val msi = movements[1]
        assertEquals("Laptop", msi.title)
        assertEquals(400_000L, msi.amountCents)
        assertTrue(msi.isMsi)
        // 4 pagadas: se guarda la del proximo corte, desde donde avanza sola.
        assertEquals(5, msi.currentInstallment)
        assertEquals(50_000L, msi.installmentMonthlyAmountCents)
        assertEquals("cat-default", msi.categoryId)
    }

    @Test
    fun `avisa si los MSI rebasan el total o la deuda el limite`() = runTest {
        seedBasics()
        val vm = formViewModel()
        vm.state.awaitUntil { it.loaded }
        vm.onCreditLimitChange("1000")
        vm.onHasExistingBalanceChange(true)
        vm.onTotalUsedChange("500")
        vm.onHasActiveMsiChange(true)
        vm.openMsiDraft()
        vm.updateMsiDraft { it.copy(monthlyText = "300", totalInstallments = 6) }
        vm.addMsiPurchase()

        val state = vm.state.value
        assertTrue(state.overLimit)
        assertEquals(
            "Tus compras a meses suman \$1,800, más que los \$500 que capturaste como usado. Revisa las cantidades.",
            state.msiExceedsTotal(strings),
        )
        assertTrue(state.hasUnsavedData)
        vm.removeMsiPurchase(0)
        assertTrue(vm.state.value.msiPurchases.isEmpty())
    }

    @Test
    fun `una compra previa a un plazo que no esta en los chips`() = runTest {
        seedBasics()
        val vm = formViewModel()
        vm.state.awaitUntil { it.loaded }
        vm.onHasExistingBalanceChange(true)
        vm.onHasActiveMsiChange(true)
        vm.openMsiDraft()
        vm.updateMsiDraft { it.copy(monthlyText = "250").withCustomTerm("") }
        assertFalse("Sin plazo no se agrega", vm.addMsiPurchase())
        assertEquals("Escribe un plazo de 2 a 60 meses.", vm.state.value.msiDraft!!.customTermError)

        vm.updateMsiDraft { it.withCustomTerm("4").copy(paidInstallments = 9) }
        assertEquals("Lo pagado se topa en el plazo - 1", 3, vm.state.value.msiDraft!!.paidInstallments)
        vm.updateMsiDraft { it.copy(paidInstallments = 1) }
        assertEquals("Te faltan 3 de 4 mensualidades", vm.state.value.msiDraft!!.remainingText(strings))
        assertTrue(vm.addMsiPurchase())

        val purchase = vm.state.value.msiPurchases.single()
        assertEquals(4, purchase.totalInstallments)
        assertEquals(1, purchase.paidInstallments)
    }
}
