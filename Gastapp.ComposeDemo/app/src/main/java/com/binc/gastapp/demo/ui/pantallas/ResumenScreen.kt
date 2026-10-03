package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Estado
import com.binc.gastapp.demo.datos.EstadoSuscripcion
import com.binc.gastapp.demo.datos.Gasto
import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.Rango
import com.binc.gastapp.demo.datos.RepositorioDemo
import com.binc.gastapp.demo.datos.delDia
import com.binc.gastapp.demo.datos.enRango
import com.binc.gastapp.demo.datos.etiquetaDe
import com.binc.gastapp.demo.datos.suscripciones
import com.binc.gastapp.demo.datos.tarjetas
import com.binc.gastapp.demo.datos.totalesPorDia
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.EmphasizedDecelerate
import com.binc.gastapp.demo.ui.EncabezadoSeccion
import com.binc.gastapp.demo.ui.EstadoChip
import com.binc.gastapp.demo.ui.FilaAgrupada
import com.binc.gastapp.demo.ui.FilaGasto
import com.binc.gastapp.demo.ui.IconoTonal
import com.binc.gastapp.demo.ui.MargenPantalla
import com.binc.gastapp.demo.ui.MontoAnimado
import com.binc.gastapp.demo.ui.aparecer
import com.binc.gastapp.demo.ui.colorDeEstado
import com.binc.gastapp.demo.ui.conExtra
import com.binc.gastapp.demo.ui.diaSemanaCorto
import com.binc.gastapp.demo.ui.escalaAlPresionar
import com.binc.gastapp.demo.ui.etiquetaFecha
import com.binc.gastapp.demo.ui.pesos
import com.binc.gastapp.demo.ui.rangoCorto
import com.binc.gastapp.demo.ui.recienAbierta
import com.binc.gastapp.demo.ui.theme.cifraGrande
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val AnchoChipDia = 56.dp

@Composable
fun ResumenScreen(
    contentPadding: PaddingValues,
    estadoLista: LazyListState,
    periodo: Rango,
    onPeriodo: (Rango) -> Unit,
    dia: LocalDate,
    onDia: (LocalDate) -> Unit,
    onIrAHoy: () -> Unit,
    onExplorar: () -> Unit,
    onAbrirTarjetas: () -> Unit,
    onAbrirSuscripciones: () -> Unit,
    onEditarGasto: (Gasto) -> Unit,
) {
    val todos = RepositorioDemo.gastos
    val delPeriodo = todos.enRango(periodo)
    val gastosDia = todos.delDia(dia)
    val entrando = recienAbierta()

    LazyColumn(
        state = estadoLista,
        contentPadding = contentPadding.conExtra(arriba = 4.dp, abajo = 96.dp),
    ) {
        item(key = "periodo") {
            NavegadorDePeriodo(
                periodo = periodo,
                total = delPeriodo.sumOf { it.monto },
                puedeSiguiente = !periodo.siguiente().inicio.isAfter(Hoy),
                onAnterior = { onPeriodo(periodo.anterior()) },
                onSiguiente = { onPeriodo(periodo.siguiente()) },
                onExplorar = onExplorar,
                modifier = Modifier.aparecer(0, entrando),
            )
        }

        item(key = "tira") {
            TiraDelPeriodo(
                periodo = periodo,
                dia = dia,
                onDia = onDia,
                modifier = Modifier.aparecer(1, entrando),
            )
        }

        item(key = "total") {
            TarjetaTotalDelDia(
                dia = dia,
                gastos = gastosDia,
                onIrAHoy = onIrAHoy,
                modifier = Modifier.aparecer(2, entrando),
            )
        }

        item(key = "atajo-tarjetas") {
            val proxima = tarjetas.first { it.estadoPago == Estado.Pronto }
            AccesoDirecto(
                icono = Icons.Rounded.CreditCard,
                titulo = "Mis tarjetas y MSI",
                subtitulo = "Cortes, fechas de pago y saldo diferido",
                onClick = onAbrirTarjetas,
                modifier = Modifier.aparecer(3, entrando),
                chip = {
                    EstadoChip(
                        "${proxima.etiqueta} · ${proxima.textoPago.lowercase()}",
                        colorDeEstado(proxima.estadoPago),
                        pulsar = true,
                    )
                },
            )
        }

        item(key = "atajo-suscripciones") {
            val alMes = suscripciones.filter { it.estado == EstadoSuscripcion.Activa }.sumOf { it.monto }
            AccesoDirecto(
                icono = Icons.Rounded.Autorenew,
                titulo = "Suscripciones y membresías",
                subtitulo = "${pesos(alMes)} al mes · Netflix cobra mañana",
                onClick = onAbrirSuscripciones,
                modifier = Modifier.aparecer(4, entrando),
            )
        }

        item(key = "encabezado") {
            EncabezadoSeccion(
                titulo = "Tus movimientos",
                subtitulo = if (gastosDia.isEmpty()) null else "Toca un gasto para editarlo",
                modifier = Modifier.aparecer(5, entrando),
            )
        }

        if (gastosDia.isEmpty()) {
            item(key = "vacio") { EstadoVacio(Modifier.animateItem(fadeOutSpec = null)) }
        } else {
            itemsIndexed(gastosDia, key = { _, gasto -> gasto.id }) { indice, gasto ->
                FilaAgrupada(
                    indice = indice,
                    total = gastosDia.size,
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .aparecer(6 + indice, entrando),
                ) {
                    FilaGasto(gasto, onClick = { onEditarGasto(gasto) })
                }
            }
        }
    }
}

@Composable
private fun NavegadorDePeriodo(
    periodo: Rango,
    total: Double,
    puedeSiguiente: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onExplorar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAnterior) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Periodo anterior")
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onExplorar)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // El periodo se desliza hacia el lado en que se navega.
            AnimatedContent(
                targetState = periodo,
                transitionSpec = {
                    val adelante = targetState.inicio.isAfter(initialState.inicio)
                    (slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { if (adelante) it / 3 else -it / 3 } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { if (adelante) -it / 3 else it / 3 } + fadeOut(tween(150)))
                },
                contentAlignment = Alignment.Center,
                label = "periodo",
            ) { rango ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        etiquetaDe(rango),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rangoCorto(rango), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total del periodo ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MontoAnimado(
                    valor = total,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    desdeCero = false,
                )
            }
        }
        IconButton(onClick = onSiguiente, enabled = puedeSiguiente) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Periodo siguiente")
        }
    }
}

/**
 * Todos los dias del periodo elegido, con scroll horizontal y una mini barra con lo
 * gastado respecto al dia mas alto del periodo.
 *
 * Cada periodo es una tira nueva que entra deslizandose hacia el lado al que se navega.
 * Cambiar los elementos de una misma lista encimaba los dias viejos (desvaneciendose)
 * con los nuevos.
 */
@Composable
private fun TiraDelPeriodo(
    periodo: Rango,
    dia: LocalDate,
    onDia: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = periodo,
        transitionSpec = {
            val adelante = targetState.inicio.isAfter(initialState.inicio)
            (slideInHorizontally(tween(380, easing = EmphasizedDecelerate)) { if (adelante) it / 2 else -it / 2 } +
                fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(260)) { if (adelante) -it / 2 else it / 2 } + fadeOut(tween(180)))
        },
        modifier = modifier.fillMaxWidth(),
        label = "tira",
    ) { rango ->
        DiasDelRango(rango = rango, dia = dia, onDia = onDia)
    }
}

@Composable
private fun DiasDelRango(rango: Rango, dia: LocalDate, onDia: (LocalDate) -> Unit) {
    val fechas = remember(rango) { rango.fechas() }
    val totales = RepositorioDemo.gastos.enRango(rango).totalesPorDia()
    val maximo = totales.values.maxOrNull() ?: 0.0
    val estado = rememberLazyListState(
        initialFirstVisibleItemIndex = (fechas.indexOf(dia) - 2).coerceAtLeast(0),
    )
    var yaCentrada by remember { mutableStateOf(false) }

    // Centra el dia elegido: de golpe la primera vez (la tira viene entrando) y con
    // animacion cuando se toca otro dia.
    LaunchedEffect(dia) {
        val indice = fechas.indexOf(dia)
        if (indice < 0) return@LaunchedEffect
        snapshotFlow { estado.layoutInfo.viewportSize.width }.first { it > 0 }
        estado.centrarElemento(indice, animado = yaCentrada)
        yaCentrada = true
    }

    LazyRow(
        state = estado,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = MargenPantalla),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(fechas, key = { it.toEpochDay() }) { fecha ->
            ChipDia(
                fecha = fecha,
                seleccionado = fecha == dia,
                proporcion = if (maximo > 0) ((totales[fecha] ?: 0.0) / maximo).toFloat() else 0f,
                onClick = { onDia(fecha) },
            )
        }
    }
}

/** Deja el elemento [indice] en el centro de la tira (o lo mas cerca posible en los extremos). */
private suspend fun LazyListState.centrarElemento(indice: Int, animado: Boolean) {
    fun distanciaAlCentro(): Float? {
        val info = layoutInfo
        val elemento = info.visibleItemsInfo.firstOrNull { it.index == indice } ?: return null
        val centroVista = (info.viewportStartOffset + info.viewportEndOffset) / 2
        return (elemento.offset + elemento.size / 2 - centroVista).toFloat()
    }

    // Si no esta a la vista, primero se trae y despues se ajusta al centro.
    if (distanciaAlCentro() == null) {
        if (animado) animateScrollToItem(indice) else scrollToItem(indice)
    }
    val distancia = distanciaAlCentro() ?: return
    if (animado) {
        animateScrollBy(distancia, tween(350, easing = EmphasizedDecelerate))
    } else {
        scrollBy(distancia)
    }
}

@Composable
private fun ChipDia(
    fecha: LocalDate,
    seleccionado: Boolean,
    proporcion: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val futuro = fecha.isAfter(Hoy)
    val esquema = MaterialTheme.colorScheme
    val fondo by animateColorAsState(
        if (seleccionado) esquema.primary else esquema.surfaceContainerLow,
        tween(250),
        label = "fondo",
    )
    val texto by animateColorAsState(
        when {
            seleccionado -> esquema.onPrimary
            futuro -> esquema.outline
            else -> esquema.onSurface
        },
        tween(250),
        label = "texto",
    )
    val escala by animateFloatAsState(
        if (seleccionado) 1f else 0.92f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "escala",
    )
    val barra by animateFloatAsState(proporcion, tween(600, easing = EmphasizedDecelerate), label = "barra")

    Surface(
        onClick = onClick,
        enabled = !futuro,
        shape = RoundedCornerShape(18.dp),
        color = fondo,
        contentColor = texto,
        modifier = modifier
            .width(AnchoChipDia)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            },
    ) {
        Column(
            Modifier.padding(top = 10.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(diaSemanaCorto(fecha), style = MaterialTheme.typography.labelSmall)
            Text(fecha.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            futuro -> Color.Transparent
                            seleccionado -> esquema.onPrimary.copy(alpha = 0.3f)
                            else -> esquema.outlineVariant.copy(alpha = 0.6f)
                        }
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barra)
                        .background(if (seleccionado) esquema.onPrimary else esquema.primary)
                )
            }
        }
    }
}

@Composable
private fun TarjetaTotalDelDia(
    dia: LocalDate,
    gastos: List<Gasto>,
    onIrAHoy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = gastos.sumOf { it.monto }
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total del día",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                AnimatedVisibility(
                    visible = dia != Hoy,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    TextButton(onClick = onIrAHoy) { Text("Ir a hoy") }
                }
            }
            MontoAnimado(valor = total, style = cifraGrande)
            AnimatedContent(
                targetState = dia to gastos.size,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 2 })
                },
                label = "detalle-dia",
            ) { (fecha, cuantos) ->
                Text(
                    "${etiquetaFecha(fecha)} · " + when (cuantos) {
                        0 -> "sin movimientos"
                        1 -> "1 movimiento"
                        else -> "$cuantos movimientos"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AccesoDirecto(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chip: (@Composable () -> Unit)? = null,
) {
    val fuente = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = fuente,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla, vertical = 4.dp)
            .escalaAlPresionar(fuente),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ColoresFilaTransparente,
            leadingContent = {
                IconoTonal(
                    icono,
                    contenedor = MaterialTheme.colorScheme.tertiaryContainer,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            headlineContent = { Text(titulo) },
            supportingContent = {
                Column {
                    Text(subtitulo)
                    if (chip != null) {
                        Spacer(Modifier.height(8.dp))
                        chip()
                    }
                }
            },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        )
    }
}

@Composable
private fun EstadoVacio(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconoTonal(Icons.Rounded.ReceiptLong, tamano = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "No hay gastos registrados este día.",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Toca \"Nuevo gasto\" para agregar uno en esta fecha.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
