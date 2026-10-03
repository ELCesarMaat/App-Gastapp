package com.binc.gastapp.domain.paridad

import com.binc.gastapp.domain.cards.PendingMsiPurchase
import com.binc.gastapp.domain.cards.balanceAdjustment
import com.binc.gastapp.domain.cards.buildCardSummary
import com.binc.gastapp.domain.cards.calculateCycleDates
import com.binc.gastapp.domain.cards.inUseCardMovements
import com.binc.gastapp.domain.cards.lastCutOffDate
import com.binc.gastapp.domain.cards.savesPendingLevel
import com.binc.gastapp.domain.cards.suggestedPayment
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.Spending
import org.junit.Test
import java.time.LocalDateTime

class TarjetasParidadTest {

    @Test
    fun `fechas de corte y pago iguales a MAUI`() {
        val comparison = Comparison("ciclos_tarjeta")
        for (row in Fixtures.load("ciclos_tarjeta.json")["casos"].list) {
            comparison.case {
                val cut = row[0].number
                val pay = row[1].number
                val today = row[2].date
                val context = "corte $cut, pago $pay, hoy $today"
                val dates = calculateCycleDates(cut, pay, today)
                comparison.equal("$context, proximo corte", row[3].date, dates.cutOffDate)
                comparison.equal("$context, proximo pago", row[4].date, dates.paymentDueDate)
                comparison.equal("$context, ultimo corte", row[5].date, lastCutOffDate(cut, today))
            }
        }
        comparison.verify()
    }

    @Test
    fun `resumen de tarjeta igual a MAUI`() {
        val comparison = Comparison("escenarios_tarjeta")
        Fixtures.load("escenarios_tarjeta.json")["escenarios"].list.forEachIndexed { n, scenario ->
            comparison.case {
                val today = scenario["hoy"].date
                val card = CreditCard(
                    creditCardId = "T",
                    cutOffDay = scenario["corte"].number,
                    paymentDay = scenario["pago"].number,
                    creditLimit = scenario["limite"].decimal,
                )
                val spendings = scenario["movs"].list.mapIndexed { i, m ->
                    Spending(
                        spendingId = "m$i",
                        creditCardId = if (m[0].flag) "T" else "OTRA",
                        isCreditCard = m[1].flag,
                        isDeleted = m[2].flag,
                        amount = m[3].decimal,
                        date = m[4].dateTime,
                        isMsi = m[5].flag,
                        totalInstallments = m[6].number,
                        currentInstallment = m[7].number,
                        installmentMonthlyAmount = m[8].decimal,
                    )
                }
                val expected = scenario["esperado"]
                val summary = buildCardSummary(card, spendings, today)
                val c = "escenario $n (hoy $today, corte ${card.cutOffDay}, pago ${card.paymentDay})"

                comparison.equal("$c corte", expected["corte"].date, summary.nextCutOffDate)
                comparison.equal("$c fecha limite", expected["pago"].date, summary.nextPaymentDueDate)
                comparison.equalMoney("$c deuda", expected["deuda"].decimal, summary.totalDebt)
                comparison.equalMoney("$c disponible", expected["disponible"].decimal, summary.availableCredit)
                comparison.equalDouble("$c uso", expected["uso"].double, summary.usagePercentage)
                comparison.equalMoney("$c ciclo actual", expected["cicloActual"].decimal, summary.currentCycleAmount)
                comparison.equalDivision("$c MSI futuro", expected["msiFuturo"].decimal, summary.totalMsiRemainingDebt)
                comparison.equal("$c MSI activas", expected["msiActivas"].number, summary.activeMsiCount)
                comparison.equal("$c dias al corte", expected["diasCorte"].number, summary.daysUntilCutOff)
                comparison.equal("$c dias al pago", expected["diasPago"].number, summary.daysUntilPayment)
                comparison.equal("$c color pago", expected["colorPago"].text, summary.paymentLevel.colorHex)
                comparison.equal("$c color uso", expected["colorUso"].text, summary.usageLevel.colorHex)
                comparison.equal(
                    "$c color en ahorros",
                    expected["colorAhorros"].text,
                    savesPendingLevel(summary.daysUntilPayment).colorHex,
                )
                comparison.equalMoney("$c pago sugerido", expected["pagoSugerido"].decimal, suggestedPayment(summary))

                val adjustment = balanceAdjustment(card, summary.totalDebt, scenario["nuevoSaldo"].decimal, LocalDateTime.MIN)
                val expectedAdjustment = expected["ajuste"]
                if (expectedAdjustment.isNull) {
                    comparison.equal("$c ajuste", null, adjustment)
                } else {
                    comparison.equal("$c ajuste es compra", expectedAdjustment[0].flag, adjustment?.isCreditCard)
                    comparison.equalMoney("$c ajuste monto", expectedAdjustment[1].decimal, adjustment?.amount ?: java.math.BigDecimal(-1))
                    comparison.equal("$c ajuste forma de pago", expectedAdjustment[2].text, adjustment?.paymentMethod)
                }
            }
        }
        comparison.verify()
    }

    @Test
    fun `alta de tarjeta en uso igual a MAUI`() {
        val comparison = Comparison("tarjeta_en_uso")
        Fixtures.load("tarjeta_en_uso.json")["casos"].list.forEachIndexed { n, case ->
            comparison.case {
                val card = CreditCard(creditCardId = "T", cutOffDay = case["corte"].number, paymentDay = 1, cardName = "Mi tarjeta")
                val purchases = case["msi"].list.map {
                    PendingMsiPurchase(
                        title = it[0].text,
                        monthlyAmount = it[1].decimal,
                        paidInstallments = it[2].number,
                        totalInstallments = it[3].number,
                    )
                }
                val movements = inUseCardMovements(
                    card = card,
                    totalUsed = case["totalUsado"].decimal,
                    currentCycleDebt = case["cicloActual"].decimal,
                    balanceAlreadyCut = case["saldoYaCortado"].bool,
                    msiPurchases = purchases,
                    now = case["ahora"].dateTime,
                )
                val expected = case["esperado"].list
                val c = "caso $n"
                comparison.equal("$c cuantos movimientos", expected.size, movements.size)
                expected.zip(movements).forEachIndexed { i, (e, m) ->
                    comparison.equal("$c mov $i titulo", e[0].text, m.title)
                    comparison.equalMoney("$c mov $i monto", e[1].decimal, m.amount)
                    comparison.equal("$c mov $i fecha", e[2].dateTime, m.date)
                    comparison.equal("$c mov $i es MSI", e[3].flag, m.isMsi)
                    comparison.equalMoney("$c mov $i mensualidad", e[4].decimal, m.installmentMonthlyAmount)
                    comparison.equal("$c mov $i mensualidad actual", e[5].number, m.currentInstallment)
                    comparison.equal("$c mov $i plazo", e[6].number, m.totalInstallments)
                }
            }
        }
        comparison.verify()
    }
}
