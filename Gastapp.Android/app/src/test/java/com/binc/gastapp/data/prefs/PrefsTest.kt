package com.binc.gastapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataStore en memoria. Lo que se prueba es la logica de los stores (llaves, valores
 * por defecto, caducidad); la persistencia es de la libreria. Ademas su almacenamiento
 * por archivo no sirve en la JVM de Windows: renombra el .tmp encima del archivo y
 * Windows no lo deja (en Android si funciona).
 */
private class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class PrefsTest {
    private val now = Instant.parse("2026-10-02T18:00:00Z")

    @Test
    fun sesionGuardaYSeBorra() = runTest {
        val store = SessionStore(InMemoryDataStore())
        assertNull(store.get())

        store.save("token-abc", now.plus(Duration.ofDays(7)))
        val session = store.get()!!
        assertEquals("token-abc", session.token)
        assertTrue(session.isValidAt(now))
        assertFalse(session.isValidAt(now.plus(Duration.ofDays(8))))

        store.clear()
        assertNull(store.get())
    }

    @Test
    fun ajustesConLosValoresDeMaui() = runTest {
        val store = SettingsStore(InMemoryDataStore())
        assertEquals(AppSettings(remindersEnabled = true, reminderFrequencyHours = 4, savingsModeIsPercent = true, themeMode = ThemeMode.SYSTEM, dynamicColor = false), store.settings.first())

        store.setReminders(enabled = false, frequencyHours = 8)
        store.setThemeMode(ThemeMode.DARK)
        store.setDynamicColor(true)
        store.setSavingsModeIsPercent(false)

        assertEquals(AppSettings(false, 8, false, ThemeMode.DARK, true), store.settings.first())
    }

    @Test
    fun borradorDelRegistroCaducaALosDosDias() = runTest {
        val data = InMemoryDataStore()
        val draft = RegisterDraft(step = 2, email = "a@b.com", emailVerified = true, name = "Ana", salaryText = "12,500.50")

        RegisterDraftStore(data, Clock.fixed(now, ZoneOffset.UTC)).save(draft)

        val unDiaDespues = RegisterDraftStore(data, Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC))
        assertEquals(draft.copy(savedAtMillis = now.toEpochMilli()), unDiaDespues.load())

        val tresDiasDespues = RegisterDraftStore(data, Clock.fixed(now.plus(Duration.ofDays(3)), ZoneOffset.UTC))
        assertNull(tresDiasDespues.load())
        // Al caducar se borra: ni el reloj de antes lo vuelve a ver.
        assertNull(unDiaDespues.load())
    }
}
