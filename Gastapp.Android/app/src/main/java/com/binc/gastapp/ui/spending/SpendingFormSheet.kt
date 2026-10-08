package com.binc.gastapp.ui.spending

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.isLargeFontScale
import com.binc.gastapp.ui.format.amountPlaceholder
import com.binc.gastapp.ui.format.categoryLabel
import com.binc.gastapp.ui.format.currencySymbol
import com.binc.gastapp.ui.format.dayLabel
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.rememberStrings
import com.binc.gastapp.ui.theme.Baloo
import com.binc.gastapp.ui.theme.parseColorHex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** Las 4 formas de pago, en el orden y con las etiquetas de NewSpendingBottomSheet. */
private val PaymentOptions = listOf(PaymentMethods.CASH, PaymentMethods.DEBIT, PaymentMethods.TRANSFER, PaymentMethods.CREDIT_CARD)

/**
 * Hoja para crear o editar un gasto (la misma, como en MAUI). Se cierra con su
 * animacion y despues avisa [onResult]: asi se ve como la lista de abajo reacciona.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingFormSheet(
    request: SpendingFormRequest,
    onDismiss: () -> Unit,
    onResult: (SpendingFormResult) -> Unit,
    viewModel: SpendingFormViewModel = hiltViewModel(key = "spending-form"),
) {
    LaunchedEffect(request) { viewModel.open(request) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }

    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                viewModel.closed()
                action()
            }
        }
    }

    // El monto invalido "tiembla" y vibra, como en el demo.
    LaunchedEffect(state.amountError) {
        if (state.amountError != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    -14f at 60
                    12f at 120
                    -9f at 180
                    6f at 240
                    -3f at 300
                },
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.closed()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(bottom = 16.dp),
        ) {
            // Guardar arriba, como en la hoja de MAUI: queda visible aunque el teclado tape el resto.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.isEdit) R.string.edit_spending else R.string.new_spending),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        viewModel.save { result ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            closeThen { onResult(result) }
                        }
                    },
                    enabled = state.loaded && !state.saving,
                ) { Text(stringResource(R.string.save)) }
            }

            AnimatedVisibility(state.error != null) {
                ErrorCard(state.error.orEmpty(), Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.amountText,
                onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onAmountChange) },
                label = { Text(stringResource(R.string.amount)) },
                placeholder = { Text(amountPlaceholder) },
                prefix = { Text("$currencySymbol ") },
                isError = state.amountError != null,
                supportingText = state.amountError?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = Baloo),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake.value.dp.toPx() },
            )

            FieldLabel(stringResource(R.string.category))
            CategoryPicker(state, viewModel, onTick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) })

            FieldLabel(stringResource(R.string.payment_method))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaymentOptions.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = state.paymentMethod == method,
                        onClick = { viewModel.onPaymentMethodSelect(method) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PaymentOptions.size),
                        icon = {},
                    ) { FitText(stringResource(paymentShortLabel(method))) }
                }
            }

            // La parte de tarjeta y MSI se despliega solo con "Crédito".
            AnimatedVisibility(
                visible = state.isCreditCard,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                CreditCardSection(state, viewModel)
            }

            FieldLabel(stringResource(R.string.spending_details_section))
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.title_label)) },
                placeholder = { Text(stringResource(R.string.spending_title_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.description_optional)) },
                placeholder = { Text(stringResource(R.string.add_note_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedCard(onClick = { datePickerOpen = true }, shape = RoundedCornerShape(16.dp)) {
                ListItem(
                    colors = TransparentListItemColors,
                    leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                    overlineContent = { Text(stringResource(R.string.spending_date)) },
                    headlineContent = {
                        AnimatedContent(
                            targetState = state.date,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "fecha",
                        ) { Text(dayLabel(it, state.today)) }
                    },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.change_date)) },
                )
            }

            if (state.isEdit) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.delete { result -> closeThen { onResult(result) } }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_spending))
                }
            }
        }
    }

    if (datePickerOpen) {
        SpendingDatePicker(
            date = state.date,
            today = state.today,
            onDismiss = { datePickerOpen = false },
            onConfirm = {
                viewModel.onDateChange(it)
                datePickerOpen = false
            },
        )
    }

    state.deletePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeletePrompt,
            title = { Text(stringResource(R.string.delete_category)) },
            text = { Text(prompt.message(rememberStrings())) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteCategory,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeletePrompt) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CategoryPicker(state: SpendingFormState, viewModel: SpendingFormViewModel, onTick: () -> Unit) {
    // Las categorias llegan despues de abrir la hoja. Si la fila se arma vacia y luego se le
    // insertan, los chips entran con la animacion de aparicion y en la hoja se quedaban
    // invisibles hasta tocarla o deslizarla. Se arma ya con la lista completa.
    if (!state.loaded || state.categories.isEmpty()) {
        Spacer(Modifier.height(48.dp))
        return
    }
    val selectedIndex = state.categories.indexOfFirst { it.categoryId == state.selectedCategoryId }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.categories, key = { it.categoryId }) { category ->
            FilterChip(
                selected = category.categoryId == state.selectedCategoryId,
                onClick = {
                    viewModel.onCategorySelect(category.categoryId)
                    onTick()
                },
                label = { Text(categoryLabel(category.categoryName)) },
                leadingIcon = { Icon(categoryIcon(category.categoryName), contentDescription = null, modifier = Modifier.size(18.dp)) },
                // Sin fundido de entrada: un chip nuevo no debe depender de que se redibuje la hoja.
                modifier = Modifier.animateItem(fadeInSpec = null),
            )
        }
        item(key = "nueva") {
            FilterChip(
                selected = state.newCategoryOpen,
                onClick = viewModel::toggleNewCategory,
                label = { Text(stringResource(R.string.new_category)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
    // Llevar a la vista la categoria elegida (la nueva queda al final de la lista).
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex) }

    AnimatedVisibility(
        visible = state.newCategoryOpen,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = state.newCategoryName,
                onValueChange = viewModel::onNewCategoryNameChange,
                label = { Text(stringResource(R.string.new_category)) },
                placeholder = { Text(stringResource(R.string.category_name_placeholder)) },
                singleLine = true,
                isError = state.newCategoryError != null,
                supportingText = state.newCategoryError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveNewCategory() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::saveNewCategory, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.save)) }
        }
    }
    AnimatedVisibility(visible = state.canDeleteSelectedCategory && !state.newCategoryOpen) {
        TextButton(
            onClick = viewModel::requestDeleteSelectedCategory,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.delete_category)) }
    }
}

@Composable
private fun CreditCardSection(state: SpendingFormState, viewModel: SpendingFormViewModel) {
    Column {
        FieldLabel(stringResource(R.string.select_card))
        if (state.cards.isEmpty()) {
            Text(
                stringResource(R.string.no_cards_registered),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(state.cards, key = { it.creditCardId }) { card ->
                    FilterChip(
                        selected = card.creditCardId == state.selectedCardId,
                        onClick = { viewModel.onCardSelect(card.creditCardId) },
                        label = { Text(card.cardName.ifBlank { card.bankName }) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(parseColorHex(card.colorHex) ?: Color.Gray, CircleShape),
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedCard(shape = RoundedCornerShape(16.dp)) {
            ListItem(
                colors = TransparentListItemColors,
                headlineContent = { Text(stringResource(R.string.msi_title)) },
                supportingContent = { Text(stringResource(R.string.msi_subtitle)) },
                trailingContent = { Switch(checked = state.isMsi, onCheckedChange = viewModel::onMsiChange) },
            )
            AnimatedVisibility(
                visible = state.isMsi,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    MsiTermChips(state, viewModel)
                    Spacer(Modifier.height(4.dp))
                    CurrentInstallmentStepper(
                        current = state.currentInstallment,
                        total = state.selectedInstallments ?: state.installments,
                        onChange = viewModel::onCurrentInstallmentChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    AnimatedContent(
                        targetState = state.msiPreview(rememberStrings()),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "mensualidad",
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Plazos comunes en chips y al final "Otro", que abre un campo para escribir cualquiera. */
@Composable
private fun MsiTermChips(state: SpendingFormState, viewModel: SpendingFormViewModel) {
    val selectedIndex = if (state.isCustomInstallments) MsiTerms.size else MsiTerms.indexOf(state.installments)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MsiTerms) { months ->
            FilterChip(
                selected = !state.isCustomInstallments && months == state.installments,
                onClick = { viewModel.onInstallmentsSelect(months) },
                label = { Text(stringResource(R.string.msi_months, months)) },
            )
        }
        item(key = "otro") {
            FilterChip(
                selected = state.isCustomInstallments,
                onClick = viewModel::onCustomInstallmentsSelect,
                label = { Text(stringResource(R.string.msi_other_term)) },
            )
        }
    }
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex) }

    AnimatedVisibility(
        visible = state.isCustomInstallments,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        MsiCustomTermField(
            text = state.customInstallmentsText.orEmpty(),
            error = state.customInstallmentsError,
            onChange = viewModel::onCustomInstallmentsChange,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Campo de "Otro" plazo. Toma el foco al aparecer vacio (recien elegido), pero no al
 * abrir un gasto que ya traia un plazo escrito.
 */
@Composable
fun MsiCustomTermField(text: String, error: String?, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.msi_custom_term_label)) },
        placeholder = { Text(stringResource(R.string.msi_custom_term_placeholder)) },
        suffix = { Text(stringResource(R.string.msi_custom_term_suffix)) },
        isError = error != null,
        supportingText = { Text(error ?: stringResource(R.string.msi_custom_term_hint, MsiTermRange.first, MsiTermRange.last)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
    LaunchedEffect(Unit) { if (text.isEmpty()) focusRequester.requestFocus() }
}

/** "Mensualidad actual  −  2 de 4  +", para una compra que ya se venia pagando. */
@Composable
private fun CurrentInstallmentStepper(current: Int, total: Int, onChange: (Int) -> Unit) {
    val labels: @Composable () -> Unit = {
        Column {
            Text(stringResource(R.string.msi_current_installment), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.msi_current_installment_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val stepper: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange(current - 1) }, enabled = current > 1) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.msi_current_installment_previous))
            }
            AnimatedContent(
                targetState = stringResource(R.string.msi_current_installment_value, current, total),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mensualidad actual",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            FilledTonalIconButton(onClick = { onChange(current + 1) }, enabled = current < total) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.msi_current_installment_next))
            }
        }
    }
    if (isLargeFontScale) {
        // Con la fuente al 200 % el texto y los botones no caben en una fila.
        Column(Modifier.padding(top = 8.dp)) {
            labels()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { stepper() }
        }
    } else {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { labels() }
            stepper()
        }
    }
}

/** Selector de fecha sin dias futuros: no se registran gastos a futuro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingDatePicker(date: LocalDate, today: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val todayMillis = today.toUtcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
        yearRange = 2000..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let { onConfirm(it.utcMillisToDate()) } ?: onDismiss() }) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Los selectores de fecha de Material trabajan en milisegundos UTC a medianoche. */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.utcMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
