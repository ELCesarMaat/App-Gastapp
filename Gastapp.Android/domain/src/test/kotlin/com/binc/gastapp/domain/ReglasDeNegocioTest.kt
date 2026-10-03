package com.binc.gastapp.domain

import com.binc.gastapp.domain.cards.PendingMsiPurchase
import com.binc.gastapp.domain.cards.calculateCycleDates
import com.binc.gastapp.domain.cards.inUseCardMovements
import com.binc.gastapp.domain.cards.pendingAmount
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.periods.periodBounds
import com.binc.gastapp.domain.spendings.dayTotal
import com.binc.gastapp.domain.spendings.msiMonthlyInstallment
import com.binc.gastapp.domain.spendings.periodTotal
import com.binc.gastapp.domain.subscriptions.buildSubscriptionSummary
import com.binc.gastapp.domain.subscriptions.nextChargeDate
import com.binc.gastapp.domain.subscriptions.paymentMethodDisplayName
import com.binc.gastapp.domain.subscriptions.subscriptionCharge
import com.binc.gastapp.domain.subscriptions.totalMonthlyCost
import com.binc.gastapp.domain.time.toApiInstant
import com.binc.gastapp.domain.time.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Las reglas del anexo C del plan de migracion, una prueba por regla. Si una se rompe,
 * el nombre de la prueba dice cual. Complementan a las pruebas de paridad.
 */
class ReglasDeNegocioTest {

    private fun d(text: String) = LocalDate.parse(text)
    private fun dt(text: String) = LocalDateTime.parse(text)
    private fun m(text: String) = BigDecimal(text)

    private val card = CreditCard(creditCardId = "T", cutOffDay = 25, paymentDay = 15, cardName = "Azul")

    private fun purchase(amount: String, date: String) =
        Spending(spendingId = "c$date$amount", amount = m(amount), date = dt(date), isCreditCard = true, creditCardId = "T")

    private fun payment(amount: String, date: String) =
        Spending(spendingId = "p$date$amount", amount = m(amount), date = dt(date), isCreditCard = false, creditCardId = "T")

    // 1
    @Test
    fun `corte y pago se calculan cada uno por separado`() {
        // Hoy 30, corte 25, pago 15: el pago del 15 de septiembre es el del corte del
        // 25 de agosto. Calcularlo desde el proximo corte saltaria a octubre.
        val dates = calculateCycleDates(cutOffDay = 25, paymentDay = 15, referenceDate = d("2026-08-30"))
        assertEquals(d("2026-09-25"), dates.cutOffDate)
        assertEquals(d("2026-09-15"), dates.paymentDueDate)
    }

    // 2
    @Test
    fun `la fecha limite salta al mes siguiente cuando el corte ya quedo pagado`() {
        val billedBeforeCut = purchase("1000.00", "2026-08-20T13:00")
        val today = d("2026-09-05")

        val unpaid = calculateCycleDates(card, listOf(billedBeforeCut), today)
        assertEquals(d("2026-09-15"), unpaid.paymentDueDate)

        val paid = calculateCycleDates(card, listOf(billedBeforeCut, payment("1000.00", "2026-09-01T10:00")), today)
        assertEquals(d("2026-10-15"), paid.paymentDueDate)
    }

    @Test
    fun `si el corte del estado de cuenta no ha llegado no se toca la fecha limite`() {
        // Corte 25, pago 30: el 10 de septiembre el pago del 30 liquida el corte del 25
        // de septiembre, que todavia no ocurre.
        val lateCard = card.copy(cutOffDay = 25, paymentDay = 30)
        val dates = calculateCycleDates(lateCard, emptyList(), d("2026-09-10"))
        assertEquals(d("2026-09-30"), dates.paymentDueDate)
    }

    @Test
    fun `una tarjeta nueva sin historial antes del corte no se da por pagada`() {
        // Corte 14, pago 4. Solo hay compras despues del corte: lo facturado es 0, pero
        // se debe dinero, asi que el pago del 4 sigue vigente.
        val newCard = card.copy(cutOffDay = 14, paymentDay = 4)
        val today = d("2026-09-02")
        val dates = calculateCycleDates(newCard, listOf(purchase("500.00", "2026-08-20T09:00")), today)
        assertEquals(d("2026-09-04"), dates.paymentDueDate)

        // Sin deuda alguna, en cambio, si se da por cubierto.
        assertEquals(d("2026-10-04"), calculateCycleDates(newCard, emptyList(), today).paymentDueDate)
    }

    @Test
    fun `un centavo de diferencia cuenta como pagado`() {
        val spendings = listOf(purchase("1000.00", "2026-08-20T13:00"), payment("999.995", "2026-09-01T10:00"))
        assertEquals(d("2026-10-15"), calculateCycleDates(card, spendings, d("2026-09-05")).paymentDueDate)
    }

    // 3
    @Test
    fun `los pagos a tarjeta son gastos sin isCreditCard y restan deuda`() {
        val spendings = listOf(
            purchase("1000.00", "2026-09-01T10:00"),
            purchase("500.00", "2026-09-02T10:00"),
            payment("400.00", "2026-09-03T10:00"),
            payment("50.00", "2026-09-03T11:00").copy(isDeleted = true),
        )
        assertEquals(0, m("1100.00").compareTo(pendingAmount("T", spendings)))
        // Nunca negativa aunque se abone de mas.
        assertEquals(0, BigDecimal.ZERO.compareTo(pendingAmount("T", spendings + payment("5000", "2026-09-04T10:00"))))
    }

    // 4
    @Test
    fun `una suscripcion con ancla el 31 no pierde el dia en meses cortos`() {
        val anchor = d("2026-01-31")
        assertEquals(d("2026-02-28"), nextChargeDate(anchor, BillingCycles.MONTHLY, d("2026-02-01")))
        assertEquals(d("2026-03-31"), nextChargeDate(anchor, BillingCycles.MONTHLY, d("2026-03-01")))
        assertEquals(d("2026-04-30"), nextChargeDate(anchor, BillingCycles.MONTHLY, d("2026-04-01")))
        assertEquals(d("2026-05-31"), nextChargeDate(anchor, BillingCycles.MONTHLY, d("2026-05-01")))
    }

    // 5
    @Test
    fun `ni pausadas ni en prueba gratis cuentan en el gasto recurrente`() {
        val today = d("2026-10-02")
        val base = Subscription(subscriptionId = "a", serviceName = "A", amount = m("100"), firstChargeDate = d("2026-01-10"))
        val subscriptions = listOf(
            base,
            base.copy(subscriptionId = "b", amount = m("200"), isActive = false),
            base.copy(subscriptionId = "c", amount = m("300"), isTrial = true, trialEndDate = d("2026-10-20")),
            // La prueba ya termino: ya se esta cobrando y si cuenta.
            base.copy(subscriptionId = "d", amount = m("40"), isTrial = true, trialEndDate = d("2026-09-20")),
        )
        assertEquals(0, m("140").compareTo(totalMonthlyCost(subscriptions, today)))
    }

    // 6
    @Test
    fun `registrar cobro avisa pero no bloquea`() {
        val subscription = Subscription(
            subscriptionId = "s",
            serviceName = "Netflix",
            planName = "Estándar",
            amount = m("299"),
            firstChargeDate = d("2026-01-03"),
            paymentMethod = PaymentMethods.CREDIT_CARD,
            creditCardId = "T",
            lastChargeRegisteredAt = dt("2026-10-03T09:00"),
        )
        val summary = buildSubscriptionSummary(subscription, m("299"), emptyMap(), emptyMap(), d("2026-10-05"))
        assertTrue("el cobro del periodo ya estaba registrado", summary.isCurrentCycleCharged)

        // Aun asi se puede registrar otro (puede ser un cargo doble real), y como va a
        // tarjeta, suma a la deuda de esa tarjeta.
        val charge = subscriptionCharge(subscription, m("299"), dt("2026-10-05T10:00"))
        assertTrue(charge.isCreditCard)
        assertEquals("T", charge.creditCardId)
        assertEquals("Suscripción - Netflix", charge.title)
        assertEquals("Cobro mensual de Netflix (Estándar)", charge.description)
    }

    // 7
    @Test
    fun `las formas de pago son las mismas cuatro con las mismas etiquetas`() {
        assertEquals("Efectivo", paymentMethodDisplayName(PaymentMethods.CASH))
        assertEquals("Tarjeta de débito", paymentMethodDisplayName(PaymentMethods.DEBIT))
        assertEquals("Transferencia bancaria", paymentMethodDisplayName(PaymentMethods.TRANSFER))
        assertEquals("Tarjeta de crédito", paymentMethodDisplayName(PaymentMethods.CREDIT_CARD))
    }

    // 8 y 9
    @Test
    fun `la fecha de un gasto viaja en UTC y regresa a hora local`() {
        val mexico = ZoneId.of("America/Mexico_City")
        val local = dt("2026-10-02T23:30")
        val instant = local.toApiInstant(mexico)
        assertEquals(Instant.parse("2026-10-03T05:30:00Z"), instant)
        assertEquals(local, instant.toLocalDateTime(mexico))
    }

    // 10
    @Test
    fun `en modo semanal el dia de pago es FirstPayDay con domingo igual a cero`() {
        // Viernes 2 de octubre de 2026, pago los domingos (0): el periodo empezo el
        // domingo 27 de septiembre y llega hasta hoy.
        val period = periodBounds(IncomeTypes.WEEKLY, firstPayDay = 0, secondPayDay = null, referenceDate = d("2026-10-02"))
        assertEquals(d("2026-09-27"), period.start)
        assertEquals(d("2026-10-02"), period.end)
    }

    // 11
    @Test
    fun `el redondeo es bancario como Math Round de NET`() {
        assertEquals(m("0.12"), msiMonthlyInstallment(m("0.25"), 2)) // 0.125 -> 0.12
        assertEquals(m("0.18"), msiMonthlyInstallment(m("0.35"), 2)) // 0.175 -> 0.18
        assertEquals(m("33.33"), msiMonthlyInstallment(m("100"), 3))
    }

    @Test
    fun `las compras con tarjeta no cuentan en el periodo pero si en el dia`() {
        val day = d("2026-10-02")
        val spendings = listOf(
            purchase("100.00", "2026-10-02T12:00"),
            Spending(spendingId = "x", amount = m("50.00"), date = dt("2026-10-02T13:00")),
        )
        assertEquals(0, m("150.00").compareTo(dayTotal(spendings, day)))
        assertEquals(0, m("50.00").compareTo(periodTotal(spendings, day, day)))
    }

    @Test
    fun `el total del periodo incluye todo el ultimo dia`() {
        // Diferencia intencional con MAUI, que dejaba fuera lo gastado el ultimo dia.
        val spendings = listOf(Spending(spendingId = "x", amount = m("80.00"), date = dt("2026-10-02T18:45")))
        assertEquals(0, m("80.00").compareTo(periodTotal(spendings, d("2026-09-27"), d("2026-10-02"))))
    }

    @Test
    fun `una tarjeta en uso fecha el saldo del corte el dia del ultimo corte`() {
        val movements = inUseCardMovements(
            card = card,
            totalUsed = m("5000"),
            currentCycleDebt = m("1200"),
            balanceAlreadyCut = false,
            msiPurchases = listOf(PendingMsiPurchase("Pantalla", m("500"), paidInstallments = 4, totalInstallments = 6)),
            now = dt("2026-10-02T10:00"),
        )
        // 5000 usados - 1000 que faltan del MSI = 4000 de contado: 1200 del corte + 2800 previos.
        assertEquals(3, movements.size)
        assertEquals(dt("2026-09-25T00:00"), movements[0].date)
        assertEquals(0, m("1200").compareTo(movements[0].amount))
        assertEquals(0, m("2800").compareTo(movements[1].amount))
        assertTrue(movements[2].isMsi)
        assertEquals(0, m("1000").compareTo(movements[2].amount))
        assertFalse(movements[0].isMsi)
    }
}
