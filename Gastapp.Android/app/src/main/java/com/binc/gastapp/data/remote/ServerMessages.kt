package com.binc.gastapp.data.remote

import com.binc.gastapp.R
import com.binc.gastapp.ui.format.Strings

/**
 * El API responde sus errores en espanol ("Email en uso"). Los que le llegan al usuario
 * se muestran en el idioma del telefono; uno que no este aqui se muestra tal cual. Si el
 * API cambia un texto, hay que cambiarlo aqui tambien.
 */
fun translateServerMessage(strings: Strings, message: String): String {
    val text = message.trim()
    KnownMessages[text]?.let { return strings.get(it) }
    WaitSeconds.matchEntire(text)?.let { match ->
        val seconds = match.groupValues[1].toInt()
        return strings.plural(R.plurals.server_wait_seconds, seconds)
    }
    WaitMinutes.matchEntire(text)?.let { match ->
        val minutes = match.groupValues[1].toInt()
        return strings.plural(R.plurals.server_wait_minutes, minutes)
    }
    return text
}

// Los de CodeRequestLimiter.Message en el API.
private val WaitSeconds = Regex("""Espera (\d+) segundos para pedir otro código\.""")
private val WaitMinutes = Regex("""Ya pediste varios códigos\. Intenta de nuevo en (\d+) minutos\.""")

private val KnownMessages = mapOf(
    "Email en uso" to R.string.server_email_in_use,
    "Este correo ya tiene una cuenta. Inicia sesión o usa 'Olvidé mi contraseña'." to R.string.server_email_has_account,
    "El usuario no existe. Verifica tu correo o registrate." to R.string.server_user_not_found,
    "Contraseña incorrecta. Verifica tu contraseña o usa la opción 'Olvidé mi contraseña'." to R.string.server_wrong_password,
    "Debes confirmar tu correo antes de crear la cuenta." to R.string.server_confirm_email_first,
    "Código inválido o expirado." to R.string.server_invalid_code,
    "Codigo no valido o expirado." to R.string.server_invalid_code,
    "Demasiados intentos fallidos. Solicita un código nuevo." to R.string.server_too_many_code_attempts,
    "Demasiados intentos. Espera 15 minutos e intenta de nuevo." to R.string.server_too_many_link_attempts,
    "No se pudo generar la contraseña temporal. Verifica que el correo esté registrado." to R.string.server_temporary_password_failed,
    "El correo es requerido." to R.string.server_email_required,
    "El correo y la contraseña son requeridos." to R.string.server_email_password_required,
    "El correo y el código son requeridos." to R.string.server_email_code_required,
    "El correo, el código y la contraseña nueva son requeridos." to R.string.server_email_code_password_required,
    "Usuario no encontrado." to R.string.server_user_missing,
    "Usuario no encontrado" to R.string.server_user_missing,
    "Dispositivo no encontrado." to R.string.server_device_not_found,
)
