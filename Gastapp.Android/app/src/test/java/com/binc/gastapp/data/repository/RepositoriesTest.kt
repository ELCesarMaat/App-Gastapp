package com.binc.gastapp.data.repository

import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.PlannedSpending
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.model.Subscription
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest : DbTest() {

    @Test
    fun guardarUnGastoLoDejaPendienteYPideSincronizar() = runTest {
        seedBasics()
        val saved = spendings.save(
            Spending(spendingId = "s1", amount = BigDecimal("199.99"), date = LocalDateTime.of(today, java.time.LocalTime.NOON), title = "Comida"),
        )

        // Sin usuario ni categoria: los de la sesion y "Sin categoria".
        assertEquals(userId, saved.userId)
        assertEquals("cat-default", saved.categoryId)
        val row = db.spendingDao().get("s1")!!
        assertEquals(19_999L, row.amountCents)
        assertFalse(row.isSynced)
        assertEquals(1, scheduler.requests)
        assertEquals(BigDecimal("199.99"), spendings.get("s1")!!.amount)
    }

    @Test
    fun borrarYDeshacerUnGasto() = runTest {
        seedBasics()
        spendings.save(Spending(spendingId = "s1", amount = BigDecimal.TEN, date = today.atTime(10, 0), title = "x"))

        assertTrue(spendings.delete("s1"))
        assertTrue(spendings.observeDay(today).first().isEmpty())
        assertFalse(spendings.delete("s1"))

        assertTrue(spendings.restore("s1"))
        assertEquals(listOf("s1"), spendings.observeDay(today).first().map { it.spendingId })
    }

    @Test
    fun borrarUnaCategoriaPasaSusGastosASinCategoria() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("s1", 100, at(today, 9), categoryId = "cat-food"))
        db.subscriptionDao().upsert(subscription("sub-1", categoryId = "cat-food"))

        assertEquals(1, categories.countActiveSpendings("cat-food"))
        assertTrue(categories.delete("cat-food"))

        val moved = db.spendingDao().get("s1")!!
        assertEquals("cat-default", moved.categoryId)
        assertFalse(moved.isSynced)
        assertNull(db.subscriptionDao().get("sub-1")!!.categoryId)
        // Solo se marca: la borra de verdad la sincronizacion cuando el API confirme.
        val category = db.categoryDao().get("cat-food")!!
        assertTrue(category.isDeleted)
        assertEquals(listOf("cat-food"), db.categoryDao().pendingDeletion().map { it.categoryId })
        assertEquals(listOf("Sin categoria"), categories.observeAll().first().map { it.categoryName })
    }

    @Test
    fun laCategoriaPorDefectoNoSeBorra() = runTest {
        seedBasics()
        assertFalse(categories.delete("cat-default"))
        assertFalse(db.categoryDao().get("cat-default")!!.isDeleted)
    }

    @Test
    fun reconoceLaPorDefectoPorNombreEnBasesViejas() = runTest {
        seedBasics()
        db.categoryDao().upsert(CategoryEntity("cat-default", userId, "SIN CATEGORÍA ", isDefaultCategory = false, isSynced = true))

        val default = categories.ensureDefault()

        assertEquals("cat-default", default.categoryId)
        assertEquals("Sin categoria", default.categoryName)
        assertTrue(default.isDefaultCategory)
    }

    @Test
    fun creaLaPorDefectoSiNoExiste() = runTest {
        seedBasics()
        db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE categoryId = 'cat-default'")

        val default = categories.ensureDefault()

        assertTrue(default.isDefaultCategory)
        assertFalse(db.categoryDao().get(default.categoryId)!!.isSynced)
    }

    @Test
    fun tarjetasConSuDeuda() = runTest {
        seedBasics()
        db.spendingDao().upsertAll(
            listOf(
                spending("compra", 100_000, at(today, 9), isCreditCard = true, creditCardId = "card-1"),
                spending("abono", 30_000, at(today, 10), creditCardId = "card-1"),
            ),
        )
        db.creditCardDao().upsert(card("card-2"))
        db.spendingDao().upsert(spending("sobrepago", 5_000, at(today, 11), creditCardId = "card-2"))

        val withDebt = cards.observeCardsWithDebt().first().associateBy { it.card.creditCardId }

        assertEquals(BigDecimal("700.00"), withDebt.getValue("card-1").debt)
        assertEquals(BigDecimal("-50.00"), withDebt.getValue("card-2").balance)
        assertEquals(BigDecimal.ZERO, withDebt.getValue("card-2").debt)
    }

    @Test
    fun altaDeTarjetaEnUsoConSusMovimientos() = runTest {
        seedBasics()
        val card = CreditCard(creditCardId = "nueva", cutOffDay = 10, paymentDay = 30, cardName = "Nueva", bankName = "Banco")
        val saldo = PlannedSpending(
            title = "Saldo inicial",
            description = "",
            amount = BigDecimal("1500.50"),
            date = today.minusDays(3).atStartOfDay(),
            isCreditCard = true,
            creditCardId = "nueva",
            paymentMethod = PaymentMethods.CREDIT_CARD,
        )

        cards.createWithMovements(card, listOf(saldo), categoryId = null)

        val withDebt = cards.observeCardsWithDebt().first().single { it.card.creditCardId == "nueva" }
        assertEquals(BigDecimal("1500.50"), withDebt.debt)
        assertEquals(userId, withDebt.card.userId)
    }

    @Test
    fun borrarUnaTarjetaEsLogico() = runTest {
        seedBasics()
        assertTrue(cards.delete("card-1"))
        assertTrue(cards.observeCards().first().isEmpty())
        val row = db.creditCardDao().get("card-1")!!
        assertTrue(row.isDeleted)
        assertEquals(clock.instant(), row.deletedAt)
        assertNull(cards.get("card-1"))
    }

    @Test
    fun registrarCobroCreaElGastoEnLaTarjetaYAnotaElCobro() = runTest {
        seedBasics()
        subscriptions.save(
            Subscription(
                subscriptionId = "sub-1",
                serviceName = "Netflix",
                amount = BigDecimal("219.00"),
                firstChargeDate = LocalDate.of(2026, 1, 31),
                billingCycle = BillingCycles.MONTHLY,
                paymentMethod = PaymentMethods.CREDIT_CARD,
                creditCardId = "card-1",
                categoryId = "cat-food",
            ),
        )

        val spending = subscriptions.registerCharge("sub-1", BigDecimal("219.00"))!!

        assertTrue(spending.isCreditCard)
        assertEquals("card-1", spending.creditCardId)
        assertEquals("cat-food", spending.categoryId)
        assertEquals("Suscripción - Netflix", spending.title)
        assertEquals(LocalDateTime.now(clock), spending.date)
        val sub = subscriptions.get("sub-1")!!
        assertEquals(LocalDateTime.now(clock), sub.lastChargeRegisteredAt)
        assertEquals(clock.instant(), db.subscriptionDao().get("sub-1")!!.lastChargeRegisteredAt)
        assertEquals(BigDecimal("219.00"), cards.observeCardsWithDebt().first().single().debt)
    }

    @Test
    fun lasFechasDeCalendarioDeLaSuscripcionNoSeMueven() = runTest {
        seedBasics()
        val original = Subscription(
            subscriptionId = "sub-1",
            serviceName = "Anual",
            amount = BigDecimal("999.00"),
            firstChargeDate = LocalDate.of(2026, 1, 31),
            billingCycle = BillingCycles.YEARLY,
            isTrial = true,
            trialEndDate = LocalDate.of(2026, 2, 28),
        )
        subscriptions.save(original)

        val leida = subscriptions.get("sub-1")!!
        assertEquals(LocalDate.of(2026, 1, 31), leida.firstChargeDate)
        assertEquals(LocalDate.of(2026, 2, 28), leida.trialEndDate)
        assertEquals("2026-01-31", db.query("SELECT firstChargeDate FROM subscriptions", null).use { it.moveToFirst(); it.getString(0) })
    }

    @Test
    fun pausarYBorrarUnaSuscripcion() = runTest {
        seedBasics()
        db.subscriptionDao().upsert(subscription("sub-1"))

        assertTrue(subscriptions.setActive("sub-1", false))
        assertFalse(subscriptions.get("sub-1")!!.isActive)
        assertTrue(subscriptions.delete("sub-1"))
        assertTrue(subscriptions.observeAll().first().isEmpty())
        assertNotNull(db.subscriptionDao().get("sub-1")!!.deletedAt)
    }

    @Test
    fun perfilDelUsuario() = runTest {
        seedBasics()
        val user = users.getUser()!!
        assertEquals(BigDecimal("10000.00"), user.salary)
        assertEquals(BigDecimal("15.3846"), user.percentSave)

        users.saveProfile(user.copy(incomeTypeId = 1, firstPayDay = 5, salary = BigDecimal("2500.00")))

        val row = db.userDao().pendingSync()!!
        assertEquals(250_000L, row.salaryCents)
        assertEquals(5, row.firstPayDay)
        assertEquals(listOf(1, 2, 3), users.observeIncomeTypes().first().map { it.incomeTypeId })
    }
}
