package com.binc.gastapp.ui.format

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Formatos de fecha de la UI (Formato.kt del demo). Siempre en es-MX, sin importar el
// idioma del telefono, igual que los montos.

private val LongDateFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", MexicoLocale)
private val LongDateYearFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", MexicoLocale)
private val DayMonthYearFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", MexicoLocale)
private val ShortDateFormat = DateTimeFormatter.ofPattern("d MMM", MexicoLocale)
private val ShortDateYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", MexicoLocale)
private val WeekdayShortFormat = DateTimeFormatter.ofPattern("EEE", MexicoLocale)
private val MonthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy", MexicoLocale)
private val MonthFormat = DateTimeFormatter.ofPattern("MMMM", MexicoLocale)
private val TimeFormat = DateTimeFormatter.ofPattern("h:mm a", MexicoLocale)
private val NumericDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", MexicoLocale)
private val DayMonthFormat = DateTimeFormatter.ofPattern("dd/MMM", MexicoLocale)

private fun String.capitalized() = replaceFirstChar { it.titlecase(MexicoLocale) }

/** Quita el punto de las abreviaturas ("oct." -> "oct"). */
private fun String.withoutDot() = replace(".", "")

/** "Viernes 2 de octubre" */
fun longDate(date: LocalDate): String = date.format(LongDateFormat).capitalized()

/** "Viernes 2 de octubre de 2026" */
fun longDateWithYear(date: LocalDate): String = date.format(LongDateYearFormat).capitalized()

/** "31 de enero de 1990" (sin dia de la semana: fechas de nacimiento). */
fun dayMonthYear(date: LocalDate): String = date.format(DayMonthYearFormat)

/** "2 oct" */
fun shortDate(date: LocalDate): String = date.format(ShortDateFormat).withoutDot()

/** "2 oct 2025": con anio solo si no es el actual. */
fun shortDate(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) shortDate(date) else date.format(ShortDateYearFormat).withoutDot()

/** "Vie" */
fun weekdayShort(date: LocalDate): String = date.format(WeekdayShortFormat).withoutDot().capitalized()

private val MonthShortFormat = DateTimeFormatter.ofPattern("MMM", MexicoLocale)

/** "oct" */
fun monthShort(date: LocalDate): String = date.format(MonthShortFormat).withoutDot()

/** "Septiembre 2026" */
fun monthYear(month: YearMonth): String = month.format(MonthYearFormat).capitalized()

/** "7:05 p.m." */
fun timeText(time: LocalTime): String = time.format(TimeFormat)

/** "02/10/2026" */
fun numericDate(date: LocalDate): String = date.format(NumericDateFormat)

/** "05/oct", el formato de las fechas de las tarjetas en MAUI. */
fun dayMonth(date: LocalDate): String = date.format(DayMonthFormat).withoutDot()

/** "1 – 15 oct" o "28 sep – 2 oct" */
fun shortRange(start: LocalDate, end: LocalDate): String = when {
    start == end -> shortDate(start)
    YearMonth.from(start) == YearMonth.from(end) -> "${start.dayOfMonth} – ${shortDate(end)}"
    else -> "${shortDate(start)} – ${shortDate(end)}"
}

/** "16 al 30 de septiembre" o "3 de septiembre al 2 de octubre" */
fun longRange(start: LocalDate, end: LocalDate): String = when {
    start == end -> longDate(start)
    YearMonth.from(start) == YearMonth.from(end) ->
        "${start.dayOfMonth} al ${end.dayOfMonth} de ${end.format(MonthFormat)}"
    start.year == end.year ->
        "${start.dayOfMonth} de ${start.format(MonthFormat)} al ${end.dayOfMonth} de ${end.format(MonthFormat)}"
    else ->
        "${start.dayOfMonth} de ${start.format(MonthFormat)} de ${start.year} al ${end.dayOfMonth} de ${end.format(MonthFormat)} de ${end.year}"
}

/** "Hoy, viernes 2 de octubre" / "Ayer, ..." / "Lunes 28 de septiembre" */
fun dayLabel(date: LocalDate, today: LocalDate): String {
    val long = longDate(date)
    val lower = long.replaceFirstChar { it.lowercase(MexicoLocale) }
    return when (date) {
        today -> "Hoy, $lower"
        today.minusDays(1) -> "Ayer, $lower"
        else -> long
    }
}

/** "Hoy", "Mañana", "En 3 días", "Hace 2 días" */
fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Hoy"
        days == 1L -> "Mañana"
        days == -1L -> "Ayer"
        days > 1L -> "En $days días"
        else -> "Hace ${-days} días"
    }
}

/** "1 movimiento", "3 movimientos", "sin movimientos" */
fun movementsText(count: Int, zero: String = "sin movimientos"): String = when (count) {
    0 -> zero
    1 -> "1 movimiento"
    else -> "$count movimientos"
}
