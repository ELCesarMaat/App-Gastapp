package com.binc.gastapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** El token del API y cuando vence. */
data class Session(val token: String, val expiresAt: Instant) {
    fun isValidAt(now: Instant): Boolean = now < expiresAt
}

/**
 * Sesion del API. Reemplaza las Preferences "token" y "tokenexpiration" de MAUI; la
 * expiracion ahora va en milisegundos y no como DateTime.ToString() (dependia de la
 * cultura del telefono).
 */
class SessionStore(private val dataStore: DataStore<Preferences>) {

    val session: Flow<Session?> = dataStore.data.map { prefs ->
        val token = prefs[Token]
        val expiresAt = prefs[ExpiresAtMillis]
        if (token.isNullOrEmpty() || expiresAt == null) null else Session(token, Instant.ofEpochMilli(expiresAt))
    }

    suspend fun get(): Session? = session.first()

    suspend fun save(token: String, expiresAt: Instant) {
        dataStore.edit {
            it[Token] = token
            it[ExpiresAtMillis] = expiresAt.toEpochMilli()
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val Token = stringPreferencesKey("token")
        val ExpiresAtMillis = longPreferencesKey("token_expires_at_millis")
    }
}
