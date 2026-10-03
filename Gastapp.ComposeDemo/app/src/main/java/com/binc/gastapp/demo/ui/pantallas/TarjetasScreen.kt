package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Estado
import com.binc.gastapp.demo.datos.Tarjeta
import com.binc.gastapp.demo.datos.tarjetas
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.EncabezadoSeccion
import com.binc.gastapp.demo.ui.EstadoChip
import com.binc.gastapp.demo.ui.FilaAgrupada
import com.binc.gastapp.demo.ui.IconoTonal
import com.binc.gastapp.demo.ui.MargenPantalla
import com.binc.gastapp.demo.ui.MontoAnimado
import com.binc.gastapp.demo.ui.animarDesdeCero
import com.binc.gastapp.demo.ui.aparecer
import com.binc.gastapp.demo.ui.colorDeEstado
import com.binc.gastapp.demo.ui.conExtra
import com.binc.gastapp.demo.ui.cuandoRelativo
import com.binc.gastapp.demo.ui.estadoNeutro
import com.binc.gastapp.demo.ui.fechaCorta
import com.binc.gastapp.demo.ui.pesos
import com.binc.gastapp.demo.ui.recienAbierta
import com.binc.gastapp.demo.ui.theme.cifraMediana
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetasScreen(onVolver: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pager = rememberPagerState { tarjetas.size }
    val tarjeta = tarjetas[pager.currentPage]
    val entrando = recienAbierta()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Mis tarjetas") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Rounded.Add, contentDescription = "Agregar tarjeta") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding.conExtra(abajo = 24.dp)) {
            item(key = "global") { ResumenGlobal(Modifier.aparecer(0, entrando)) }

            item(key = "carrusel") { CarruselTarjetas(pager, Modifier.aparecer(1, entrando)) }

            item(key = "detalle") {
                // El detalle cambia con la tarjeta, deslizandose hacia el mismo lado.
                AnimatedContent(
                    targetState = pager.currentPage,
                    transitionSpec = {
                        val adelante = targetState > initialState
                        ((slideInHorizontally(tween(320)) { if (adelante) it / 6 else -it / 6 } +
                            fadeIn(tween(250, delayMillis = 60))) togetherWith
                            (slideOutHorizontally(tween(200)) { if (adelante) -it / 6 else it / 6 } +
                                fadeOut(tween(150)))) using SizeTransform(clip = false)
                    },
                    modifier = Modifier.aparecer(2, entrando),
                    label = "detalle",
                ) { pagina ->
                    DetalleTarjeta(tarjetas[pagina])
                }
            }

            item(key = "acciones") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MargenPantalla),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .aparecer(3, entrando),
                ) {
                    item { AssistChip(onClick = {}, label = { Text("Ajustar saldo") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }) }
                    item { AssistChip(onClick = {}, label = { Text("Nueva compra") }, leadingIcon = { Icon(Icons.Rounded.ShoppingBag, null) }) }
                    item { AssistChip(onClick = {}, label = { Text("Compra a MSI") }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) }) }
                    item { AssistChip(onClick = {}, label = { Text("Editar") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }) }
                }
            }

            item(key = "encabezado-msi") {
                EncabezadoSeccion(
                    titulo = "Compras a meses sin intereses",
                    subtitulo = "Plazos diferidos de ${tarjeta.etiqueta}",
                    accion = {
                        if (tarjeta.msi.isNotEmpty()) {
                            EstadoChip("${tarjeta.msi.size} activas", estadoNeutro())
                        }
                    },
                    modifier = Modifier.aparecer(4, entrando),
                )
            }

            if (tarjeta.msi.isEmpty()) {
                item(key = "msi-vacio-${tarjeta.etiqueta}") {
                    Text(
                        "No tienes compras a meses sin intereses en esta tarjeta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .padding(horizontal = MargenPantalla + 4.dp),
                    )
                }
            } else {
                itemsIndexed(tarjeta.msi, key = { i, _ -> "msi-${tarjeta.etiqueta}-$i" }) { i, compra ->
                    val avance = animarDesdeCero(compra.mensualidadActual / compra.plazo.toFloat(), retraso = 300 + i * 100)
                    FilaAgrupada(
                        indice = i,
                        total = tarjeta.msi.size,
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .aparecer(5 + i, entrando),
                    ) {
                        ListItem(
                            colors = ColoresFilaTransparente,
                            leadingContent = { IconoTonal(compra.categoria.icono) },
                            headlineContent = { Text(compra.titulo) },
                            supportingContent = {
                                Column {
                                    Text("Mensualidad ${compra.mensualidadActual} de ${compra.plazo} · Total ${pesos(compra.total)}")
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { avance },
                                        modifier = Modifier.fillMaxWidth(),
                                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                                    )
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        pesos(compra.mensualidad),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text("al mes", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumenGlobal(modifier: Modifier = Modifier) {
    val deuda = tarjetas.sumOf { it.deuda }
    val limite = tarjetas.sumOf { it.limite }
    val ciclo = tarjetas.sumOf { it.pagoSinIntereses }
    val uso = animarDesdeCero((deuda / limite).toFloat(), retraso = 250)
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Deuda total", style = MaterialTheme.typography.labelLarge)
                    MontoAnimado(deuda, cifraMediana)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Crédito disponible", style = MaterialTheme.typography.labelLarge)
                    MontoAnimado(limite - deuda, cifraMediana)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { uso },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Límite combinado ${pesos(limite)} · Ciclo actual ${pesos(ciclo)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CarruselTarjetas(pager: PagerState, modifier: Modifier = Modifier) {
    Column(modifier) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.padding(top = 16.dp),
        ) { pagina ->
            PlasticoTarjeta(
                tarjetas[pagina],
                // Las tarjetas de los lados se ven mas chicas y tenues, y crecen al centrarse.
                Modifier.graphicsLayer {
                    val distancia = ((pager.currentPage - pagina) + pager.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f)
                    val escala = 1f - 0.1f * distancia
                    scaleX = escala
                    scaleY = escala
                    alpha = 1f - 0.45f * distancia
                },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(tarjetas.size) { i ->
                val ancho by animateDpAsState(if (i == pager.currentPage) 18.dp else 6.dp, label = "indicador")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = ancho, height = 6.dp)
                        .background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                )
            }
        }
    }
}

@Composable
private fun PlasticoTarjeta(tarjeta: Tarjeta, modifier: Modifier = Modifier) {
    val degradado = Brush.linearGradient(
        listOf(tarjeta.color, lerp(tarjeta.color, Color.Black, 0.35f))
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(184.dp)
            .background(degradado, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text(tarjeta.banco, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(tarjeta.producto, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "•••• ${tarjeta.ultimos4}",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(top = 24.dp),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Deuda", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                Text(pesos(tarjeta.deuda), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "${(tarjeta.usoPorcentaje * 100).roundToInt()}% usado",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun DetalleTarjeta(tarjeta: Tarjeta) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla, vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Dato(
                    titulo = "Pago para no generar intereses",
                    valor = tarjeta.pagoSinIntereses,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Dato(
                    titulo = "Deuda futura MSI",
                    valor = tarjeta.deudaFuturaMsi,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Fecha(
                    titulo = "Próximo corte",
                    fecha = fechaCorta(tarjeta.proximoCorte),
                    chip = { EstadoChip(cuandoRelativo(tarjeta.proximoCorte), estadoNeutro()) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Fecha(
                    titulo = "Límite de pago",
                    fecha = fechaCorta(tarjeta.limitePago),
                    chip = {
                        EstadoChip(
                            tarjeta.textoPago,
                            colorDeEstado(tarjeta.estadoPago),
                            pulsar = tarjeta.estadoPago != Estado.AlCorriente,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar pago")
            }
        }
    }
}

@Composable
private fun Dato(titulo: String, valor: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MontoAnimado(valor, MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Fecha(
    titulo: String,
    fecha: String,
    chip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fecha, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            chip()
        }
    }
}
