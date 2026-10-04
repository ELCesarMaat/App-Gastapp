package com.binc.gastapp.data.session

import com.binc.gastapp.data.prefs.Session
import com.binc.gastapp.data.prefs.SessionStore
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dueno del token para quien llama al API (sincronizacion, dispositivos...). Aplica la
 * regla de la Fase 3, la misma de App.xaml.cs en MAUI:
 *  - Solo un 401 explicito del servidor invalida la sesion: se borra el token y la app
 *    manda a iniciar sesion ([revoked]).
 *  - Un 5xx, un timeout o la falta de red nunca expulsan.
 *  - Un token vencido por tiempo no expulsa: se sigue usando la app con los datos
 *    locales y se avisa que hay que iniciar sesion para volver a sincronizar.
 */
@Singleton
class SessionGuard @Inject constructor(
    private val sessionStore: SessionStore,
    private val clock: Clock,
) {
    private val _revoked = MutableStateFlow(false)

    /** El servidor rechazo el token (401) en esta ejecucion de la app. */
    val revoked: StateFlow<Boolean> = _revoked.asStateFlow()

    /** El token si existe y no ha vencido; si no, null (no se llama al API). */
    suspend fun validToken(): String? = sessionStore.get()?.takeIf { it.isValidAt(clock.instant()) }?.token

    suspend fun session(): Session? = sessionStore.get()

    /** 401 explicito: el token ya no sirve (cuenta borrada, contrasena cambiada, firma...). */
    suspend fun onUnauthorized() {
        sessionStore.clear()
        _revoked.value = true
    }

    /** Despues de iniciar sesion o de cerrarla. */
    fun clearRevoked() {
        _revoked.value = false
    }
}
