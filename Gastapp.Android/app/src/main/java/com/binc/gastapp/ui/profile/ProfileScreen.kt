package com.binc.gastapp.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.EmphasizedDecelerate
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.IncomeTypeSelector
import com.binc.gastapp.ui.components.InitialAvatar
import com.binc.gastapp.ui.components.ListGroup
import com.binc.gastapp.ui.components.MonthDaySelector
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.WeekDaySelector
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.dayMonthYear
import com.binc.gastapp.ui.theme.amountMedium
import java.math.BigDecimal
import java.math.RoundingMode

private class SummaryRow(val icon: ImageVector, val title: String, val value: String)

/**
 * Perfil (ProfilePage de MAUI con el estilo de PerfilScreen del demo): la cuenta, un
 * resumen que se actualiza mientras se edita, la frecuencia de ingreso con sus dias de
 * pago, el sueldo y la meta de ahorro. Se guarda solo y sube con la sincronizacion.
 */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val opening = rememberJustOpened()

    val summary = listOfNotNull(
        SummaryRow(Icons.Rounded.AccountBalanceWallet, "Ingreso", state.incomeSummary),
        SummaryRow(Icons.Rounded.Event, "Días de pago", state.scheduleSummary),
        SummaryRow(Icons.Rounded.Savings, "Meta de ahorro", state.goalSummary),
        state.birthDate?.let { SummaryRow(Icons.Rounded.Cake, "Fecha de nacimiento", dayMonthYear(it)) },
    )

    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withExtra(top = 8.dp, bottom = 32.dp),
        modifier = Modifier
            .fillMaxSize()
            // El teclado no tapa los campos: la lista se encoge lo que le falta.
            .consumeWindowInsets(contentPadding)
            .imePadding(),
    ) {
        item(key = "cabecera") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .appear(0, opening),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Colores del tema: en oscuro el primario es claro y la letra blanca no se leeria.
                InitialAvatar(
                    state.initials,
                    MaterialTheme.colorScheme.primary,
                    size = 88.dp,
                    letters = 2,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(state.displayName, style = MaterialTheme.typography.headlineSmall)
                state.email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "resumen-titulo") { SectionHeader("Ingreso y ahorro", modifier = Modifier.appear(1, opening)) }
        item(key = "resumen") {
            ListGroup(summary, Modifier.appear(2, opening)) { row ->
                ListItem(
                    colors = TransparentListItemColors,
                    leadingContent = { TonalIcon(row.icon) },
                    headlineContent = { Text(row.title) },
                    supportingContent = { Text(row.value) },
                )
            }
        }

        item(key = "frecuencia") {
            SectionCard(
                icon = Icons.Rounded.Event,
                title = "Frecuencia de pago",
                subtitle = state.scheduleSummary,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .appear(3, opening),
            ) {
                IncomeTypeSelector(state.incomeTypeId, viewModel::onIncomeTypeChange)
                AnimatedContent(
                    targetState = state.incomeTypeId,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "dias",
                ) { incomeTypeId ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FieldCaption(
                            when (incomeTypeId) {
                                IncomeTypes.WEEKLY -> "Día de la semana en que te pagan"
                                IncomeTypes.BIWEEKLY -> "Tus dos días de pago"
                                else -> "Tu día de pago"
                            },
                        )
                        if (incomeTypeId == IncomeTypes.WEEKLY) {
                            WeekDaySelector(state.weekPayDay, viewModel::onWeekPayDayChange)
                        } else {
                            MonthDaySelector(state.monthPayDays, viewModel::onMonthPayDayToggle)
                        }
                    }
                }
            }
        }

        item(key = "ahorro") {
            SectionCard(
                icon = Icons.Rounded.Savings,
                title = "Objetivo de ahorro",
                subtitle = state.goalSummary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .appear(4, opening),
            ) {
                SavingsForm(state, viewModel)
                SavingsSplit(state)
            }
        }

        item(key = "estado") {
            SaveStatusRow(
                state.saveStatus,
                Modifier
                    .padding(top = 16.dp)
                    .appear(5, opening),
            )
        }
    }
}

/**
 * Tarjeta de una seccion del perfil: icono, titulo y un resumen que cambia mientras se
 * edita, y debajo sus controles.
 */
@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalIcon(icon, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "resumen",
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

/** Rotulo chico sobre un grupo de controles. */
@Composable
private fun FieldCaption(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Atajos de porcentaje de ahorro. */
private val PercentPresets = listOf(5, 10, 15, 20, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SavingsForm(state: ProfileUiState, viewModel: ProfileViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.salaryText,
            onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onSalaryChange) },
            label = { Text("Sueldo por periodo") },
            placeholder = { Text("0") },
            prefix = { Text("$") },
            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldCaption("Calcular el ahorro por")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.byPercent,
                onClick = { viewModel.onSavingsModeChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { FitText("Porcentaje") }
            SegmentedButton(
                selected = !state.byPercent,
                onClick = { viewModel.onSavingsModeChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { FitText("Monto fijo") }
        }
        if (state.byPercent) {
            OutlinedTextField(
                value = state.percentText,
                onValueChange = { text -> filterAmountInput(text, decimals = 4)?.let(viewModel::onPercentChange) },
                label = { Text("Porcentaje de ahorro") },
                placeholder = { Text("10") },
                suffix = { Text("%") },
                leadingIcon = { Icon(Icons.Rounded.Percent, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PercentPresets.forEach { preset ->
                    FilterChip(
                        selected = state.percentText == preset.toString(),
                        onClick = { viewModel.onPercentChange(preset.toString()) },
                        label = { Text("$preset%") },
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onAmountChange) },
                label = { Text("Cantidad a ahorrar") },
                placeholder = { Text("0") },
                prefix = { Text("$") },
                leadingIcon = { Icon(Icons.Rounded.Savings, contentDescription = null) },
                supportingText = state.computedPercentInfo?.let { { Text(it) } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Como queda repartido el sueldo: una barra con lo que se ahorra y lo que queda para
 * gastar, y sus montos ("Ahorrarias / Tendrias" de MAUI).
 */
@Composable
private fun SavingsSplit(state: ProfileUiState) {
    val salary = state.salary
    val ratio = if (salary.signum() > 0) {
        state.estimatedSavings.divide(salary, 4, RoundingMode.HALF_EVEN).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val animated by animateFloatAsState(ratio, animationSpec = tween(500, easing = EmphasizedDecelerate), label = "reparto")
    val savingsColor = MaterialTheme.colorScheme.primary
    val spendColor = MaterialTheme.colorScheme.tertiary
    val spendable = state.estimatedSpendable.max(BigDecimal.ZERO)

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(spendColor),
            ) {
                if (animated > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated)
                            .background(savingsColor),
                    )
                }
            }
            SplitRow(
                color = savingsColor,
                label = "Ahorras",
                detail = "${percentLabel((ratio * 100).toBigDecimal())} del sueldo",
                amount = state.estimatedSavings,
            )
            SplitRow(
                color = spendColor,
                label = "Para gastar",
                detail = "${percentLabel(((1f - ratio) * 100).toBigDecimal())} del sueldo",
                amount = spendable,
            )
        }
    }
}

@Composable
private fun SplitRow(color: Color, label: String, detail: String, amount: BigDecimal) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        AnimatedAmount(amount, amountMedium, fromZero = false)
    }
}

/** El aviso del guardado automatico. TalkBack lo lee cuando cambia. */
@Composable
private fun SaveStatusRow(status: SaveStatus, modifier: Modifier = Modifier) {
    val invalid = status is SaveStatus.Invalid
    val color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin + 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (status) {
                is SaveStatus.Invalid -> Icons.Rounded.ErrorOutline
                SaveStatus.Pending -> Icons.Rounded.Sync
                else -> Icons.Rounded.CheckCircle
            },
            contentDescription = null,
            tint = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(status.text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
