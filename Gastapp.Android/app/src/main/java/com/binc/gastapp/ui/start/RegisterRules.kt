package com.binc.gastapp.ui.start

import java.math.BigDecimal

/**
 * Validaciones del registro: las reglas y los mensajes de RegisterValidator (MAUI,
 * FluentValidation). Cada funcion devuelve el primer error, como lo mostraba MAUI
 * (`result.Errors.First()`), o null si el valor es valido.
 */
object RegisterRules {

    fun emailError(email: String): String? = when {
        email.isBlank() -> "El correo electrónico es obligatorio"
        !looksLikeEmail(email) -> "Ingrese un correo electrónico válido"
        else -> null
    }

    fun confirmEmailError(confirmEmail: String, email: String): String? = when {
        confirmEmail.isBlank() -> "La confirmación del correo es obligatoria"
        confirmEmail != email -> "Los correos electrónicos no coinciden"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isBlank() -> "La contraseña es obligatoria"
        password.length < 6 -> "La contraseña debe de tener al menos 6 caracteres"
        password.length > 20 -> "La contraseña no puede tener mas de 20 caracteres"
        !password.any { it in 'A'..'Z' } -> "La contraseña debe contener al menos una letra mayúscula"
        !password.any { it in '0'..'9' } -> "La contraseña debe contener al menos un número"
        password.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } ->
            "La contraseña debe contener al menos un carácter especial"
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.isBlank() -> "El nombre es obligatorio"
        name.length < 2 -> "El nombre debe tener al menos 2 caracteres"
        else -> null
    }

    fun salaryError(salary: BigDecimal?): String? =
        if (salary == null || salary.signum() <= 0) "El salario debe ser mayor que 0" else null

    fun percentSaveError(percent: BigDecimal?): String? =
        if (percent == null || percent < BigDecimal.ZERO || percent > BigDecimal(99)) {
            "El porcentaje de ahorro debe estar entre 0 y 99"
        } else {
            null
        }

    /**
     * EmailAddress() de FluentValidation 9+: solo pide una arroba que no este al
     * principio ni al final y que sea la unica. El servidor manda el codigo de todos
     * modos, que es la verificacion real.
     */
    private fun looksLikeEmail(value: String): Boolean {
        val at = value.indexOf('@')
        return at > 0 && at != value.lastIndex && at == value.lastIndexOf('@')
    }
}

/** Reglas de la contrasena nueva de ForgetPasswordViewModel (mas laxas que las del registro). */
object ResetPasswordRules {
    fun error(newPassword: String, confirmPassword: String): String? = when {
        newPassword.isBlank() -> "Ingresa tu nueva contraseña."
        newPassword.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
        newPassword != confirmPassword -> "Las contraseñas no coinciden."
        else -> null
    }
}
