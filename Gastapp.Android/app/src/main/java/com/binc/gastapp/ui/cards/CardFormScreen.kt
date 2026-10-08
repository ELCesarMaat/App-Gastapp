package com.binc.gastapp.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
import com.binc.gastapp.domain.cards.PendingMsiPurchase
import com.binc.gastapp.ui.components.ColorPicker
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.format.currencySymbol
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.formatDecimal
import com.binc.gastapp.ui.format.formatMoneyWhole
import com.binc.gastapp.ui.format.rememberStrings
import com.binc.gastapp.ui.spending.MsiCustomTermField
import com.binc.gastapp.ui.spending.filterMsiTermInput
import com.binc.gastapp.ui.theme.parseColorHex

/**
 * Alta y edicion de tarjeta en pantalla completa (CreditCardFormBottomSheet de MAUI).
 * Si hay algo capturado, salir pide confirmacion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CardFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val strings = rememberStrings()

    val requestClose = { if (state.hasUnsavedData && !state.saving) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedData && !state.saving) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = requestClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) } },
                title = { Text(stringResource(if (state.isEdit) R.string.edit_card else R.string.new_card)) },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }, enabled = state.loaded && !state.saving) {
                        Text(stringResource(if (state.isEdit) R.string.save else R.string.add))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin + 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardPreview(state)

            AnimatedVisibility(state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(state.error.orEmpty())
                    }
                }
            }

            OutlinedTextField(
                value = state.cardName,
                onValueChange = viewModel::onCardNameChange,
                label = { Text(stringResource(R.string.card_name)) },
                placeholder = { Text(stringResource(R.string.card_name_placeholder)) },
                supportingText = { Text(stringResource(R.string.card_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.bankName,
                onValueChange = viewModel::onBankNameChange,
                label = { Text(stringResource(R.string.issuing_bank)) },
                placeholder = { Text(stringResource(R.string.issuing_bank_placeholder)) },
                supportingText = { Text(stringResource(R.string.issuing_bank_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.lastFour,
                    onValueChange = viewModel::onLastFourChange,
                    label = { Text(stringResource(R.string.last_four_digits)) },
                    placeholder = { Text("1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.creditLimitText,
                    onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onCreditLimitChange) },
                    label = { Text(stringResource(R.string.credit_limit)) },
                    placeholder = { Text(stringResource(R.string.example_amount, "25000")) },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }

            Text(stringResource(R.string.card_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPicker(CardColors, state.colorHex, viewModel::onColorSelect)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DayDropdown(stringResource(R.string.cut_off_day), state.cutOffDay, viewModel::onCutOffDayChange, Modifier.weight(1f))
                DayDropdown(stringResource(R.string.payment_day), state.paymentDay, viewModel::onPaymentDayChange, Modifier.weight(1f))
            }
            Text(state.cyclePreview(strings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            if (!state.isEdit) InUseSection(state, viewModel)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(52.dp),
            ) { Text(stringResource(if (state.isEdit) R.string.save_changes else R.string.add_card)) }
            Spacer(Modifier.height(16.dp))
        }
    }

    state.msiDraft?.let { draft ->
        MsiPurchaseSheet(
            draft = draft,
            onChange = viewModel::updateMsiDraft,
            onAdd = { viewModel.addMsiPurchase() },
            onDismiss = viewModel::closeMsiDraft,
        )
    }

    if (confirmDiscard) {
        val message = if (state.msiPurchases.isNotEmpty()) {
            pluralStringResource(R.plurals.discard_message_msi, state.msiPurchases.size, state.msiPurchases.size)
        } else {
            stringResource(R.string.discard_message)
        }
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(if (state.isEdit) R.string.discard_changes_question else R.string.discard_card_question)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
}

/** La tarjeta como va a quedar, con su color. */
@Composable
private fun CardPreview(state: CardFormState) {
    val color = parseColorHex(state.colorHex) ?: MaterialTheme.colorScheme.primary
    // Alto minimo de tarjeta; con la fuente muy grande crece en vez de encimar textos.
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.35f))), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                state.bankName.ifBlank { stringResource(R.string.bank) },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(state.cardName.ifBlank { stringResource(R.string.card_name) }, color = Color.White.copy(alpha = 0.8f))
        }
        Text(
            "•••• ${state.lastFour.ifBlank { "0000" }}",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDropdown(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..31).forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.toString()) },
                    onClick = {
                        onChange(day)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** "¿Esta tarjeta ya está en uso?": saldo actual y compras a meses que ya venían corriendo. */
@Composable
private fun InUseSection(state: CardFormState, viewModel: CardFormViewModel) {
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        ListItem(
            colors = TransparentListItemColors,
            headlineContent = { Text(stringResource(R.string.card_in_use_question)) },
            supportingContent = { Text(stringResource(R.string.card_in_use_hint)) },
            trailingContent = { Switch(checked = state.hasExistingBalance, onCheckedChange = viewModel::onHasExistingBalanceChange) },
        )
        AnimatedVisibility(
            visible = state.hasExistingBalance,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.totalUsedText,
                    onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onTotalUsedChange) },
                    label = { Text(stringResource(R.string.total_used_balance)) },
                    placeholder = { Text(stringResource(R.string.example_amount, "6000")) },
                    supportingText = { Text(stringResource(R.string.total_used_hint)) },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.currentCycleText,
                    onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onCurrentCycleChange) },
                    label = { Text(stringResource(R.string.pay_to_avoid_interest)) },
                    placeholder = { Text(stringResource(R.string.example_amount_optional, "2500")) },
                    supportingText = { Text(stringResource(R.string.current_statement_hint)) },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    colors = TransparentListItemColors,
                    headlineContent = { Text(stringResource(R.string.balance_already_cut_question)) },
                    supportingContent = {
                        Text(stringResource(R.string.balance_already_cut_hint))
                    },
                    trailingContent = { Switch(checked = state.balanceAlreadyCut, onCheckedChange = viewModel::onBalanceAlreadyCutChange) },
                )
                HorizontalDivider()
                ListItem(
                    colors = TransparentListItemColors,
                    headlineContent = { Text(stringResource(R.string.has_msi_question)) },
                    supportingContent = { Text(stringResource(R.string.has_msi_hint)) },
                    trailingContent = { Switch(checked = state.hasActiveMsi, onCheckedChange = viewModel::onHasActiveMsiChange) },
                )
                AnimatedVisibility(state.hasActiveMsi) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.msiPurchases.isEmpty()) {
                            Text(
                                stringResource(R.string.msi_none_yet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(state.msiSummary(rememberStrings()), style = MaterialTheme.typography.labelLarge)
                            state.msiPurchases.forEachIndexed { index, purchase ->
                                PendingMsiRow(purchase, onRemove = { viewModel.removeMsiPurchase(index) })
                            }
                        }
                        OutlinedButton(onClick = viewModel::openMsiDraft) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_msi_purchase))
                        }
                    }
                }
                if (state.showBreakdown) DebtBreakdown(state)
            }
        }
    }
}

@Composable
private fun PendingMsiRow(purchase: PendingMsiPurchase, onRemove: () -> Unit) {
    val title = purchase.title.ifBlank { stringResource(R.string.msi_purchase) }
    ListItem(
        colors = TransparentListItemColors,
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.pending_msi_monthly,
                        formatMoneyWhole(purchase.monthlyAmount),
                        purchase.paidInstallments,
                        purchase.totalInstallments,
                    ),
                )
                Text(
                    if (purchase.remainingInstallments > 0) {
                        stringResource(R.string.pending_msi_remaining, purchase.remainingInstallments, formatMoneyWhole(purchase.remainingAmount))
                    } else {
                        stringResource(R.string.pending_msi_done)
                    },
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove_item, title)) }
        },
    )
}

/** "Cómo se reparte tu saldo": lo de contado se deduce restando los MSI del total usado. */
@Composable
private fun DebtBreakdown(state: CardFormState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val strings = rememberStrings()
            Text(stringResource(R.string.balance_breakdown), style = MaterialTheme.typography.titleSmall)
            BreakdownLine(stringResource(R.string.total_used), state.totalUsedLabel)
            if (state.hasActiveMsi && state.msiPurchases.isNotEmpty()) BreakdownLine(state.msiCountLabel(strings), state.msiDebtLabel)
            BreakdownLine(stringResource(R.string.regular_purchases), state.cashDebtLabel, bold = true)
            Text(
                stringResource(R.string.balance_breakdown_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.availablePreview(strings)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.overLimit) {
                Text(
                    stringResource(R.string.over_limit_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.msiExceedsTotal(strings)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun BreakdownLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

/** Hoja "Compra a meses" (MsiPurchaseBottomSheet de MAUI). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MsiPurchaseSheet(
    draft: MsiDraft,
    onChange: ((MsiDraft) -> MsiDraft) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.msi_sheet_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.msi_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { text -> onChange { it.copy(title = text) } },
                label = { Text(stringResource(R.string.msi_what)) },
                placeholder = { Text(stringResource(R.string.msi_what_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.monthlyText,
                onValueChange = { text -> filterAmountInput(text)?.let { clean -> onChange { it.copy(monthlyText = clean, error = null) } } },
                label = { Text(stringResource(R.string.msi_monthly_question)) },
                placeholder = { Text(stringResource(R.string.example_amount, "1500")) },
                prefix = { Text(currencySymbol) },
                isError = draft.error != null,
                supportingText = draft.error?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.msi_months_question), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviousMsiTerms.forEach { months ->
                    FilterChip(
                        selected = !draft.isCustomTerm && months == draft.totalInstallments,
                        onClick = { onChange { it.withPresetTerm(months) } },
                        label = { Text("$months") },
                    )
                }
                FilterChip(
                    selected = draft.isCustomTerm,
                    onClick = { onChange { it.withCustomTerm(it.customTermText.orEmpty()) } },
                    label = { Text(stringResource(R.string.msi_other_term)) },
                )
            }
            AnimatedVisibility(draft.isCustomTerm) {
                MsiCustomTermField(
                    text = draft.customTermText.orEmpty(),
                    error = draft.customTermError,
                    onChange = { text -> filterMsiTermInput(text)?.let { clean -> onChange { it.withCustomTerm(clean) } } },
                )
            }
            Text(stringResource(R.string.msi_paid_question), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onChange { it.copy(paidInstallments = it.paidInstallments - 1) } },
                    enabled = draft.paidInstallments > 0,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) { Text("−") }
                Text(
                    "${draft.paidInstallments}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                OutlinedButton(
                    onClick = { onChange { it.copy(paidInstallments = it.paidInstallments + 1) } },
                    enabled = draft.paidInstallments < draft.totalInstallments - 1,
                ) { Text("+") }
            }
            AnimatedVisibility(draft.hasPreview) {
                Column {
                    Text(draft.remainingText(rememberStrings()), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(draft.detailText(rememberStrings()), style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.msi_add_to_card)) }
        }
    }
}
