package com.binc.gastapp.demo.ui

import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.LocaleMx
import com.binc.gastapp.demo.datos.Rango
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val formatoPesos: NumberFormat = NumberFormat.getCurrencyInstance(LocaleMx)

fun pesos(valor: Double): String = formatoPesos.format(valor)

private fun String.capitalizar() = replaceFirstChar { it.titlecase(LocaleMx) }

/** "Viernes 2 de octubre" */
fun fechaLarga(fecha: LocalDate): String =
    fecha.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LocaleMx)).capitalizar()

/** "2 oct" */
fun fechaCorta(fecha: LocalDate): String =
    fecha.format(DateTimeFormatter.ofPattern("d MMM", LocaleMx)).replace(".", "")

/** "Vie" */
fun diaSemanaCorto(fecha: LocalDate): String =
    fecha.format(DateTimeFormatter.ofPattern("EEE", LocaleMx)).replace(".", "").capitalizar()

/** "oct" */
fun mesCorto(fecha: LocalDate): String =
    fecha.format(DateTimeFormatter.ofPattern("MMM", LocaleMx)).replace(".", "")

/** "Septiembre 2026" */
fun mesAnio(mes: YearMonth): String =
    mes.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocaleMx)).capitalizar()

fun hora(valor: LocalTime): String =
    valor.format(DateTimeFormatter.ofPattern("h:mm a", LocaleMx))

/** "1 – 15 oct" o "28 sep – 2 oct" */
fun rangoCorto(rango: Rango): String =
    if (YearMonth.from(rango.inicio) == YearMonth.from(rango.fin)) {
        "${rango.inicio.dayOfMonth} – ${fechaCorta(rango.fin)}"
    } else {
        "${fechaCorta(rango.inicio)} – ${fechaCorta(rango.fin)}"
    }

/** "16 al 30 de septiembre" o "3 de septiembre al 2 de octubre" */
fun rangoLargo(rango: Rango): String {
    val mes = DateTimeFormatter.ofPattern("MMMM", LocaleMx)
    return when {
        rango.dias == 1 -> fechaLarga(rango.inicio)
        YearMonth.from(rango.inicio) == YearMonth.from(rango.fin) ->
            "${rango.inicio.dayOfMonth} al ${rango.fin.dayOfMonth} de ${rango.fin.format(mes)}"
        else ->
            "${rango.inicio.dayOfMonth} de ${rango.inicio.format(mes)} al ${rango.fin.dayOfMonth} de ${rango.fin.format(mes)}"
    }
}

/** "Hoy, viernes 2 de octubre" / "Ayer, ..." / "Lunes 28 de septiembre" */
fun etiquetaFecha(fecha: LocalDate): String {
    val larga = fechaLarga(fecha)
    val enMinuscula = larga.replaceFirstChar { it.lowercase(LocaleMx) }
    return when (fecha) {
        Hoy -> "Hoy, $enMinuscula"
        Hoy.minusDays(1) -> "Ayer, $enMinuscula"
        else -> larga
    }
}

fun cuandoRelativo(fecha: LocalDate): String {
    val dias = ChronoUnit.DAYS.between(Hoy, fecha)
    return when {
        dias == 0L -> "Hoy"
        dias == 1L -> "Mañana"
        dias == -1L -> "Ayer"
        dias > 1L -> "En $dias días"
        else -> "Hace ${-dias} días"
    }
}

/** Los selectores de fecha de Material trabajan en milisegundos UTC a medianoche. */
fun LocalDate.millisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun fechaDesdeMillisUtc(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
