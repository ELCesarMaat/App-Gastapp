package com.binc.gastapp.domain.periods

import com.binc.gastapp.domain.model.IncomeTypes
import java.time.LocalDate

// Port de SpendingService.GetPeriodBounds y sus ayudantes (MAUI).
// Paridad verificada contra periodos.json.

/** Periodo de pago: de [start] a [end], ambos incluidos. */
data class PayPeriod(val start: LocalDate, val end: LocalDate) {
    /** Los dias del periodo del mas reciente al mas viejo, como los mostraba MAUI. */
    fun daysDescending(): List<LocalDate> =
        generateSequence(end) { it.minusDays(1) }.takeWhile { it >= start }.toList()

    val dayCount: Int get() = daysDescending().size
}

/**
 * Indice del dia de la semana con la convencion de .NET: 0 = domingo ... 6 = sabado.
 * En modo semanal, el dia de pago se guarda asi en FirstPayDay.
 */
fun dotNetDayOfWeek(date: LocalDate): Int = date.dayOfWeek.value % 7

/**
 * El periodo de pago que contiene [referenceDate] (o el de hace [periodOffset]
 * periodos). El periodo en curso termina HOY, no en su ultimo dia natural.
 *
 * - Semanal (1): [firstPayDay] es el dia de la semana (0 = domingo).
 * - Quincenal (2): [firstPayDay] y [secondPayDay] son dias del mes.
 * - Mensual (3) o desconocido: [firstPayDay] es el dia del mes.
 * Si falta un dia de pago se usa 1, igual que MAUI.
 */
fun periodBounds(
    incomeTypeId: Int,
    firstPayDay: Int?,
    secondPayDay: Int?,
    referenceDate: LocalDate,
    periodOffset: Int = 0,
): PayPeriod {
    val firstDay = firstPayDay ?: 1
    val secondDay = secondPayDay ?: 1

    var start = when (incomeTypeId) {
        IncomeTypes.WEEKLY -> lastWeeklyPayDate(referenceDate, firstDay)
        IncomeTypes.BIWEEKLY -> lastBiweeklyPayDate(referenceDate, firstDay, secondDay)
        else -> lastMonthlyPayDate(referenceDate, firstDay)
    }
    var end = referenceDate

    repeat(maxOf(periodOffset, 0)) {
        end = start.minusDays(1)
        start = when (incomeTypeId) {
            IncomeTypes.WEEKLY -> start.minusDays(7)
            IncomeTypes.BIWEEKLY -> lastBiweeklyPayDate(end, firstDay, secondDay)
            else -> lastMonthlyPayDate(end, firstDay)
        }
    }

    return PayPeriod(start, end)
}

private fun lastWeeklyPayDate(referenceDate: LocalDate, payDay: Int): LocalDate {
    val safePayDay = payDay.coerceIn(0, 6)
    val referenceIndex = dotNetDayOfWeek(referenceDate)
    val startOffset = if (referenceIndex >= safePayDay) {
        referenceIndex - safePayDay
    } else {
        7 - (safePayDay - referenceIndex)
    }
    return referenceDate.minusDays(startOffset.toLong())
}

private fun lastMonthlyPayDate(referenceDate: LocalDate, payDay: Int): LocalDate {
    val safeCurrentDay = payDay.coerceIn(1, referenceDate.lengthOfMonth())
    if (referenceDate.dayOfMonth >= safeCurrentDay) return referenceDate.withDayOfMonth(safeCurrentDay)

    val previousMonth = referenceDate.minusMonths(1)
    return previousMonth.withDayOfMonth(payDay.coerceIn(1, previousMonth.lengthOfMonth()))
}

private fun lastBiweeklyPayDate(referenceDate: LocalDate, firstPayDay: Int, secondPayDay: Int): LocalDate {
    val daysThisMonth = referenceDate.lengthOfMonth()
    var firstDate = referenceDate.withDayOfMonth(firstPayDay.coerceIn(1, daysThisMonth))
    var secondDate = referenceDate.withDayOfMonth(secondPayDay.coerceIn(1, daysThisMonth))
    if (firstDate > secondDate) {
        val temp = firstDate
        firstDate = secondDate
        secondDate = temp
    }

    if (referenceDate >= secondDate) return secondDate
    if (referenceDate >= firstDate) return firstDate

    val previousMonth = referenceDate.minusMonths(1)
    val daysPreviousMonth = previousMonth.lengthOfMonth()
    val prevFirst = previousMonth.withDayOfMonth(firstPayDay.coerceIn(1, daysPreviousMonth))
    val prevSecond = previousMonth.withDayOfMonth(secondPayDay.coerceIn(1, daysPreviousMonth))
    return if (prevFirst > prevSecond) prevFirst else prevSecond
}
