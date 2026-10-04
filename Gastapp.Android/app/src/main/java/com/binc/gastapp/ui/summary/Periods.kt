package com.binc.gastapp.ui.summary

import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.domain.model.User
import com.binc.gastapp.domain.periods.PayPeriod
import com.binc.gastapp.domain.periods.periodBounds
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import com.binc.gastapp.ui.format.MexicoLocale

/**
 * El periodo que muestra Resumen: uno de pago, contado hacia atras desde el actual
 * (PeriodOffset de MAUI), o un rango elegido en Explorar periodo.
 */
sealed interface PeriodSelection {
    data class Paid(val offset: Int) : PeriodSelection

    data class Custom(val start: LocalDate, val end: LocalDate) : PeriodSelection
}

/** Periodo ya resuelto, con lo que necesita el navegador de periodos. */
data class ResolvedPeriod(
    val selection: PeriodSelection,
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val canGoNext: Boolean,
) {
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean = date in start..end

    /** Todos los dias, del mas viejo al mas reciente (la tira de Resumen). */
    fun days(): List<LocalDate> = (0 until dayCount).map { start.plusDays(it.toLong()) }

    /** Dia que se elige al llegar al periodo: hoy si esta dentro; si no, el ultimo que ya paso. */
    fun defaultDay(today: LocalDate): LocalDate = when {
        today in this -> today
        end < today -> end
        else -> start
    }
}

/** Como se llama el periodo de pago del usuario. */
private fun periodNoun(user: User?): String = when (user?.incomeTypeId) {
    IncomeTypes.WEEKLY -> "Semana"
    IncomeTypes.BIWEEKLY -> "Quincena"
    else -> "Mes"
}

/** El periodo de pago numero [offset] hacia atras (0 = el actual, que termina hoy). */
fun paidPeriod(user: User?, today: LocalDate, offset: Int): PayPeriod = periodBounds(
    incomeTypeId = user?.incomeTypeId ?: IncomeTypes.MONTHLY,
    firstPayDay = user?.firstPayDay,
    secondPayDay = user?.secondPayDay,
    referenceDate = today,
    periodOffset = offset,
)

fun resolvePeriod(selection: PeriodSelection, user: User?, today: LocalDate): ResolvedPeriod = when (selection) {
    is PeriodSelection.Paid -> {
        val period = paidPeriod(user, today, selection.offset)
        val noun = periodNoun(user)
        ResolvedPeriod(
            selection = selection,
            start = period.start,
            end = period.end,
            label = when (selection.offset) {
                0 -> "$noun actual"
                1 -> "$noun anterior"
                else -> "Hace ${selection.offset} ${pluralNoun(noun)}"
            },
            canGoNext = selection.offset > 0,
        )
    }
    is PeriodSelection.Custom -> ResolvedPeriod(
        selection = selection,
        start = selection.start,
        end = selection.end,
        label = Shortcut.entries.firstOrNull { it.range(user, today) == (selection.start to selection.end) }?.label
            ?: "Periodo personalizado",
        canGoNext = !selection.end.plusDays(1).isAfter(today),
    )
}

private fun pluralNoun(noun: String): String = when (noun) {
    "Mes" -> "meses"
    else -> noun.lowercase() + "s"
}

/** Periodo anterior: el de pago previo, o un rango del mismo largo justo antes. */
fun ResolvedPeriod.previous(): PeriodSelection = when (val s = selection) {
    is PeriodSelection.Paid -> PeriodSelection.Paid(s.offset + 1)
    is PeriodSelection.Custom -> PeriodSelection.Custom(start.minusDays(dayCount.toLong()), start.minusDays(1))
}

/** Periodo siguiente; null si ya no hay (el actual, o uno que empezaria en el futuro). */
fun ResolvedPeriod.next(): PeriodSelection? {
    if (!canGoNext) return null
    return when (val s = selection) {
        is PeriodSelection.Paid -> PeriodSelection.Paid(s.offset - 1)
        is PeriodSelection.Custom -> PeriodSelection.Custom(end.plusDays(1), end.plusDays(dayCount.toLong()))
    }
}

/**
 * Un rango elegido en Explorar periodo. Si coincide con un periodo de pago se guarda
 * como tal, para que anterior y siguiente sigan los periodos de pago.
 */
fun selectionForRange(start: LocalDate, end: LocalDate, user: User?, today: LocalDate): PeriodSelection {
    for (offset in 0..1) {
        val paid = paidPeriod(user, today, offset)
        if (paid.start == start && paid.end == end) return PeriodSelection.Paid(offset)
    }
    return PeriodSelection.Custom(start, end)
}

/** El periodo de pago que contiene [date] (para saltar al dia de un gasto recien guardado). */
fun paidSelectionContaining(date: LocalDate, user: User?, today: LocalDate): PeriodSelection {
    if (date > today) return PeriodSelection.Paid(0)
    // Un gasto de hace muchos anios es raro; 600 periodos son mas de 11 anios semanales.
    for (offset in 0..600) {
        val period = paidPeriod(user, today, offset)
        if (date >= period.start) return PeriodSelection.Paid(offset)
    }
    return PeriodSelection.Custom(date, date)
}

/** Atajos de Explorar periodo. "Periodo actual" y "anterior" siguen la forma de cobro. */
enum class Shortcut(val label: String) {
    ThisWeek("Esta semana"),
    CurrentPeriod("Periodo actual"),
    PreviousPeriod("Periodo anterior"),
    ThisMonth("Este mes"),
    LastMonth("Mes pasado"),
    Last30Days("Últimos 30 días");

    fun range(user: User?, today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        ThisWeek -> {
            val firstDay = WeekFields.of(MexicoLocale).firstDayOfWeek
            val start = today.with(TemporalAdjusters.previousOrSame(firstDay))
            start to start.plusDays(6)
        }
        CurrentPeriod -> paidPeriod(user, today, 0).let { it.start to it.end }
        PreviousPeriod -> paidPeriod(user, today, 1).let { it.start to it.end }
        ThisMonth -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
        LastMonth -> today.minusMonths(1).let { it.withDayOfMonth(1) to it.with(TemporalAdjusters.lastDayOfMonth()) }
        Last30Days -> today.minusDays(29) to today
    }
}

/**
 * Ultimo dia natural del periodo de pago actual: el dia antes del siguiente pago. El
 * periodo en curso "termina hoy" para los totales; esto es solo para decir "dia 3 de 15".
 */
fun currentPeriodNaturalEnd(user: User?, today: LocalDate): LocalDate {
    val start = paidPeriod(user, today, 0).start
    var day = today
    // Un periodo mensual no pasa de 31 dias.
    repeat(32) {
        val next = day.plusDays(1)
        if (paidPeriod(user, next, 0).start != start) return day
        day = next
    }
    return day
}
