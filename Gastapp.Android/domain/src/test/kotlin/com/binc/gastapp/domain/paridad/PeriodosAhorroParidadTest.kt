package com.binc.gastapp.domain.paridad

import com.binc.gastapp.domain.periods.periodBounds
import com.binc.gastapp.domain.savings.budgetStatus
import com.binc.gastapp.domain.savings.categoryPercentages
import com.binc.gastapp.domain.savings.savingsAmountFromPercent
import com.binc.gastapp.domain.savings.savingsPercentFromAmount
import com.binc.gastapp.domain.spendings.msiMonthlyInstallment
import org.junit.Test

class PeriodosAhorroParidadTest {

    @Test
    fun `periodos de pago iguales a MAUI`() {
        val comparison = Comparison("periodos")
        for (row in Fixtures.load("periodos.json")["casos"].list) {
            comparison.case {
                val type = row[0].number
                val first = row[1].intOrNull
                val second = row[2].intOrNull
                val today = row[3].date
                val offset = row[4].number
                val period = periodBounds(type, first, second, today, offset)
                val c = "tipo $type ($first, $second), hoy $today, hace $offset"
                comparison.equal("$c inicio", row[5].date, period.start)
                comparison.equal("$c fin", row[6].date, period.end)
            }
        }
        comparison.verify()
    }

    @Test
    fun `presupuesto y salud del periodo iguales a MAUI`() {
        val fixture = Fixtures.load("ahorro.json")
        val comparison = Comparison("ahorro.salud")
        for (row in fixture["salud"].list) {
            comparison.case {
                val salary = row[0].decimal
                val percentSave = row[1].decimal
                val total = row[2].decimal
                val days = row[3].number
                val status = budgetStatus(salary, percentSave, total, days)
                val c = "sueldo $salary, ahorro $percentSave %, gastado $total, $days dias"
                comparison.equalMoney("$c maximo", row[4].decimal, status.maxTotalSpending)
                comparison.equalMoney("$c porcentaje", row[5].decimal, status.percent)
                comparison.equalMoney("$c progreso", row[6].decimal, status.progressPercent)
                comparison.equal("$c salud", row[7].text, status.health.text)
                comparison.equal("$c color", row[8].text, status.health.colorHex)
                comparison.equalMoney("$c restante", row[9].decimal, status.remainingBudget)
                comparison.equalMoney("$c promedio diario", row[10].decimal, status.dailyAverage)
                comparison.equalMoney("$c ahorrado o excedido", row[11].decimal, status.savedOrExceeded)
                comparison.equal("$c se excedio", row[12].bool, status.exceededSalary)
            }
        }
        comparison.verify()

        val categories = Comparison("ahorro.categorias")
        for (row in fixture["categorias"].list) {
            categories.case {
                val amounts = row[0].list.map { it.decimal }
                val expected = row[1].list.map { it.decimal }
                val actual = categoryPercentages(amounts)
                expected.zip(actual).forEachIndexed { i, (e, a) ->
                    categories.equalMoney("montos $amounts, categoria $i", e, a)
                }
            }
        }
        categories.verify()
    }

    @Test
    fun `mensualidad MSI y meta de ahorro iguales a MAUI`() {
        val fixture = Fixtures.load("msi_perfil.json")

        val msi = Comparison("msi")
        for (row in fixture["msi"].list) {
            msi.case {
                msi.equalMoney(
                    "monto ${row[0].text} a ${row[1].number} meses",
                    row[2].decimal,
                    msiMonthlyInstallment(row[0].decimal, row[1].number),
                )
            }
        }
        msi.verify()

        val percent = Comparison("perfil.porcentaje")
        for (row in fixture["porcentaje"].list) {
            percent.case {
                percent.equalMoney(
                    "monto ${row[0].text} de sueldo ${row[1].text}",
                    row[2].decimal,
                    savingsPercentFromAmount(row[0].decimal, row[1].decimal),
                )
            }
        }
        percent.verify()

        val amount = Comparison("perfil.monto")
        for (row in fixture["monto"].list) {
            amount.case {
                amount.equalMoney(
                    "sueldo ${row[0].text} al ${row[1].text} %",
                    row[2].decimal,
                    savingsAmountFromPercent(row[0].decimal, row[1].decimal),
                )
            }
        }
        amount.verify()
    }
}
