package com.binc.gastapp.wo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.binc.gastapp.wo.ui.quickadd.formatearMonto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Pantalla principal: el total del dia arriba y, al bajar, los gastos uno por uno.
 * Es un ScalingLazyColumn y no un Column con scroll porque la lista puede crecer y
 * el escalado de los extremos es lo que se espera en un reloj.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    listState: ScalingLazyListState,
    onAddExpense: () -> Unit
) {
    // En un reloj la corona o el bisel son la forma normal de recorrer una lista.
    val foco = rememberActiveFocusRequester()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), foco),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 30.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Hoy",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formatearMonto(state.total),
                    style = MaterialTheme.typography.display2,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (state.count == 1) "1 gasto" else "${state.count} gastos",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            CompactChip(
                onClick = onAddExpense,
                label = { Text("Nuevo gasto") },
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    text = "Aún no hay gastos hoy",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        } else {
            items(state.rows, key = { it.id }) { fila ->
                SpendingItem(fila)
            }
        }

        item {
            Text(
                text = "Desliza para ver opciones",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SpendingItem(fila: SpendingRow) {
    Chip(
        onClick = { },
        // No lleva accion: el reloj no edita gastos, solo los muestra.
        enabled = false,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fila.title,
                    style = MaterialTheme.typography.button,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.62f)
                )
                Text(
                    text = formatearMonto(fila.amount),
                    style = MaterialTheme.typography.button,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1
                )
            }
        },
        secondaryLabel = {
            Text(
                text = if (fila.pending) "Por enviar" else horaLocal(fila.occurredAt),
                style = MaterialTheme.typography.caption3,
                color = if (fila.pending) {
                    MaterialTheme.colors.error
                } else {
                    MaterialTheme.colors.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    )
}

private fun horaLocal(epochMillis: Long): String =
    formatoHora.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
