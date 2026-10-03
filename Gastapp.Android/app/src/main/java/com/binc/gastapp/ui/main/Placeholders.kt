package com.binc.gastapp.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.ui.components.AnimatedAmount
import com.binc.gastapp.ui.components.GroupedRow
import com.binc.gastapp.ui.components.ListGroup
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.StatusChip
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.navigation.TopLevelTab
import com.binc.gastapp.ui.theme.amountLarge
import java.math.BigDecimal

// Pantallas provisionales de la Fase 0: el esqueleto navega y se ve con el tema real,
// pero el contenido llega en la Fase 4. Este archivo desaparece cuando cada pantalla
// tenga la suya.

private data class Shortcut(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

/** Resumen provisional: muestra del tema y accesos a las pantallas apiladas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryPlaceholder(
    contentPadding: PaddingValues,
    listState: LazyListState,
    onOpenCards: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onExplorePeriod: () -> Unit,
) {
    val shortcuts = listOf(
        Shortcut("Mis tarjetas", "Entra desde la derecha", Icons.Outlined.CreditCard, onOpenCards),
        Shortcut("Suscripciones", "Entra desde la derecha", Icons.Outlined.Autorenew, onOpenSubscriptions),
        Shortcut("Explorar periodo", "Entra desde abajo", Icons.Outlined.DateRange, onExplorePeriod),
    )

    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withExtra(top = 8.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenMargin)
                    .appear(0),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Vista previa del tema", style = MaterialTheme.typography.labelLarge)
                    AnimatedAmount(BigDecimal("1234.56"), style = amountLarge)
                    Text(
                        "El Resumen real (total del día, tira del periodo y gastos) se construye en la Fase 4.2.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip("Al corriente", statusColor(StatusLevel.OK))
                        StatusChip("Vence pronto", statusColor(StatusLevel.WARNING), pulse = true)
                        StatusChip("Vencido", statusColor(StatusLevel.CRITICAL), pulse = true)
                        StatusChip("En prueba", statusColor(StatusLevel.NEUTRAL))
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            LocalDataPreviewItem(1)
        }
        item {
            SectionHeader(
                "Pantallas apiladas",
                subtitle = "Para probar el atrás predictivo (spike 0.7)",
                modifier = Modifier.appear(1),
            )
        }
        item {
            ListGroup(shortcuts, modifier = Modifier.appear(2)) { shortcut ->
                ListItem(
                    modifier = Modifier.clickable(onClick = shortcut.onClick),
                    colors = TransparentListItemColors,
                    leadingContent = { TonalIcon(shortcut.icon) },
                    headlineContent = { Text(shortcut.title) },
                    supportingContent = { Text(shortcut.subtitle) },
                )
            }
        }
    }
}

/** Pestana vacia con el nombre de la fase que la construye. */
@Composable
fun TabPlaceholder(
    contentPadding: PaddingValues,
    tab: TopLevelTab,
    phase: String,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withExtra(top = 48.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item { TonalIcon(tab.selectedIcon, size = 72.dp, modifier = Modifier.appear(0)) }
        item {
            Text(
                tab.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .appear(1),
            )
        }
        item {
            Text(
                "Se construye en la $phase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = ScreenMargin, vertical = 8.dp)
                    .appear(2),
            )
        }
    }
}

/**
 * Pantalla apilada provisional. Lleva filas de relleno para que se note como se encoge
 * y se redondea durante el gesto de atras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackedPlaceholderScreen(
    title: String,
    phase: String,
    onBack: () -> Unit,
    closeIcon: Boolean = false,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val rows = 12
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (closeIcon) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding.withExtra(top = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Pantalla provisional para probar el atrás predictivo. La real se construye en la $phase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = ScreenMargin + 4.dp)
                        .padding(bottom = 12.dp)
                        .appear(0),
                )
            }
            items(rows) { index ->
                GroupedRow(index = index, count = rows, modifier = Modifier.appear(index + 1)) {
                    ListItem(
                        colors = TransparentListItemColors,
                        leadingContent = { TonalIcon(Icons.Outlined.Receipt) },
                        headlineContent = { Text("Elemento ${index + 1}") },
                        supportingContent = { Text("Relleno de ejemplo") },
                    )
                }
            }
        }
    }
}
