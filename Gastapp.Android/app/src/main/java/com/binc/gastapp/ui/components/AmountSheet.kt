package com.binc.gastapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.binc.gastapp.R
import com.binc.gastapp.ui.format.currencySymbol
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.parseAmountInput
import com.binc.gastapp.ui.format.toFixedInputText
import com.binc.gastapp.ui.theme.Baloo
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

/** Una opcion rapida de la hoja de monto ("Sin deuda", "Pagar corte"...). */
data class AmountOption(val label: String, val amount: BigDecimal)

/** Un renglon de contexto arriba del campo ("Saldo total pendiente  $1,200.00"). */
data class AmountContextRow(val label: String, val value: String, val highlighted: Boolean = false)

/**
 * Hoja para capturar un monto (AmountInputPopup y el DisplayPromptAsync de MAUI): pagar
 * una tarjeta, ajustar su saldo, registrar el cobro de una suscripcion. Con
 * [allowZero] acepta 0 (ajustar saldo a "sin deuda").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AmountSheet(
    title: String,
    subtitle: String,
    fieldLabel: String,
    initialAmount: BigDecimal?,
    confirmLabel: String,
    onConfirm: (BigDecimal) -> Unit,
    onDismiss: () -> Unit,
    contextRows: List<AmountContextRow> = emptyList(),
    options: List<AmountOption> = emptyList(),
    allowZero: Boolean = false,
    warning: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initialText = initialAmount?.toFixedInputText().orEmpty()
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val invalidAmount = stringResource(R.string.invalid_amount)
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun confirm() {
        val amount = parseAmountInput(value.text)
        if (amount == null || amount.signum() < 0 || (!allowZero && amount.signum() == 0)) {
            error = invalidAmount
            return
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onConfirm(amount.setScale(2, RoundingMode.HALF_EVEN))
        }
    }

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
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            contextRows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.value,
                        style = if (row.highlighted) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (row.highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(warning != null) {
                Text(warning.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    filterAmountInput(next.text)?.let {
                        value = next.copy(text = it)
                        error = null
                    }
                },
                label = { Text(fieldLabel) },
                prefix = { Text("$currencySymbol ") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = Baloo),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            if (options.isNotEmpty()) {
                Text(stringResource(R.string.quick_amounts), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        SuggestionChip(
                            onClick = {
                                val text = option.amount.toFixedInputText()
                                value = TextFieldValue(text, TextRange(text.length))
                                error = null
                            },
                            label = { Text(stringResource(R.string.amount_option, option.label, formatMoney(option.amount))) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
                }) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.size(8.dp))
                Button(onClick = ::confirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) }
            }
        }
    }
}
