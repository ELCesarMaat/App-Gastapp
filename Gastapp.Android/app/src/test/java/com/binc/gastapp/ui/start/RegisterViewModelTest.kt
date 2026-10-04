package com.binc.gastapp.ui.start

import com.binc.gastapp.core.remote.ApiJson
import com.binc.gastapp.core.remote.CreateUserRequest
import com.binc.gastapp.data.prefs.RegisterDraft
import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.sync.SyncTest
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** RegisterViewModel con Room en memoria, DataStore en memoria y el API contra MockWebServer. */
class RegisterViewModelTest : SyncTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel() = RegisterViewModel(sessions, draftStore, clock)

    private fun RegisterViewModel.fillAccount(email: String = "ana@gastapp.dev") {
        onEmailChange(email)
        onConfirmEmailChange(email)
        onPasswordChange("Secreta#1")
    }

    @Test
    fun `el registro completo confirma el correo, crea la cuenta y entra`() = runTest {
        val vm = viewModel()
        vm.fillAccount()
        assertTrue(vm.state.value.canContinue)

        // Paso 1 -> 2: manda el codigo.
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.EmailCode }
        assertEquals("/api/User/EmailVerification/request?Email=ana%40gastapp.dev", server.takeRequest().path)

        // Reenviar antes de 60 s no hace nada (el reloj de la prueba esta fijo).
        vm.resendCode()
        assertEquals(1, server.requestCount)

        // Paso 2 -> 3: el codigo solo acepta 6 digitos.
        vm.onEmailCodeChange("12a34-567")
        assertEquals("123456", vm.state.value.emailCode)
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.Name }
        assertEquals("/api/User/EmailVerification/verify?Email=ana%40gastapp.dev&Code=123456", server.takeRequest().path)
        assertTrue(vm.state.value.emailVerified)

        // Ya con el correo confirmado se guarda el borrador, sin contrasena.
        val draft = draftStore.load()!!
        assertTrue(draft.emailVerified)
        assertEquals(RegisterStep.Name.ordinal, draft.step)

        vm.onNameChange("Ana")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.BirthDate }
        assertFalse("Sin fecha no se avanza", vm.state.value.canContinue)
        vm.onBirthDateChange(LocalDate.of(1995, 6, 15))
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.Salary }

        // Quincenal: se preseleccionan 15 y 30 (los de MAUI); el tercero desplaza al mas viejo.
        vm.onIncomeTypeChange(IncomeTypes.BIWEEKLY)
        assertEquals(listOf(15, 30), vm.state.value.monthPayDays)
        vm.onMonthPayDayToggle(1)
        assertEquals(listOf(30, 1), vm.state.value.monthPayDays)
        vm.onSalaryChange("12500.50")
        vm.onPercentSaveChange("150")
        assertEquals("Mas de 99 se topa", "99", vm.state.value.percentSaveText)
        vm.onPercentSaveChange("10")
        assertEquals("Estarías ahorrando \$1,250.05 por período", vm.state.value.savingText)

        respond(fixture("create_user_response.json"))
        respond(fixture("login.json"))
        vm.next()
        vm.state.awaitUntil { it.welcomeName != null }
        assertEquals("Ana", vm.state.value.welcomeName)

        val request = ApiJson.decodeFromString<CreateUserRequest>(server.takeRequest().body.readUtf8())
        assertEquals("Ana", request.name)
        assertEquals("Secreta#1", request.password)
        assertEquals(LocalDate.of(1995, 6, 15), request.birthDate)
        assertEquals(IncomeTypes.BIWEEKLY, request.incomeTypeId)
        assertEquals(1, request.firstPayDay)
        assertEquals(30, request.secondPayDay)
        assertEquals(BigDecimal("12500.50"), request.salary)
        assertEquals(BigDecimal("10"), request.percentSave)

        // Despues de crearla se entra con login (no se arma el usuario a mano como en MAUI).
        assertEquals("/api/User/Login", server.takeRequest().path)
        assertTrue(sessions.state.first() is SessionState.Active)
        assertNull("El borrador ya sobra", draftStore.load())
    }

    @Test
    fun `si el API rechaza el correo se queda en el primer paso con su mensaje`() = runTest {
        val vm = viewModel()
        vm.fillAccount()
        respond("\"Este correo ya tiene una cuenta.\"", code = 400)

        vm.next()
        val state = vm.state.awaitUntil { it.message != null }

        assertEquals(RegisterStep.Account, state.step)
        assertEquals("Este correo ya tiene una cuenta.", state.message)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `un codigo equivocado muestra el error del API y no avanza`() = runTest {
        val vm = viewModel()
        vm.fillAccount()
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.EmailCode }

        vm.onEmailCodeChange("000000")
        respond("\"Código inválido o expirado.\"", code = 400)
        vm.next()
        val state = vm.state.awaitUntil { it.emailCodeError != null }

        assertEquals(RegisterStep.EmailCode, state.step)
        assertEquals("Código inválido o expirado.", state.emailCodeError)
        assertFalse(state.emailVerified)
    }

    @Test
    fun `con el correo ya confirmado, regresar salta el codigo y seguir no pide otro`() = runTest {
        val vm = viewModel()
        vm.fillAccount()
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.EmailCode }
        vm.onEmailCodeChange("123456")
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.Name }

        assertTrue(vm.previous())
        vm.state.awaitUntil { it.step == RegisterStep.Account }

        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.Name }
        assertEquals("No se mando otro codigo", 2, server.requestCount)

        // Si cambia el correo, hay que confirmarlo de nuevo.
        vm.previous()
        vm.state.awaitUntil { it.step == RegisterStep.Account }
        vm.fillAccount("otra@gastapp.dev")
        assertFalse(vm.state.value.emailVerified)
        assertFalse("En el primer paso atras cierra la pantalla", vm.previous())
    }

    @Test
    fun `regresar al primer paso y seguir con el mismo correo no pide otro codigo`() = runTest {
        val vm = viewModel()
        vm.fillAccount()
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.EmailCode }

        // El codigo de hace un momento sigue sirviendo (y el API rechazaria otro antes de 60 s).
        assertTrue(vm.previous())
        vm.state.awaitUntil { it.step == RegisterStep.Account }
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.EmailCode }
        assertEquals(1, server.requestCount)

        // Con otro correo si se manda uno nuevo.
        assertTrue(vm.previous())
        vm.state.awaitUntil { it.step == RegisterStep.Account }
        vm.fillAccount("otra@gastapp.dev")
        respond("true")
        vm.next()
        vm.state.awaitUntil { it.step == RegisterStep.EmailCode && !it.busy }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retoma el borrador con el correo confirmado y vuelve a pedir la contrasena`() = runTest {
        draftStore.save(
            RegisterDraft(
                step = 4,
                email = "ana@gastapp.dev",
                emailVerified = true,
                name = "Ana",
                birthDate = "1995-06-15",
                incomeTypeId = IncomeTypes.MONTHLY,
                firstPayDay = 10,
                salaryText = "9000",
                percentSaveText = "5",
            ),
        )

        val vm = viewModel()
        val state = vm.state.awaitUntil { it.step == RegisterStep.Salary }

        assertEquals("Retomamos tu registro donde lo dejaste.", state.message)
        assertEquals("Ana", state.name)
        assertEquals(LocalDate.of(1995, 6, 15), state.birthDate)
        assertEquals(listOf(10), state.monthPayDays)
        assertTrue(state.askPasswordAgain)
        assertFalse("Falta la contrasena", state.canContinue)

        vm.onPasswordChange("Secreta#1")
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `un borrador sin correo confirmado empieza en el primer paso`() = runTest {
        draftStore.save(RegisterDraft(step = 3, email = "ana@gastapp.dev", emailVerified = false, name = "Ana"))

        val vm = viewModel()
        val state = vm.state.awaitUntil { it.email.isNotEmpty() }

        assertEquals(RegisterStep.Account, state.step)
        assertEquals("ana@gastapp.dev", state.confirmEmail)
        assertNull(state.message)
        assertFalse(state.askPasswordAgain)
    }

    @Test
    fun `la fecha de nacimiento respeta el rango de MAUI`() = runTest {
        val vm = viewModel()
        assertEquals(LocalDate.of(1900, 1, 1), vm.birthDateRange.start)
        assertEquals(LocalDate.of(2023, 12, 31), vm.birthDateRange.endInclusive)

        vm.onBirthDateChange(LocalDate.of(2025, 1, 1))
        assertNull(vm.state.value.birthDate)
        vm.onBirthDateChange(LocalDate.of(2023, 12, 31))
        assertNotNull(vm.state.value.birthDate)
    }

    @Test
    fun `semanal manda el dia de la semana y mensual el 15 si no se elige`() {
        val weekly = RegisterUiState(incomeTypeId = IncomeTypes.WEEKLY, weekPayDay = 5)
        assertEquals(5 to null, weekly.payDays())
        val monthly = RegisterUiState(incomeTypeId = IncomeTypes.MONTHLY)
        assertEquals(15 to null, monthly.payDays())
        val biweekly = RegisterUiState(incomeTypeId = IncomeTypes.BIWEEKLY, monthPayDays = listOf(20))
        assertEquals(20 to 30, biweekly.payDays())
    }
}
