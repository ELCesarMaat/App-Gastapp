package com.binc.gastapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
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
import com.binc.gastapp.ui.format.Strings
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
    fun reminderStatus(strings: Strings): String = when {
        !notificationsAllowed -> strings.get(R.string.reminders_notifications_off)
        !settings.remindersEnabled -> strings.get(R.string.reminders_off)
        else -> strings.plural(R.plurals.reminders_every, settings.reminderFrequencyHours)
    }

    fun frequencyLabel(strings: Strings): String = strings.plural(R.plurals.every_hours, settings.reminderFrequencyHours)

    /** RefreshCreditCardsSummary de MAUI. */
    fun cardsSummary(strings: Strings): String =
        if (cardCount == 0) strings.get(R.string.cards_summary_none) else strings.plural(R.plurals.cards_summary, cardCount)

    /** UpdateCloudSyncStatusAsync de MAUI, mas el caso de la sesion vencida. */
    fun cloud(strings: Strings): CloudStatus {
        val total = pending.total
        return when {
            session is SessionState.Expired -> CloudStatus(
                title = strings.get(R.string.cloud_not_synced),
                detail = strings.get(R.string.cloud_sign_in_again) +
                    if (total > 0) strings.plural(R.plurals.cloud_waiting_changes, total) else "",
                synced = false,
                needsLogin = true,
            )
            total == 0 -> CloudStatus(
                title = strings.get(R.string.cloud_all_synced),
                detail = lastSyncText?.let { strings.get(R.string.cloud_last_sync, it) }
                    ?: strings.get(R.string.cloud_nothing_pending),
                synced = true,
                needsLogin = false,
            )
            else -> CloudStatus(
                title = strings.get(R.string.cloud_pending),
                detail = strings.plural(R.plurals.cloud_pending_detail, total, total, pendingBreakdown(strings, pending)),
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
    private val strings: Strings,
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
                    is WearEvent.Linked -> link.update { it?.copy(busy = false, error = null, linkedName = event.deviceName.ifBlank { strings.get(R.string.your_watch) }) }
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
                    summary = if (result.value.isEmpty()) strings.get(R.string.devices_none)
                    else strings.plural(R.plurals.devices_count, result.value.size),
                    canManage = true,
                )
                DeviceResult.NoSession -> DevicesUiState(summary = strings.get(R.string.devices_sign_in))
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
                    onResult(strings.get(R.string.device_unlinked))
                    refreshDevices()
                }
                DeviceResult.NoSession -> onResult(strings.get(R.string.devices_sign_in))
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
                    link.update { it?.copy(busy = false, linkedName = result.value.deviceName.ifBlank { strings.get(R.string.your_watch) }) }
                    refreshDevices()
                }
                DeviceResult.NoSession -> link.update { it?.copy(busy = false, error = strings.get(R.string.device_link_sign_in)) }
                is DeviceResult.Failed -> link.update { it?.copy(busy = false, error = result.message) }
            }
        }
    }

    // ------------------------------------------------------------ ayudantes

    private fun toRow(device: DeviceDto): DeviceRow = DeviceRow(
        deviceId = device.deviceId,
        name = device.name.ifBlank { strings.get(R.string.watch) },
        detail = device.lastSeenAt?.let { strings.get(R.string.device_last_seen, instantText(it)) }
            ?: strings.get(R.string.device_linked_at, instantText(device.createdAt)),
    )

    private fun syncTimeText(instant: Instant): String {
        val zone = clock.zone
        val date = instant.atZone(zone).toLocalDate()
        val time = timeText(instant.atZone(zone).toLocalTime())
        return if (date == LocalDate.now(clock)) strings.get(R.string.sync_time_today, time) else strings.get(R.string.date_time, shortDate(date), time)
    }

    private fun instantText(instant: Instant): String {
        val local = instant.atZone(clock.zone)
        return strings.get(R.string.date_time, shortDate(local.toLocalDate(), LocalDate.now(clock)), timeText(local.toLocalTime()))
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
internal fun pendingBreakdown(strings: Strings, p: PendingCounts): String = buildList {
    if (p.userChanges > 0) add(strings.plural(R.plurals.pending_profile, p.userChanges))
    if (p.activeSpendings > 0) add(strings.plural(R.plurals.pending_spendings, p.activeSpendings))
    if (p.deletedSpendings > 0) add(strings.plural(R.plurals.pending_deleted_spendings, p.deletedSpendings))
    if (p.categories > 0) add(strings.plural(R.plurals.pending_categories, p.categories))
    if (p.deletedCategories > 0) add(strings.plural(R.plurals.pending_deleted_categories, p.deletedCategories))
    if (p.creditCards > 0) add(strings.plural(R.plurals.pending_cards, p.creditCards))
    if (p.subscriptions > 0) add(strings.plural(R.plurals.pending_subscriptions, p.subscriptions))
}.joinToString(", ")
