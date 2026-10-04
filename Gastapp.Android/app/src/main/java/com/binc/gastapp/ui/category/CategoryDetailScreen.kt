package com.binc.gastapp.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.AppSnackbarHost
import com.binc.gastapp.ui.components.GroupedRow
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.SwipeToDeleteRow
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.dayLabel
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.longRange
import com.binc.gastapp.ui.format.movementsText
import com.binc.gastapp.ui.spending.SpendingRow
import com.binc.gastapp.ui.theme.amountLarge

/**
 * Detalle de categoria (CategoryDetailPage de MAUI): total gastado en el periodo y sus
 * gastos por dia. Tocar uno abre su detalle; deslizarlo lo borra con "Deshacer".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    onBack: () -> Unit,
    onOpenSpending: (String) -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val opening = rememberJustOpened()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar") }
                },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        LazyColumn(
            contentPadding = padding.withExtra(top = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "total") {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenMargin, vertical = 8.dp)
                        .appear(0, opening),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total gastado", style = MaterialTheme.typography.labelLarge)
                        AnimatedAmount(state.total, style = amountLarge)
                        Text(
                            "${longRange(state.start, state.end)} · ${movementsText(state.count)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (state.loaded && state.groups.isEmpty()) {
                item(key = "vacio") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 32.dp)
                            .animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TonalIcon(Icons.AutoMirrored.Rounded.ReceiptLong, size = 56.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No hay gastos registrados para esta categoría en el periodo.",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            state.groups.forEachIndexed { groupIndex, group ->
                item(key = "dia-${group.day}") {
                    SectionHeader(
                        title = dayLabel(group.day, state.today),
                        action = { Text(formatMoney(group.total), style = MaterialTheme.typography.titleSmall) },
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .appear(1 + groupIndex, opening),
                    )
                }
                itemsIndexed(group.items, key = { _, item -> item.id }) { index, item ->
                    SwipeToDeleteRow(
                        onDelete = {
                            viewModel.delete(item.id) {
                                messages.showUndo("Gasto eliminado") { viewModel.restore(item.id) }
                            }
                        },
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .appear(2 + groupIndex, opening),
                    ) {
                        GroupedRow(index = index, count = group.items.size) {
                            SpendingRow(item = item, onClick = { onOpenSpending(item.id) })
                        }
                    }
                }
            }

            if (state.groups.isNotEmpty()) {
                item(key = "fin") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .animateItem(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Fin del resumen de esta categoría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
