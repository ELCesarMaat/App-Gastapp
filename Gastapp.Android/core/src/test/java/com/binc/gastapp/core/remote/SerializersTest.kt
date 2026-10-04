package com.binc.gastapp.core.remote

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Los tres tipos que no son de JSON (dinero, instantes y fechas de calendario). */
class SerializersTest {

    @Serializable
    private data class Money(@Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal)

    @Serializable
    private data class When(@Serializable(with = ApiInstantSerializer::class) val at: Instant)

    @Serializable
    private data class Day(@Serializable(with = CalendarDateSerializer::class) val day: LocalDate)

    @Test
    fun `el dinero se lee exacto, sin pasar por Double`() {
        // 0.1 + 0.2 en Double da 0.30000000000000004.
        val a = ApiJson.decodeFromString<Money>("""{"amount":0.1}""").amount
        val b = ApiJson.decodeFromString<Money>("""{"amount":0.2}""").amount
        assertEquals(BigDecimal("0.3"), a + b)

        assertEquals(BigDecimal("12500.00"), ApiJson.decodeFromString<Money>("""{"amount":12500.00}""").amount)
        assertEquals(BigDecimal("45"), ApiJson.decodeFromString<Money>("""{"amount":45}""").amount)
        assertEquals(BigDecimal("99999999999999.99"), ApiJson.decodeFromString<Money>("""{"amount":99999999999999.99}""").amount)
        // ASP.NET acepta y a veces manda numeros como texto (JsonSerializerDefaults.Web).
        assertEquals(BigDecimal("7.50"), ApiJson.decodeFromString<Money>("""{"amount":"7.50"}""").amount)
    }

    @Test
    fun `el dinero se escribe como numero JSON con su escala`() {
        val json = ApiJson.encodeToString(Money.serializer(), Money(BigDecimal("0.10")))
        assertEquals("""{"amount":0.10}""", json)
        val primitive = ApiJson.parseToJsonElement(json).jsonObject["amount"]!!.jsonPrimitive
        assertFalse("Debe ir sin comillas", primitive.isString)
    }

    @Test
    fun `los instantes se leen con Z, con desfase o sin zona`() {
        val expected = Instant.parse("2026-10-02T20:15:33.123456Z")
        assertEquals(expected, decodeInstant("2026-10-02T20:15:33.123456Z"))
        assertEquals(expected, decodeInstant("2026-10-02T14:15:33.123456-06:00"))
        assertEquals(expected, decodeInstant("2026-10-02T20:15:33.123456+00:00"))
        // Sin zona = UTC, como hace el API con un DateTime Unspecified.
        assertEquals(expected, decodeInstant("2026-10-02T20:15:33.123456"))
        // Siete decimales, la precision de .NET.
        assertEquals(Instant.parse("2026-10-02T20:15:33.1234567Z"), decodeInstant("2026-10-02T20:15:33.1234567Z"))
        assertEquals(Instant.parse("2026-10-02T20:15:00Z"), decodeInstant("2026-10-02T20:15:00Z"))
    }

    @Test
    fun `los instantes se escriben en UTC con Z`() {
        assertEquals("""{"at":"2026-10-02T20:15:00Z"}""", ApiJson.encodeToString(When.serializer(), When(Instant.parse("2026-10-02T20:15:00Z"))))
    }

    @Test
    fun `una fecha de calendario no se corre de dia aunque traiga zona u hora`() {
        assertEquals(LocalDate.of(2026, 1, 31), decodeDay("2026-01-31T00:00:00"))
        // BirthDate sale de una columna timestamptz: 00:00 Z. Convertirla a hora de
        // Mexico la pasaria al 14 de junio.
        assertEquals(LocalDate.of(1995, 6, 15), decodeDay("1995-06-15T00:00:00Z"))
        assertEquals(LocalDate.of(1995, 6, 15), decodeDay("1995-06-15T00:00:00-06:00"))
        assertEquals(LocalDate.of(1995, 6, 15), decodeDay("1995-06-15"))
    }

    @Test
    fun `una fecha de calendario se escribe sin zona a las 00 00`() {
        assertEquals("""{"day":"2026-01-31T00:00:00"}""", ApiJson.encodeToString(Day.serializer(), Day(LocalDate.of(2026, 1, 31))))
    }

    private fun decodeInstant(text: String) = ApiJson.decodeFromString<When>("""{"at":"$text"}""").at

    private fun decodeDay(text: String) = ApiJson.decodeFromString<Day>("""{"day":"$text"}""").day
}
