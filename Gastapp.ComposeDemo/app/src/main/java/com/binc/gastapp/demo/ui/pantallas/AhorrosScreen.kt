package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Estado
import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.RepositorioDemo
import com.binc.gastapp.demo.datos.enRango
import com.binc.gastapp.demo.datos.presupuestoPeriodo
import com.binc.gastapp.demo.datos.quincenaDe
import com.binc.gastapp.demo.datos.tarjetas
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.EncabezadoSeccion
import com.binc.gastapp.demo.ui.EstadoChip
import com.binc.gastapp.demo.ui.GrupoLista
import com.binc.gastapp.demo.ui.IconoTonal
import com.binc.gastapp.demo.ui.MargenPantalla
import com.binc.gastapp.demo.ui.MontoAnimado
import com.binc.gastapp.demo.ui.animarDesdeCero
import com.binc.gastapp.demo.ui.aparecer
import com.binc.gastapp.demo.ui.colorDeEstado
import com.binc.gastapp.demo.ui.conExtra
import com.binc.gastapp.demo.ui.pesos
import com.binc.gastapp.demo.ui.rangoCorto
import com.binc.gastapp.demo.ui.recienAbierta
import com.binc.gastapp.demo.ui.theme.LocalColoresEstado
import com.binc.gastapp.demo.ui.theme.cifraGrande
import com.binc.gastapp.demo.ui.theme.cifraMediana
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun AhorrosScreen(
    contentPadding: PaddingValues,
    estadoLista: LazyListState,
    onAbrirTarjetas: () -> Unit,
) {
    val periodo = quincenaDe(Hoy)
    val gastos = RepositorioDemo.gastos.enRango(periodo)
    val total = gastos.sumOf { it.monto }
    val diasTranscurridos = ChronoUnit.DAYS.between(periodo.inicio, Hoy).toInt() + 1
    val uso = (total / presupuestoPeriodo).toFloat()
    val porCategoria = gastos
        .groupBy { it.categoria }
        .mapValues { (_, lista) -> lista.sumOf { it.monto } }
        .toList()
        .sortedByDescending { it.second }
    val principal = porCategoria.firstOrNull()
    val ok = LocalColoresEstado.current.ok
    val entrando = recienAbierta()

    LazyColumn(
        state = estadoLista,
        contentPadding = contentPadding.conExtra(arriba = 4.dp, abajo = 96.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aparecer(0, entrando),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Periodo anterior")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Quincena actual",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(rangoCorto(periodo), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Periodo siguiente")
                }
            }
        }

        // Total gastado + estado de salud del periodo.
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargenPantalla, vertical = 8.dp)
                    .aparecer(1, entrando),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text("Total gastado", style = MaterialTheme.typography.labelLarge)
                    MontoAnimado(total, cifraGrande)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EstadoChip("Vas bien", ok)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Día $diasTranscurridos de ${periodo.dias} del periodo",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Uso del presupuesto: el anillo se llena y el porcentaje cuenta al entrar.
        item {
            val usoAnimado = animarDesdeCero(uso, retraso = 250, duracion = 1100)
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargenPantalla, vertical = 8.dp)
                    .aparecer(2, entrando),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { usoAnimado },
                            modifier = Modifier.size(112.dp),
                            strokeWidth = 12.dp,
                            trackColor = MaterialTheme.colorScheme.secondaryContainer,
                            strokeCap = StrokeCap.Round,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(usoAnimado * 100).roundToInt()}%", style = cifraMediana)
                            Text(
                                "del límite",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Uso de presupuesto", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Llevas ${pesos(total)} de ${pesos(presupuestoPeriodo)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Te quedan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MontoAnimado(
                            presupuestoPeriodo - total,
                            MaterialTheme.typography.titleLarge,
                            color = ok.fuerte,
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargenPantalla, vertical = 8.dp)
                    .aparecer(3, entrando),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniEstadistica(
                    icono = Icons.Rounded.CalendarToday,
                    titulo = "Promedio diario",
                    valor = { MontoAnimado(total / diasTranscurridos, MaterialTheme.typography.titleLarge) },
                    detalle = "En $diasTranscurridos días",
                    modifier = Modifier.weight(1f),
                )
                MiniEstadistica(
                    icono = Icons.Rounded.Star,
                    titulo = "Categoría principal",
                    valor = { Text(principal?.first?.nombre ?: "—", style = MaterialTheme.typography.titleLarge) },
                    detalle = principal?.let { "${pesos(it.second)} · ${(it.second / total * 100).roundToInt()}%" } ?: "",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            EncabezadoSeccion(
                titulo = "Tarjetas por pagar",
                subtitulo = "Saldos pendientes y vencimientos",
                accion = { TextButton(onClick = onAbrirTarjetas) { Text("Ver wallet") } },
                modifier = Modifier.aparecer(4, entrando),
            )
        }

        item {
            GrupoLista(tarjetas, modifier = Modifier.aparecer(5, entrando)) { tarjeta ->
                ListItem(
                    colors = ColoresFilaTransparente,
                    leadingContent = {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .background(tarjeta.color, RoundedCornerShape(6.dp))
                        )
                    },
                    headlineContent = { Text(tarjeta.etiqueta) },
                    supportingContent = { Text("Saldo ${pesos(tarjeta.deuda)}") },
                    trailingContent = {
                        EstadoChip(
                            tarjeta.textoPago,
                            colorDeEstado(tarjeta.estadoPago),
                            pulsar = tarjeta.estadoPago != Estado.AlCorriente,
                        )
                    },
                )
            }
        }

        item {
            EncabezadoSeccion(
                titulo = "Distribución por categoría",
                subtitulo = "Peso de cada categoría en lo que llevas del periodo",
                modifier = Modifier.aparecer(6, entrando),
            )
        }

        item {
            GrupoLista(porCategoria, modifier = Modifier.aparecer(7, entrando)) { (categoria, monto) ->
                val proporcion = (monto / total).toFloat()
                val indice = porCategoria.indexOfFirst { it.first == categoria }
                val animada = animarDesdeCero(proporcion, retraso = 350 + indice * 80)
                ListItem(
                    colors = ColoresFilaTransparente,
                    leadingContent = { IconoTonal(categoria.icono) },
                    headlineContent = {
                        Row {
                            Text(categoria.nombre, Modifier.weight(1f))
                            Text(pesos(monto), style = MaterialTheme.typography.titleSmall)
                        }
                    },
                    supportingContent = {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { animada },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${(proporcion * 100).roundToInt()}% del total")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MiniEstadistica(
    icono: ImageVector,
    titulo: String,
    valor: @Composable () -> Unit,
    detalle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            IconoTonal(
                icono,
                tamano = 32.dp,
                contenedor = MaterialTheme.colorScheme.tertiaryContainer,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            valor()
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
