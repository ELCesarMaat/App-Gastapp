package com.binc.gastapp.data.local

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun fechaDeGastoTieneAnchoFijoYConservaNanosegundos() {
        val exacta = LocalDateTime.of(2026, 10, 2, 14, 15, 0)
        val conFraccion = LocalDateTime.of(2026, 10, 2, 14, 15, 30, 123_456_789)

        val texto = converters.localDateTimeToText(exacta)!!
        assertEquals("2026-10-02T14:15:00.000000000", texto)
        assertEquals(29, converters.localDateTimeToText(conFraccion)!!.length)
        assertEquals(conFraccion, converters.textToLocalDateTime(converters.localDateTimeToText(conFraccion)))
        // Los primeros 10 caracteres son el dia (substr(date, 1, 10) en las consultas).
        assertEquals("2026-10-02", texto.substring(0, 10))
    }

    @Test
    fun elOrdenDelTextoEsElOrdenDelTiempo() {
        val fechas = listOf(
            LocalDateTime.of(2026, 10, 2, 14, 15),
            LocalDateTime.of(2026, 10, 2, 14, 15, 0, 1),
            LocalDateTime.of(2026, 10, 2, 14, 15, 30),
            LocalDateTime.of(2026, 10, 2, 9, 0),
            LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_999_999),
            LocalDateTime.of(2026, 10, 3, 0, 0),
        )
        val porTexto = fechas.sortedBy { converters.localDateTimeToText(it) }
        assertEquals(fechas.sorted(), porTexto)
    }

    @Test
    fun fechaDeCalendarioViajaTalCual() {
        assertEquals("2026-01-31", converters.localDateToText(LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 1, 31), converters.textToLocalDate("2026-01-31"))
    }

    @Test
    fun instanteEnMilisegundosYPorcentajeSinPerderDecimales() {
        val instante = Instant.parse("2026-10-02T18:00:00.123Z")
        assertEquals(instante, converters.millisToInstant(converters.instantToMillis(instante)))

        val porcentaje = BigDecimal("15.3846")
        assertEquals("15.3846", converters.bigDecimalToText(porcentaje))
        assertTrue(porcentaje.compareTo(converters.textToBigDecimal("15.3846")) == 0)
    }
}
