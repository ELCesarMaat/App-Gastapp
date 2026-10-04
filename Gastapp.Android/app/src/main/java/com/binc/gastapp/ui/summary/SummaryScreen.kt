package com.binc.gastapp.ui.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.EmphasizedDecelerate
import com.binc.gastapp.ui.components.GroupedRow
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.StatusChip
import com.binc.gastapp.ui.components.SwipeToDeleteRow
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.scaleOnPress
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.dayLabel
import com.binc.gastapp.ui.format.longDate
import com.binc.gastapp.ui.format.movementsText
import com.binc.gastapp.ui.format.shortRange
import com.binc.gastapp.ui.format.weekdayShort
import com.binc.gastapp.ui.spending.SpendingItem
import com.binc.gastapp.ui.spending.SpendingRow
import com.binc.gastapp.ui.theme.amountLarge
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private val DayChipWidth = 56.dp

/** Lo que Resumen le pide a la pantalla principal. */
class SummaryActions(
    val onPreviousPeriod: () -> Unit,
    val onNextPeriod: () -> Unit,
    val onExplore: () -> Unit,
    val onSelectDay: (LocalDate) -> Unit,
    val onGoToToday: () -> Unit,
    val onOpenCards: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
    val onOpenSpending: (String) -> Unit,
    val onDeleteSpending: (SpendingItem) -> Unit,
    val onLogin: () -> Unit,
)

/** Resumen (SummaryPage de MAUI con el diseno de ResumenScreen del demo). */
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    sessionExpired: Boolean,
    contentPadding: PaddingValues,
    listState: LazyListState,
    actions: SummaryActions,
) {
    val period = state.period
    // Hasta tener datos no se dibuja la lista: si se agregaran despues el periodo y la
    // tira arriba de lo que ya se ve, la lista se quedaria anclada mas abajo y no se verian.
    if (!state.loaded || period == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val opening = rememberJustOpened()

    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withExtra(top = 4.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (sessionExpired) {
            item(key = "sesion") { ExpiredSessionBanner(onLogin = actions.onLogin, modifier = Modifier.animateItem()) }
        }

        item(key = "periodo") {
            PeriodNavigator(
                period = period,
                total = state.periodTotal,
                onPrevious = actions.onPreviousPeriod,
                onNext = actions.onNextPeriod,
                onExplore = actions.onExplore,
                modifier = Modifier.appear(0, opening),
            )
        }
        item(key = "tira") {
            PeriodStrip(
                period = period,
                days = state.days,
                selectedDay = state.selectedDay,
                onSelectDay = actions.onSelectDay,
                modifier = Modifier.appear(1, opening),
            )
        }

        item(key = "total") {
            DayTotalCard(
                day = state.selectedDay,
                today = state.today,
                total = state.dayTotal,
                items = state.dayItems,
                onGoToToday = actions.onGoToToday,
                modifier = Modifier.appear(2, opening),
            )
        }

        item(key = "atajo-tarjetas") {
            Shortcut(
                icon = Icons.Rounded.CreditCard,
                title = "Mis tarjetas y MSI",
                subtitle = state.cardsSubtitle,
                onClick = actions.onOpenCards,
                modifier = Modifier.appear(3, opening),
                chip = state.cardChip?.let { chip ->
                    {
                        StatusChip(chip.text, statusColor(chip.level), pulse = chip.level.ordinal > 0)
                    }
                },
            )
        }

        item(key = "atajo-suscripciones") {
            Shortcut(
                icon = Icons.Rounded.Autorenew,
                title = "Suscripciones y membresías",
                subtitle = state.subscriptionsSubtitle,
                onClick = actions.onOpenSubscriptions,
                modifier = Modifier.appear(4, opening),
            )
        }

        item(key = "encabezado") {
            SectionHeader(
                title = "Tus movimientos",
                subtitle = if (state.dayItems.isEmpty()) null else "Toca un gasto para ver su detalle · desliza para borrarlo",
                modifier = Modifier.appear(5, opening),
            )
        }

        if (state.dayItems.isEmpty()) {
            item(key = "vacio") { EmptyDay(Modifier.animateItem(fadeOutSpec = null)) }
        } else {
            itemsIndexed(state.dayItems, key = { _, item -> item.id }) { index, item ->
                SwipeToDeleteRow(
                    onDelete = { actions.onDeleteSpending(item) },
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .appear(6 + index, opening),
                ) {
                    GroupedRow(index = index, count = state.dayItems.size) {
                        SpendingRow(
                            item = item,
                            onClick = { actions.onOpenSpending(item.id) },
                            modifier = Modifier.semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Eliminar gasto") {
                                        actions.onDeleteSpending(item)
                                        true
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiredSessionBanner(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "Tu sesión venció. Tus datos están a salvo en este teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLogin) { Text("Entrar") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodNavigator(
    period: ResolvedPeriod,
    total: java.math.BigDecimal,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Periodo anterior")
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Explorar periodo", onClick = onExplore)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // El periodo se desliza hacia el lado en que se navega.
            AnimatedContent(
                targetState = period,
                transitionSpec = {
                    val forward = targetState.start.isAfter(initialState.start)
                    (slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { if (forward) it / 3 else -it / 3 } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(150)))
                },
                contentAlignment = Alignment.Center,
                label = "periodo",
            ) { shown ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shown.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shortRange(shown.start, shown.end), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            // Con la fuente muy grande el monto baja de renglon en vez de cortarse.
            FlowRow(horizontalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total del periodo ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedAmount(
                    value = total,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    fromZero = false,
                )
            }
        }
        IconButton(onClick = onNext, enabled = period.canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Periodo siguiente")
        }
    }
}

/**
 * Todos los dias del periodo, con scroll horizontal y una mini barra con lo gastado
 * respecto al dia mas alto. Cada periodo es una tira nueva que entra deslizandose
 * hacia el lado al que se navega (cambiar los elementos de una misma lista encimaba los
 * dias viejos con los nuevos).
 */
@Composable
private fun PeriodStrip(
    period: ResolvedPeriod,
    days: List<StripDay>,
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cada tira lleva sus propios dias: la que sale conserva los suyos durante la
    // transicion. contentKey hace que un cambio de montos no cuente como otra tira.
    AnimatedContent(
        targetState = days,
        contentKey = { list -> list.firstOrNull()?.date to list.lastOrNull()?.date },
        transitionSpec = {
            val forward = (targetState.firstOrNull()?.date ?: period.start) > (initialState.firstOrNull()?.date ?: period.start)
            (slideInHorizontally(tween(380, easing = EmphasizedDecelerate)) { if (forward) it / 2 else -it / 2 } +
                fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(260)) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(180)))
        },
        modifier = modifier.fillMaxWidth(),
        label = "tira",
    ) { shownDays ->
        StripDays(days = shownDays, selectedDay = selectedDay, onSelectDay = onSelectDay)
    }
}

@Composable
private fun StripDays(days: List<StripDay>, selectedDay: LocalDate, onSelectDay: (LocalDate) -> Unit) {
    val dates = remember(days) { days.map { it.date } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (dates.indexOf(selectedDay) - 2).coerceAtLeast(0))
    var centered by remember { mutableStateOf(false) }

    // Centra el dia elegido: de golpe la primera vez (la tira viene entrando) y con
    // animacion cuando se toca otro dia.
    LaunchedEffect(selectedDay, dates) {
        val index = dates.indexOf(selectedDay)
        if (index < 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
        listState.centerItem(index, animated = centered)
        centered = true
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = ScreenMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.date.toEpochDay() }) { day ->
            DayChip(
                day = day,
                selected = day.date == selectedDay,
                onClick = { onSelectDay(day.date) },
            )
        }
    }
}

/** Deja el elemento [index] en el centro de la tira (o lo mas cerca posible en los extremos). */
private suspend fun LazyListState.centerItem(index: Int, animated: Boolean) {
    fun distanceToCenter(): Float? {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        return (item.offset + item.size / 2 - center).toFloat()
    }

    if (distanceToCenter() == null) {
        if (animated) animateScrollToItem(index) else scrollToItem(index)
    }
    val distance = distanceToCenter() ?: return
    if (animated) animateScrollBy(distance, tween(350, easing = EmphasizedDecelerate)) else scrollBy(distance)
}

@Composable
private fun DayChip(day: StripDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerLow, tween(250), label = "fondo")
    val content by animateColorAsState(
        when {
            selected -> colors.onPrimary
            day.isFuture -> colors.outline
            else -> colors.onSurface
        },
        tween(250),
        label = "texto",
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.92f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "escala")
    val bar by animateFloatAsState(day.ratio, tween(600, easing = EmphasizedDecelerate), label = "barra")

    Surface(
        onClick = onClick,
        enabled = !day.isFuture,
        shape = RoundedCornerShape(18.dp),
        color = background,
        contentColor = content,
        modifier = modifier
            .width(DayChipWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                this.selected = selected
                contentDescription = longDate(day.date)
            },
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekdayShort(day.date), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            day.isFuture -> Color.Transparent
                            selected -> colors.onPrimary.copy(alpha = 0.3f)
                            else -> colors.outlineVariant.copy(alpha = 0.6f)
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bar.coerceIn(0f, 1f))
                        .background(if (selected) colors.onPrimary else colors.primary),
                )
            }
        }
    }
}

@Composable
private fun DayTotalCard(
    day: LocalDate,
    today: LocalDate,
    total: java.math.BigDecimal,
    items: List<SpendingItem>,
    onGoToToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardPurchases = items.count { it.isCardPurchase }
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total del día",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                AnimatedVisibility(visible = day != today, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    TextButton(onClick = onGoToToday) { Text("Ir a hoy") }
                }
            }
            AnimatedAmount(value = total, style = amountLarge)
            AnimatedContent(
                targetState = Triple(day, items.size, cardPurchases),
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 2 })
                },
                label = "detalle-dia",
            ) { (date, count, purchases) ->
                Column {
                    Text("${dayLabel(date, today)} · ${movementsText(count)}", style = MaterialTheme.typography.bodyMedium)
                    if (purchases > 0) {
                        Text(
                            if (purchases == 1) "1 compra con tarjeta no suma: cuenta al pagarla"
                            else "$purchases compras con tarjeta no suman: cuentan al pagarlas",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Shortcut(
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

@Composable
private fun EmptyDay(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TonalIcon(Icons.AutoMirrored.Rounded.ReceiptLong, size = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Todavía no tienes gastos registrados para esta fecha.",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Toca \"Nuevo gasto\" para agregar uno en este día.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
