package com.binc.gastapp.ui.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.ColorPicker
import com.binc.gastapp.ui.components.DateField
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.theme.parseColorHex

/** Alta y edicion de suscripcion en pantalla completa (SubscriptionFormBottomSheet de MAUI). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubscriptionFormScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: SubscriptionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (state.hasUnsavedData && !state.saving) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedData && !state.saving) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = requestClose) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") } },
                title = { Text(if (state.isEdit) "Editar suscripción" else "Nueva suscripción") },
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
                value = state.serviceName,
                onValueChange = viewModel::onServiceNameChange,
                label = { Text("Servicio") },
                placeholder = { Text("Ej. Netflix") },
                supportingText = { Text("Ej. Netflix, Spotify, gimnasio") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.planName,
                onValueChange = viewModel::onPlanNameChange,
                label = { Text("Plan contratado (opcional)") },
                placeholder = { Text("Ej. Premium 4K") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onAmountChange) },
                label = { Text("Costo por periodo") },
                placeholder = { Text("Ej. 219") },
                prefix = { Text("$") },
                supportingText = state.monthlyPreview?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Label("Cada cuánto")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BillingCycleOptions.forEach { (value, label) ->
                    FilterChip(selected = state.billingCycle == value, onClick = { viewModel.onBillingCycleSelect(value) }, label = { Text(label) })
                }
            }

            DateField(
                label = "Fecha del primer cobro",
                date = state.firstChargeDate,
                onChange = viewModel::onFirstChargeDateChange,
                supportingText = "Es la fecha con la que se calculan todos los cobros que siguen. Si ya llevas tiempo pagándolo, pon el primer cargo que recuerdes.",
            )
            Text(state.chargePreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            Label("¿De dónde sale el pago?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubscriptionPaymentOptions.forEach { (value, label) ->
                    FilterChip(selected = state.paymentMethod == value, onClick = { viewModel.onPaymentMethodSelect(value) }, label = { Text(label) })
                }
            }
            AnimatedVisibility(state.isCreditCard) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Tarjeta a la que se carga")
                    if (state.cards.isEmpty()) {
                        Text(
                            "No tienes tarjetas registradas. Agrégalas en «Mis tarjetas» o elige otra forma de pago.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.cards.forEach { card ->
                                FilterChip(
                                    selected = card.creditCardId == state.creditCardId,
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
                        Text(
                            "Al registrar el cobro, el gasto se suma a la deuda de esa tarjeta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Label("Categoría del gasto")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = category.categoryId == state.categoryId,
                        onClick = { viewModel.onCategorySelect(category.categoryId) },
                        label = { Text(category.categoryName) },
                        leadingIcon = { Icon(categoryIcon(category.categoryName), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                ListItem(
                    colors = TransparentListItemColors,
                    headlineContent = { Text("¿Estás en periodo de prueba?") },
                    supportingContent = { Text("Mientras dure la prueba no se cobra nada y no suma a tus totales.") },
                    trailingContent = { Switch(checked = state.isTrial, onCheckedChange = viewModel::onTrialChange) },
                )
                AnimatedVisibility(state.isTrial) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        DateField(
                            label = "¿Cuándo termina la prueba?",
                            date = state.trialEndDate,
                            onChange = viewModel::onTrialEndDateChange,
                            minDate = state.today,
                        )
                        state.trialPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Label("Color de la suscripción")
            ColorPicker(SubscriptionColors, state.colorHex, viewModel::onColorSelect)

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notas (opcional)") },
                placeholder = { Text("Ej. Cuenta compartida con mi hermana") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(52.dp),
            ) { Text(if (state.isEdit) "Guardar cambios" else "Agregar suscripción") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (state.isEdit) "¿Descartar los cambios?" else "¿Descartar esta suscripción?") },
            text = { Text("Perderás los datos que llevas capturados.") },
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

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
