package com.binc.gastapp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.data.prefs.ThemeMode
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.ListGroup
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.isLargeFontScale
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.theme.LocalStatusColors

/** Lo que Ajustes le pide a la pantalla principal y a la sesion. */
class SettingsActions(
    val onOpenCards: () -> Unit,
    /** Con la sesion vencida: abrir el inicio de sesion. */
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
)

private val ThemeLabels = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro")

/**
 * Ajustes (SettingsPage de MAUI con el estilo de AjustesScreen del demo): notificaciones,
 * apariencia, la nube y las tarjetas, relojes vinculados y cerrar sesion.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    actions: SettingsActions,
    listState: LazyListState = rememberLazyListState(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val context = LocalContext.current
    val opening = rememberJustOpened()

    var askOpenSettings by remember { mutableStateOf(false) }
    var choosingFrequency by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<DeviceRow?>(null) }
    // Lo que se queria hacer cuando se pidio el permiso (p. ej. mandar la prueba).
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    // El usuario pudo cambiar el permiso en los ajustes del sistema.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotifications()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshNotifications()
        val pending = afterPermission
        afterPermission = null
        if (granted) pending?.invoke() else askOpenSettings = true
    }

    /** EnableSystemNotifications de MAUI: pide el permiso o manda a los ajustes del sistema. */
    fun withNotifications(action: () -> Unit) {
        viewModel.refreshNotifications()
        when {
            // Se pregunta al sistema: el estado de la pantalla puede ir un paso atras.
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> action()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context) -> {
                afterPermission = action
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> askOpenSettings = true
        }
    }

    if (!state.loaded) return

    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withExtra(top = 8.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "notificaciones-titulo") { SectionHeader("Notificaciones", modifier = Modifier.appear(0, opening)) }
        item(key = "notificaciones") {
            Rows(Modifier.appear(1, opening)) {
                add {
                    SwitchRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Recordatorios de gasto",
                        detail = state.reminderStatus,
                        checked = state.settings.remindersEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setRemindersEnabled(enabled)
                            if (enabled && !state.notificationsAllowed) withNotifications { }
                        },
                    )
                }
                add {
                    OptionRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Frecuencia",
                        detail = state.frequencyLabel,
                        enabled = state.settings.remindersEnabled,
                        onClick = { choosingFrequency = true },
                    )
                }
                if (!state.notificationsAllowed) {
                    add {
                        OptionRow(
                            icon = Icons.Rounded.NotificationsOff,
                            title = "Notificaciones del sistema",
                            detail = "Están desactivadas para Gastapp.",
                            action = {
                                FilledTonalButton(onClick = { withNotifications { messages.show("Notificaciones activadas.") } }) {
                                    Text("Activar")
                                }
                            },
                        )
                    }
                }
                add {
                    OptionRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notificación de prueba",
                        detail = "Comprueba que te llegan los avisos.",
                        onClick = {
                            withNotifications {
                                if (viewModel.sendTestNotification()) {
                                    messages.show("Notificación de prueba enviada.")
                                } else {
                                    messages.show("No se pudo enviar la notificación. Revisa permisos de notificación.")
                                }
                            }
                        },
                    )
                }
            }
        }

        item(key = "apariencia-titulo") { SectionHeader("Apariencia", modifier = Modifier.appear(2, opening)) }
        item(key = "apariencia") {
            Rows(Modifier.appear(3, opening)) {
                add { ThemeRow(state.settings.themeMode, viewModel::setThemeMode) }
                add {
                    SwitchRow(
                        icon = Icons.Rounded.Palette,
                        title = "Colores del fondo de pantalla",
                        detail = "Usa la paleta de tu teléfono (Material You).",
                        checked = state.settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }
        }

        item(key = "datos-titulo") { SectionHeader("Tus datos", modifier = Modifier.appear(4, opening)) }
        item(key = "datos") {
            Rows(Modifier.appear(5, opening)) {
                add { CloudRow(state, onSync = viewModel::syncNow, onLogin = actions.onLogin) }
                add {
                    OptionRow(
                        icon = Icons.Rounded.CreditCard,
                        title = "Mis tarjetas de crédito",
                        detail = state.cardsSummary,
                        onClick = actions.onOpenCards,
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
        }

        item(key = "reloj-titulo") {
            SectionHeader(
                "Reloj",
                subtitle = state.devices.summary.ifEmpty { null },
                modifier = Modifier.appear(6, opening),
                action = {
                    if (state.devices.loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refreshDevices) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Recargar dispositivos")
                        }
                    }
                },
            )
        }
        item(key = "reloj") {
            Rows(Modifier.appear(7, opening)) {
                state.devices.devices.forEach { device ->
                    add {
                        OptionRow(
                            icon = Icons.Rounded.Watch,
                            title = device.name,
                            detail = device.detail,
                            action = if (state.devices.canManage) {
                                { TextButton(onClick = { revoking = device }) { Text("Quitar") } }
                            } else {
                                null
                            },
                        )
                    }
                }
                add {
                    OptionRow(
                        icon = Icons.Rounded.AddLink,
                        title = "Vincular reloj",
                        detail = "Registra gastos por voz sin sacar el teléfono.",
                        enabled = state.devices.canManage,
                        onClick = viewModel::openLinkDialog,
                    )
                }
            }
        }

        item(key = "cuenta-titulo") { SectionHeader("Cuenta", modifier = Modifier.appear(8, opening)) }
        item(key = "cuenta") {
            Rows(Modifier.appear(9, opening)) {
                add {
                    OptionRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Cerrar sesión",
                        detail = "Borra la sesión y los datos de este teléfono.",
                        destructive = true,
                        onClick = { confirmLogout = true },
                    )
                }
                add {
                    OptionRow(
                        icon = Icons.Rounded.Info,
                        title = "Gastapp",
                        detail = "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                }
            }
        }
    }

    if (choosingFrequency) {
        FrequencyDialog(
            selected = state.settings.reminderFrequencyHours,
            onSelect = { hours ->
                choosingFrequency = false
                viewModel.setReminderFrequency(hours)
            },
            onDismiss = { choosingFrequency = false },
        )
    }

    if (askOpenSettings) {
        AlertDialog(
            onDismissRequest = { askOpenSettings = false },
            icon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
            title = { Text("Notificaciones desactivadas") },
            text = { Text("El permiso está denegado o desactivado. ¿Quieres abrir los ajustes de la app para habilitar notificaciones?") },
            confirmButton = {
                TextButton(onClick = {
                    askOpenSettings = false
                    openNotificationSettings(context)
                }) { Text("Abrir ajustes") }
            },
            dismissButton = { TextButton(onClick = { askOpenSettings = false }) { Text("Cancelar") } },
        )
    }

    revoking?.let { device ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            icon = { Icon(Icons.Rounded.Watch, contentDescription = null) },
            title = { Text("Quitar dispositivo") },
            text = { Text("${device.name} dejará de poder registrar gastos. Para volver a usarlo tendrás que vincularlo de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    viewModel.revokeDevice(device.deviceId) { messages.show(it) }
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancelar") } },
        )
    }

    if (confirmLogout) {
        val pending = state.pending.total
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Se borrarán de este teléfono tus datos y la sesión. Lo que ya está en la nube vuelve al iniciar sesión." +
                        if (pending > 0) {
                            "\n\nOjo: hay $pending ${if (pending == 1) "cambio que todavía no se sube y se perderá" else "cambios que todavía no se suben y se perderán"}."
                        } else {
                            ""
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    actions.onLogout()
                }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }

    state.link?.let { link ->
        LinkWatchDialog(
            state = link,
            onCodeEdited = viewModel::clearLinkError,
            onConfirm = viewModel::confirmLink,
            onDismiss = viewModel::dismissLinkDialog,
        )
    }
}

// ---------------------------------------------------------------- filas

/** Filas de un grupo, para armarlas con condiciones. */
private class RowsScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    fun add(row: @Composable () -> Unit) {
        rows += row
    }
}

@Composable
private fun Rows(modifier: Modifier = Modifier, build: RowsScope.() -> Unit) {
    val rows = RowsScope().apply(build).rows
    ListGroup(rows, modifier) { it() }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Un boton: a la derecha, o debajo del texto con la fuente muy grande. */
    action: (@Composable () -> Unit)? = null,
) {
    val stacked = action != null && isLargeFontScale
    val titleColor = when {
        destructive -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        colors = TransparentListItemColors,
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
        leadingContent = {
            if (destructive) {
                TonalIcon(icon, containerColor = MaterialTheme.colorScheme.errorContainer, color = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                TonalIcon(icon)
            }
        },
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = if (detail != null || stacked) {
            {
                Column {
                    detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (stacked) action?.invoke()
                }
            }
        } else {
            null
        },
        trailingContent = if (stacked) null else action ?: trailing,
    )
}

/** Toda la fila es el interruptor: TalkBack la lee como uno. */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = TransparentListItemColors,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        leadingContent = { TonalIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    ListItem(
        colors = TransparentListItemColors,
        leadingContent = { TonalIcon(Icons.Rounded.DarkMode) },
        headlineContent = { Text("Tema") },
        supportingContent = {
            Column {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeLabels.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = mode == selected,
                            onClick = { onSelect(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeLabels.size),
                        ) { FitText(label) }
                    }
                }
            }
        },
    )
}

@Composable
private fun CloudRow(state: SettingsUiState, onSync: () -> Unit, onLogin: () -> Unit) {
    val cloud = state.cloud
    // Con la fuente muy grande el boton de iniciar sesion va debajo del texto.
    val loginBelow = cloud.needsLogin && !state.sync.running && isLargeFontScale
    val status = LocalStatusColors.current
    val color = when {
        cloud.needsLogin -> status.critical
        cloud.synced -> status.ok
        else -> status.warning
    }
    ListItem(
        colors = TransparentListItemColors,
        leadingContent = {
            TonalIcon(
                when {
                    cloud.needsLogin -> Icons.Rounded.CloudOff
                    cloud.synced -> Icons.Rounded.CloudDone
                    else -> Icons.Rounded.CloudUpload
                },
                containerColor = color.container,
                color = color.onContainer,
            )
        },
        headlineContent = { Text(if (state.sync.running) "Sincronizando…" else cloud.title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cloud.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val error = state.sync.lastError
                if (error != null && !cloud.synced && !state.sync.running) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (loginBelow) TextButton(onClick = onLogin) { Text("Iniciar sesión") }
            }
        },
        trailingContent = {
            when {
                state.sync.running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                loginBelow -> Unit
                cloud.needsLogin -> TextButton(onClick = onLogin) { Text("Iniciar sesión") }
                else -> IconButton(onClick = onSync) { Icon(Icons.Rounded.Sync, contentDescription = "Sincronizar ahora") }
            }
        },
    )
}

// ---------------------------------------------------------------- dialogos

@Composable
private fun FrequencyDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        title = { Text("Frecuencia de recordatorios") },
        text = {
            Column(Modifier.selectableGroup()) {
                ReminderFrequencies.forEach { hours ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(selected = hours == selected, role = Role.RadioButton, onClick = { onSelect(hours) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = hours == selected, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text("Cada $hours horas", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/**
 * Vincular un reloj (LinkDevicePopup de MAUI). El reloj muestra un codigo y aqui se
 * teclea; en la Fase 5.4 el reloj tambien lo mandara solo por Bluetooth.
 */
@Composable
private fun LinkWatchDialog(
    state: LinkDialogState,
    onCodeEdited: () -> Unit,
    onConfirm: (code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Se guarda tal cual lo manda el teclado; las mayusculas y el guion solo se pintan
    // (LinkCodeTransformation). Cambiar el texto mientras el teclado "compone" la palabra
    // los desfasa y se pierden letras.
    var code by remember { mutableStateOf(TextFieldValue()) }
    val canSubmit = !state.busy && isCompleteLinkCode(code.text)
    val linkedName = state.linkedName
    if (linkedName != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalStatusColors.current.ok.strong) },
            title = { Text("¡Reloj vinculado!") },
            text = { Text("$linkedName ya puede registrar gastos.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Aceptar") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Watch, contentDescription = null) },
        title = { Text("Vincular reloj") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Abre Gastapp en tu reloj y escribe el código que aparece en su pantalla.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { typed ->
                        // Solo A-Z y 0-9: asi lo que se pinta mide lo mismo (mas el guion) que lo tecleado.
                        if (typed.text.length <= LinkCodeLength && typed.text.all { it.isAsciiLetterOrDigit() }) {
                            if (typed.text != code.text) onCodeEdited()
                            code = typed
                        }
                    },
                    visualTransformation = LinkCodeTransformation,
                    label = { Text("Código") },
                    placeholder = { Text("K7M-2QX") },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it) } },
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onConfirm(code.text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code.text) }, enabled = canSubmit) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Vincular")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancelar") } },
    )
}

private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

/** "k7m2qx" se ve "K7M-2QX": mayusculas y un guion despues del tercero, sin tocar el texto. */
private object LinkCodeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = formatLinkCode(raw)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 3 || raw.length <= 3) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int =
                (if (offset <= 3 || raw.length <= 3) offset else offset - 1).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

// ---------------------------------------------------------------- sistema

/** En Android 12 no existe el permiso: basta el interruptor de la app. */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** OpenAppNotificationSettingsAsync de MAUI. */
private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
