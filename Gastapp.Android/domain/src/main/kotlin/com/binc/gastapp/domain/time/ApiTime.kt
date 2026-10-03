package com.binc.gastapp.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Las tres clases de fecha de la app (ver anexo C del plan de migracion):
//  - LocalDate: fechas de calendario (primer cobro, fin de prueba, nacimiento). Viajan
//    tal cual; convertirlas de zona correria el dia.
//  - LocalDateTime: la fecha de un gasto, en hora local del telefono.
//  - Instant: instantes (DeletedAt, LastChargeRegisteredAt al viajar).

/** DateTimeUtils.SpendingToApiUtc: hora local del telefono -> instante UTC para el API. */
fun LocalDateTime.toApiInstant(zone: ZoneId = ZoneId.systemDefault()): Instant = atZone(zone).toInstant()

/** DateTimeUtils.SpendingFromApiToLocal: instante UTC del API -> hora local del telefono. */
fun Instant.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(this, zone)

/** (a - b).Days de .NET entre dos fechas de calendario. */
fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
