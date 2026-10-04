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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
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
import com.binc.gastapp.ui.format.billingCycleName
import com.binc.gastapp.ui.format.categoryLabel
import com.binc.gastapp.ui.format.chargeStatusText
import com.binc.gastapp.ui.format.dayMonth
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.label
import com.binc.gastapp.ui.format.lastChargeText
import com.binc.gastapp.ui.format.monthShort
import com.binc.gastapp.ui.format.paymentMethodName
import com.binc.gastapp.ui.format.paymentSource
import com.binc.gastapp.ui.format.priceCycle
import com.binc.gastapp.ui.format.rememberStrings
import com.binc.gastapp.ui.format.shareText
import com.binc.gastapp.ui.format.shortDate
import com.binc.gastapp.ui.format.trialStatusText
import com.binc.gastapp.ui.format.whenText
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
    val strings = rememberStrings()
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
                title = { Text(stringResource(R.string.subscriptions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_subscription)) } },
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
            title = { Text(stringResource(R.string.charge_already_registered)) },
            text = { Text(stringResource(R.string.charge_already_registered_text, current.summary.lastChargeText(rememberStrings()))) },
            confirmButton = { TextButton(onClick = { overlay = SubscriptionOverlay.Charge(current.summary) }) { Text(stringResource(R.string.register_another)) } },
            dismissButton = { TextButton(onClick = { overlay = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is SubscriptionOverlay.Charge -> {
            val summary = current.summary
            val subscription = summary.subscription
            AmountSheet(
                title = stringResource(R.string.register_charge),
                subtitle = listOfNotNull(subscription.serviceName, subscription.planName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                fieldLabel = stringResource(R.string.amount_charged),
                initialAmount = subscription.amount,
                confirmLabel = stringResource(R.string.register_charge),
                onConfirm = { amount ->
                    overlay = null
                    viewModel.registerCharge(summary, amount) { messages.show(strings.get(R.string.charge_registered, formatMoney(amount))) }
                },
                onDismiss = { overlay = null },
                contextRows = buildList {
                    add(AmountContextRow(strings.get(R.string.period_cost), formatMoney(subscription.amount), highlighted = true))
                    add(AmountContextRow(strings.get(R.string.billing_frequency), strings.get(billingCycleName(subscription.billingCycle))))
                    add(AmountContextRow(strings.get(R.string.next_charge), shortDate(summary.nextChargeDate, state.today)))
                    if (summary.hasLinkedCard) add(AmountContextRow(strings.get(R.string.charged_to), summary.linkedCardName))
                },
                options = listOf(AmountOption(stringResource(R.string.period_cost), subscription.amount)),
            )
        }
        is SubscriptionOverlay.ConfirmDelete -> AlertDialog(
            onDismissRequest = { overlay = null },
            title = { Text(stringResource(R.string.delete_subscription)) },
            text = {
                Text(stringResource(R.string.delete_subscription_confirm, current.summary.subscription.serviceName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        overlay = null
                        viewModel.delete(current.summary) { messages.show(strings.get(R.string.subscription_deleted)) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { overlay = null }) { Text(stringResource(R.string.cancel)) } },
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
        Text(stringResource(R.string.no_subscriptions_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.appear(1))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_subscriptions_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(2),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, modifier = Modifier.appear(3)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_subscription))
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
            SectionHeader(stringResource(R.string.your_subscriptions), subtitle = stringResource(R.string.your_subscriptions_hint), modifier = Modifier.appear(1, opening))
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
                stringResource(R.string.upcoming_charges),
                subtitle = stringResource(R.string.upcoming_charges_hint),
                modifier = Modifier.appear(3, opening),
            )
        }
        if (state.upcoming.isEmpty()) {
            item(key = "sin-cobros") {
                Text(
                    stringResource(R.string.upcoming_charges_empty),
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
            Text(stringResource(R.string.total_recurring), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.per_month_label), style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(state.monthlyTotal, amountMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.per_year_label), style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(state.yearlyTotal, amountMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            val strings = rememberStrings()
            Text(state.countsText(strings), style = MaterialTheme.typography.bodyMedium)
            Text(state.nextChargeText(strings), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SubscriptionRow(summary: SubscriptionSummary, onClick: () -> Unit) {
    val subscription = summary.subscription
    val paused = !subscription.isActive
    ListItem(
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.see_details), onClick = onClick)
            .then(if (paused) Modifier.alpha(0.6f) else Modifier),
        colors = TransparentListItemColors,
        leadingContent = {
            InitialAvatar(subscription.serviceName, parseColorHex(subscription.colorHex) ?: MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(subscription.serviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(listOfNotNull(subscription.planName?.takeIf { it.isNotBlank() }, summary.priceCycle(rememberStrings())).joinToString(" · "))
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
        summary.badge == SubscriptionBadge.PAUSED -> StatusChip(stringResource(R.string.subscription_paused), statusColor(StatusLevel.NEUTRAL))
        summary.badge == SubscriptionBadge.TRIAL ->
            StatusChip(pluralStringResource(R.plurals.trial_chip, summary.daysUntilTrialEnds, summary.daysUntilTrialEnds), statusColor(StatusLevel.NEUTRAL))
        summary.isCurrentCycleCharged -> StatusChip(stringResource(R.string.charged_chip), statusColor(StatusLevel.OK))
        else -> {
            val status = summary.chargeStatus
            val text = summary.chargeStatusText(rememberStrings())
            StatusChip(
                if (status.showsDate) stringResource(R.string.status_with_date, text, dayMonth(summary.nextChargeDate)) else text,
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
        supportingContent = { Text(rememberStrings().let { stringResource(R.string.dot_join, charge.whenText(it), charge.paymentSource(it)) }) },
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
    val strings = rememberStrings()
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
                StatusChip(stringResource(summary.badge.label()), statusColor(summary.badge.level))
            }

            Row {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.monthly_equivalent), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary.monthlyEquivalent), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.yearly_cost), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(summary.shareText(strings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            DetailLine(strings.get(R.string.price), summary.priceCycle(strings))
            val chargeStatus = summary.chargeStatusText(strings)
            DetailLine(
                strings.get(R.string.next_charge),
                if (subscription.isActive) strings.get(R.string.dot_join, shortDate(summary.nextChargeDate, today), chargeStatus) else chargeStatus,
            )
            val method = strings.get(paymentMethodName(subscription.paymentMethod))
            DetailLine(strings.get(R.string.paid_with), if (summary.hasLinkedCard) strings.get(R.string.dot_join, method, summary.linkedCardName) else method)
            if (summary.categoryName.isNotBlank()) DetailLine(strings.get(R.string.category), categoryLabel(strings, summary.categoryName))
            summary.trialStatusText(strings).takeIf { it.isNotBlank() }?.let { DetailLine(strings.get(R.string.free_trial), it) }
            summary.lastChargeText(strings).takeIf { it.isNotBlank() }?.let { DetailLine(strings.get(R.string.last_charge), it) }
            if (summary.hasNotes) DetailLine(strings.get(R.string.notes), summary.notes)

            if (subscription.isActive && summary.upcomingCharges.isNotEmpty()) {
                Text(stringResource(R.string.upcoming_charges), style = MaterialTheme.typography.titleSmall)
                summary.upcomingCharges.forEach { charge ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(strings.get(R.string.dot_join, shortDate(charge.date, today), charge.whenText(strings)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(charge.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onRegisterCharge, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.register_charge))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggle) {
                    Icon(if (subscription.isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (subscription.isActive) R.string.pause else R.string.resume))
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit))
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete))
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
