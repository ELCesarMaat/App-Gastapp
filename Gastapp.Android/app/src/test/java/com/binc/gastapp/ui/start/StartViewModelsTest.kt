package com.binc.gastapp.ui.start

import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.sync.SyncTest
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Reloj que la prueba adelanta a mano (para las esperas entre codigos). */
internal class MutableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** Login (StartPageViewModel) y recuperar contrasena (ForgetPasswordViewModel). */
class StartViewModelsTest : SyncTest() {

    @get:Rule
    val main = MainDispatcherRule()

    // ------------------------------------------------------------------ login

    @Test
    fun `sin correo o contrasena no llama al API`() = runTest {
        val vm = LoginViewModel(sessions)
        vm.onEmailChange("ana@gastapp.dev")
        vm.login()

        assertEquals("Ingresa tu correo y contraseña para continuar.", vm.state.value.error)
        assertEquals(0, server.requestCount)

        // Escribir borra el error, como OnEmailChanged de MAUI.
        vm.onPasswordChange("x")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `credenciales malas muestran el mensaje del API`() = runTest {
        val vm = LoginViewModel(sessions)
        vm.onEmailChange("ana@gastapp.dev")
        vm.onPasswordChange("mala")
        respond("\"Correo o contraseña incorrectos\"", code = 400)

        vm.login()
        val state = vm.state.awaitUntil { !it.busy && it.error != null }

        assertEquals("Correo o contraseña incorrectos", state.error)
        assertFalse(state.loggedIn)
    }

    @Test
    fun `el login correcto deja la sesion activa`() = runTest {
        val vm = LoginViewModel(sessions)
        vm.prefillEmail("ana@gastapp.dev")
        vm.prefillEmail("otra@gastapp.dev")
        assertEquals("prefillEmail no pisa lo escrito", "ana@gastapp.dev", vm.state.value.email)
        vm.onPasswordChange("secreta")
        respond(fixture("login.json"))

        vm.login()
        vm.state.awaitUntil { it.loggedIn }

        assertTrue(sessions.state.first() is SessionState.Active)
    }

    // ------------------------------------------------------------------ recuperar contrasena

    @Test
    fun `recuperar contrasena recorre los cuatro pasos`() = runTest {
        val stepClock = MutableClock(clock.instant(), zone)
        val vm = ForgotPasswordViewModel(sessions, stepClock)

        vm.sendCode()
        assertEquals("Ingresa tu correo para recibir el código de verificación.", vm.state.value.error)

        vm.onEmailChange("ana@gastapp.dev")
        respond("true")
        vm.sendCode()
        vm.state.awaitUntil { it.step == ResetStep.Code && !it.busy }
        assertEquals("/api/User/PasswordReset/request?Email=ana%40gastapp.dev", server.takeRequest().path)

        // Antes de un minuto no se pide otro: el boton espera (y el API lo rechazaria).
        vm.sendCode()
        assertFalse("No salio al API", vm.state.value.busy)
        assertEquals(1, server.requestCount)

        // Pasado el minuto, reenviar avisa que se mando otro.
        stepClock.advance(Duration.ofSeconds(61))
        respond("true")
        vm.sendCode()
        vm.state.awaitUntil { it.message != null }
        assertEquals("Te enviamos un código nuevo.", vm.state.value.message)
        server.takeRequest()

        vm.verifyCode()
        assertEquals("Ingresa el código de verificación que recibiste en tu correo.", vm.state.value.error)
        vm.onCodeChange("654321")
        respond("true")
        vm.verifyCode()
        vm.state.awaitUntil { it.step == ResetStep.NewPassword && !it.busy }

        vm.onNewPasswordChange("corta")
        vm.resetPassword()
        assertEquals("La contraseña debe tener al menos 6 caracteres.", vm.state.value.error)
        vm.onNewPasswordChange("nueva123")
        vm.onConfirmPasswordChange("nueva124")
        vm.resetPassword()
        assertEquals("Las contraseñas no coinciden.", vm.state.value.error)

        vm.onConfirmPasswordChange("nueva123")
        respond("true")
        vm.resetPassword()
        vm.state.awaitUntil { it.step == ResetStep.Done }
        server.takeRequest()
        assertEquals(
            "/api/User/PasswordReset/confirm?email=ana%40gastapp.dev&code=654321&newPassword=nueva123",
            server.takeRequest().path,
        )
        assertEquals("", vm.state.value.newPassword)
        assertFalse("Al terminar, atras cierra la pantalla", vm.previous())
    }

    @Test
    fun `un codigo vencido muestra el mensaje y se queda en el paso`() = runTest {
        val vm = ForgotPasswordViewModel(sessions, clock)
        vm.onEmailChange("ana@gastapp.dev")
        respond("true")
        vm.sendCode()
        vm.state.awaitUntil { it.step == ResetStep.Code && !it.busy }

        vm.onCodeChange("111111")
        respond("", code = 400)
        vm.verifyCode()
        val state = vm.state.awaitUntil { !it.busy && it.error != null }

        assertEquals(ResetStep.Code, state.step)
        assertEquals("Código inválido o expirado.", state.error)
        assertTrue(vm.previous())
        assertEquals(ResetStep.Email, vm.state.value.step)
    }

    @Test
    fun `volver al correo y seguir con el mismo no pide otro codigo`() = runTest {
        val vm = ForgotPasswordViewModel(sessions, clock)
        vm.onEmailChange("ana@gastapp.dev")
        respond("true")
        vm.sendCode()
        vm.state.awaitUntil { it.step == ResetStep.Code && !it.busy }

        // El codigo de hace un momento sigue sirviendo.
        assertTrue(vm.previous())
        vm.onEmailChange("ana@gastapp.dev ")
        vm.sendCode()
        assertEquals(ResetStep.Code, vm.state.value.step)
        assertEquals(1, server.requestCount)

        // Con otro correo si se pide; si el API dice que hay que esperar, se muestra su mensaje.
        assertTrue(vm.previous())
        vm.onEmailChange("otra@gastapp.dev")
        respond("\"Espera 42 segundos para pedir otro código.\"", code = 429)
        vm.sendCode()
        val state = vm.state.awaitUntil { !it.busy && it.error != null }
        assertEquals("Espera 42 segundos para pedir otro código.", state.error)
        assertEquals(ResetStep.Email, state.step)
        assertEquals(2, server.requestCount)
    }
}
