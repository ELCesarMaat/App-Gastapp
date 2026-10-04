package com.binc.gastapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.core.remote.DeviceDto
import com.binc.gastapp.data.local.PendingCounts
import com.binc.gastapp.data.prefs.AppSettings
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.prefs.ThemeMode
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.DeviceRepository
import com.binc.gastapp.data.repository.DeviceResult
import com.binc.gastapp.data.session.SessionRepository
import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.notifications.AppNotifier
import com.binc.gastapp.sync.SyncEngine
import com.binc.gastapp.sync.SyncRunState
import com.binc.gastapp.sync.SyncScheduler
import com.binc.gastapp.ui.format.shortDate
import com.binc.gastapp.ui.format.timeText
import com.binc.gastapp.wear.WearEvent
import com.binc.gastapp.wear.WearEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Frecuencias de los recordatorios, en horas (InitReminderFrequencies de MAUI). */
val ReminderFrequencies = listOf(2, 4, 6, 8, 12, 24)

/** Un reloj vinculado, listo para pintarse. */
data class DeviceRow(val deviceId: String, val name: String, val detail: String)

/** La seccion de dispositivos: lo unico de Ajustes que sale a la red. */
data class DevicesUiState(
    val loading: Boolean = false,
    val devices: List<DeviceRow> = emptyList(),
    /** DevicesSummaryText de MAUI: cuantos hay, o por que no se pudo saber. */
    val summary: String = "",
    /** Hay sesion vigente: se puede vincular y quitar. */
    val canManage: Boolean = false,
)

/**
 * Dialogo "Vincular reloj". El codigo tecleado vive en el propio dialogo: un campo de
 * texto necesita su estado al instante, y por aqui llegaria un cuadro tarde.
 */
data class LinkDialogState(
    val busy: Boolean = false,
    val error: String? = null,
    /** Nombre del reloj recien vinculado; con el, el dialogo muestra el exito. */
    val linkedName: String? = null,
)

/** Como esta la nube, para la fila de "Tus datos". */
data class CloudStatus(val title: String, val detail: String, val synced: Boolean, val needsLogin: Boolean)

data class SettingsUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    /** Permiso del sistema (Android 13+) y el interruptor de la app. */
    val notificationsAllowed: Boolean = true,
    val session: SessionState? = null,
    val pending: PendingCounts = PendingCounts(),
    val sync: SyncRunState = SyncRunState(),
    /** "hoy, 3:45 p. m." */
    val lastSyncText: String? = null,
    val cardCount: Int = 0,
    val devices: DevicesUiState = DevicesUiState(),
    val link: LinkDialogState? = null,
) {
    /** RefreshNotificationPermissionState de MAUI. */
    val reminderStatus: String
        get() = when {
            !notificationsAllowed -> "Las notificaciones están desactivadas en tu dispositivo."
            !settings.remindersEnabled -> "Los recordatorios están apagados para esta app."
            else -> "Recibirás recordatorios aproximadamente cada ${settings.reminderFrequencyHours} horas."
        }

    val frequencyLabel: String get() = "Cada ${settings.reminderFrequencyHours} horas"

    /** RefreshCreditCardsSummary de MAUI. */
    val cardsSummary: String
        get() = when (cardCount) {
            0 -> "Aún no tienes tarjetas registradas."
            1 -> "Tienes 1 tarjeta registrada."
            else -> "Tienes $cardCount tarjetas registradas."
        }

    /** UpdateCloudSyncStatusAsync de MAUI, mas el caso de la sesion vencida. */
    val cloud: CloudStatus
        get() {
            val total = pending.total
            return when {
                session is SessionState.Expired -> CloudStatus(
                    title = "Sin sincronizar",
                    detail = "Inicia sesión de nuevo para volver a sincronizar." +
                        if (total > 0) " Hay ${plural(total, "cambio", "cambios")} en este teléfono esperando." else "",
                    synced = false,
                    needsLogin = true,
                )
                total == 0 -> CloudStatus(
                    title = "Todo está en la nube",
                    detail = lastSyncText?.let { "Última sincronización: $it" }
                        ?: "No hay cambios pendientes por subir desde este dispositivo.",
                    synced = true,
                    needsLogin = false,
                )
                else -> CloudStatus(
                    title = "Sincronización pendiente",
                    detail = "Faltan de sincronizar ${plural(total, "elemento", "elementos")}: ${pendingBreakdown(pending)}. " +
                        "Se suben solos cuando haya conexión.",
                    synced = false,
                    needsLogin = false,
                )
            }
        }
}

/**
 * Ajustes (SettingsViewModel de MAUI). Recordatorios, apariencia, estado de la nube,
 * tarjetas y relojes vinculados. Cerrar e iniciar sesion los hace el SessionViewModel
 * de la actividad; los respaldos, BackupViewModel.
 *
 * Los recordatorios solo se guardan aqui: StartupCoordinator observa SettingsStore y los
 * programa o los quita con WorkManager.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    sessions: SessionRepository,
    engine: SyncEngine,
    private val scheduler: SyncScheduler,
    cards: CreditCardRepository,
    private val devicesRepository: DeviceRepository,
    private val notifier: AppNotifier,
    wearEvents: WearEvents,
    private val clock: Clock,
) : ViewModel() {

    private val notificationsAllowed = MutableStateFlow(notifier.areEnabled())
    private val devices = MutableStateFlow(DevicesUiState(loading = true))
    private val link = MutableStateFlow<LinkDialogState?>(null)

    private val cloud = combine(sessions.state, engine.observePending(), engine.runState) { session, pending, run ->
        Triple(session, pending, run)
    }
    private val local = combine(settingsStore.settings, notificationsAllowed, cards.observeCards().map { it.size }) { settings, allowed, count ->
        Triple(settings, allowed, count)
    }

    val state: StateFlow<SettingsUiState> = combine(cloud, local, devices, link) { (session, pending, run), (settings, allowed, count), devices, link ->
        SettingsUiState(
            loaded = true,
            settings = settings,
            notificationsAllowed = allowed,
            session = session,
            pending = pending,
            sync = run,
            lastSyncText = run.lastSuccessAt?.let(::syncTimeText),
            cardCount = count,
            devices = devices,
            link = link,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        // Al abrir y cada vez que se recupera la sesion (al volver a iniciarla).
        viewModelScope.launch {
            sessions.state.map { it is SessionState.Active }.distinctUntilChanged().collect { refreshDevices() }
        }
        // Lo que pasa del lado del reloj sin tocar esta pantalla: se vinculo solo por
        // Bluetooth (si el dialogo esta abierto, muestra el exito) o se desvinculo.
        viewModelScope.launch {
            wearEvents.events.collect { event ->
                when (event) {
                    WearEvent.DevicesChanged -> refreshDevices()
                    is WearEvent.Linked -> link.update { it?.copy(busy = false, error = null, linkedName = event.deviceName.ifBlank { "Tu reloj" }) }
                }
            }
        }
    }

    // ------------------------------------------------------------ notificaciones

    /** Al volver a la pantalla: el usuario pudo cambiar el permiso en los ajustes del sistema. */
    fun refreshNotifications() {
        notificationsAllowed.value = notifier.areEnabled()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            settingsStore.setReminders(enabled, current.reminderFrequencyHours)
        }
    }

    fun setReminderFrequency(hours: Int) {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            settingsStore.setReminders(current.remindersEnabled, hours)
        }
    }

    /** true si se mando; false si las notificaciones estan apagadas. */
    fun sendTestNotification(): Boolean = notifier.sendTest().also { refreshNotifications() }

    // ------------------------------------------------------------ apariencia

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    // ------------------------------------------------------------ nube

    /** Lo mismo que al abrir la app: refrescar, subir y bajar (sin red, espera a que haya). */
    fun syncNow() = scheduler.requestFullSync()

    // ------------------------------------------------------------ dispositivos

    fun refreshDevices() {
        viewModelScope.launch {
            devices.update { it.copy(loading = true) }
            devices.value = when (val result = devicesRepository.list()) {
                is DeviceResult.Ok -> DevicesUiState(
                    devices = result.value.map(::toRow),
                    summary = when (result.value.size) {
                        0 -> "Aún no tienes ningún reloj vinculado."
                        1 -> "Tienes 1 dispositivo vinculado."
                        else -> "Tienes ${result.value.size} dispositivos vinculados."
                    },
                    canManage = true,
                )
                DeviceResult.NoSession -> DevicesUiState(summary = "Inicia sesión para administrar tus dispositivos.")
                // Se queda la lista anterior: sin conexion no se sabe si cambio.
                is DeviceResult.Failed -> devices.value.copy(loading = false, summary = result.message)
            }
        }
    }

    /** RevokeDevice de MAUI, despues de confirmar. [onResult] recibe el aviso para el usuario. */
    fun revokeDevice(deviceId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = devicesRepository.revoke(deviceId)) {
                is DeviceResult.Ok -> {
                    devices.update { state -> state.copy(devices = state.devices.filterNot { it.deviceId == deviceId }) }
                    onResult("Dispositivo desvinculado.")
                    refreshDevices()
                }
                DeviceResult.NoSession -> onResult("Inicia sesión para administrar tus dispositivos.")
                is DeviceResult.Failed -> onResult(result.message)
            }
        }
    }

    fun openLinkDialog() {
        link.value = LinkDialogState()
    }

    fun dismissLinkDialog() {
        link.value = null
    }

    /** Al corregir el codigo se quita el error anterior. */
    fun clearLinkError() {
        link.update { it?.copy(error = null) }
    }

    /**
     * Vincula con el [code] tecleado. Normalmente no hace falta: el reloj manda su codigo
     * por Bluetooth y PhoneWearListenerService lo vincula solo (llega como WearEvent.Linked).
     */
    fun confirmLink(code: String) {
        val current = link.value ?: return
        if (current.busy || !isCompleteLinkCode(code)) return
        link.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = devicesRepository.link(formatLinkCode(code))) {
                is DeviceResult.Ok -> {
                    link.update { it?.copy(busy = false, linkedName = result.value.deviceName.ifBlank { "Tu reloj" }) }
                    refreshDevices()
                }
                DeviceResult.NoSession -> link.update { it?.copy(busy = false, error = "Inicia sesión para vincular un reloj.") }
                is DeviceResult.Failed -> link.update { it?.copy(busy = false, error = result.message) }
            }
        }
    }

    // ------------------------------------------------------------ ayudantes

    private fun toRow(device: DeviceDto): DeviceRow = DeviceRow(
        deviceId = device.deviceId,
        name = device.name.ifBlank { "Reloj" },
        detail = device.lastSeenAt?.let { "Última conexión: ${instantText(it)}" }
            ?: "Vinculado: ${instantText(device.createdAt)}",
    )

    private fun syncTimeText(instant: Instant): String {
        val zone = clock.zone
        val date = instant.atZone(zone).toLocalDate()
        val time = timeText(instant.atZone(zone).toLocalTime())
        return if (date == LocalDate.now(clock)) "hoy, $time" else "${shortDate(date)}, $time"
    }

    private fun instantText(instant: Instant): String {
        val local = instant.atZone(clock.zone)
        return "${shortDate(local.toLocalDate(), LocalDate.now(clock))}, ${timeText(local.toLocalTime())}"
    }
}

/** Letras y numeros del codigo del reloj (UserCodeLength del API). */
internal const val LinkCodeLength = 6

/** El codigo ya tiene sus 6 letras o numeros. */
internal fun isCompleteLinkCode(code: String): Boolean = formatLinkCode(code).count { it != '-' } == LinkCodeLength

/** "k7m2q" -> "K7M-2Q": letras y numeros en mayusculas, guion despues del tercero (FormatUserCode del API). */
internal fun formatLinkCode(text: String): String {
    val clean = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(LinkCodeLength)
    return if (clean.length > 3) clean.substring(0, 3) + "-" + clean.substring(3) else clean
}

/** BuildSyncBreakdown de MAUI. */
internal fun pendingBreakdown(p: PendingCounts): String = buildList {
    if (p.userChanges > 0) add(plural(p.userChanges, "cambio de perfil", "cambios de perfil"))
    if (p.activeSpendings > 0) add(plural(p.activeSpendings, "gasto nuevo o editado", "gastos nuevos o editados"))
    if (p.deletedSpendings > 0) add(plural(p.deletedSpendings, "gasto eliminado", "gastos eliminados"))
    if (p.categories > 0) add(plural(p.categories, "categoría", "categorías"))
    if (p.deletedCategories > 0) add(plural(p.deletedCategories, "categoría eliminada", "categorías eliminadas"))
    if (p.creditCards > 0) add(plural(p.creditCards, "tarjeta de crédito", "tarjetas de crédito"))
    if (p.subscriptions > 0) add(plural(p.subscriptions, "suscripción", "suscripciones"))
}.joinToString(", ")

private fun plural(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
