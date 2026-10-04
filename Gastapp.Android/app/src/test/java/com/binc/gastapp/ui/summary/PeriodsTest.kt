package com.binc.gastapp.ui.summary

import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.domain.model.User
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navegacion de periodos de Resumen (PeriodOffset de MAUI) y rangos de Explorar periodo. */
class PeriodsTest {

    private val today = LocalDate.of(2026, 10, 2) // viernes
    private val biweekly = user(IncomeTypes.BIWEEKLY, 1, 16)

    private fun user(type: Int, first: Int?, second: Int? = null) = User(
        userId = "u",
        name = "Ana",
        salary = BigDecimal("10000"),
        percentSave = BigDecimal.TEN,
        birthDate = LocalDate.of(1990, 1, 1),
        incomeTypeId = type,
        firstPayDay = first,
        secondPayDay = second,
    )

    @Test
    fun `los periodos de pago van hacia atras y el actual termina hoy`() {
        val current = resolvePeriod(PeriodSelection.Paid(0), biweekly, today)
        assertEquals(LocalDate.of(2026, 10, 1), current.start)
        assertEquals(today, current.end)
        assertEquals("Quincena actual", current.label)
        assertFalse(current.canGoNext)
        assertNull(current.next())

        val previous = resolvePeriod(current.previous(), biweekly, today)
        assertEquals(LocalDate.of(2026, 9, 16), previous.start)
        assertEquals(LocalDate.of(2026, 9, 30), previous.end)
        assertEquals("Quincena anterior", previous.label)
        assertTrue(previous.canGoNext)
        assertEquals(PeriodSelection.Paid(0), previous.next())

        val older = resolvePeriod(previous.previous(), biweekly, today)
        assertEquals(LocalDate.of(2026, 9, 1), older.start)
        assertEquals(LocalDate.of(2026, 9, 15), older.end)
        assertEquals("Hace 2 quincenas", older.label)
        assertEquals(15, older.days().size)
    }

    @Test
    fun `semanal y mensual se nombran segun la forma de cobro`() {
        // Pago los viernes (5 con la numeracion de .NET): hoy empieza la semana.
        val weekly = resolvePeriod(PeriodSelection.Paid(0), user(IncomeTypes.WEEKLY, 5), today)
        assertEquals(today, weekly.start)
        assertEquals("Semana actual", weekly.label)
        assertEquals("Hace 3 semanas", resolvePeriod(PeriodSelection.Paid(3), user(IncomeTypes.WEEKLY, 5), today).label)

        // Mensual el 15: el actual va del 15 de septiembre a hoy.
        val monthly = resolvePeriod(PeriodSelection.Paid(1), user(IncomeTypes.MONTHLY, 15), today)
        assertEquals(LocalDate.of(2026, 8, 15), monthly.start)
        assertEquals(LocalDate.of(2026, 9, 14), monthly.end)
        assertEquals("Mes anterior", monthly.label)
        assertEquals("Hace 2 meses", resolvePeriod(PeriodSelection.Paid(2), user(IncomeTypes.MONTHLY, 15), today).label)
    }

    @Test
    fun `un rango personalizado se recorre con su mismo largo`() {
        val custom = resolvePeriod(PeriodSelection.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19)), biweekly, today)
        assertEquals("Periodo personalizado", custom.label)
        assertEquals(10, custom.dayCount)
        assertEquals(PeriodSelection.Custom(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 9)), custom.previous())
        assertEquals(PeriodSelection.Custom(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29)), custom.next())

        // Uno que llega a hoy ya no tiene siguiente.
        val untilToday = resolvePeriod(PeriodSelection.Custom(LocalDate.of(2026, 9, 23), today), biweekly, today)
        assertFalse(untilToday.canGoNext)
        assertEquals("Últimos 30 días", resolvePeriod(PeriodSelection.Custom(today.minusDays(29), today), biweekly, today).label)
    }

    @Test
    fun `un rango igual a un periodo de pago se guarda como periodo de pago`() {
        assertEquals(PeriodSelection.Paid(0), selectionForRange(LocalDate.of(2026, 10, 1), today, biweekly, today))
        assertEquals(PeriodSelection.Paid(1), selectionForRange(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30), biweekly, today))
        assertEquals(
            PeriodSelection.Custom(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 29)),
            selectionForRange(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 29), biweekly, today),
        )
    }

    @Test
    fun `el dia de un gasto guardado lleva a su periodo de pago`() {
        assertEquals(PeriodSelection.Paid(0), paidSelectionContaining(today, biweekly, today))
        assertEquals(PeriodSelection.Paid(1), paidSelectionContaining(LocalDate.of(2026, 9, 16), biweekly, today))
        assertEquals(PeriodSelection.Paid(2), paidSelectionContaining(LocalDate.of(2026, 9, 3), biweekly, today))
        assertEquals(PeriodSelection.Paid(24), paidSelectionContaining(LocalDate.of(2025, 10, 2), biweekly, today))
    }

    @Test
    fun `el dia por defecto es hoy si esta dentro, si no el ultimo que ya paso`() {
        val current = resolvePeriod(PeriodSelection.Paid(0), biweekly, today)
        assertEquals(today, current.defaultDay(today))
        val previous = resolvePeriod(PeriodSelection.Paid(1), biweekly, today)
        assertEquals(LocalDate.of(2026, 9, 30), previous.defaultDay(today))
    }

    @Test
    fun `los atajos de Explorar periodo`() {
        // En es-MX la semana empieza en domingo.
        assertEquals(LocalDate.of(2026, 9, 27) to LocalDate.of(2026, 10, 3), Shortcut.ThisWeek.range(biweekly, today))
        assertEquals(LocalDate.of(2026, 10, 1) to today, Shortcut.CurrentPeriod.range(biweekly, today))
        assertEquals(LocalDate.of(2026, 9, 16) to LocalDate.of(2026, 9, 30), Shortcut.PreviousPeriod.range(biweekly, today))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), Shortcut.ThisMonth.range(biweekly, today))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), Shortcut.LastMonth.range(biweekly, today))
        assertEquals(LocalDate.of(2026, 9, 3) to today, Shortcut.Last30Days.range(biweekly, today))
    }
}
