package com.binc.gastapp.ui.profile

import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.data.prefs.InMemoryDataStore
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Perfil con Room en memoria. El usuario base es quincenal (1 y 16), gana $10,000 y
 * ahorra 15.3846 %. El guardado automatico espera 700 ms de reloj virtual.
 */
class ProfileViewModelTest : DbTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private val settingsStore = SettingsStore(InMemoryDataStore())

    private suspend fun TestScope.loaded(): ProfileViewModel {
        seedBasics()
        val vm = ProfileViewModel(users, settingsStore, strings)
        vm.state.launchIn(backgroundScope)
        vm.state.awaitUntil { it.loaded }
        return vm
    }

    private suspend fun ProfileViewModel.awaitSaved() = state.awaitUntil { it.saveStatus == SaveStatus.Saved }

    @Test
    fun `carga la cuenta y el formulario como MAUI`() = runTest {
        val state = loaded().state.value

        assertEquals("Prueba", state.displayName(strings))
        assertEquals("P", state.initials)
        assertEquals("prueba@example.com", state.email)
        assertEquals(IncomeTypes.BIWEEKLY, state.incomeTypeId)
        assertEquals(listOf(1, 16), state.monthPayDays)
        assertEquals("10000", state.salaryText)
        assertEquals("15.3846", state.percentText)
        assertEquals("1538.46", state.amountText)
        assertTrue(state.byPercent)
        assertEquals("Tus pagos quincenales llegan los días 1 y 16.", state.scheduleSummary(strings))
        assertEquals("$10,000.00 por periodo", state.incomeSummary(strings))
        assertEquals("15.38% · $1,538.46 por periodo", state.goalSummary(strings))
        assertEquals(0, BigDecimal("8461.54").compareTo(state.estimatedSpendable))
        assertEquals(SaveStatus.Idle, state.saveStatus)
    }

    @Test
    fun `guarda solo una vez despues de varias ediciones seguidas y lo marca para subir`() = runTest {
        val vm = loaded()
        val before = scheduler.requests

        vm.onSalaryChange("1")
        vm.onSalaryChange("12")
        vm.onSalaryChange("12000")
        assertEquals(SaveStatus.Pending, vm.state.value.saveStatus)
        // Con porcentaje, el monto se recalcula para mostrarse: 12000 x 15.3846 % = 1846.152.
        assertEquals("1846.15", vm.state.value.amountText)
        vm.awaitSaved()

        val user = db.userDao().get()!!
        assertEquals(1_200_000L, user.salaryCents)
        assertEquals(0, BigDecimal("15.3846").compareTo(user.percentSave))
        assertFalse("El perfil editado se sube en SyncAllData", user.isSynced)
        assertEquals("Un solo guardado", before + 1, scheduler.requests)
    }

    @Test
    fun `no guarda mientras haya un error y dice cual`() = runTest {
        val vm = loaded()

        vm.onSalaryChange("")
        assertEquals(SaveStatus.Invalid("Ingresa un sueldo mayor a 0 para guardar automáticamente."), vm.state.value.saveStatus)

        vm.onSalaryChange("10000")
        assertEquals("Volver al valor guardado no guarda nada", SaveStatus.UpToDate, vm.state.value.saveStatus)

        vm.onPercentChange("120")
        assertEquals(SaveStatus.Invalid("El porcentaje de ahorro debe estar entre 0 y 99."), vm.state.value.saveStatus)
        assertEquals(1_000_000L, db.userDao().get()!!.salaryCents)
        assertTrue(db.userDao().get()!!.isSynced)
    }

    @Test
    fun `semanal pide el dia y lo guarda en FirstPayDay con domingo = 0`() = runTest {
        val vm = loaded()

        vm.onIncomeTypeChange(IncomeTypes.WEEKLY)
        assertEquals(SaveStatus.Invalid("Selecciona el día de tu pago semanal."), vm.state.value.saveStatus)
        assertEquals("Selecciona el día en que recibes tu pago semanal.", vm.state.value.scheduleSummary(strings))

        vm.onWeekPayDayChange(5)
        assertEquals("Recibes tu pago cada viernes.", vm.state.value.scheduleSummary(strings))
        vm.awaitSaved()

        val user = db.userDao().get()!!
        assertEquals(IncomeTypes.WEEKLY, user.incomeTypeId)
        assertEquals(5, user.firstPayDay)
        assertNull(user.secondPayDay)
    }

    @Test
    fun `quincenal necesita dos dias y mensual se queda con uno`() = runTest {
        val vm = loaded()

        vm.onMonthPayDayToggle(1)
        assertEquals(listOf(16), vm.state.value.monthPayDays)
        assertEquals(SaveStatus.Invalid("Selecciona tus dos días de pago quincenal."), vm.state.value.saveStatus)

        vm.onMonthPayDayToggle(30)
        vm.awaitSaved()
        db.userDao().get()!!.let {
            assertEquals(16, it.firstPayDay)
            assertEquals(30, it.secondPayDay)
        }

        vm.onIncomeTypeChange(IncomeTypes.MONTHLY)
        assertEquals(listOf(16), vm.state.value.monthPayDays)
        assertEquals("Tu pago mensual llega el día 16.", vm.state.value.scheduleSummary(strings))
        vm.state.awaitUntil { it.saveStatus == SaveStatus.Saved && it.incomeTypeId == IncomeTypes.MONTHLY }
        db.userDao().get()!!.let {
            assertEquals(IncomeTypes.MONTHLY, it.incomeTypeId)
            assertEquals(16, it.firstPayDay)
            assertNull(it.secondPayDay)
        }
    }

    @Test
    fun `con monto fijo el porcentaje sale del monto con 4 decimales`() = runTest {
        val vm = loaded()

        vm.onSavingsModeChange(false)
        assertFalse(vm.state.value.byPercent)
        assertEquals("1538.46", vm.state.value.amountText)
        assertFalse("El modo se recuerda en el telefono", settingsStore.settings.first().savingsModeIsPercent)

        vm.onAmountChange("1000")
        assertEquals("Equivale al 10% de tu sueldo", vm.state.value.computedPercentInfo(strings))
        vm.onSalaryChange("3000")
        // 1000 de 3000: 33.3333 %, y el monto tecleado no se mueve.
        assertEquals("1000", vm.state.value.amountText)
        assertEquals("33.3333", vm.state.value.percentText)
        vm.awaitSaved()
        assertEquals(0, BigDecimal("33.3333").compareTo(db.userDao().get()!!.percentSave))

        vm.onAmountChange("2980")
        assertEquals(
            SaveStatus.Invalid("La cantidad a ahorrar no puede superar el 99% de tu sueldo."),
            vm.state.value.saveStatus,
        )

        // De regreso a porcentaje se ve el mismo que se guardo.
        vm.onAmountChange("1000")
        vm.onSavingsModeChange(true)
        assertEquals("33.3333", vm.state.value.percentText)
    }

    @Test
    fun `un cambio que llega de fuera recarga el formulario si no se esta editando`() = runTest {
        val vm = loaded()

        db.userDao().upsert(db.userDao().get()!!.copy(salaryCents = 2_000_000, name = "Prueba Dos"))
        val state = vm.state.awaitUntil { it.salaryText == "20000" }
        assertEquals("Prueba Dos", state.displayName(strings))
        assertEquals("PD", state.initials)
    }
}
