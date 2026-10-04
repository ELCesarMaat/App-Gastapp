package com.binc.gastapp.wo.ui.home

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.binc.gastapp.wo.R
import com.binc.gastapp.wo.ui.quickadd.formatearMonto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoHora24 = DateTimeFormatter.ofPattern("HH:mm")
private val formatoHora12 = DateTimeFormatter.ofPattern("h:mm a")

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
                    text = stringResource(R.string.today),
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
                    text = pluralStringResource(R.plurals.expense_count, state.count, state.count),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            CompactChip(
                onClick = onAddExpense,
                label = { Text(stringResource(R.string.new_expense)) },
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_expenses_today),
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
                text = stringResource(R.string.swipe_for_options),
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
                text = if (fila.pending) stringResource(R.string.pending_send) else horaLocal(fila.occurredAt, DateFormat.is24HourFormat(LocalContext.current)),
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

/** Con el formato de hora del reloj: 24 h o 12 h. */
private fun horaLocal(epochMillis: Long, formato24: Boolean): String =
    (if (formato24) formatoHora24 else formatoHora12.withLocale(Locale.getDefault()))
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
