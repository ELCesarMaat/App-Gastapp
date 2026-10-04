package com.binc.gastapp.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
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
import com.binc.gastapp.ui.components.StatusChip
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.WeekDaySelector
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.scaleOnPress
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.currencySymbol
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.dayMonthYear
import com.binc.gastapp.ui.format.formatPercent
import com.binc.gastapp.ui.format.rememberStrings
import com.binc.gastapp.ui.theme.amountMedium
import com.binc.gastapp.ui.summary.CardChip
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
    shortcuts: ProfileShortcuts,
    listState: LazyListState = rememberLazyListState(),
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val opening = rememberJustOpened()
    val strings = rememberStrings()
    val scheduleSummary = state.scheduleSummary(strings)
    val goalSummary = state.goalSummary(strings)

    val summary = listOfNotNull(
        SummaryRow(Icons.Rounded.AccountBalanceWallet, strings.get(R.string.summary_income), state.incomeSummary(strings)),
        SummaryRow(Icons.Rounded.Event, strings.get(R.string.summary_paydays), scheduleSummary),
        SummaryRow(Icons.Rounded.Savings, strings.get(R.string.summary_goal), goalSummary),
        state.birthDate?.let { SummaryRow(Icons.Rounded.Cake, strings.get(R.string.summary_birth_date), dayMonthYear(it)) },
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
                Text(state.displayName(strings), style = MaterialTheme.typography.headlineSmall)
                state.email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "atajo-tarjetas") {
            ShortcutCard(
                icon = Icons.Rounded.CreditCard,
                title = stringResource(R.string.my_cards_description),
                subtitle = shortcuts.cardsSubtitle,
                onClick = shortcuts.onOpenCards,
                modifier = Modifier.appear(1, opening),
                chip = shortcuts.cardChip?.let { chip ->
                    {
                        StatusChip(chip.text, statusColor(chip.level), pulse = chip.level.ordinal > 0)
                    }
                },
            )
        }

        item(key = "atajo-suscripciones") {
            ShortcutCard(
                icon = Icons.Rounded.Subscriptions,
                title = stringResource(R.string.subscriptions_description),
                subtitle = shortcuts.subscriptionsSubtitle,
                onClick = shortcuts.onOpenSubscriptions,
                modifier = Modifier.appear(2, opening),
            )
        }

        item(key = "resumen-titulo") { SectionHeader(stringResource(R.string.income_and_savings), modifier = Modifier.appear(3, opening)) }
        item(key = "resumen") {
            ListGroup(summary, Modifier.appear(4, opening)) { row ->
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
                title = stringResource(R.string.pay_frequency),
                subtitle = scheduleSummary,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .appear(5, opening),
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
                                IncomeTypes.WEEKLY -> stringResource(R.string.weekly_payday_caption)
                                IncomeTypes.BIWEEKLY -> stringResource(R.string.biweekly_paydays_caption)
                                else -> stringResource(R.string.monthly_payday_caption)
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
                title = stringResource(R.string.savings_goal),
                subtitle = goalSummary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .appear(6, opening),
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
                    .appear(7, opening),
            )
        }
    }
}

/** Lo que Perfil muestra y abre de tarjetas y suscripciones (viene de Resumen). */
class ProfileShortcuts(
    val cardsSubtitle: String,
    val cardChip: CardChip?,
    val subscriptionsSubtitle: String,
    val onOpenCards: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
)

@Composable
private fun ShortcutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chip: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 4.dp)
            .scaleOnPress(interaction),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = TransparentListItemColors,
            leadingContent = {
                TonalIcon(
                    icon,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            headlineContent = { Text(title) },
            supportingContent = {
                Column {
                    Text(subtitle)
                    if (chip != null) {
                        Spacer(Modifier.height(8.dp))
                        chip()
                    }
                }
            },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        )
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
            label = { Text(stringResource(R.string.income_per_period_label)) },
            placeholder = { Text("0") },
            prefix = { Text(currencySymbol) },
            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldCaption(stringResource(R.string.calculate_savings_by))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.byPercent,
                onClick = { viewModel.onSavingsModeChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { FitText(stringResource(R.string.percentage)) }
            SegmentedButton(
                selected = !state.byPercent,
                onClick = { viewModel.onSavingsModeChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { FitText(stringResource(R.string.fixed_amount)) }
        }
        if (state.byPercent) {
            OutlinedTextField(
                value = state.percentText,
                onValueChange = { text -> filterAmountInput(text, decimals = 4)?.let(viewModel::onPercentChange) },
                label = { Text(stringResource(R.string.savings_percentage)) },
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
                        label = { Text(formatPercent(preset.toBigDecimal())) },
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onAmountChange) },
                label = { Text(stringResource(R.string.amount_to_save)) },
                placeholder = { Text("0") },
                prefix = { Text(currencySymbol) },
                leadingIcon = { Icon(Icons.Rounded.Savings, contentDescription = null) },
                supportingText = state.computedPercentInfo(rememberStrings())?.let { { Text(it) } },
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
                label = stringResource(R.string.you_save),
                detail = stringResource(R.string.of_income, percentLabel((ratio * 100).toBigDecimal())),
                amount = state.estimatedSavings,
            )
            SplitRow(
                color = spendColor,
                label = stringResource(R.string.to_spend),
                detail = stringResource(R.string.of_income, percentLabel(((1f - ratio) * 100).toBigDecimal())),
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
        Text(status.text(rememberStrings()), style = MaterialTheme.typography.bodySmall, color = color)
    }
}
