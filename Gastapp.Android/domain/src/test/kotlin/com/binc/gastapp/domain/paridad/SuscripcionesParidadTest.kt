package com.binc.gastapp.domain.paridad

import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.subscriptions.buildSubscriptionSummary
import com.binc.gastapp.domain.subscriptions.nextChargeDate
import com.binc.gastapp.domain.subscriptions.previousChargeDate
import com.binc.gastapp.domain.subscriptions.upcomingCharges
import org.junit.Test
import java.math.BigDecimal

class SuscripcionesParidadTest {

    @Test
    fun `proximo y anterior cobro iguales a MAUI`() {
        val fixture = Fixtures.load("cobros_suscripcion.json")
        val anchors = fixture["anclas"].list.map { it.date }
        val cycles = fixture["ciclos"].list.map { it.text }
        val comparison = Comparison("cobros_suscripcion")
        for (row in fixture["casos"].list) {
            comparison.case {
                val anchor = anchors[row[0].number]
                val cycle = cycles[row[1].number]
                val today = row[2].date
                val c = "ancla $anchor, $cycle, hoy $today"
                comparison.equal("$c proximo", row[3].date, nextChargeDate(anchor, cycle, today))
                comparison.equal("$c anterior", row[4].date, previousChargeDate(anchor, cycle, today))
            }
        }
        comparison.verify()
    }

    @Test
    fun `resumen de suscripcion igual a MAUI`() {
        val comparison = Comparison("escenarios_suscripcion")
        Fixtures.load("escenarios_suscripcion.json")["escenarios"].list.forEachIndexed { n, s ->
            comparison.case {
                val today = s["hoy"].date
                val subscription = Subscription(
                    subscriptionId = "S",
                    serviceName = "Servicio",
                    amount = s["monto"].decimal,
                    billingCycle = s["ciclo"].text,
                    firstChargeDate = s["primerCobro"].date,
                    isActive = s["activa"].bool,
                    isTrial = s["prueba"].bool,
                    trialEndDate = s["finPrueba"].dateOrNull,
                    lastChargeRegisteredAt = s["ultimoCobro"].dateTimeOrNull,
                )
                val summary = buildSubscriptionSummary(subscription, s["totalMensual"].decimal, emptyMap(), emptyMap(), today)
                val e = s["esperado"]
                val c = "escenario $n (hoy $today, ${subscription.billingCycle}, primer cobro ${subscription.firstChargeDate})"

                comparison.equalDivision("$c mensual", e["mensual"].decimal, summary.monthlyEquivalent)
                comparison.equalDivision("$c anual", e["anual"].decimal, summary.yearlyEquivalent)
                comparison.equal("$c proximo cobro", e["proximo"].date, summary.nextChargeDate)
                comparison.equal("$c dias", e["dias"].number, summary.daysUntilCharge)
                // MAUI agrega la fecha al texto; aqui la pone la UI (ver ChargeStatus.showsDate).
                val chargeText = summary.chargeStatus.text + if (summary.chargeStatus.showsDate) " (fecha)" else ""
                comparison.equal("$c texto del cobro", e["textoCobro"].text, chargeText)
                comparison.equal("$c color del cobro", e["colorCobro"].text, summary.chargeStatus.level.colorHex)
                comparison.equal("$c prueba activa", e["pruebaActiva"].bool, summary.isTrialActive)
                comparison.equal("$c dias de prueba", e["diasPrueba"].number, summary.daysUntilTrialEnds)
                comparison.equal("$c cuenta en totales", e["cuenta"].bool, summary.countsTowardTotals)
                comparison.equalDouble("$c parte del total", e["parte"].double, summary.shareOfMonthlyRatio)
                comparison.equal("$c texto de la parte", e["textoParte"].text, summary.shareOfMonthlyText)
                comparison.equal("$c cobro del periodo registrado", e["cobrado"].bool, summary.isCurrentCycleCharged)
                comparison.equal("$c insignia", e["insignia"].text, summary.badge.text)
                comparison.equal("$c color insignia", e["colorInsignia"].text, summary.badge.level.colorHex)
                comparison.equal(
                    "$c siguientes cobros",
                    e["siguientes"].list.map { it.date },
                    summary.upcomingCharges.map { it.date },
                )
            }
        }
        comparison.verify()
    }

    @Test
    fun `proximos cobros de 45 dias iguales a MAUI`() {
        val comparison = Comparison("proximos_cobros")
        Fixtures.load("proximos_cobros.json")["escenarios"].list.forEachIndexed { n, s ->
            comparison.case {
                val today = s["hoy"].date
                val subscriptions = s["subs"].list.mapIndexed { i, sub ->
                    Subscription(
                        subscriptionId = i.toString(),
                        serviceName = sub[0].text,
                        billingCycle = sub[1].text,
                        firstChargeDate = sub[2].date,
                        isActive = sub[3].bool,
                        isTrial = sub[4].bool,
                        trialEndDate = sub[5].dateOrNull,
                        isDeleted = sub[6].bool,
                        amount = BigDecimal.ONE,
                    )
                }
                val charges = upcomingCharges(subscriptions, today)
                val expected = s["esperado"].list
                val c = "escenario $n (hoy $today)"
                comparison.equal("$c cuantos cobros", expected.size, charges.size)
                expected.zip(charges).forEachIndexed { i, (e, charge) ->
                    comparison.equal("$c cobro $i suscripcion", e[0].number.toString(), charge.subscriptionId)
                    comparison.equal("$c cobro $i fecha", e[1].date, charge.date)
                    comparison.equal("$c cobro $i dias", e[2].number, charge.daysUntil)
                    comparison.equal("$c cobro $i texto", e[3].text, charge.whenText)
                    comparison.equal("$c cobro $i color", e[4].text, charge.level.colorHex)
                }
            }
        }
        comparison.verify()
    }
}
