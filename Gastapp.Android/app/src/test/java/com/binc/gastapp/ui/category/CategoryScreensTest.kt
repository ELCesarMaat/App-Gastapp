package com.binc.gastapp.ui.category

import androidx.lifecycle.SavedStateHandle
import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.spendings.CategoryTotal
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import com.binc.gastapp.ui.spending.SpendingDetailState
import com.binc.gastapp.ui.spending.SpendingDetailViewModel
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Sin categoria" agrupada, detalle de categoria y detalle de gasto. */
class CategoryScreensTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `dos Sin categoria se muestran como una sola`() {
        val directory = CategoryDirectory(
            listOf(
                Category("cat-default", "Sin categoria", isDefaultCategory = true),
                Category("cat-food", "Comida"),
                Category("cat-default-2", "Sin categoría"),
            ),
        )
        assertEquals(setOf("cat-default", "cat-default-2"), directory.defaultIds)
        assertEquals(listOf("cat-default", "cat-food"), directory.pickerCategories.map { it.categoryId })
        assertEquals("Sin categoria", directory.nameOf("cat-default-2"))
        assertEquals("cat-default", directory.displayId("cat-default-2"))
        assertEquals(listOf("cat-default", "cat-default-2"), directory.idsFor("cat-default").sorted())
        assertEquals(listOf("cat-food"), directory.idsFor("cat-food"))

        // Una categoria que ya no existe en el telefono tambien se ve como "Sin categoria".
        val merged = directory.mergeTotals(
            listOf(
                CategoryTotal("cat-food", "Comida", BigDecimal("100")),
                CategoryTotal("cat-default", "Sin categoria", BigDecimal("30")),
                CategoryTotal("cat-default-2", "Sin categoría", BigDecimal("50")),
                CategoryTotal("borrada", "Sin categoria", BigDecimal("40")),
            ),
        )
        assertEquals(listOf("Sin categoria" to BigDecimal("120"), "Comida" to BigDecimal("100")), merged.map { it.name to it.amount })
        assertEquals("cat-default", merged.first().categoryId)
    }

    @Test
    fun `iconos de categoria por palabras`() {
        assertEquals(categoryIcon("Comida rápida"), categoryIcon("Tacos"))
        assertEquals(categoryIcon("Súper"), categoryIcon("Despensa"))
        assertEquals(categoryIcon("Sin categoría"), categoryIcon(null))
    }

    @Test
    fun `el detalle de categoria junta las Sin categoria y no cuenta compras con tarjeta`() = runTest {
        seedBasics()
        db.categoryDao().upsert(CategoryEntity("cat-default-2", userId, "Sin categoría", isSynced = true))
        val sep20 = LocalDate.of(2026, 9, 20)
        db.spendingDao().upsertAll(
            listOf(
                spending("a", 1_000, at(today, 9), categoryId = "cat-default"),
                spending("b", 2_000, at(today, 10), categoryId = "cat-default-2"),
                spending("c", 4_000, at(sep20, 10), categoryId = "cat-default-2"),
                spending("compra", 9_000, at(today, 11), categoryId = "cat-default", isCreditCard = true, creditCardId = "card-1"),
                spending("fuera", 9_000, at(LocalDate.of(2026, 8, 1), 11), categoryId = "cat-default"),
            ),
        )
        val vm = CategoryDetailViewModel(
            SavedStateHandle(mapOf("categoryId" to "cat-default", "start" to "2026-09-16", "end" to "2026-10-02")),
            categories,
            spendings,
            cards,
            clock,
        )
        vm.state.launchIn(backgroundScope)
        val state = vm.state.awaitUntil { it.loaded && it.count == 3 }

        assertEquals("Sin categoria", state.name)
        assertEquals(BigDecimal("70.00"), state.total)
        assertEquals(listOf(today, sep20), state.groups.map { it.day })
        assertEquals(listOf("b", "a"), state.groups.first().items.map { it.id })
        assertEquals(BigDecimal("30.00"), state.groups.first().total)
    }

    @Test
    fun `el detalle del gasto se cierra si lo borran`() = runTest {
        seedBasics()
        db.spendingDao().upsert(
            spending("s1", 120_000, at(today, 9), isCreditCard = true, creditCardId = "card-1")
                .copy(isMsi = true, totalInstallments = 6, installmentMonthlyAmountCents = 20_000, paymentMethod = "CreditCard"),
        )
        val vm = SpendingDetailViewModel(SavedStateHandle(mapOf("spendingId" to "s1")), spendings, categories, cards, strings, clock)
        vm.state.launchIn(backgroundScope)
        val shown = vm.state.awaitUntil { it is SpendingDetailState.Shown } as SpendingDetailState.Shown

        assertEquals("Comida", shown.detail.categoryName)
        assertEquals("Tarjeta de crédito", shown.detail.paymentText)
        assertEquals("Banco - Tarjeta card-1", shown.detail.cardText)
        assertEquals("Plan MSI a 6 meses · \$200.00/mes (Cuota 1 de 6)", shown.detail.msiText)
        assertTrue(shown.detail.isCardPurchase)

        spendings.delete("s1")
        vm.state.awaitUntil { it is SpendingDetailState.Gone }
    }
}
