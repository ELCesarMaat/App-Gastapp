package com.binc.gastapp.ui.start

import androidx.test.core.app.ApplicationProvider
import com.binc.gastapp.ui.format.ResourceStrings
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Mismas reglas y mensajes que RegisterValidator (FluentValidation) en MAUI. */
@RunWith(RobolectricTestRunner::class)
class RegisterRulesTest {

    private val strings = ResourceStrings(ApplicationProvider.getApplicationContext())

    private fun text(id: Int?): String? = id?.let { strings.get(it) }

    @Test
    fun correo() {
        assertEquals("El correo electrónico es obligatorio", text(RegisterRules.emailError("  ")))
        assertEquals("Ingrese un correo electrónico válido", text(RegisterRules.emailError("ana")))
        assertEquals("Ingrese un correo electrónico válido", text(RegisterRules.emailError("@gastapp.dev")))
        assertEquals("Ingrese un correo electrónico válido", text(RegisterRules.emailError("ana@")))
        assertEquals("Ingrese un correo electrónico válido", text(RegisterRules.emailError("a@b@c")))
        assertNull(RegisterRules.emailError("ana@gastapp.dev"))

        assertEquals("La confirmación del correo es obligatoria", text(RegisterRules.confirmEmailError("", "ana@gastapp.dev")))
        assertEquals("Los correos electrónicos no coinciden", text(RegisterRules.confirmEmailError("Ana@gastapp.dev", "ana@gastapp.dev")))
        assertNull(RegisterRules.confirmEmailError("ana@gastapp.dev", "ana@gastapp.dev"))
    }

    @Test
    fun contrasenaEnElOrdenDeMaui() {
        assertEquals("La contraseña es obligatoria", text(RegisterRules.passwordError("")))
        assertEquals("La contraseña debe de tener al menos 6 caracteres", text(RegisterRules.passwordError("Ab1#")))
        assertEquals("La contraseña no puede tener mas de 20 caracteres", text(RegisterRules.passwordError("Abcdefghij1#klmnopqrs")))
        assertEquals("La contraseña debe contener al menos una letra mayúscula", text(RegisterRules.passwordError("abcdef1#")))
        assertEquals("La contraseña debe contener al menos un número", text(RegisterRules.passwordError("Abcdefg#")))
        assertEquals("La contraseña debe contener al menos un carácter especial", text(RegisterRules.passwordError("Abcdefg1")))
        // Un espacio cuenta como especial ([^a-zA-Z0-9]), igual que en MAUI.
        assertNull(RegisterRules.passwordError("Abcdef 1"))
        assertNull(RegisterRules.passwordError("Secreta#1"))
    }

    @Test
    fun nombreSueldoYAhorro() {
        assertEquals("El nombre es obligatorio", text(RegisterRules.nameError(" ")))
        assertEquals("El nombre debe tener al menos 2 caracteres", text(RegisterRules.nameError("A")))
        assertNull(RegisterRules.nameError("Al"))

        assertEquals("El salario debe ser mayor que 0", text(RegisterRules.salaryError(BigDecimal.ZERO)))
        assertEquals("El salario debe ser mayor que 0", text(RegisterRules.salaryError(null)))
        assertNull(RegisterRules.salaryError(BigDecimal("0.01")))

        assertEquals("El porcentaje de ahorro debe estar entre 0 y 99", text(RegisterRules.percentSaveError(BigDecimal("99.5"))))
        assertNull(RegisterRules.percentSaveError(BigDecimal.ZERO))
        assertNull(RegisterRules.percentSaveError(BigDecimal(99)))
    }

    @Test
    fun contrasenaNueva() {
        assertEquals("Ingresa tu nueva contraseña.", text(ResetPasswordRules.error("", "")))
        assertEquals("La contraseña debe tener al menos 6 caracteres.", text(ResetPasswordRules.error("12345", "12345")))
        assertEquals("Las contraseñas no coinciden.", text(ResetPasswordRules.error("123456", "123457")))
        assertNull(ResetPasswordRules.error("123456", "123456"))
    }
}
