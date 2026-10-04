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
import androidx.compose.ui.res.stringResource
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
import com.binc.gastapp.wo.R

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
                    text = stringResource(R.string.preparing),
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    // El plan gratuito de Render puede tardar casi un minuto en despertar.
                    text = stringResource(R.string.first_time_slow),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            is PairingState.ShowingCode -> {
                Text(
                    text = stringResource(R.string.link_your_watch),
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
                        ?: stringResource(R.string.link_instructions),
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
                    text = stringResource(R.string.watch_unlinked),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.link_again_anytime),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.link))
                }
            }

            is PairingState.Expired -> {
                Text(
                    text = stringResource(R.string.code_expired),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.generate_another))
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
                    Text(stringResource(R.string.retry))
                }
            }

            is PairingState.Success -> {
                Text(
                    text = stringResource(R.string.done),
                    style = MaterialTheme.typography.title2,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.watch_linked),
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
            label = { Text(stringResource(if (channel.testing) R.string.testing else R.string.test_phone)) },
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

@Composable
private fun formatearCuentaRegresiva(segundos: Int): String = stringResource(R.string.expires_in, segundos / 60, segundos % 60)
