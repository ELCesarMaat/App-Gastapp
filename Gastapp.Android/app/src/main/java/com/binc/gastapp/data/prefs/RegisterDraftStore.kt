package com.binc.gastapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Lo que el usuario lleva capturado en el registro (RegisterDraft de MAUI).
 *
 * La contrasena NO va aqui ni en ningun otro lado: si el registro se interrumpe, se
 * vuelve a pedir (en MAUI iba en SecureStorage). Los montos van como el texto que
 * escribio el usuario, y la fecha de nacimiento como fecha ISO.
 */
@Serializable
data class RegisterDraft(
    val step: Int = 0,
    val email: String = "",
    val emailVerified: Boolean = false,
    val name: String = "",
    val birthDate: String? = null,
    val incomeTypeId: Int = 0,
    val firstPayDay: Int? = null,
    val secondPayDay: Int? = null,
    val salaryText: String = "",
    val percentSaveText: String = "",
    val savedAtMillis: Long = 0,
)

/**
 * Conserva el avance del registro para no empezar de cero si se cierra la app, sobre
 * todo entre confirmar el correo y capturar los datos.
 */
class RegisterDraftStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    /** El borrador o null si no hay, si no se puede leer o si ya caduco (el codigo expiro hace mucho). */
    suspend fun load(): RegisterDraft? {
        val json = dataStore.data.first()[Draft] ?: return null
        val draft = try {
            JsonFormat.decodeFromString(RegisterDraft.serializer(), json)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val savedAt = draft?.let { Instant.ofEpochMilli(it.savedAtMillis) }
        if (draft == null || savedAt == null || Duration.between(savedAt, clock.instant()) > Lifetime) {
            clear()
            return null
        }
        return draft
    }

    suspend fun save(draft: RegisterDraft) {
        val stamped = draft.copy(savedAtMillis = clock.millis())
        dataStore.edit { it[Draft] = JsonFormat.encodeToString(RegisterDraft.serializer(), stamped) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(Draft) }
    }

    companion object {
        /** DraftLifetime de MAUI. */
        val Lifetime: Duration = Duration.ofDays(2)

        private val Draft = stringPreferencesKey("register_draft")
        private val JsonFormat = Json { ignoreUnknownKeys = true }
    }
}
