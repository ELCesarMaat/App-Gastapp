package com.binc.gastapp.ui.savings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.binc.gastapp.R
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.EmphasizedDecelerate
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.GroupedRow
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.StatusChip
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.animateFromZero
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.isLargeFontScale
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.categoryLabel
import com.binc.gastapp.ui.format.formatDecimal
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.shortRange
import com.binc.gastapp.ui.summary.text
import com.binc.gastapp.ui.theme.LocalStatusColors
import com.binc.gastapp.ui.theme.amountLarge
import com.binc.gastapp.ui.theme.amountMedium
import com.binc.gastapp.ui.theme.parseColorHex
import java.math.BigDecimal
import kotlin.math.roundToInt

class SavingsActions(
    val onPreviousPeriod: () -> Unit,
    val onNextPeriod: () -> Unit,
    val onOpenCards: () -> Unit,
    val onOpenCategory: (CategoryShare) -> Unit,
    val onPayCard: (PendingCard) -> Unit,
)

/** Ahorros (SavesPage de MAUI con el diseno de AhorrosScreen del demo). */
@Composable
fun SavingsScreen(
    state: SavingsUiState,
    contentPadding: PaddingValues,
    listState: LazyListState,
    actions: SavingsActions,
) {
    val period = state.period
    if (!state.loaded || period == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val opening = rememberJustOpened()
    val budget = state.budget

    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withExtra(top = 4.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "periodo") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .appear(0, opening),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onPreviousPeriod) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_period))
                }
                AnimatedContent(
                    targetState = period,
                    transitionSpec = {
                        val forward = targetState.start.isAfter(initialState.start)
                        (slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { if (forward) it / 3 else -it / 3 } +
                            fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(150)))
                    },
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                    label = "periodo",
                ) { shown ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(shown.label.text(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(shortRange(shown.start, shown.end), style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(onClick = actions.onNextPeriod, enabled = period.canGoNext) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.next_period))
                }
            }
        }

        // Total gastado + salud del periodo.
        item(key = "total") {
            val health = budget?.let { healthInfo(it.health) }
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenMargin, vertical = 8.dp)
                    .appear(1, opening),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(stringResource(R.string.total_spent), style = MaterialTheme.typography.labelLarge)
                    AnimatedAmount(state.totalSpending, amountLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (health != null) {
                            StatusChip(stringResource(health.label), statusColor(health.level), pulse = health.level == StatusLevel.CRITICAL)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (state.isPastPeriod) pluralStringResource(R.plurals.period_days_past, state.elapsedDays, state.elapsedDays)
                            else stringResource(R.string.period_day_of, state.elapsedDays, state.naturalDayCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (health != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(health.message), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (budget != null) {
            item(key = "presupuesto") { BudgetCard(state, Modifier.appear(2, opening)) }
        }

        if (state.isPastPeriod && budget != null) {
            item(key = "ahorro") { SavedCard(budget.savedOrExceeded, budget.exceededSalary, Modifier.appear(3, opening)) }
        }

        item(key = "estadisticas") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenMargin, vertical = 8.dp)
                    .appear(3, opening),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniStat(
                    icon = Icons.Rounded.CalendarToday,
                    title = stringResource(R.string.daily_average),
                    value = { AnimatedAmount(budget?.dailyAverage ?: BigDecimal.ZERO, MaterialTheme.typography.titleLarge) },
                    detail = pluralStringResource(R.plurals.in_days_count, state.elapsedDays, state.elapsedDays),
                    modifier = Modifier.weight(1f),
                )
                val top = state.topCategory
                MiniStat(
                    icon = Icons.Rounded.Star,
                    title = stringResource(R.string.top_category),
                    value = {
                        Text(
                            top?.total?.name?.let { categoryLabel(it) } ?: stringResource(R.string.no_spending),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    detail = top?.let { stringResource(R.string.amount_and_percent, formatMoney(it.total.amount), formatDecimal(it.percent, 1)) }
                        ?: stringResource(R.string.no_movements_yet),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.pendingCards.isNotEmpty()) {
            item(key = "tarjetas-encabezado") {
                SectionHeader(
                    title = stringResource(R.string.cards_to_pay),
                    subtitle = stringResource(R.string.cards_to_pay_subtitle),
                    action = { TextButton(onClick = actions.onOpenCards) { Text(stringResource(R.string.see_cards)) } },
                    modifier = Modifier.appear(4, opening),
                )
            }
            itemsIndexed(state.pendingCards, key = { _, card -> "tarjeta-${card.summary.card.creditCardId}" }) { index, pending ->
                GroupedRow(
                    index = index,
                    count = state.pendingCards.size,
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .appear(5 + index, opening),
                ) {
                    PendingCardRow(pending, onPay = { actions.onPayCard(pending) })
                }
            }
        }

        item(key = "categorias-encabezado") {
            SectionHeader(
                title = stringResource(R.string.category_distribution),
                subtitle = if (state.categories.isEmpty()) null else stringResource(R.string.category_distribution_hint),
                modifier = Modifier.appear(6, opening),
            )
        }
        if (state.categories.isEmpty()) {
            item(key = "sin-gastos") {
                Text(
                    stringResource(R.string.category_distribution_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = ScreenMargin + 4.dp)
                        .appear(7, opening),
                )
            }
        } else {
            itemsIndexed(state.categories, key = { _, share -> "cat-${share.total.categoryId}" }) { index, share ->
                GroupedRow(
                    index = index,
                    count = state.categories.size,
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .appear(7 + index, opening),
                ) {
                    CategoryShareRow(share, index, onClick = { actions.onOpenCategory(share) })
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(state: SavingsUiState, modifier: Modifier = Modifier) {
    val budget = state.budget ?: return
    val statusColors = LocalStatusColors.current
    val usage = animateFromZero((budget.progressPercent.toFloat() / 100f).coerceIn(0f, 1f), delayMillis = 250, durationMillis = 1100)
    val remaining = budget.remainingBudget
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // Con la fuente muy grande "del límite" no cabe en el hueco del anillo: va debajo.
            val captionBelow = isLargeFontScale
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { usage },
                        modifier = Modifier.size(112.dp),
                        strokeWidth = 12.dp,
                        color = statusColor(healthInfo(budget.health).level).strong,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        strokeCap = StrokeCap.Round,
                    )
                    // Dentro del hueco del anillo: si no cabe, el texto se encoge en vez de salirse.
                    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // El porcentaje real (puede pasar de 100); el anillo se topa en lleno.
                        FitText(stringResource(R.string.percent, budget.percent.toFloat().roundToInt()), style = amountMedium)
                        if (!captionBelow) {
                            Text(stringResource(R.string.of_limit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (captionBelow) {
                    Text(
                        stringResource(R.string.of_limit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.budget_usage), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.budget_spent_of, formatMoney(state.totalSpending), formatMoney(budget.maxTotalSpending)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (remaining.signum() >= 0) R.string.budget_left else R.string.budget_exceeded),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedAmount(
                    remaining.abs(),
                    MaterialTheme.typography.titleLarge,
                    color = if (remaining.signum() >= 0) statusColors.ok.strong else statusColors.critical.strong,
                )
                Text(
                    stringResource(if (remaining.signum() >= 0) R.string.budget_within else R.string.budget_over),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Solo en periodos pasados: cuanto se ahorro del sueldo, o cuanto se paso. */
@Composable
private fun SavedCard(amount: BigDecimal, exceeded: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val color = if (exceeded) colors.critical else colors.ok
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = color.container, contentColor = color.onContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIcon(Icons.Rounded.Savings, containerColor = color.strong, color = Color.White)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(if (exceeded) R.string.saved_exceeded_title else R.string.saved_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                AnimatedAmount(amount, amountMedium)
                Text(
                    stringResource(if (exceeded) R.string.saved_exceeded_message else R.string.saved_message),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    icon: ImageVector,
    title: String,
    value: @Composable () -> Unit,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            TonalIcon(
                icon,
                size = 32.dp,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            value()
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PendingCardRow(pending: PendingCard, onPay: () -> Unit) {
    val card = pending.summary.card
    // Con la fuente muy grande el boton va debajo: a un lado deja el texto sin ancho.
    val stacked = isLargeFontScale
    ListItem(
        colors = TransparentListItemColors,
        leadingContent = {
            Box(
                Modifier
                    .size(width = 40.dp, height = 28.dp)
                    .background(parseColorHex(card.colorHex) ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
            )
        },
        headlineContent = { Text(card.cardName.ifBlank { card.bankName }) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.balance_amount, formatMoney(pending.summary.totalDebt)))
                Spacer(Modifier.height(6.dp))
                StatusChip(pending.statusText, statusColor(pending.level), pulse = pending.level.ordinal > 0)
                if (stacked) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onPay) { Text(stringResource(R.string.register_payment)) }
                }
            }
        },
        trailingContent = if (stacked) null else ({ FilledTonalButton(onClick = onPay) { Text(stringResource(R.string.register_payment)) } }),
    )
}

/** Paleta de las barras por categoria (CategoryPalette de MAUI). */
private val CategoryPalette = listOf("#126E63", "#1E8477", "#2F9D8F", "#F2A65A", "#E76F51", "#4D7CFE", "#7A8C52")

@Composable
private fun CategoryShareRow(share: CategoryShare, index: Int, onClick: () -> Unit) {
    val animated = animateFromZero(share.ratio, delayMillis = 350 + index * 80)
    val accent = parseColorHex(CategoryPalette[index % CategoryPalette.size]) ?: MaterialTheme.colorScheme.primary
    val name = categoryLabel(share.total.name)
    ListItem(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.see_category_spending, name), onClick = onClick),
        colors = TransparentListItemColors,
        leadingContent = { TonalIcon(categoryIcon(share.total.name)) },
        headlineContent = {
            Row {
                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMoney(share.total.amount), style = MaterialTheme.typography.titleSmall)
            }
        },
        supportingContent = {
            Column {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.percent_of_total, formatDecimal(share.percent, 1)))
            }
        },
    )
}
