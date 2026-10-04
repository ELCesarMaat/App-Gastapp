package com.binc.gastapp.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.domain.subscriptions.SubscriptionBadge
import com.binc.gastapp.domain.subscriptions.SubscriptionSummary
import com.binc.gastapp.domain.subscriptions.UpcomingCharge
import com.binc.gastapp.ui.components.AmountContextRow
import com.binc.gastapp.ui.components.AmountOption
import com.binc.gastapp.ui.components.AmountSheet
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.AppSnackbarHost
import com.binc.gastapp.ui.components.GroupedRow
import com.binc.gastapp.ui.components.InitialAvatar
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.StatusChip
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.dayMonth
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.monthShort
import com.binc.gastapp.ui.format.shortDate
import com.binc.gastapp.ui.theme.amountMedium
import com.binc.gastapp.ui.theme.parseColorHex

private sealed interface SubscriptionOverlay {
    data class Detail(val id: String) : SubscriptionOverlay
    data class ConfirmCharge(val summary: SubscriptionSummary) : SubscriptionOverlay
    data class Charge(val summary: SubscriptionSummary) : SubscriptionOverlay
    data class ConfirmDelete(val summary: SubscriptionSummary) : SubscriptionOverlay
}

/**
 * Suscripciones (SubscriptionsPage de MAUI con el diseno de SuscripcionesScreen del
 * demo). Tocar una abre su detalle con las acciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var overlay by remember { mutableStateOf<SubscriptionOverlay?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Suscripciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar") }
                },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Agregar suscripción") } },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize())
            !state.hasSubscriptions -> EmptySubscriptions(onAdd, Modifier.padding(padding))
            else -> SubscriptionsList(state, padding, onSelect = { overlay = SubscriptionOverlay.Detail(it.subscription.subscriptionId) })
        }
    }

    when (val current = overlay) {
        is SubscriptionOverlay.Detail -> {
            val summary = state.summaries.firstOrNull { it.subscription.subscriptionId == current.id }
            if (summary == null) {
                // Se borro mientras estaba abierta.
                LaunchedEffect(current) { overlay = null }
            } else {
                SubscriptionDetailSheet(
                    summary = summary,
                    today = state.today,
                    onDismiss = { overlay = null },
                    onRegisterCharge = {
                        // Se avisa si ya se registro el cobro del periodo, sin bloquear (regla 6).
                        overlay = if (summary.isCurrentCycleCharged) SubscriptionOverlay.ConfirmCharge(summary)
                        else SubscriptionOverlay.Charge(summary)
                    },
                    onToggle = { viewModel.toggleActive(summary) { messages.show(it) } },
                    onEdit = {
                        overlay = null
                        onEdit(summary.subscription.subscriptionId)
                    },
                    onDelete = { overlay = SubscriptionOverlay.ConfirmDelete(summary) },
                )
            }
        }
        is SubscriptionOverlay.ConfirmCharge -> AlertDialog(
            onDismissRequest = { overlay = null },
            title = { Text("Este cobro ya está registrado") },
            text = { Text("${current.summary.lastChargeText(::dayMonth)} Si registras otro, se sumará como un gasto aparte.") },
            confirmButton = { TextButton(onClick = { overlay = SubscriptionOverlay.Charge(current.summary) }) { Text("Registrar otro") } },
            dismissButton = { TextButton(onClick = { overlay = null }) { Text("Cancelar") } },
        )
        is SubscriptionOverlay.Charge -> {
            val summary = current.summary
            val subscription = summary.subscription
            AmountSheet(
                title = "Registrar cobro",
                subtitle = listOfNotNull(subscription.serviceName, subscription.planName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                fieldLabel = "Monto cobrado",
                initialAmount = subscription.amount,
                confirmLabel = "Registrar cobro",
                onConfirm = { amount ->
                    overlay = null
                    viewModel.registerCharge(summary, amount) { messages.show("Cobro de ${formatMoney(amount)} registrado.") }
                },
                onDismiss = { overlay = null },
                contextRows = buildList {
                    add(AmountContextRow("Costo del periodo", formatMoney(subscription.amount), highlighted = true))
                    add(AmountContextRow("Periodicidad", summary.billingCycleText))
                    add(AmountContextRow("Próximo cobro", shortDate(summary.nextChargeDate, state.today)))
                    if (summary.hasLinkedCard) add(AmountContextRow("Se carga a", summary.linkedCardName))
                },
                options = listOf(AmountOption("Costo del periodo", subscription.amount)),
            )
        }
        is SubscriptionOverlay.ConfirmDelete -> AlertDialog(
            onDismissRequest = { overlay = null },
            title = { Text("Eliminar suscripción") },
            text = {
                Text("¿Seguro que deseas eliminar '${current.summary.subscription.serviceName}'?\nLos gastos que ya registraste se conservarán.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        overlay = null
                        viewModel.delete(current.summary) { messages.show("Suscripción eliminada.") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { overlay = null }) { Text("Cancelar") } },
        )
        null -> Unit
    }
}

@Composable
private fun EmptySubscriptions(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TonalIcon(Icons.Rounded.Subscriptions, size = 72.dp, modifier = Modifier.appear(0))
        Spacer(Modifier.height(16.dp))
        Text("Aún no registras suscripciones", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.appear(1))
        Spacer(Modifier.height(8.dp))
        Text(
            "Registra tus servicios de cobro recurrente (streaming, nube, gimnasio, membresías) para ver cuánto te " +
                "cuestan al mes y al año, y no perder de vista los cobros que vienen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(2),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, modifier = Modifier.appear(3)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Agregar suscripción")
        }
    }
}

@Composable
private fun SubscriptionsList(
    state: SubscriptionsUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onSelect: (SubscriptionSummary) -> Unit,
) {
    val opening = rememberJustOpened()
    LazyColumn(contentPadding = padding.withExtra(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "resumen") { RecurringSummary(state, Modifier.appear(0, opening)) }
        item(key = "encabezado") {
            SectionHeader("Tus suscripciones", subtitle = "Toca una para ver su detalle", modifier = Modifier.appear(1, opening))
        }
        itemsIndexed(state.summaries, key = { _, s -> s.subscription.subscriptionId }) { index, summary ->
            GroupedRow(
                index = index,
                count = state.summaries.size,
                modifier = Modifier
                    .animateItem(fadeOutSpec = null)
                    .appear(2 + index, opening),
            ) {
                SubscriptionRow(summary, onClick = { onSelect(summary) })
            }
        }
        item(key = "encabezado-cobros") {
            SectionHeader(
                "Próximos cobros",
                subtitle = "Lo que se te va a cobrar en los siguientes 45 días",
                modifier = Modifier.appear(3, opening),
            )
        }
        if (state.upcoming.isEmpty()) {
            item(key = "sin-cobros") {
                Text(
                    "No hay cobros programados en los próximos 45 días.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenMargin + 4.dp),
                )
            }
        } else {
            itemsIndexed(state.upcoming, key = { _, c -> "cobro-${c.subscriptionId}-${c.date}" }) { index, charge ->
                GroupedRow(
                    index = index,
                    count = state.upcoming.size,
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .appear(4 + index, opening),
                ) { UpcomingChargeRow(charge) }
            }
        }
    }
}

@Composable
private fun RecurringSummary(state: SubscriptionsUiState, modifier: Modifier = Modifier) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Gasto recurrente total", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Al mes", style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(state.monthlyTotal, amountMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Al año", style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(state.yearlyTotal, amountMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(state.countsText, style = MaterialTheme.typography.bodyMedium)
            Text(state.nextChargeText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SubscriptionRow(summary: SubscriptionSummary, onClick: () -> Unit) {
    val subscription = summary.subscription
    val paused = !subscription.isActive
    ListItem(
        modifier = Modifier
            .clickable(onClickLabel = "Ver detalle", onClick = onClick)
            .then(if (paused) Modifier.alpha(0.6f) else Modifier),
        colors = TransparentListItemColors,
        leadingContent = {
            InitialAvatar(subscription.serviceName, parseColorHex(subscription.colorHex) ?: MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(subscription.serviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(listOfNotNull(subscription.planName?.takeIf { it.isNotBlank() }, summary.priceCycleText).joinToString(" · "))
                Spacer(Modifier.height(6.dp))
                SubscriptionStatusChip(summary)
            }
        },
    )
}

/** Pausada, en prueba, ya cobrada este periodo o cuanto falta para el cobro. */
@Composable
private fun SubscriptionStatusChip(summary: SubscriptionSummary) {
    when {
        summary.badge == SubscriptionBadge.PAUSED -> StatusChip("Pausada", statusColor(StatusLevel.NEUTRAL))
        summary.badge == SubscriptionBadge.TRIAL ->
            StatusChip("Prueba · ${summary.daysUntilTrialEnds} días", statusColor(StatusLevel.NEUTRAL))
        summary.isCurrentCycleCharged -> StatusChip("Cobrada", statusColor(StatusLevel.OK))
        else -> {
            val status = summary.chargeStatus
            StatusChip(
                if (status.showsDate) "${status.text} (${dayMonth(summary.nextChargeDate)})" else status.text,
                statusColor(status.level),
                pulse = status.level != StatusLevel.OK,
            )
        }
    }
}

@Composable
private fun UpcomingChargeRow(charge: UpcomingCharge) {
    ListItem(
        colors = TransparentListItemColors,
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(charge.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(monthShort(charge.date), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        headlineContent = { Text(charge.serviceName) },
        supportingContent = { Text("${charge.whenText} · ${charge.paymentSourceText}") },
        trailingContent = {
            Text(formatMoney(charge.amount), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        },
    )
}

/** Detalle de una suscripcion con sus acciones (el panel de la seleccionada en MAUI). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionDetailSheet(
    summary: SubscriptionSummary,
    today: java.time.LocalDate,
    onDismiss: () -> Unit,
    onRegisterCharge: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val subscription = summary.subscription
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(subscription.serviceName, parseColorHex(subscription.colorHex) ?: MaterialTheme.colorScheme.primary, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(subscription.serviceName, style = MaterialTheme.typography.headlineSmall)
                    subscription.planName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatusChip(summary.badge.text, statusColor(summary.badge.level))
            }

            Row {
                Column(Modifier.weight(1f)) {
                    Text("Equivalente mensual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary.monthlyEquivalent), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text("Costo anual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary.yearlyEquivalent), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (summary.countsTowardTotals) {
                LinearProgressIndicator(
                    progress = { summary.shareOfMonthlyRatio.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
            Text(summary.shareOfMonthlyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            DetailLine("Precio", summary.priceCycleText)
            DetailLine(
                "Próximo cobro",
                if (subscription.isActive) "${shortDate(summary.nextChargeDate, today)} · ${summary.chargeStatus.text}" else summary.chargeStatus.text,
            )
            DetailLine("Se paga con", if (summary.hasLinkedCard) "${summary.paymentMethodText} · ${summary.linkedCardName}" else summary.paymentMethodText)
            if (summary.categoryName.isNotBlank()) DetailLine("Categoría", summary.categoryName)
            summary.trialStatusText(today, ::dayMonth).takeIf { it.isNotBlank() }?.let { DetailLine("Prueba gratis", it) }
            summary.lastChargeText(::dayMonth).takeIf { it.isNotBlank() }?.let { DetailLine("Último cobro", it) }
            if (summary.hasNotes) DetailLine("Notas", summary.notes)

            if (subscription.isActive && summary.upcomingCharges.isNotEmpty()) {
                Text("Próximos cobros", style = MaterialTheme.typography.titleSmall)
                summary.upcomingCharges.forEach { charge ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${shortDate(charge.date, today)} · ${charge.whenText}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(charge.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onRegisterCharge, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar cobro")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggle) {
                    Icon(if (subscription.isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(summary.toggleStateActionText)
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
