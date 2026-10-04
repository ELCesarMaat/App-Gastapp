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
import com.binc.gastapp.domain.cards.PendingMsiPurchase
import com.binc.gastapp.ui.components.ColorPicker
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.formatDecimal
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

    val requestClose = { if (state.hasUnsavedData && !state.saving) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedData && !state.saving) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = requestClose) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") } },
                title = { Text(if (state.isEdit) "Editar tarjeta" else "Nueva tarjeta") },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }, enabled = state.loaded && !state.saving) {
                        Text(if (state.isEdit) "Guardar" else "Agregar")
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
                label = { Text("Nombre de la tarjeta") },
                placeholder = { Text("Ej. Tarjeta Oro") },
                supportingText = { Text("Ej. Oro, Platino, Nu, Banamex") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.bankName,
                onValueChange = viewModel::onBankNameChange,
                label = { Text("Banco emisor") },
                placeholder = { Text("Ej. BBVA") },
                supportingText = { Text("Ej. BBVA, Santander, Citibanamex, Nu") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.lastFour,
                    onValueChange = viewModel::onLastFourChange,
                    label = { Text("Últimos 4 dígitos") },
                    placeholder = { Text("1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.creditLimitText,
                    onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onCreditLimitChange) },
                    label = { Text("Límite de crédito") },
                    placeholder = { Text("Ej. 25000") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Color de la tarjeta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPicker(CardColors, state.colorHex, viewModel::onColorSelect)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DayDropdown("Día de corte", state.cutOffDay, viewModel::onCutOffDayChange, Modifier.weight(1f))
                DayDropdown("Día límite de pago", state.paymentDay, viewModel::onPaymentDayChange, Modifier.weight(1f))
            }
            Text(state.cyclePreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            if (!state.isEdit) InUseSection(state, viewModel)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(52.dp),
            ) { Text(if (state.isEdit) "Guardar cambios" else "Agregar tarjeta") }
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
        val msiNote = if (state.msiPurchases.isNotEmpty()) {
            " Incluye ${state.msiPurchases.size} compra${if (state.msiPurchases.size == 1) "" else "s"} a meses que capturaste."
        } else {
            ""
        }
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (state.isEdit) "¿Descartar los cambios?" else "¿Descartar esta tarjeta?") },
            text = { Text("Perderás los datos que llevas capturados.$msiNote") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
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
                state.bankName.ifBlank { "Banco" },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(state.cardName.ifBlank { "Nombre de la tarjeta" }, color = Color.White.copy(alpha = 0.8f))
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
            headlineContent = { Text("¿Esta tarjeta ya está en uso?") },
            supportingContent = { Text("Configura el saldo actual que ya debes o tus MSI activos.") },
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
                    label = { Text("Saldo total que tienes usado") },
                    placeholder = { Text("Ej. 6000") },
                    supportingText = { Text("Tal cual lo ves en tu app del banco, incluyendo tus compras a meses.") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.currentCycleText,
                    onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onCurrentCycleChange) },
                    label = { Text("Pago para no generar intereses") },
                    placeholder = { Text("Ej. 2500 (opcional)") },
                    supportingText = { Text("Lo que pide tu estado de cuenta en el corte actual. Opcional.") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    colors = TransparentListItemColors,
                    headlineContent = { Text("¿Este saldo ya te lo cobraron en un corte?") },
                    supportingContent = {
                        Text(
                            "Sí, si ya salió en tu estado de cuenta y lo debes pagar en la fecha límite más próxima. " +
                                "No, si son compras posteriores al último corte.",
                        )
                    },
                    trailingContent = { Switch(checked = state.balanceAlreadyCut, onCheckedChange = viewModel::onBalanceAlreadyCutChange) },
                )
                HorizontalDivider()
                ListItem(
                    colors = TransparentListItemColors,
                    headlineContent = { Text("¿Tienes compras a meses?") },
                    supportingContent = { Text("Agrega todas las que sigas pagando.") },
                    trailingContent = { Switch(checked = state.hasActiveMsi, onCheckedChange = viewModel::onHasActiveMsiChange) },
                )
                AnimatedVisibility(state.hasActiveMsi) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.msiPurchases.isEmpty()) {
                            Text(
                                "Aún no agregas compras a meses. Agrégalas una por una para llevar el control de cada plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(state.msiSummary, style = MaterialTheme.typography.labelLarge)
                            state.msiPurchases.forEachIndexed { index, purchase ->
                                PendingMsiRow(purchase, onRemove = { viewModel.removeMsiPurchase(index) })
                            }
                        }
                        OutlinedButton(onClick = viewModel::openMsiDraft) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar compra a meses")
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
    val title = purchase.title.ifBlank { "Compra a MSI" }
    ListItem(
        colors = TransparentListItemColors,
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text("$${formatDecimal(purchase.monthlyAmount, 0)} al mes · ${purchase.paidInstallments} de ${purchase.totalInstallments} pagadas")
                Text(
                    if (purchase.remainingInstallments > 0) {
                        "Te faltan ${purchase.remainingInstallments} · debes $${formatDecimal(purchase.remainingAmount, 0)}"
                    } else {
                        "Ya la terminaste de pagar"
                    },
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Quitar $title") }
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
            Text("Cómo se reparte tu saldo", style = MaterialTheme.typography.titleSmall)
            BreakdownLine("Total usado", state.totalUsedLabel)
            if (state.hasActiveMsi && state.msiPurchases.isNotEmpty()) BreakdownLine(state.msiCountLabel, state.msiDebtLabel)
            BreakdownLine("Compras de contado", state.cashDebtLabel, bold = true)
            Text(
                "Lo que no pusiste a meses lo calculamos solos, no tienes que sacar la cuenta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.availablePreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.overLimit) {
                Text(
                    "Ojo: la deuda que capturaste supera el límite de crédito que pusiste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.msiExceedsTotal?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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
            Text("Compra a meses", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Registra una compra que ya venías pagando.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { text -> onChange { it.copy(title = text) } },
                label = { Text("¿Qué compraste?") },
                placeholder = { Text("Ej. Laptop, Refrigerador, Celular") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.monthlyText,
                onValueChange = { text -> filterAmountInput(text)?.let { clean -> onChange { it.copy(monthlyText = clean, error = null) } } },
                label = { Text("¿Cuánto pagas al mes?") },
                placeholder = { Text("Ej. 1500") },
                prefix = { Text("$") },
                isError = draft.error != null,
                supportingText = draft.error?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("¿A cuántos meses?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviousMsiTerms.forEach { months ->
                    FilterChip(
                        selected = months == draft.totalInstallments,
                        onClick = { onChange { it.copy(totalInstallments = months) } },
                        label = { Text("$months") },
                    )
                }
            }
            Text("¿Cuántas ya pagaste?", style = MaterialTheme.typography.labelLarge)
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
                    Text(draft.remainingText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(draft.detailText, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Agregar a la tarjeta") }
        }
    }
}
