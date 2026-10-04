package com.binc.gastapp.ui.settings

import com.binc.gastapp.data.local.PendingCounts
import com.binc.gastapp.data.prefs.InMemoryDataStore
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.prefs.ThemeMode
import com.binc.gastapp.data.repository.DeviceRepository
import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.notifications.AppNotifier
import com.binc.gastapp.sync.SyncTest
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.awaitUntil
import com.binc.gastapp.wear.FakeWearChannel
import com.binc.gastapp.wear.WearEvent
import com.binc.gastapp.wear.WearEvents
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

private class FakeNotifier(var enabled: Boolean = true) : AppNotifier {
    var sent = 0
    override fun areEnabled() = enabled
    override fun sendTest(): Boolean {
        if (!enabled) return false
        sent++
        return true
    }
    override fun showReminder(message: String) = enabled
    override fun showCardReminder(notificationId: Int, title: String, text: String) = enabled
    override fun showWatchExpense(text: String) = enabled
}

/** Ajustes con Room en memoria, DataStore en memoria y el API contra MockWebServer. */
class SettingsViewModelTest : SyncTest() {

    @get:Rule
    val main = MainDispatcherRule()

    private val settingsStore = SettingsStore(InMemoryDataStore())
    private val notifier = FakeNotifier()
    private val wearChannel = FakeWearChannel()
    private val wearEvents = WearEvents()

    private fun TestScope.viewModel(): SettingsViewModel {
        val vm = SettingsViewModel(
            settingsStore = settingsStore,
            sessions = sessions,
            engine = engine,
            scheduler = scheduler,
            cards = cards,
            devicesRepository = DeviceRepository(api, guard, wearChannel),
            notifier = notifier,
            wearEvents = wearEvents,
            clock = clock,
        )
        vm.state.launchIn(backgroundScope)
        return vm
    }

    @Test
    fun `recordatorios, frecuencia y tema se guardan en el telefono`() = runTest {
        seedBasics()
        val vm = viewModel()
        var state = vm.state.awaitUntil { it.loaded }
        assertEquals("Recibirás recordatorios aproximadamente cada 4 horas.", state.reminderStatus)
        assertEquals("Tienes 1 tarjeta registrada.", state.cardsSummary)

        vm.setReminderFrequency(12)
        state = vm.state.awaitUntil { it.settings.reminderFrequencyHours == 12 }
        assertEquals("Cada 12 horas", state.frequencyLabel)

        vm.setRemindersEnabled(false)
        state = vm.state.awaitUntil { !it.settings.remindersEnabled }
        assertEquals("Los recordatorios están apagados para esta app.", state.reminderStatus)
        assertEquals("La frecuencia se conserva", 12, settingsStore.settings.first().reminderFrequencyHours)

        vm.setThemeMode(ThemeMode.DARK)
        vm.setDynamicColor(true)
        settingsStore.settings.first().let {
            assertEquals(ThemeMode.DARK, it.themeMode)
            assertTrue(it.dynamicColor)
        }
    }

    @Test
    fun `sin permiso lo dice y la prueba no se manda`() = runTest {
        seedBasics()
        notifier.enabled = false
        val vm = viewModel()
        val state = vm.state.awaitUntil { it.loaded }
        assertFalse(state.notificationsAllowed)
        assertEquals("Las notificaciones están desactivadas en tu dispositivo.", state.reminderStatus)
        assertFalse(vm.sendTestNotification())

        // El usuario lo activo en los ajustes del sistema y volvio.
        notifier.enabled = true
        vm.refreshNotifications()
        vm.state.awaitUntil { it.notificationsAllowed }
        assertTrue(vm.sendTestNotification())
        assertEquals(1, notifier.sent)
    }

    @Test
    fun `estado de la nube con pendientes, al dia y con la sesion vencida`() = runTest {
        seedBasics()
        signedIn()
        respond(fixture("devices.json"))
        db.spendingDao().upsertAll(listOf(spending("nuevo", 10_000, at(today, 9), isSynced = false)))
        val vm = viewModel()

        var cloud = vm.state.awaitUntil { it.pending.total == 1 }.cloud
        assertEquals("Sincronización pendiente", cloud.title)
        assertEquals(
            "Faltan de sincronizar 1 elemento: 1 gasto nuevo o editado. Se suben solos cuando haya conexión.",
            cloud.detail,
        )
        assertFalse(cloud.needsLogin)

        val before = scheduler.requests
        vm.syncNow()
        assertEquals(before + 1, scheduler.requests)

        db.spendingDao().upsertAll(listOf(spending("nuevo", 10_000, at(today, 9), isSynced = true)))
        cloud = vm.state.awaitUntil { it.pending.total == 0 }.cloud
        assertEquals("Todo está en la nube", cloud.title)
        assertTrue(cloud.synced)

        sessionStore.clear()
        cloud = vm.state.awaitUntil { it.session is SessionState.Expired }.cloud
        assertEquals("Sin sincronizar", cloud.title)
        assertTrue(cloud.needsLogin)
    }

    @Test
    fun `lista los relojes y quita uno`() = runTest {
        seedBasics()
        signedIn()
        respond(fixture("devices.json"))
        val vm = viewModel()

        val devices = vm.state.awaitUntil { it.devices.devices.size == 2 }.devices
        assertTrue(devices.canManage)
        assertEquals("Tienes 2 dispositivos vinculados.", devices.summary)
        assertEquals(DeviceRow("dev-1", "Pixel Watch", "Última conexión: 2 oct, 3:00 a.m."), devices.devices[0].normalized())
        assertEquals("Vinculado: 5 ene, 4:00 a.m.", devices.devices[1].normalized().detail)
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))

        // Revocar: el API dice 404 (ya no existia), que cuenta como quitado; luego se recarga.
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(404).setBody("\"Dispositivo no encontrado.\""))
        respond("[]")
        var message: String? = null
        vm.revokeDevice("dev-1") { message = it }
        vm.state.awaitUntil { it.devices.devices.isEmpty() && !it.devices.loading }
        assertEquals("Dispositivo desvinculado.", message)
        assertEquals("Aún no tienes ningún reloj vinculado.", vm.state.value.devices.summary)
        val revoke = server.takeRequest()
        assertEquals("/api/Device/Revoke", revoke.path)
        assertTrue(revoke.body.readUtf8().contains("\"deviceId\":\"dev-1\""))
        assertEquals("Al reloj se le avisa por Bluetooth", listOf("dev-1"), wearChannel.revoked)
    }

    @Test
    fun `el reloj se vincula solo por Bluetooth y Ajustes se entera`() = runTest {
        seedBasics()
        signedIn()
        respond("[]")
        val vm = viewModel()
        vm.state.awaitUntil { it.devices.canManage && !it.devices.loading }

        vm.openLinkDialog()
        respond(fixture("devices.json"))
        wearEvents.emit(WearEvent.Linked("Pixel Watch 4"))
        wearEvents.emit(WearEvent.DevicesChanged)
        assertEquals("Pixel Watch 4", vm.state.awaitUntil { it.link?.linkedName != null }.link!!.linkedName)
        vm.state.awaitUntil { it.devices.devices.size == 2 }

        // Sin el dialogo abierto no aparece de la nada.
        vm.dismissLinkDialog()
        wearEvents.emit(WearEvent.Linked("Otro"))
        assertNull(vm.state.value.link)
    }

    @Test
    fun `sin sesion vigente no se llama al API de dispositivos`() = runTest {
        seedBasics()
        val vm = viewModel()
        val devices = vm.state.awaitUntil { it.loaded && !it.devices.loading }.devices
        assertFalse(devices.canManage)
        assertEquals("Inicia sesión para administrar tus dispositivos.", devices.summary)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `vincular con el codigo del reloj`() = runTest {
        seedBasics()
        signedIn()
        respond("[]")
        val vm = viewModel()
        vm.state.awaitUntil { it.devices.canManage }
        server.takeRequest()

        vm.openLinkDialog()
        // Incompleto: ni se llama al API.
        vm.confirmLink("k7m 2q")
        assertFalse(vm.state.value.link!!.busy)
        assertEquals(1, server.requestCount)

        // Codigo vencido: 400 con el texto del API.
        respond("\"Codigo no valido o expirado.\"", code = 400)
        vm.confirmLink("k7m2qx")
        var link = vm.state.awaitUntil { it.link?.error != null }.link!!
        assertEquals("El código no es válido o ya expiró. Revisa el que muestra tu reloj.", link.error)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"userCode\":\"K7M-2QX\""))
        vm.clearLinkError()
        assertNull(vm.state.value.link!!.error)

        respond("""{"deviceName":"Pixel Watch 4","platform":"wearos"}""")
        respond(fixture("devices.json"))
        vm.confirmLink("K7M-2QX")
        link = vm.state.awaitUntil { it.link?.linkedName != null }.link!!
        assertEquals("Pixel Watch 4", link.linkedName)
        vm.state.awaitUntil { it.devices.devices.size == 2 }

        vm.dismissLinkDialog()
        assertNull(vm.state.value.link)
    }

    @Test
    fun `codigo y desglose de pendientes`() {
        assertEquals("ABC-123", formatLinkCode("abc-123"))
        assertTrue(isCompleteLinkCode("abc 12-3"))
        assertFalse(isCompleteLinkCode("abc-12"))
        assertEquals("AB", formatLinkCode("a-b"))
        assertEquals("ABC-DEF", formatLinkCode("abcdefgh"))
        assertEquals(
            "1 cambio de perfil, 2 gastos eliminados, 1 suscripción",
            pendingBreakdown(PendingCounts(userChanges = 1, deletedSpendings = 2, subscriptions = 1)),
        )
    }
}

/** El formato de hora de Java usa espacios finos o raros segun la version: se comparan normales. */
private fun DeviceRow.normalized() = copy(detail = detail.replace(' ', ' ').replace(' ', ' ').replace(". m.", ".m."))
