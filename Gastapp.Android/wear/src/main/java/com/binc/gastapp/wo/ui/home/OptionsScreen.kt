package com.binc.gastapp.wo.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Segunda pagina, a un deslizamiento de la principal: estado de la sincronizacion y
 * las dos acciones que el reloj puede hacer por si mismo.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun OptionsScreen(
    state: HomeState,
    listState: ScalingLazyListState,
    onSyncNow: () -> Unit,
    onTestChannel: () -> Unit,
    onUnlink: () -> Unit
) {
    val foco = rememberActiveFocusRequester()

    // La confirmacion vive aqui y no en el ViewModel: es estado de esta pantalla y
    // debe olvidarse en cuanto el usuario se va a otra pagina.
    var confirmandoDesvinculacion by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScrollable(RotaryScrollableDefaults.behavior(listState), foco),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 30.dp)
    ) {
        item {
            Text(
                text = "Opciones",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        item { EstadoDeSincronizacion(state) }

        item {
            Chip(
                onClick = onSyncNow,
                enabled = !state.syncing && !state.unlinking,
                colors = if (state.allSynced) {
                    ChipDefaults.secondaryChipColors()
                } else {
                    ChipDefaults.primaryChipColors()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = {
                    Text(
                        text = if (state.syncing) "Sincronizando..." else "Sincronizar ahora",
                        maxLines = 1
                    )
                }
            )
        }

        if (state.syncMessage != null) {
            item {
                Text(
                    text = state.syncMessage,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }

        // Temporal (Fase 0): comprueba que el canal Bluetooth con el telefono existe
        // de ida y vuelta. Se quita cuando el canal se use de verdad.
        item {
            Chip(
                onClick = onTestChannel,
                enabled = !state.testingChannel && !state.unlinking,
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                label = {
                    Text(
                        text = if (state.testingChannel) "Probando..." else "Probar teléfono",
                        maxLines = 1
                    )
                }
            )
        }

        if (state.channelMessage != null) {
            item {
                Text(
                    text = state.channelMessage,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }

        if (!confirmandoDesvinculacion) {
            item {
                Chip(
                    onClick = { confirmandoDesvinculacion = true },
                    enabled = !state.unlinking && !state.syncing,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    label = { Text("Desvincular reloj", maxLines = 1) }
                )
            }
        } else {
            item {
                Text(
                    text = if (state.pending > 0) {
                        // Desvincular ya no intenta subir nada: la red por delante del
                        // borrado era justo lo que dejaba el reloj a medio desvincular.
                        "Perderás ${state.pending} sin enviar. Sincroniza antes."
                    } else {
                        "Tendrás que volver a vincularlo desde el teléfono."
                    },
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                )
            }

            item {
                Chip(
                    onClick = onUnlink,
                    enabled = !state.unlinking,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.error,
                        contentColor = MaterialTheme.colors.onError
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = if (state.unlinking) "Desvinculando..." else "Sí, desvincular",
                            maxLines = 1
                        )
                    }
                )
            }

            item {
                CompactChip(
                    onClick = { confirmandoDesvinculacion = false },
                    enabled = !state.unlinking,
                    label = { Text("Cancelar") },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EstadoDeSincronizacion(state: HomeState) {
    // Todo en una Column: el contenido de un item se apila en un Box, y dos Text
    // sueltos se dibujarian uno encima del otro.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        if (state.syncing || state.refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            return@Column
        }

        Text(
            text = when {
                state.allSynced -> "Todo sincronizado"
                state.pending == 1 -> "1 gasto sin enviar"
                state.pending > 0 -> "${state.pending} gastos sin enviar"
                state.failed == 1 -> "1 gasto rechazado"
                else -> "${state.failed} gastos rechazados"
            },
            style = MaterialTheme.typography.title3,
            color = if (state.allSynced) MaterialTheme.colors.primary else MaterialTheme.colors.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.failed > 0) {
            Text(
                text = "Sincronizar los reintenta",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}
