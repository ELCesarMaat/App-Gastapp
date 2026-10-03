package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Estado
import com.binc.gastapp.demo.datos.EstadoSuscripcion
import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.Suscripcion
import com.binc.gastapp.demo.datos.cobrosProximos
import com.binc.gastapp.demo.datos.suscripciones
import com.binc.gastapp.demo.ui.AvatarInicial
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.EncabezadoSeccion
import com.binc.gastapp.demo.ui.EstadoChip
import com.binc.gastapp.demo.ui.GrupoLista
import com.binc.gastapp.demo.ui.MargenPantalla
import com.binc.gastapp.demo.ui.MontoAnimado
import com.binc.gastapp.demo.ui.aparecer
import com.binc.gastapp.demo.ui.recienAbierta
import com.binc.gastapp.demo.ui.colorDeEstado
import com.binc.gastapp.demo.ui.conExtra
import com.binc.gastapp.demo.ui.cuandoRelativo
import com.binc.gastapp.demo.ui.estadoNeutro
import com.binc.gastapp.demo.ui.mesCorto
import com.binc.gastapp.demo.ui.pesos
import com.binc.gastapp.demo.ui.theme.cifraMediana
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuscripcionesScreen(onVolver: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val entrando = recienAbierta()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Suscripciones") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Rounded.Add, contentDescription = "Agregar suscripción") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding.conExtra(abajo = 24.dp)) {
            item { ResumenRecurrente(Modifier.aparecer(0, entrando)) }

            item { EncabezadoSeccion("Tus suscripciones", modifier = Modifier.aparecer(1, entrando)) }

            item {
                GrupoLista(suscripciones, modifier = Modifier.aparecer(2, entrando)) { FilaSuscripcion(it) }
            }

            item {
                EncabezadoSeccion(
                    titulo = "Próximos cobros",
                    subtitulo = "Lo que se te va a cobrar en los siguientes 45 días",
                    modifier = Modifier.aparecer(3, entrando),
                )
            }

            item {
                GrupoLista(cobrosProximos, modifier = Modifier.aparecer(4, entrando)) { cobro ->
                    ListItem(
                        colors = ColoresFilaTransparente,
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(cobro.fecha.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                                    Text(mesCorto(cobro.fecha), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                }
                            }
                        },
                        headlineContent = { Text(cobro.servicio) },
                        supportingContent = { Text("${cuandoRelativo(cobro.fecha)} · ${cobro.origen}") },
                        trailingContent = {
                            Text(pesos(cobro.monto), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumenRecurrente(modifier: Modifier = Modifier) {
    val activas = suscripciones.filter { it.estado == EstadoSuscripcion.Activa }
    val enPrueba = suscripciones.count { it.estado == EstadoSuscripcion.Prueba }
    val pausadas = suscripciones.count { it.estado == EstadoSuscripcion.Pausada }
    val alMes = activas.sumOf { it.monto }
    val siguiente = activas.filter { it.proximoCobro != null }.minBy { it.proximoCobro!! }

    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Gasto recurrente total", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Al mes", style = MaterialTheme.typography.labelMedium)
                    MontoAnimado(alMes, cifraMediana)
                }
                Column(Modifier.weight(1f)) {
                    Text("Al año", style = MaterialTheme.typography.labelMedium)
                    MontoAnimado(alMes * 12, cifraMediana)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${activas.size} activas · $enPrueba en prueba · $pausadas pausada",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Próximo cobro: ${siguiente.servicio} ${cuandoRelativo(siguiente.proximoCobro!!).lowercase()} · ${pesos(siguiente.monto)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FilaSuscripcion(suscripcion: Suscripcion) {
    val pausada = suscripcion.estado == EstadoSuscripcion.Pausada
    ListItem(
        colors = ColoresFilaTransparente,
        modifier = if (pausada) Modifier.alpha(0.6f) else Modifier,
        leadingContent = { AvatarInicial(suscripcion.servicio, suscripcion.color) },
        headlineContent = { Text(suscripcion.servicio) },
        supportingContent = { Text("${suscripcion.plan} · ${pesos(suscripcion.monto)} al mes") },
        trailingContent = {
            when {
                pausada -> EstadoChip("Pausada", estadoNeutro())
                suscripcion.estado == EstadoSuscripcion.Prueba -> {
                    val dias = ChronoUnit.DAYS.between(Hoy, suscripcion.proximoCobro!!)
                    EstadoChip("Prueba · $dias días", estadoNeutro())
                }
                suscripcion.cobradaEsteCiclo -> EstadoChip("Cobrada", colorDeEstado(Estado.AlCorriente))
                else -> {
                    val dias = ChronoUnit.DAYS.between(Hoy, suscripcion.proximoCobro!!)
                    EstadoChip(
                        cuandoRelativo(suscripcion.proximoCobro),
                        if (dias <= 3) colorDeEstado(Estado.Pronto) else estadoNeutro(),
                        pulsar = dias <= 3,
                    )
                }
            }
        },
    )
}
