package com.binc.gastapp.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binc.gastapp.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// Formatos de fecha de la UI (Formato.kt del demo), en el idioma de los textos (AppLocale).
// El espanol conserva los de siempre ("Viernes 2 de octubre"); el portugues el mismo orden con "de"; cualquier otro idioma usa
// el orden del ingles ("Friday, October 2").

private class DatePatterns(
    val longDate: String,
    val dayMonthYearShort: String,
    val dayMonthLong: String,
    val longDateYear: String,
    val dayMonthYear: String,
    val shortDate: String,
    val shortDateYear: String,
    val dayMonth: String,
)

private val SpanishPatterns = DatePatterns(
    longDate = "EEEE d 'de' MMMM",
    dayMonthYearShort = "dd/MMM/yyyy",
    dayMonthLong = "dd 'de' MMMM",
    longDateYear = "EEEE d 'de' MMMM 'de' yyyy",
    dayMonthYear = "d 'de' MMMM 'de' yyyy",
    shortDate = "d MMM",
    shortDateYear = "d MMM yyyy",
    dayMonth = "dd/MMM",
)

private val PortuguesePatterns = DatePatterns(
    longDate = "EEEE, d 'de' MMMM",
    dayMonthYearShort = "dd/MMM/yyyy",
    dayMonthLong = "dd 'de' MMMM",
    longDateYear = "EEEE, d 'de' MMMM 'de' yyyy",
    dayMonthYear = "d 'de' MMMM 'de' yyyy",
    shortDate = "d MMM",
    shortDateYear = "d MMM yyyy",
    dayMonth = "dd/MMM",
)

private val EnglishPatterns = DatePatterns(
    longDate = "EEEE, MMMM d",
    dayMonthYearShort = "MMM d, yyyy",
    dayMonthLong = "MMMM d",
    longDateYear = "EEEE, MMMM d, yyyy",
    dayMonthYear = "MMMM d, yyyy",
    shortDate = "MMM d",
    shortDateYear = "MMM d, yyyy",
    dayMonth = "MMM d",
)

private val isSpanish: Boolean get() = AppLocale.language == "es"

private val isPortuguese: Boolean get() = AppLocale.language == "pt"

/** Espanol y portugues comparten el orden dia-mes ("2 de outubro"); el ingles va al reves. */
private val isDayFirst: Boolean get() = isSpanish || isPortuguese

private val patterns: DatePatterns
    get() = when {
        isSpanish -> SpanishPatterns
        isPortuguese -> PortuguesePatterns
        else -> EnglishPatterns
    }

private val formatters = ConcurrentHashMap<Pair<Locale, String>, DateTimeFormatter>()

/** DateTimeFormatter si es seguro entre hilos: uno por idioma y patron. */
private fun format(pattern: String): DateTimeFormatter =
    AppLocale.locale.let { locale -> formatters.getOrPut(locale to pattern) { DateTimeFormatter.ofPattern(pattern, locale) } }

private fun String.capitalized() = replaceFirstChar { it.titlecase(AppLocale.locale) }

/** Quita el punto de las abreviaturas ("oct." -> "oct", "Sept." -> "Sept"). */
private fun String.withoutDot() = replace(".", "")

/** "Viernes 2 de octubre" / "Friday, October 2" */
fun longDate(date: LocalDate): String = date.format(format(patterns.longDate)).capitalized()

/** "Viernes 2 de octubre de 2026" / "Friday, October 2, 2026" */
fun longDateWithYear(date: LocalDate): String = date.format(format(patterns.longDateYear)).capitalized()

/** "31 de enero de 1990" / "January 31, 1990" (sin dia de la semana: fechas de nacimiento). */
fun dayMonthYear(date: LocalDate): String = date.format(format(patterns.dayMonthYear))

/** "15/oct/2026" / "Oct 15, 2026" (las vistas previas de cobros). */
fun dayMonthYearShort(date: LocalDate): String = date.format(format(patterns.dayMonthYearShort)).withoutDot()

/** "05 de octubre" / "October 5" (los avisos de tarjeta, como en MAUI). */
fun dayMonthLong(date: LocalDate): String = date.format(format(patterns.dayMonthLong))

/** "2 oct" / "Oct 2" */
fun shortDate(date: LocalDate): String = date.format(format(patterns.shortDate)).withoutDot()

/** "2 oct 2025" / "Oct 2, 2025": con anio solo si no es el actual. */
fun shortDate(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) shortDate(date) else date.format(format(patterns.shortDateYear)).withoutDot()

/** "Vie" / "Fri" */
fun weekdayShort(date: LocalDate): String = date.format(format("EEE")).withoutDot().capitalized()

/** "oct" / "Oct" */
fun monthShort(date: LocalDate): String = date.format(format("MMM")).withoutDot()

/** "Septiembre 2026" / "September 2026" */
fun monthYear(month: YearMonth): String = month.format(format("MMMM yyyy")).capitalized()

private fun monthName(date: LocalDate): String = date.format(format("MMMM"))

/** "7:05 p.m." / "7:05 PM" */
fun timeText(time: LocalTime): String = time.format(format("h:mm a"))

/** "02/10/2026" en Mexico, "10/02/2026" en Estados Unidos: el orden de la region. */
fun numericDate(date: LocalDate): String = date.format(format(numericPattern(AppLocale.locale)))

private fun numericPattern(locale: Locale): String =
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        .replace(Regex("y+"), "yyyy")
        .replace(Regex("M+"), "MM")
        .replace(Regex("d+"), "dd")

/** "05/oct" (el formato de las fechas de las tarjetas en MAUI) / "Oct 5". */
fun dayMonth(date: LocalDate): String = date.format(format(patterns.dayMonth)).withoutDot()

/** "1 – 15 oct" o "28 sep – 2 oct" / "Oct 1 – 15" o "Sep 28 – Oct 2" */
fun shortRange(start: LocalDate, end: LocalDate): String = when {
    start == end -> shortDate(start)
    YearMonth.from(start) == YearMonth.from(end) ->
        if (isDayFirst) "${start.dayOfMonth} – ${shortDate(end)}" else "${shortDate(start)} – ${end.dayOfMonth}"
    else -> "${shortDate(start)} – ${shortDate(end)}"
}

/**
 * "16 al 30 de septiembre" o "3 de septiembre al 2 de octubre" /
 * "September 16 – 30" o "September 3 – October 2".
 */
fun longRange(start: LocalDate, end: LocalDate): String = when {
    start == end -> longDate(start)
    isDayFirst -> {
        val to = if (isPortuguese) "a" else "al"
        when {
            YearMonth.from(start) == YearMonth.from(end) ->
                "${start.dayOfMonth} $to ${end.dayOfMonth} de ${monthName(end)}"
            start.year == end.year ->
                "${start.dayOfMonth} de ${monthName(start)} $to ${end.dayOfMonth} de ${monthName(end)}"
            else ->
                "${start.dayOfMonth} de ${monthName(start)} de ${start.year} $to ${end.dayOfMonth} de ${monthName(end)} de ${end.year}"
        }
    }
    YearMonth.from(start) == YearMonth.from(end) -> "${monthName(start)} ${start.dayOfMonth} – ${end.dayOfMonth}"
    start.year == end.year -> "${monthName(start)} ${start.dayOfMonth} – ${monthName(end)} ${end.dayOfMonth}"
    else -> "${monthName(start)} ${start.dayOfMonth}, ${start.year} – ${monthName(end)} ${end.dayOfMonth}, ${end.year}"
}

/**
 * longDate para ir dentro de una frase ("Desde el viernes 2 de octubre"): en espanol el
 * dia de la semana va en minuscula; en ingles siempre con mayuscula.
 */
fun longDateInSentence(date: LocalDate): String =
    longDate(date).let { if (isDayFirst) it.replaceFirstChar { c -> c.lowercase(AppLocale.locale) } else it }

/** "Hoy, viernes 2 de octubre" / "Ayer, ..." / "Lunes 28 de septiembre" */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String {
    val long = longDate(date)
    val inner = longDateInSentence(date)
    return when (date) {
        today -> stringResource(R.string.day_label_today, inner)
        today.minusDays(1) -> stringResource(R.string.day_label_yesterday, inner)
        else -> long
    }
}

/** "Hoy", "Mañana", "En 3 días", "Hace 2 días" */
@Composable
fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return when {
        days == 0 -> stringResource(R.string.today)
        days == 1 -> stringResource(R.string.tomorrow)
        days == -1 -> stringResource(R.string.yesterday)
        days > 1 -> pluralStringResource(R.plurals.in_days, days, days)
        else -> pluralStringResource(R.plurals.days_ago, -days, -days)
    }
}

/** "1 movimiento", "3 movimientos", "sin movimientos" */
@Composable
fun movementsText(count: Int): String =
    if (count == 0) stringResource(R.string.no_movements) else pluralStringResource(R.plurals.movements, count, count)
