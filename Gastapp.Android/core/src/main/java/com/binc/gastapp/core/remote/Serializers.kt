package com.binc.gastapp.core.remote

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

// Como viajan los tipos que no son de JSON (anexo C del plan). El API es ASP.NET Core con
// System.Text.Json en su configuracion por defecto, y asi escribe cada cosa:
//  - decimal: numero JSON (12.50, 0.1). Nunca se pasa por Double: 0.1 + 0.2 no da 0.3.
//  - DateTime segun su Kind:
//      Utc         -> "2026-10-02T20:15:00Z"          (gastos, DeletedAt; timestamptz en Neon)
//      Local       -> "2026-10-02T20:15:00.123+00:00" (TokenExpiration: DateTime.Now en Render)
//      Unspecified -> "2026-10-02T00:00:00"           (FirstChargeDate, TrialEndDate: columna date)
//    con 0 a 7 decimales en los segundos.
// Al leer se aceptan las tres formas. Al escribir:
//  - Los instantes van en UTC con Z (SpendingToApiUtc de MAUI).
//  - Las fechas de calendario van sin zona, a las 00:00: el API las guarda tal cual
//    (NormalizeCalendarDate). Convertirlas a UTC correria el dia.

/** decimal de .NET <-> BigDecimal, sin perder ni un centavo. */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val text = value.toPlainString()
        if (encoder is JsonEncoder) {
            // Literal sin comillas: sale como numero y no como texto ni como Double.
            encoder.encodeJsonElement(JsonUnquotedLiteral(text))
        } else {
            encoder.encodeString(text)
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val text = if (decoder is JsonDecoder) {
            (decoder.decodeJsonElement() as? JsonPrimitive)?.content
                ?: throw SerializationException("Se esperaba un numero")
        } else {
            decoder.decodeString()
        }
        return try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            throw SerializationException("Monto invalido: $text", e)
        }
    }
}

/**
 * Instante (fecha de un gasto, DeletedAt, LastChargeRegisteredAt, TokenExpiration).
 * Se lee con Z, con desfase o sin zona (sin zona = UTC, como hace el API con los
 * Unspecified). Se escribe en UTC con Z.
 */
object ApiInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ApiInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(formatApiInstant(value))

    override fun deserialize(decoder: Decoder): Instant = parseApiInstant(decoder.decodeString())
}

/**
 * Fecha de calendario (FirstChargeDate, TrialEndDate, BirthDate). Se lee solo el dia,
 * sin convertir de zona aunque el texto traiga Z u hora (BirthDate sale de una columna
 * timestamptz). Se escribe sin zona: "2026-10-02T00:00:00".
 */
object CalendarDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CalendarDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString("${value}T00:00:00")

    override fun deserialize(decoder: Decoder): LocalDate = parseCalendarDate(decoder.decodeString())
}

/**
 * Fecha de calendario que el API escribe en una columna timestamptz SIN normalizarla:
 * BirthDate en CreateUser. Npgsql rechaza un DateTime sin zona en esa columna, asi que
 * va a las 00:00 en UTC ("1995-06-15T00:00:00Z"), igual que lo manda MAUI.
 */
object CalendarDateUtcSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CalendarDateUtc", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString("${value}T00:00:00Z")

    override fun deserialize(decoder: Decoder): LocalDate = parseCalendarDate(decoder.decodeString())
}

private val UtcFormat: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

fun formatApiInstant(value: Instant): String = UtcFormat.format(value)

/** "2026-10-02T20:15:00Z", "...+00:00", "...-06:00" o sin zona (= UTC). */
fun parseApiInstant(text: String): Instant {
    val trimmed = text.trim()
    return try {
        if (hasZone(trimmed)) {
            OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } else {
            LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC)
        }
    } catch (e: DateTimeParseException) {
        throw SerializationException("Fecha invalida: $text", e)
    }
}

/** Los primeros 10 caracteres son el dia; lo demas (hora, zona) no cuenta. */
fun parseCalendarDate(text: String): LocalDate {
    val trimmed = text.trim()
    return try {
        LocalDate.parse(trimmed.take(10))
    } catch (e: DateTimeParseException) {
        throw SerializationException("Fecha invalida: $text", e)
    }
}

/** Despues de la "T": termina en Z o trae un desfase +hh:mm / -hh:mm. */
private fun hasZone(text: String): Boolean {
    val time = text.substringAfter('T', missingDelimiterValue = "")
    return time.endsWith("Z", ignoreCase = true) || time.contains('+') || time.contains('-')
}
