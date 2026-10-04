package com.binc.gastapp.ui.start

import androidx.annotation.StringRes
import com.binc.gastapp.R
import java.math.BigDecimal

/**
 * Validaciones del registro: las reglas y los mensajes de RegisterValidator (MAUI,
 * FluentValidation). Cada funcion devuelve el texto del primer error, como lo mostraba MAUI
 * (`result.Errors.First()`), o null si el valor es valido.
 */
object RegisterRules {

    @StringRes
    fun emailError(email: String): Int? = when {
        email.isBlank() -> R.string.error_email_required
        !looksLikeEmail(email) -> R.string.error_email_invalid
        else -> null
    }

    @StringRes
    fun confirmEmailError(confirmEmail: String, email: String): Int? = when {
        confirmEmail.isBlank() -> R.string.error_confirm_email_required
        confirmEmail != email -> R.string.error_emails_mismatch
        else -> null
    }

    @StringRes
    fun passwordError(password: String): Int? = when {
        password.isBlank() -> R.string.error_password_required
        password.length < 6 -> R.string.error_password_short
        password.length > 20 -> R.string.error_password_long
        !password.any { it in 'A'..'Z' } -> R.string.error_password_uppercase
        !password.any { it in '0'..'9' } -> R.string.error_password_number
        password.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } -> R.string.error_password_special
        else -> null
    }

    @StringRes
    fun nameError(name: String): Int? = when {
        name.isBlank() -> R.string.error_name_required
        name.length < 2 -> R.string.error_name_short
        else -> null
    }

    @StringRes
    fun salaryError(salary: BigDecimal?): Int? =
        if (salary == null || salary.signum() <= 0) R.string.error_salary_positive else null

    @StringRes
    fun percentSaveError(percent: BigDecimal?): Int? =
        if (percent == null || percent < BigDecimal.ZERO || percent > BigDecimal(99)) R.string.error_percent_save_range else null

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
    @StringRes
    fun error(newPassword: String, confirmPassword: String): Int? = when {
        newPassword.isBlank() -> R.string.error_new_password_required
        newPassword.length < 6 -> R.string.error_new_password_short
        newPassword != confirmPassword -> R.string.error_passwords_mismatch
        else -> null
    }
}
