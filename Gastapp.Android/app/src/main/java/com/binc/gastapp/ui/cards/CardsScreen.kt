package com.binc.gastapp.ui.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.AppSnackbarHost
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.GroupedRow
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.StatusChip
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.animateFromZero
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.dayMonth
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.relativeDay
import com.binc.gastapp.ui.format.shortDate
import com.binc.gastapp.ui.spending.SpendingFormRequest
import com.binc.gastapp.ui.spending.SpendingFormResult
import com.binc.gastapp.ui.spending.SpendingFormSheet
import com.binc.gastapp.ui.spending.SpendingRow
import com.binc.gastapp.ui.spending.spendingItems
import com.binc.gastapp.ui.theme.amountMedium
import com.binc.gastapp.ui.theme.parseColorHex
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** Que hoja o dialogo esta abierto sobre Mis tarjetas. */
private sealed interface CardsOverlay {
    data class Pay(val summary: CardSummary) : CardsOverlay
    data class Adjust(val summary: CardSummary) : CardsOverlay
    data class ConfirmDelete(val summary: CardSummary) : CardsOverlay
    data class Purchase(val request: SpendingFormRequest) : CardsOverlay
}

/**
 * Mis tarjetas (CreditCardsPage de MAUI con el diseno de TarjetasScreen del demo):
 * resumen global, carrusel, detalle animado de la tarjeta elegida, acciones, compras a
 * MSI activas y compras del ciclo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onOpenSpending: (String) -> Unit,
    viewModel: CardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var overlay by remember { mutableStateOf<CardsOverlay?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Mis tarjetas") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar") }
                },
                actions = {
                    IconButton(onClick = onAddCard) { Icon(Icons.Rounded.Add, contentDescription = "Agregar tarjeta") }
                },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize())
            !state.hasCards -> EmptyCards(padding, onAddCard)
            else -> CardsContent(
                state = state,
                padding = padding,
                onPay = { overlay = CardsOverlay.Pay(it) },
                onAdjust = { overlay = CardsOverlay.Adjust(it) },
                onNewPurchase = { overlay = CardsOverlay.Purchase(SpendingFormRequest.New(state.today, it.card.creditCardId, msi = false)) },
                onMsiPurchase = { overlay = CardsOverlay.Purchase(SpendingFormRequest.New(state.today, it.card.creditCardId, msi = true)) },
                onEdit = { onEditCard(it.card.creditCardId) },
                onDelete = { overlay = CardsOverlay.ConfirmDelete(it) },
                onOpenSpending = onOpenSpending,
            )
        }
    }

    when (val current = overlay) {
        is CardsOverlay.Pay -> CardPaymentSheet(
            summary = current.summary,
            onConfirm = { amount ->
                overlay = null
                viewModel.registerPayment(current.summary, amount) { messages.show("Pago de ${formatMoney(amount)} registrado.") }
            },
            onDismiss = { overlay = null },
        )
        is CardsOverlay.Adjust -> CardAdjustSheet(
            summary = current.summary,
            onConfirm = { amount ->
                overlay = null
                viewModel.adjustBalance(current.summary, amount) { changed ->
                    messages.show(
                        if (changed) "Saldo de ${current.summary.card.cardName} ajustado a ${formatMoney(amount)}"
                        else "El saldo ya era ${formatMoney(amount)}.",
                    )
                }
            },
            onDismiss = { overlay = null },
        )
        is CardsOverlay.ConfirmDelete -> AlertDialog(
            onDismissRequest = { overlay = null },
            title = { Text("Eliminar tarjeta") },
            text = {
                Text(
                    "¿Seguro que deseas eliminar la tarjeta '${current.summary.card.cardName}'?\n" +
                        "Los registros de compras se conservarán pero ya no estarán vinculados.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        overlay = null
                        viewModel.delete(current.summary) { messages.show("Tarjeta eliminada.") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { overlay = null }) { Text("Cancelar") } },
        )
        is CardsOverlay.Purchase -> SpendingFormSheet(
            request = current.request,
            onDismiss = { overlay = null },
            onResult = { result ->
                overlay = null
                if (result is SpendingFormResult.Saved) messages.show("Compra registrada")
            },
        )
        null -> Unit
    }
}

@Composable
private fun EmptyCards(padding: PaddingValues, onAddCard: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TonalIcon(Icons.Rounded.CreditCard, size = 72.dp, modifier = Modifier.appear(0))
        Spacer(Modifier.height(16.dp))
        Text(
            "Aún no tienes tarjetas registradas",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(1),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Registra tus tarjetas de crédito para dar seguimiento a tus fechas de corte, crédito disponible, " +
                "límite usado y compras a meses sin intereses (MSI).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(2),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddCard, modifier = Modifier.appear(3)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Agregar tarjeta")
        }
    }
}

@Composable
private fun CardsContent(
    state: CardsUiState,
    padding: PaddingValues,
    onPay: (CardSummary) -> Unit,
    onAdjust: (CardSummary) -> Unit,
    onNewPurchase: (CardSummary) -> Unit,
    onMsiPurchase: (CardSummary) -> Unit,
    onEdit: (CardSummary) -> Unit,
    onDelete: (CardSummary) -> Unit,
    onOpenSpending: (String) -> Unit,
) {
    val summaries = state.summaries
    val pager = rememberPagerState { summaries.size }
    // Si se borra la ultima tarjeta del carrusel, la pagina actual ya no existe.
    LaunchedEffect(summaries.size) {
        if (pager.currentPage > summaries.lastIndex) pager.scrollToPage(summaries.lastIndex.coerceAtLeast(0))
    }
    val page = pager.currentPage.coerceIn(0, summaries.lastIndex)
    val selected = summaries[page]
    val opening = rememberJustOpened()
    val cycleItems = spendingItems(selected.currentCycleSpendings.sortedByDescending { it.date }, state.categories, listOf(selected.card))
    val cardKey = selected.card.creditCardId

    LazyColumn(contentPadding = padding.withExtra(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "global") { GlobalSummary(state, Modifier.appear(0, opening)) }
        item(key = "carrusel") { CardCarousel(summaries, pager, Modifier.appear(1, opening)) }

        item(key = "detalle") {
            // El detalle cambia con la tarjeta, deslizandose hacia el mismo lado.
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    ((slideInHorizontally(tween(320)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(250, delayMillis = 60))) togetherWith
                        (slideOutHorizontally(tween(200)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(150)))) using
                        SizeTransform(clip = false)
                },
                modifier = Modifier.appear(2, opening),
                label = "detalle",
            ) { shownPage ->
                summaries.getOrNull(shownPage)?.let { CardDetail(it, state, onPay = { onPay(it) }) }
            }
        }

        item(key = "acciones") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .appear(3, opening),
            ) {
                item { ActionChip("Ajustar saldo", Icons.Rounded.Tune) { onAdjust(selected) } }
                item { ActionChip("Nueva compra", Icons.Rounded.ShoppingBag) { onNewPurchase(selected) } }
                item { ActionChip("Compra a MSI", Icons.Rounded.CalendarMonth) { onMsiPurchase(selected) } }
                item { ActionChip("Editar", Icons.Rounded.Edit) { onEdit(selected) } }
                item {
                    AssistChip(
                        onClick = { onDelete(selected) },
                        label = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                            leadingIconContentColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
            }
        }

        item(key = "encabezado-msi") {
            SectionHeader(
                title = "Compras a meses sin intereses",
                subtitle = "Plazos diferidos de ${selected.card.cardName}",
                action = {
                    if (selected.activeMsiCount > 0) {
                        StatusChip("${selected.activeMsiCount} activas", statusColor(StatusLevel.NEUTRAL))
                    }
                },
                modifier = Modifier.appear(4, opening),
            )
        }
        if (selected.activeMsiSpendings.isEmpty()) {
            item(key = "msi-vacio-$cardKey") {
                EmptyLine("No tienes compras a meses sin intereses en esta tarjeta.", Modifier.animateItem(fadeOutSpec = null))
            }
        } else {
            itemsIndexed(selected.activeMsiSpendings, key = { _, s -> "msi-$cardKey-${s.spendingId}" }) { index, msi ->
                val progress = animateFromZero(
                    (msi.currentInstallment.toFloat() / maxOf(1, msi.totalInstallments)).coerceIn(0f, 1f),
                    delayMillis = 300 + index * 100,
                )
                val monthly = if (msi.installmentMonthlyAmount.signum() > 0) msi.installmentMonthlyAmount
                else msi.amount.divide(java.math.BigDecimal(maxOf(1, msi.totalInstallments)), 2, java.math.RoundingMode.HALF_EVEN)
                val categoryName = state.categories.nameOf(msi.categoryId)
                GroupedRow(
                    index = index,
                    count = selected.activeMsiSpendings.size,
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .appear(5 + index, opening),
                ) {
                    ListItem(
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                        colors = TransparentListItemColors,
                        leadingContent = { TonalIcon(categoryIcon(categoryName)) },
                        headlineContent = { Text(msi.title.ifBlank { categoryName }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text("Mensualidad ${msi.currentInstallment} de ${msi.totalInstallments} · Total ${formatMoney(msi.amount)}")
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                                )
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(monthly), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("al mes", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
                }
            }
        }

        item(key = "encabezado-ciclo") {
            SectionHeader(
                title = "Compras en este ciclo",
                subtitle = "Del corte anterior al ${shortDate(selected.nextCutOffDate)}",
                modifier = Modifier.appear(6, opening),
            )
        }
        if (cycleItems.isEmpty()) {
            item(key = "ciclo-vacio-$cardKey") {
                EmptyLine("No hay gastos registrados en el ciclo de corte actual.", Modifier.animateItem(fadeOutSpec = null))
            }
        } else {
            itemsIndexed(cycleItems, key = { _, item -> "ciclo-$cardKey-${item.id}" }) { index, item ->
                GroupedRow(
                    index = index,
                    count = cycleItems.size,
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .appear(7 + index, opening),
                ) {
                    SpendingRow(item = item, onClick = { onOpenSpending(item.id) }, showTime = false)
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = ScreenMargin + 4.dp),
    )
}

@Composable
private fun GlobalSummary(state: CardsUiState, modifier: Modifier = Modifier) {
    val usage = animateFromZero(state.combinedUsage, delayMillis = 250)
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Deuda total", style = MaterialTheme.typography.labelLarge)
                    AnimatedAmount(state.totalDebt, amountMedium)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Crédito disponible", style = MaterialTheme.typography.labelLarge)
                    AnimatedAmount(state.totalAvailable, amountMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { usage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Límite combinado ${formatMoney(state.totalLimit)} · Ciclo actual ${formatMoney(state.totalCurrentCycle)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CardCarousel(summaries: List<CardSummary>, pager: PagerState, modifier: Modifier = Modifier) {
    Column(modifier) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.padding(top = 16.dp),
        ) { page ->
            PlasticCard(
                summaries[page],
                // Las tarjetas de los lados se ven mas chicas y tenues, y crecen al centrarse.
                Modifier.graphicsLayer {
                    val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.1f * distance
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.45f * distance
                },
            )
        }
        if (summaries.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(summaries.size) { i ->
                    val width by animateDpAsState(if (i == pager.currentPage) 18.dp else 6.dp, label = "indicador")
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = width, height = 6.dp)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlasticCard(summary: CardSummary, modifier: Modifier = Modifier) {
    val card = summary.card
    val color = parseColorHex(card.colorHex) ?: MaterialTheme.colorScheme.primary
    val gradient = Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.35f)))
    // Alto minimo de tarjeta de plastico; con la fuente muy grande crece en vez de encimar textos.
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 184.dp)
            .background(gradient, RoundedCornerShape(24.dp))
            .padding(20.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${card.cardName}, ${card.bankName}, deuda ${formatMoney(summary.totalDebt)}"
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(card.bankName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(card.cardName, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
        card.lastFourDigits?.takeIf { it.isNotBlank() }?.let {
            Text(
                "•••• $it",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Deuda", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                FitText(formatMoney(summary.totalDebt), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            if (summary.creditLimit.signum() > 0) {
                Text("${summary.usagePercentage.roundToInt()}% usado", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CardDetail(summary: CardSummary, state: CardsUiState, onPay: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Figure("Pago para no generar intereses", summary.currentCycleAmount, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Figure("Deuda futura MSI", summary.totalMsiRemainingDebt, Modifier.weight(1f))
            }
            if (summary.creditLimit.signum() > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Disponible ${formatMoney(summary.availableCredit)} de ${formatMoney(summary.creditLimit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(summary.usageLevel).strong,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                DateInfo(
                    title = "Próximo corte",
                    date = shortDate(summary.nextCutOffDate, state.today),
                    chip = { StatusChip(relativeDay(summary.nextCutOffDate, state.today), statusColor(StatusLevel.NEUTRAL)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                DateInfo(
                    title = "Límite de pago",
                    date = shortDate(summary.nextPaymentDueDate, state.today),
                    chip = {
                        StatusChip(
                            // La fecha ya se ve arriba: el chip solo dice cuanto falta.
                            summary.paymentStatusText(::dayMonth).substringBefore(" ("),
                            statusColor(summary.paymentLevel),
                            pulse = summary.paymentLevel != StatusLevel.OK,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar pago")
            }
        }
    }
}

@Composable
private fun Figure(title: String, value: java.math.BigDecimal, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedAmount(value, MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DateInfo(title: String, date: String, chip: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            chip()
        }
    }
}
