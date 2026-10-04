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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.format.dayLabel
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.theme.Baloo
import com.binc.gastapp.ui.theme.parseColorHex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** Las 4 formas de pago, en el orden y con las etiquetas de NewSpendingBottomSheet. */
private val PaymentOptions = listOf(
    PaymentMethods.CASH to "Efectivo",
    PaymentMethods.DEBIT to "Débito",
    PaymentMethods.TRANSFER to "Transf.",
    PaymentMethods.CREDIT_CARD to "Crédito",
)

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
                    if (state.isEdit) "Editar gasto" else "Nuevo gasto",
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
                ) { Text("Guardar") }
            }

            AnimatedVisibility(state.error != null) {
                ErrorCard(state.error.orEmpty(), Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.amountText,
                onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onAmountChange) },
                label = { Text("Cantidad") },
                placeholder = { Text("0.00") },
                prefix = { Text("$ ") },
                isError = state.amountError != null,
                supportingText = state.amountError?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = Baloo),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake.value.dp.toPx() },
            )

            FieldLabel("Categoría")
            CategoryPicker(state, viewModel, onTick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) })

            FieldLabel("Método de pago")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaymentOptions.forEachIndexed { index, (method, label) ->
                    SegmentedButton(
                        selected = state.paymentMethod == method,
                        onClick = { viewModel.onPaymentMethodSelect(method) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PaymentOptions.size),
                        icon = {},
                    ) { FitText(label) }
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

            FieldLabel("Detalle del gasto")
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Título") },
                placeholder = { Text("Ej. Supermercado, café...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Agrega una nota…") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedCard(onClick = { datePickerOpen = true }, shape = RoundedCornerShape(16.dp)) {
                ListItem(
                    colors = TransparentListItemColors,
                    leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                    overlineContent = { Text("Fecha del gasto") },
                    headlineContent = {
                        AnimatedContent(
                            targetState = state.date,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "fecha",
                        ) { Text(dayLabel(it, state.today)) }
                    },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = "Cambiar fecha") },
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
                    Text("Eliminar gasto")
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
            title = { Text("Eliminar categoría") },
            text = { Text(prompt.message) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteCategory,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeletePrompt) { Text("Cancelar") } },
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
                label = { Text(category.categoryName) },
                leadingIcon = { Icon(categoryIcon(category.categoryName), contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "nueva") {
            FilterChip(
                selected = state.newCategoryOpen,
                onClick = viewModel::toggleNewCategory,
                label = { Text("Nueva categoría") },
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
                label = { Text("Nueva categoría") },
                placeholder = { Text("Nombre de la categoría") },
                singleLine = true,
                isError = state.newCategoryError != null,
                supportingText = state.newCategoryError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveNewCategory() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::saveNewCategory, modifier = Modifier.padding(top = 8.dp)) { Text("Guardar") }
        }
    }
    AnimatedVisibility(visible = state.canDeleteSelectedCategory && !state.newCategoryOpen) {
        TextButton(
            onClick = viewModel::requestDeleteSelectedCategory,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Eliminar categoría") }
    }
}

@Composable
private fun CreditCardSection(state: SpendingFormState, viewModel: SpendingFormViewModel) {
    Column {
        FieldLabel("Selecciona tarjeta")
        if (state.cards.isEmpty()) {
            Text(
                "No tienes tarjetas de crédito registradas. Agrégalas en «Mis tarjetas».",
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
                headlineContent = { Text("Meses sin intereses (MSI)") },
                supportingContent = { Text("Diferir el gasto en mensualidades fijas") },
                trailingContent = { Switch(checked = state.isMsi, onCheckedChange = viewModel::onMsiChange) },
            )
            AnimatedVisibility(
                visible = state.isMsi,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MsiTerms) { months ->
                            FilterChip(
                                selected = months == state.installments,
                                onClick = { viewModel.onInstallmentsSelect(months) },
                                label = { Text("$months meses") },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = state.msiPreview,
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
                Text("Aceptar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Los selectores de fecha de Material trabajan en milisegundos UTC a medianoche. */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.utcMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
