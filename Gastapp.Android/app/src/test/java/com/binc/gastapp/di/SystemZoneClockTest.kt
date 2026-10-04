package com.binc.gastapp.di

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class SystemZoneClockTest {

    private val original = TimeZone.getDefault()

    @After
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun `sigue el cambio de zona horaria sin recrearse`() {
        val clock = DataModule.provideClock()

        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
        assertEquals(ZoneId.of("America/Mexico_City"), clock.zone)

        // Es lo que hace Android en un proceso vivo al cambiar la zona del telefono.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneId.of("Asia/Tokyo"), clock.zone)
    }
}
