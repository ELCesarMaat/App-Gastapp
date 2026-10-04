package com.binc.gastapp.ui.spending

import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Formulario de gasto (NewSpendingViewModel de MAUI) con Room en memoria. */
class SpendingFormViewModelTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel() = SpendingFormViewModel(spendings, categories, cards, clock)

    private suspend fun SpendingFormViewModel.saveAndWait(): SpendingFormResult {
        val result = CompletableDeferred<SpendingFormResult>()
        save { result.complete(it) }
        return result.await()
    }

    @Test
    fun `un gasto nuevo en efectivo cae en el dia elegido con la hora de ahora`() = runTest {
        seedBasics()
        val vm = viewModel()
        val day = LocalDate.of(2026, 9, 20)
        vm.open(SpendingFormRequest.New(day))
        vm.state.awaitUntil { it.loaded && it.categories.isNotEmpty() }
        assertEquals("cat-default", vm.state.value.selectedCategoryId)

        // Sin monto no se guarda.
        vm.save { error("no debia guardar") }
        assertEquals("Ingresa un monto válido mayor a 0.", vm.state.value.amountError)

        vm.onAmountChange("123.45")
        vm.onTitleChange("  Tacos ")
        vm.onDescriptionChange("*sin descripcion*")
        vm.onCategorySelect("cat-food")
        val result = vm.saveAndWait() as SpendingFormResult.Saved

        assertTrue(result.isNew)
        val row = db.spendingDao().get(result.spending.spendingId)!!
        assertEquals(12_345L, row.amountCents)
        assertEquals("Tacos", row.title)
        assertEquals("", row.description)
        assertEquals("cat-food", row.categoryId)
        assertEquals(userId, row.userId)
        assertEquals(PaymentMethods.CASH, row.paymentMethod)
        assertFalse(row.isCreditCard)
        // El reloj de la prueba marca las 12:00 en Ciudad de Mexico.
        assertEquals(LocalDateTime.of(2026, 9, 20, 12, 0), row.date)
        assertFalse(row.isSynced)
    }

    @Test
    fun `una compra a MSI desde Mis tarjetas guarda la mensualidad`() = runTest {
        seedBasics()
        val vm = viewModel()
        vm.open(SpendingFormRequest.New(today, creditCardId = "card-1", msi = true))
        val state = vm.state.awaitUntil { it.loaded && it.cards.isNotEmpty() }
        assertEquals(PaymentMethods.CREDIT_CARD, state.paymentMethod)
        assertTrue(state.isMsi)
        assertEquals("card-1", state.selectedCardId)

        vm.onAmountChange("1000")
        vm.onInstallmentsSelect(6)
        assertEquals("Pagarás \$166.67 al mes durante 6 meses.", vm.state.value.msiPreview)
        val saved = (vm.saveAndWait() as SpendingFormResult.Saved).spending

        val row = db.spendingDao().get(saved.spendingId)!!
        assertTrue(row.isCreditCard)
        assertEquals("card-1", row.creditCardId)
        assertTrue(row.isMsi)
        assertEquals(6, row.totalInstallments)
        assertEquals(1, row.currentInstallment)
        assertEquals(16_667L, row.installmentMonthlyAmountCents)
    }

    @Test
    fun `con credito y sin tarjetas no se puede guardar`() = runTest {
        seedBasics()
        cards.delete("card-1")
        val vm = viewModel()
        vm.open(SpendingFormRequest.New(today))
        vm.state.awaitUntil { it.loaded }
        vm.onAmountChange("50")
        vm.onPaymentMethodSelect(PaymentMethods.CREDIT_CARD)

        vm.save { error("no debia guardar") }
        assertEquals("Selecciona una tarjeta de crédito para el pago.", vm.state.value.error)

        // Si se cambia a debito ya se puede, sin tarjeta.
        vm.onPaymentMethodSelect(PaymentMethods.DEBIT)
        val saved = (vm.saveAndWait() as SpendingFormResult.Saved).spending
        assertNull(db.spendingDao().get(saved.spendingId)!!.creditCardId)
    }

    @Test
    fun `editar conserva la hora y la cuota, y no deja fechas futuras`() = runTest {
        seedBasics()
        db.spendingDao().upsert(
            spending("s1", 25_000, at(LocalDate.of(2026, 9, 20), 9, 30), isCreditCard = true, creditCardId = "card-1")
                .copy(isMsi = true, totalInstallments = 12, currentInstallment = 4, paymentMethod = PaymentMethods.CREDIT_CARD),
        )
        val vm = viewModel()
        vm.open(SpendingFormRequest.Edit("s1"))
        val state = vm.state.awaitUntil { it.loaded && it.isEdit }
        assertEquals("250", state.amountText)
        assertEquals(12, state.installments)
        assertEquals("cat-food", state.selectedCategoryId)

        vm.onDateChange(today.plusDays(1))
        assertEquals(LocalDate.of(2026, 9, 20), vm.state.value.date)
        vm.onDateChange(LocalDate.of(2026, 9, 21))
        val result = vm.saveAndWait() as SpendingFormResult.Saved

        assertFalse(result.isNew)
        val row = db.spendingDao().get("s1")!!
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 30), row.date)
        assertEquals(4, row.currentInstallment)
        assertEquals(2_083L, row.installmentMonthlyAmountCents)
    }

    @Test
    fun `crear categoria valida el nombre y la deja elegida`() = runTest {
        seedBasics()
        val vm = viewModel()
        vm.open(SpendingFormRequest.New(today))
        vm.state.awaitUntil { it.loaded && it.categories.size == 2 }

        vm.toggleNewCategory()
        vm.saveNewCategory()
        assertEquals("Ingresa un nombre de categoría.", vm.state.value.newCategoryError)
        vm.onNewCategoryNameChange(" comida ")
        vm.saveNewCategory()
        assertEquals("Ya existe una categoría con ese nombre.", vm.state.value.newCategoryError)

        vm.onNewCategoryNameChange("Viajes")
        vm.saveNewCategory()
        val state = vm.state.awaitUntil { it.categories.size == 3 && !it.newCategoryOpen }
        val created = state.categories.first { it.categoryName == "Viajes" }
        assertEquals(created.categoryId, state.selectedCategoryId)
    }

    @Test
    fun `borrar una categoria avisa cuantos gastos tiene y los pasa a Sin categoria`() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("s1", 1_000, at(today, 9)))
        val vm = viewModel()
        vm.open(SpendingFormRequest.New(today))
        vm.state.awaitUntil { it.loaded && it.categories.size == 2 }

        // "Sin categoria" no se puede borrar.
        assertFalse(vm.state.value.canDeleteSelectedCategory)
        vm.onCategorySelect("cat-food")
        vm.requestDeleteSelectedCategory()
        val prompt = vm.state.awaitUntil { it.deletePrompt != null }.deletePrompt!!
        assertEquals(1, prompt.spendingCount)
        assertEquals(
            "La categoría 'Comida' se está usando en 1 gasto(s). Si la eliminas, esos gastos pasarán a 'Sin categoria'.\n\n¿Deseas continuar?",
            prompt.message,
        )

        vm.confirmDeleteCategory()
        val state = vm.state.awaitUntil { it.categories.size == 1 }
        assertEquals("cat-default", state.selectedCategoryId)
        assertEquals("cat-default", db.spendingDao().get("s1")!!.categoryId)
    }

    @Test
    fun `eliminar desde la edicion`() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("s1", 1_000, at(today, 9)))
        val vm = viewModel()
        vm.open(SpendingFormRequest.Edit("s1"))
        vm.state.awaitUntil { it.loaded && it.isEdit }

        val result = CompletableDeferred<SpendingFormResult>()
        vm.delete { result.complete(it) }
        assertEquals(SpendingFormResult.Deleted("s1"), result.await())
        assertTrue(db.spendingDao().get("s1")!!.isDeleted)
    }

    @Test
    fun `con dos Sin categoria se ve una sola y un gasto de la otra no se mueve`() = runTest {
        seedBasics()
        db.categoryDao().upsert(CategoryEntity("cat-default-2", userId, "Sin categoría", isSynced = true))
        db.spendingDao().upsert(spending("s1", 1_000, at(today, 9), categoryId = "cat-default-2"))
        val vm = viewModel()
        vm.open(SpendingFormRequest.Edit("s1"))
        val state = vm.state.awaitUntil { it.loaded && it.isEdit && it.categories.isNotEmpty() }

        assertEquals(listOf("cat-default", "cat-food"), state.categories.map { it.categoryId })
        assertEquals("cat-default", state.selectedCategoryId)
        vm.onAmountChange("20")
        vm.saveAndWait()
        assertEquals("cat-default-2", db.spendingDao().get("s1")!!.categoryId)
        assertEquals(2_000L, db.spendingDao().get("s1")!!.amountCents)
    }

    @Test
    fun `un gasto que ya no existe avisa`() = runTest {
        seedBasics()
        val vm = viewModel()
        vm.open(SpendingFormRequest.Edit("no-existe"))
        assertEquals("El gasto ya no existe.", vm.state.awaitUntil { it.loaded }.error)
    }
}
