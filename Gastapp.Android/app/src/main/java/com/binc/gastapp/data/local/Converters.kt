package com.binc.gastapp.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Como se guardan en SQLite los tipos que Room no conoce (ver anexo C del plan):
//  - LocalDate (fechas de calendario): texto ISO "2026-10-02". Sin zona horaria.
//  - LocalDateTime (fecha de un gasto, hora local): texto ISO de ANCHO FIJO
//    "2026-10-02T14:15:00.000000000". El ancho fijo importa: las consultas por rango
//    comparan texto, y "14:15" contra "14:15:30" solo ordena bien si todos tienen la
//    misma forma. Ademas los primeros 10 caracteres son el dia (substr(date, 1, 10)).
//  - Instant (DeletedAt, LastChargeRegisteredAt): milisegundos desde 1970 en UTC.
//  - BigDecimal (solo porcentajes; el dinero va en centavos Long): texto, sin perder
//    ningun decimal.
class Converters {

    @TypeConverter
    fun localDateToText(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun textToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localDateTimeToText(value: LocalDateTime?): String? = value?.format(LocalDateTimeFormat)

    @TypeConverter
    fun textToLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, LocalDateTimeFormat) }

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun bigDecimalToText(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun textToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    companion object {
        /** Siempre 29 caracteres: segundos y nanosegundos aunque valgan cero. */
        val LocalDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS")
    }
}
