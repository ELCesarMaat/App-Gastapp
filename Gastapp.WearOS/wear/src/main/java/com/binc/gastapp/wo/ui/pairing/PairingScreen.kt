package com.binc.gastapp.wo.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun PairingScreen(
    state: PairingState,
    channel: ChannelState,
    autoPairStatus: String?,
    onRequestCode: () -> Unit,
    onTestChannel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is PairingState.Idle,
            is PairingState.RequestingCode -> {
                CircularProgressIndicator()
                Text(
                    text = "Preparando...",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    // El plan gratuito de Render puede tardar casi un minuto en despertar.
                    text = "La primera vez puede tardar",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            is PairingState.ShowingCode -> {
                Text(
                    text = "Vincula tu reloj",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = state.userCode,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )

                // Con el telefono a la vista el codigo se manda solo, asi que la
                // instruccion de teclearlo sobra y solo confunde.
                Text(
                    text = autoPairStatus
                        ?: "Gastapp en el teléfono:\nAjustes › Dispositivos › Vincular reloj",
                    style = MaterialTheme.typography.caption3,
                    color = if (autoPairStatus != null) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = formatearCuentaRegresiva(state.secondsLeft),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            is PairingState.Unlinked -> {
                Text(
                    text = "Reloj desvinculado",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Vincúlalo otra vez cuando quieras",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Vincular")
                }
            }

            is PairingState.Expired -> {
                Text(
                    text = "El código expiró",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Generar otro")
                }
            }

            is PairingState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Reintentar")
                }
            }

            is PairingState.Success -> {
                Text(
                    text = "¡Listo!",
                    style = MaterialTheme.typography.title2,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tu reloj quedó vinculado",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Temporal (Fase 0): comprobar el canal Bluetooth con el telefono sin
        // necesidad de estar vinculado.
        CompactChip(
            onClick = onTestChannel,
            enabled = !channel.testing,
            label = { Text(if (channel.testing) "Probando..." else "Probar teléfono") },
            modifier = Modifier.padding(top = 16.dp)
        )

        if (channel.message != null) {
            Text(
                text = channel.message,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

private fun formatearCuentaRegresiva(segundos: Int): String {
    val minutos = segundos / 60
    val resto = segundos % 60
    return "Expira en %d:%02d".format(minutos, resto)
}
