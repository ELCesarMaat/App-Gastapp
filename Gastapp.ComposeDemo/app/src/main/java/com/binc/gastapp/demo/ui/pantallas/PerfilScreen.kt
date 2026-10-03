package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.ui.AvatarInicial
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.EncabezadoSeccion
import com.binc.gastapp.demo.ui.GrupoLista
import com.binc.gastapp.demo.ui.IconoTonal
import com.binc.gastapp.demo.ui.MargenPantalla
import com.binc.gastapp.demo.ui.conExtra
import com.binc.gastapp.demo.ui.pesos

@Composable
fun PerfilScreen(contentPadding: PaddingValues) {
    var porPorcentaje by remember { mutableStateOf(true) }

    val ingreso = listOf(
        Triple(Icons.Rounded.AccountBalanceWallet, "Ingreso quincenal", pesos(18_000.0)),
        Triple(Icons.Rounded.Event, "Días de pago", "15 y 30 de cada mes"),
        Triple(Icons.Rounded.Savings, "Meta de ahorro", "20% · ${pesos(3_600.0)} por quincena"),
    )

    LazyColumn(contentPadding = contentPadding.conExtra(arriba = 8.dp, abajo = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarInicial("M", MaterialTheme.colorScheme.primary, tamano = 88.dp)
                Spacer(Modifier.height(12.dp))
                Text("Mariana Torres", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "mariana@ejemplo.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { EncabezadoSeccion("Ingreso y ahorro") }
        item {
            GrupoLista(ingreso) { (icono: ImageVector, titulo, valor) ->
                ListItem(
                    colors = ColoresFilaTransparente,
                    leadingContent = { IconoTonal(icono) },
                    headlineContent = { Text(titulo) },
                    supportingContent = { Text(valor) },
                )
            }
        }

        item { EncabezadoSeccion("Calcular el ahorro por") }
        item {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargenPantalla),
            ) {
                SegmentedButton(
                    selected = porPorcentaje,
                    onClick = { porPorcentaje = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Porcentaje") }
                SegmentedButton(
                    selected = !porPorcentaje,
                    onClick = { porPorcentaje = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Monto fijo") }
            }
        }

        item {
            OutlinedButton(
                onClick = {},
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargenPantalla, vertical = 28.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }
}
