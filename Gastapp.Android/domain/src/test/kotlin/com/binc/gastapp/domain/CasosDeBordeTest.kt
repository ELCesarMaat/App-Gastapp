package com.binc.gastapp.domain

import com.binc.gastapp.domain.cards.calculateCycleDates
import com.binc.gastapp.domain.cards.nextOccurrenceOfDay
import com.binc.gastapp.domain.cards.statementCutOffFor
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.periods.periodBounds
import com.binc.gastapp.domain.subscriptions.nextChargeDate
import com.binc.gastapp.domain.subscriptions.previousChargeDate
import com.binc.gastapp.domain.time.toApiInstant
import com.binc.gastapp.domain.time.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Casos de borde de la Fase 6.2 (fin de mes, dia 31, febrero, corte el mismo dia del
 * pago, anualidad el 29 de febrero, semanal en domingo y cambio de zona horaria). La
 * paridad ya los recorre con fechas al azar; aqui quedan con nombre para que, si uno se
 * rompe, se sepa cual.
 */
class CasosDeBordeTest {

    private fun d(text: String) = LocalDate.parse(text)
    private fun dt(text: String) = LocalDateTime.parse(text)

    // ------------------------------------------------------------ Tarjetas

    @Test
    fun `un corte el 31 cae el ultimo dia de febrero y regresa al 31 en marzo`() {
        assertEquals(d("2027-02-28"), nextOccurrenceOfDay(d("2027-02-10"), 31))
        assertEquals(d("2028-02-29"), nextOccurrenceOfDay(d("2028-02-10"), 31)) // bisiesto
        assertEquals(d("2027-03-31"), nextOccurrenceOfDay(d("2027-03-01"), 31))
        // El mismo dia cuenta como "proximo": el dia del corte no salta al mes siguiente.
        assertEquals(d("2027-02-28"), nextOccurrenceOfDay(d("2027-02-28"), 31))
    }

    @Test
    fun `al pagar un corte con fecha limite el 31 recortada a febrero la siguiente es el 31 de marzo`() {
        val card = CreditCard(creditCardId = "T", cutOffDay = 10, paymentDay = 31, cardName = "Azul")
        val purchase = Spending(spendingId = "c", amount = BigDecimal("500"), date = dt("2027-01-20T12:00"), isCreditCard = true, creditCardId = "T")
        val payment = Spending(spendingId = "p", amount = BigDecimal("500"), date = dt("2027-02-15T12:00"), creditCardId = "T")

        val unpaid = calculateCycleDates(card, listOf(purchase), d("2027-02-20"))
        assertEquals(d("2027-02-28"), unpaid.paymentDueDate)

        val paid = calculateCycleDates(card, listOf(purchase, payment), d("2027-02-20"))
        assertEquals(d("2027-03-31"), paid.paymentDueDate)
    }

    @Test
    fun `con corte y pago el mismo dia la fecha limite liquida el corte del mes anterior`() {
        val card = CreditCard(creditCardId = "T", cutOffDay = 15, paymentDay = 15, cardName = "Azul")
        // Antes del dia 15 los dos apuntan al 15 de este mes...
        val before = calculateCycleDates(card.cutOffDay, card.paymentDay, d("2026-10-10"))
        assertEquals(d("2026-10-15"), before.cutOffDate)
        assertEquals(d("2026-10-15"), before.paymentDueDate)
        // ...y ese pago es el del corte del 15 de septiembre, no el del mismo dia.
        assertEquals(d("2026-09-15"), statementCutOffFor(before.paymentDueDate, card.cutOffDay))

        // Lo comprado hasta el 15 de septiembre y ya pagado mueve la fecha limite a noviembre.
        val spendings = listOf(
            Spending(spendingId = "c", amount = BigDecimal("300"), date = dt("2026-09-12T12:00"), isCreditCard = true, creditCardId = "T"),
            Spending(spendingId = "p", amount = BigDecimal("300"), date = dt("2026-10-01T12:00"), creditCardId = "T"),
        )
        assertEquals(d("2026-11-15"), calculateCycleDates(card, spendings, d("2026-10-10")).paymentDueDate)
    }

    // ------------------------------------------------------------ Suscripciones

    @Test
    fun `una anualidad con ancla el 29 de febrero cobra el 28 y vuelve al 29 en bisiesto`() {
        val anchor = d("2024-02-29")
        assertEquals(d("2025-02-28"), nextChargeDate(anchor, BillingCycles.YEARLY, d("2024-03-01")))
        assertEquals(d("2026-02-28"), nextChargeDate(anchor, BillingCycles.YEARLY, d("2025-03-01")))
        assertEquals(d("2028-02-29"), nextChargeDate(anchor, BillingCycles.YEARLY, d("2027-03-01")))
        // Y hacia atras, para saber si el cobro del periodo ya se registro.
        assertEquals(d("2026-02-28"), previousChargeDate(anchor, BillingCycles.YEARLY, d("2026-10-03")))
    }

    @Test
    fun `una mensual con ancla el 30 cobra el 28 de febrero y el 30 de marzo`() {
        val anchor = d("2026-01-30")
        assertEquals(d("2026-02-28"), nextChargeDate(anchor, BillingCycles.MONTHLY, d("2026-02-01")))
        assertEquals(d("2026-03-30"), nextChargeDate(anchor, BillingCycles.MONTHLY, d("2026-03-01")))
    }

    // ------------------------------------------------------------ Periodos de pago

    @Test
    fun `en modo semanal con pago en domingo el periodo empieza ese mismo domingo`() {
        // Domingo 4 de octubre de 2026: el periodo es solo hoy.
        val today = periodBounds(IncomeTypes.WEEKLY, firstPayDay = 0, secondPayDay = null, referenceDate = d("2026-10-04"))
        assertEquals(d("2026-10-04"), today.start)
        assertEquals(d("2026-10-04"), today.end)

        // El anterior va del domingo 27 de septiembre al sabado 3 de octubre.
        val previous = periodBounds(IncomeTypes.WEEKLY, 0, null, d("2026-10-04"), periodOffset = 1)
        assertEquals(d("2026-09-27"), previous.start)
        assertEquals(d("2026-10-03"), previous.end)
    }

    @Test
    fun `una quincena con pago el 30 cae el 28 de febrero y la siguiente empieza el 15 de marzo`() {
        val feb = periodBounds(IncomeTypes.BIWEEKLY, firstPayDay = 15, secondPayDay = 30, referenceDate = d("2027-02-28"))
        assertEquals(d("2027-02-28"), feb.start)

        // El 1 de marzo sigue en la quincena que empezo el 28 de febrero.
        val march1 = periodBounds(IncomeTypes.BIWEEKLY, 15, 30, d("2027-03-01"))
        assertEquals(d("2027-02-28"), march1.start)

        // Y la de antes es del 15 al 27 de febrero.
        val previous = periodBounds(IncomeTypes.BIWEEKLY, 15, 30, d("2027-03-01"), periodOffset = 1)
        assertEquals(d("2027-02-15"), previous.start)
        assertEquals(d("2027-02-27"), previous.end)
    }

    @Test
    fun `un pago mensual el 31 empieza el periodo el ultimo dia de los meses cortos`() {
        val april = periodBounds(IncomeTypes.MONTHLY, firstPayDay = 31, secondPayDay = null, referenceDate = d("2026-05-10"))
        assertEquals(d("2026-04-30"), april.start)
        val feb = periodBounds(IncomeTypes.MONTHLY, 31, null, d("2026-02-28"))
        assertEquals(d("2026-02-28"), feb.start)
    }

    // ------------------------------------------------------------ Zona horaria

    @Test
    fun `un gasto conserva su hora local y solo la conversion al API usa la zona actual`() {
        // El gasto se guarda en hora local (como MAUI). Si el usuario viaja, la hora que
        // ve no cambia; lo que cambia es el instante con que se sube si se sincroniza ya
        // en la otra zona. Es la conducta de MAUI y se deja igual.
        val local = dt("2026-10-02T23:30")
        val mexico = ZoneId.of("America/Mexico_City")
        val tijuana = ZoneId.of("America/Tijuana")
        assertEquals(local, local.toApiInstant(mexico).toLocalDateTime(mexico))
        assertEquals(local, local.toApiInstant(tijuana).toLocalDateTime(tijuana))
        // Un gasto que llega del servidor se ve en la hora de la zona actual: un gasto
        // de las 23:30 en Mexico se ve a las 22:30 en Tijuana (mismo dia).
        assertEquals(dt("2026-10-02T22:30"), local.toApiInstant(mexico).toLocalDateTime(tijuana))
    }
}
