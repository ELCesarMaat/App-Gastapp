package com.binc.gastapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Ajustes del telefono. Los valores por defecto son los de MAUI: recordatorios
 * encendidos cada 4 horas y meta de ahorro en porcentaje. Material You va apagado por
 * defecto (decision del usuario: tema de marca).
 */
data class AppSettings(
    val remindersEnabled: Boolean = true,
    val reminderFrequencyHours: Int = 4,
    val savingsModeIsPercent: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

/** Cuando toca el siguiente recordatorio y con que frecuencia se programo. */
data class ReminderSchedule(val nextAt: Instant, val hours: Int)

/**
 * Preferencias del dispositivo (reminders_enabled, reminder_frequency_hours y
 * SavingsModeIsPercent en MAUI, mas el tema).
 *
 * A diferencia de MAUI, cerrar sesion NO las borra: son del telefono, no de la cuenta.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            remindersEnabled = prefs[RemindersEnabled] ?: defaults.remindersEnabled,
            reminderFrequencyHours = prefs[ReminderFrequencyHours] ?: defaults.reminderFrequencyHours,
            savingsModeIsPercent = prefs[SavingsModeIsPercent] ?: defaults.savingsModeIsPercent,
            themeMode = prefs[Theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            dynamicColor = prefs[DynamicColor] ?: defaults.dynamicColor,
        )
    }

    suspend fun setReminders(enabled: Boolean, frequencyHours: Int) {
        dataStore.edit {
            it[RemindersEnabled] = enabled
            it[ReminderFrequencyHours] = frequencyHours
        }
    }

    suspend fun setSavingsModeIsPercent(isPercent: Boolean) {
        dataStore.edit { it[SavingsModeIsPercent] = isPercent }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Theme] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DynamicColor] = enabled }
    }

    /**
     * Turno del siguiente mensaje de recordatorio, y avanza el contador. Los mensajes van
     * rotando como en MAUI aunque cada recordatorio lo dispare un trabajo distinto.
     */
    suspend fun takeReminderIndex(): Int {
        var taken = 0
        dataStore.edit {
            taken = it[ReminderIndex] ?: 0
            it[ReminderIndex] = taken + 1
        }
        return taken
    }

    /** La alarma del siguiente recordatorio (AlarmReminderScheduler), o null si no hay. */
    suspend fun reminderSchedule(): ReminderSchedule? {
        val prefs = dataStore.data.first()
        val nextAt = prefs[ReminderNextAt] ?: return null
        val hours = prefs[ReminderScheduledHours] ?: return null
        return ReminderSchedule(Instant.ofEpochMilli(nextAt), hours)
    }

    suspend fun setReminderSchedule(schedule: ReminderSchedule?) {
        dataStore.edit {
            if (schedule == null) {
                it.remove(ReminderNextAt)
                it.remove(ReminderScheduledHours)
            } else {
                it[ReminderNextAt] = schedule.nextAt.toEpochMilli()
                it[ReminderScheduledHours] = schedule.hours
            }
        }
    }

    private companion object {
        val RemindersEnabled = booleanPreferencesKey("reminders_enabled")
        val ReminderFrequencyHours = intPreferencesKey("reminder_frequency_hours")
        val SavingsModeIsPercent = booleanPreferencesKey("savings_mode_is_percent")
        val Theme = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val ReminderIndex = intPreferencesKey("reminder_index")
        val ReminderNextAt = longPreferencesKey("reminder_next_at")
        val ReminderScheduledHours = intPreferencesKey("reminder_scheduled_hours")
    }
}
