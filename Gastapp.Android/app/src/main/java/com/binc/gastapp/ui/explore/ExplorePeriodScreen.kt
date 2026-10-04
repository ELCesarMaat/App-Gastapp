package com.binc.gastapp.ui.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.domain.spendings.CategoryTotal
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.EmphasizedDecelerate
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.animateFromZero
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.MexicoLocale
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.longDate
import com.binc.gastapp.ui.format.longRange
import com.binc.gastapp.ui.format.monthYear
import com.binc.gastapp.ui.format.shortDate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val FirstDayOfWeek = WeekFields.of(MexicoLocale).firstDayOfWeek

/**
 * Explorar periodo (reemplazo del SfCalendar de MAUI, diseno del demo). Calendario
 * propio en lugar del DateRangePicker de Material: pinta en cada dia cuanto se gasto,
 * que es justo lo que uno busca al explorar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorePeriodScreen(
    onClose: () -> Unit,
    onApply: (LocalDate, LocalDate) -> Unit,
    onOpenCategory: (categoryId: String, start: LocalDate, end: LocalDate) -> Unit,
    viewModel: ExplorePeriodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val opening = rememberJustOpened()
    val months = state.months
    val pager = rememberPagerState(
        initialPage = months.indexOf(YearMonth.from(state.start ?: state.today)).coerceAtLeast(0),
    ) { months.size }

    // Al llegar la lista completa de meses, el mes del rango tiene otro indice.
    LaunchedEffect(months.size) {
        val target = months.indexOf(YearMonth.from(state.start ?: state.today))
        if (target >= 0 && target != pager.currentPage) pager.scrollToPage(target)
    }

    fun goToMonth(date: LocalDate) {
        val index = months.indexOf(YearMonth.from(date))
        if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") }
                },
                title = { Text("Explorar periodo") },
                actions = { TextButton(onClick = { goToMonth(state.today) }) { Text("Hoy") } },
            )
        },
        bottomBar = { ApplyBar(state, onApply) },
    ) { padding ->
        LazyColumn(contentPadding = padding.withExtra(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "atajos") {
                LazyRow(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .appear(0, opening),
                    contentPadding = PaddingValues(horizontal = ScreenMargin),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.shortcuts, key = { it.first.name }) { (shortcut, range) ->
                        FilterChip(
                            selected = state.start == range.first && state.end == range.second,
                            onClick = {
                                viewModel.onShortcut(shortcut, range)
                                goToMonth(range.first)
                            },
                            label = { Text(shortcut.label) },
                        )
                    }
                }
            }

            item(key = "encabezado") { RangeHeader(state, Modifier.appear(1, opening)) }

            item(key = "calendario") {
                CalendarCard(
                    pager = pager,
                    state = state,
                    onDayClick = { date ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        viewModel.onDayClick(date)
                    },
                    onMonth = { index -> scope.launch { pager.animateScrollToPage(index) } },
                    modifier = Modifier.appear(2, opening),
                )
            }

            val summary = state.summary
            if (state.hasRange && summary != null) {
                item(key = "resumen") {
                    RangeSummaryCard(
                        summary = summary,
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .appear(3, opening),
                    )
                }
                if (summary.topCategories.isNotEmpty()) {
                    item(key = "categorias") {
                        TopCategories(
                            categories = summary.topCategories,
                            total = summary.total,
                            onOpen = { categoryId -> onOpenCategory(categoryId, summary.start, summary.end) },
                            modifier = Modifier
                                .animateItem(fadeOutSpec = null)
                                .appear(4, opening),
                        )
                    }
                }
            } else if (!state.hasRange) {
                item(key = "pista") { EndDayHint(Modifier.animateItem(fadeOutSpec = null)) }
            }
        }
    }
}

@Composable
private fun RangeHeader(state: ExploreUiState, modifier: Modifier = Modifier) {
    val summary = state.summary
    AnimatedContent(
        targetState = Triple(state.start, state.end, summary?.movements),
        transitionSpec = {
            ((fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { -it / 3 })) using SizeTransform(clip = false)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin + 4.dp, vertical = 12.dp),
        label = "rango",
    ) { (start, end, movements) ->
        Column {
            Text(
                when {
                    start != null && end != null -> longRange(start, end)
                    start != null -> "Desde el ${longDate(start).replaceFirstChar { it.lowercase(MexicoLocale) }}"
                    else -> "Elige un periodo"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (start != null && end != null) {
                    val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
                    "$days ${if (days == 1) "día" else "días"} · ${movements ?: 0} movimientos"
                } else {
                    "Ahora toca el día en que termina"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarCard(
    pager: PagerState,
    state: ExploreUiState,
    onDayClick: (LocalDate) -> Unit,
    onMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = state.months
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = pager.currentPage.coerceIn(0, months.lastIndex),
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInVertically { if (forward) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (forward) -it else it } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "mes",
                ) { page -> Text(monthYear(months[page]), style = MaterialTheme.typography.titleMedium) }
                IconButton(onClick = { onMonth(pager.currentPage - 1) }, enabled = pager.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                IconButton(onClick = { onMonth(pager.currentPage + 1) }, enabled = pager.currentPage < months.lastIndex) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Mes siguiente")
                }
            }

            Row(Modifier.padding(vertical = 4.dp)) {
                repeat(7) { i ->
                    Text(
                        FirstDayOfWeek.plus(i.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, MexicoLocale).uppercase(MexicoLocale),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                MonthGrid(months[page], state, onDayClick)
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            IntensityLegend(Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, state: ExploreUiState, onDayClick: (LocalDate) -> Unit) {
    val blanks = (month.atDay(1).dayOfWeek.value - FirstDayOfWeek.value + 7) % 7
    Column {
        // Siempre 6 filas, para que el calendario no cambie de alto al pasar de mes.
        repeat(6) { row ->
            Row {
                repeat(7) { column ->
                    val number = row * 7 + column - blanks + 1
                    val date = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                    DayCell(
                        date = date,
                        column = column,
                        state = state,
                        level = date?.let { state.levels[it] } ?: 0,
                        onDayClick = onDayClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    column: Int,
    state: ExploreUiState,
    level: Int,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        if (date == null) return@Box
        val colors = MaterialTheme.colorScheme
        val start = state.start
        val end = state.end
        val isStart = date == start
        val isEnd = date == end
        val edge = isStart || isEnd
        val inside = start != null && end != null && date in start..end
        val future = date.isAfter(state.today)
        val isToday = date == state.today

        // La banda del rango aparece en "barrido": cada dia un poco despues del anterior.
        val delay = if (inside) (ChronoUnit.DAYS.between(start, date).toInt() * 14).coerceAtMost(420) else 0
        val band by animateFloatAsState(if (inside && start != end) 1f else 0f, tween(220, delayMillis = delay), label = "banda")
        val circle by animateFloatAsState(if (edge) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "circulo")
        val bandColor = colors.primaryContainer
        val circleColor = colors.primary

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    if (band > 0f) {
                        val height = 40.dp.toPx()
                        val top = (size.height - height) / 2
                        val left = if (isStart) size.width / 2 else 0f
                        val right = if (isEnd) size.width / 2 else size.width
                        val radius = CornerRadius(height / 2)
                        // La banda se redondea donde termina una semana o el rango.
                        val roundLeft = column == 0 && !isStart
                        val roundRight = column == 6 && !isEnd
                        val shape = RoundRect(
                            left = left,
                            top = top,
                            right = right,
                            bottom = top + height,
                            topLeftCornerRadius = if (roundLeft) radius else CornerRadius.Zero,
                            bottomLeftCornerRadius = if (roundLeft) radius else CornerRadius.Zero,
                            topRightCornerRadius = if (roundRight) radius else CornerRadius.Zero,
                            bottomRightCornerRadius = if (roundRight) radius else CornerRadius.Zero,
                        )
                        drawPath(Path().apply { addRoundRect(shape) }, bandColor.copy(alpha = band))
                    }
                    if (circle > 0f) {
                        drawCircle(circleColor, radius = 20.dp.toPx() * circle)
                    } else if (isToday) {
                        drawCircle(circleColor, radius = 20.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
                .semantics {
                    role = Role.Button
                    selected = edge
                    contentDescription = longDate(date)
                }
                .clickable(
                    enabled = !future,
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = 22.dp),
                ) { onDayClick(date) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (edge || isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        edge -> colors.onPrimary
                        future -> colors.outline.copy(alpha = 0.6f)
                        inside -> colors.onPrimaryContainer
                        else -> colors.onSurface
                    },
                )
                // Punto de intensidad: cuanto se gasto ese dia.
                val base = if (edge) colors.onPrimary else colors.primary
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .background(
                            when (level) {
                                0 -> Color.Transparent
                                1 -> base.copy(alpha = 0.35f)
                                2 -> base.copy(alpha = 0.65f)
                                else -> base
                            },
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun IntensityLegend(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Gasto por día",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text("Menos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0.35f, 0.65f, 1f).forEach { alpha ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .background(primary.copy(alpha = alpha), CircleShape),
            )
        }
        Text("Más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RangeSummaryCard(summary: RangeSummary, modifier: Modifier = Modifier) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Total del periodo", style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(summary.total, MaterialTheme.typography.headlineSmall, fromZero = false)
                }
                Column(Modifier.weight(1f)) {
                    Text("Promedio diario", style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(summary.dailyAverage, MaterialTheme.typography.headlineSmall, fromZero = false)
                }
            }
            Spacer(Modifier.height(16.dp))
            DaysChart(summary)
            summary.highestDay?.let { (day, amount) ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Día más alto: ${shortDate(day)} · ${formatMoney(amount)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Barras por dia del rango; crecen desde abajo cada vez que cambia el rango. */
@Composable
private fun DaysChart(summary: RangeSummary) {
    val values = summary.days.map { it.second.toFloat() }
    if (values.isEmpty()) return
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val maxIndex = values.indexOf(values.max())

    val growth = remember { Animatable(0f) }
    LaunchedEffect(summary.start, summary.end) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(800, easing = EmphasizedDecelerate))
    }

    val barColor = MaterialTheme.colorScheme.primary
    val maxColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .semantics { contentDescription = "Gráfica del gasto de cada día del periodo" },
    ) {
        val n = values.size
        val gap = (size.width / n * 0.3f).coerceIn(1.dp.toPx(), 6.dp.toPx())
        val width = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, value ->
            val height = (value / max * size.height * growth.value).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = when {
                    value <= 0f -> emptyColor
                    i == maxIndex -> maxColor
                    else -> barColor
                },
                topLeft = Offset(i * (width + gap), size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(minOf(width / 2, 6.dp.toPx())),
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(shortDate(summary.days.first().first), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(shortDate(summary.days.last().first), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopCategories(
    categories: List<CategoryTotal>,
    total: BigDecimal,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (total.signum() <= 0) return
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("En qué se fue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            categories.forEachIndexed { i, category ->
                val ratio = (category.amount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val animated = animateFromZero(ratio, delayMillis = 200 + i * 90)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = category.categoryId != null,
                            onClickLabel = "Ver gastos de ${category.name}",
                        ) { category.categoryId?.let(onOpen) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TonalIcon(categoryIcon(category.name), size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        // Nombre y monto a los extremos; si no caben juntos, el monto baja (fuente al 200 %).
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
                            Text(formatMoney(category.amount), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${(ratio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EndDayHint(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin + 4.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TonalIcon(Icons.Rounded.TouchApp, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            "Elige el día final en el calendario para ver cuánto gastaste en ese periodo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApplyBar(state: ExploreUiState, onApply: (LocalDate, LocalDate) -> Unit) {
    val start = state.start
    val end = state.end
    val days = if (start != null && end != null) ChronoUnit.DAYS.between(start, end).toInt() + 1 else null
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { if (start != null && end != null) onApply(start, end) },
                enabled = days != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                AnimatedContent(
                    targetState = days,
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "boton",
                ) { count ->
                    Text(
                        when (count) {
                            null -> "Elige el día final"
                            1 -> "Ver 1 día"
                            else -> "Ver $count días"
                        },
                    )
                }
            }
        }
    }
}
