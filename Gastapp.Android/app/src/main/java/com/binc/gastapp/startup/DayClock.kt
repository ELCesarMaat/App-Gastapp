package com.binc.gastapp.startup

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El "hoy" de los procesos de fondo (avisos de tarjeta, datos del reloj): reemplaza al
 * DayChangedMessage de MAUI. Emite al volver de segundo plano ([refresh], lo llama
 * GastappApplication) y a medianoche ([runMidnightTicks]).
 *
 * Las pantallas ya revisan el dia por su cuenta al volver y a medianoche (Fase 4,
 * refreshToday de cada ViewModel); esto es para lo que corre sin pantalla.
 */
@Singleton
class DayClock @Inject constructor(private val clock: Clock) {

    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    /** Si cambio el dia, emite el nuevo; si no, no pasa nada (StateFlow no repite). */
    fun refresh() {
        _today.value = LocalDate.now(clock)
    }

    /** Despierta un segundo despues de cada medianoche mientras viva el proceso. */
    suspend fun runMidnightTicks() {
        while (true) {
            delay(untilNextMidnight().toMillis() + 1_000)
            refresh()
        }
    }

    private fun untilNextMidnight(): Duration {
        val now = LocalDateTime.now(clock)
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).coerceAtLeast(Duration.ZERO)
    }
}
