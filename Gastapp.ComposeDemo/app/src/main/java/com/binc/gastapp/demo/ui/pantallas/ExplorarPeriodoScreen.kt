package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Atajo
import com.binc.gastapp.demo.datos.Gasto
import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.LocaleMx
import com.binc.gastapp.demo.datos.Rango
import com.binc.gastapp.demo.datos.RepositorioDemo
import com.binc.gastapp.demo.datos.enRango
import com.binc.gastapp.demo.datos.primerDiaSemana
import com.binc.gastapp.demo.datos.totalesPorDia
import com.binc.gastapp.demo.ui.EmphasizedDecelerate
import com.binc.gastapp.demo.ui.IconoTonal
import com.binc.gastapp.demo.ui.MargenPantalla
import com.binc.gastapp.demo.ui.MontoAnimado
import com.binc.gastapp.demo.ui.animarDesdeCero
import com.binc.gastapp.demo.ui.aparecer
import com.binc.gastapp.demo.ui.conExtra
import com.binc.gastapp.demo.ui.fechaCorta
import com.binc.gastapp.demo.ui.fechaLarga
import com.binc.gastapp.demo.ui.mesAnio
import com.binc.gastapp.demo.ui.pesos
import com.binc.gastapp.demo.ui.rangoLargo
import com.binc.gastapp.demo.ui.recienAbierta
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Meses que se pueden recorrer en el calendario: los ultimos 10 hasta el actual. */
private val mesesDisponibles: List<YearMonth> =
    (9 downTo 0).map { YearMonth.from(Hoy).minusMonths(it.toLong()) }

/**
 * Reemplazo del SfCalendar de "Explora por periodo".
 *
 * Calendario propio en lugar del DateRangePicker de Material, por dos razones: puede
 * pintar en cada dia cuanto se gasto (que es justo lo que uno busca al explorar), y
 * se integra con los atajos y el resumen del rango en una sola pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorarPeriodoScreen(
    rangoInicial: Rango,
    onCerrar: () -> Unit,
    onAplicar: (Rango) -> Unit,
) {
    var inicio by remember { mutableStateOf<LocalDate?>(rangoInicial.inicio) }
    var fin by remember { mutableStateOf<LocalDate?>(rangoInicial.fin) }
    val rango = inicio?.let { i -> fin?.let { f -> Rango(i, f) } }

    val gastos = RepositorioDemo.gastos
    val totales = gastos.totalesPorDia()
    val maximoDia = totales.values.maxOrNull() ?: 1.0

    val pager = rememberPagerState(
        initialPage = mesesDisponibles.indexOf(YearMonth.from(rangoInicial.inicio)).coerceAtLeast(0),
    ) { mesesDisponibles.size }
    val alcance = rememberCoroutineScope()
    val haptica = LocalHapticFeedback.current
    val entrando = recienAbierta()

    fun irAMes(fecha: LocalDate) {
        val indice = mesesDisponibles.indexOf(YearMonth.from(fecha))
        if (indice >= 0) alcance.launch { pager.animateScrollToPage(indice) }
    }

    fun elegir(fecha: LocalDate) {
        haptica.performHapticFeedback(HapticFeedbackType.SegmentTick)
        val actual = inicio
        when {
            // Sin seleccion o con un rango completo: se empieza uno nuevo.
            actual == null || fin != null -> {
                inicio = fecha
                fin = null
            }
            fecha.isBefore(actual) -> inicio = fecha
            else -> fin = fecha
        }
    }

    fun nivelDe(fecha: LocalDate): Int {
        val total = totales[fecha] ?: return 0
        return when {
            total <= 0.0 -> 0
            total < maximoDia * 0.25 -> 1
            total < maximoDia * 0.55 -> 2
            else -> 3
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCerrar) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") }
                },
                title = { Text("Explorar periodo") },
                actions = {
                    TextButton(onClick = { irAMes(Hoy) }) { Text("Hoy") }
                },
            )
        },
        bottomBar = { BarraAplicar(rango = rango, onAplicar = onAplicar) },
    ) { padding ->
        LazyColumn(contentPadding = padding.conExtra(abajo = 16.dp)) {
            item(key = "atajos") {
                Atajos(
                    rango = rango,
                    onAtajo = { atajo ->
                        val nuevo = atajo.rango()
                        inicio = nuevo.inicio
                        fin = nuevo.fin
                        irAMes(nuevo.inicio)
                    },
                    modifier = Modifier.aparecer(0, entrando),
                )
            }

            item(key = "encabezado") {
                EncabezadoRango(
                    rango = rango,
                    inicio = inicio,
                    movimientos = rango?.let { gastos.enRango(it).size } ?: 0,
                    modifier = Modifier.aparecer(1, entrando),
                )
            }

            item(key = "calendario") {
                TarjetaCalendario(
                    pager = pager,
                    inicio = inicio,
                    fin = fin,
                    nivelDe = ::nivelDe,
                    onElegir = ::elegir,
                    onMes = { indice -> alcance.launch { pager.animateScrollToPage(indice) } },
                    modifier = Modifier.aparecer(2, entrando),
                )
            }

            if (rango != null) {
                item(key = "resumen") {
                    ResumenDelRango(
                        rango = rango,
                        gastos = gastos.enRango(rango),
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .aparecer(3, entrando),
                    )
                }
                item(key = "categorias") {
                    CategoriasDelRango(
                        gastos = gastos.enRango(rango),
                        modifier = Modifier
                            .animateItem(fadeOutSpec = null)
                            .aparecer(4, entrando),
                    )
                }
            } else {
                item(key = "pista") { PistaFinDelRango(Modifier.animateItem(fadeOutSpec = null)) }
            }
        }
    }
}

@Composable
private fun Atajos(rango: Rango?, onAtajo: (Atajo) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = MargenPantalla),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Atajo.entries) { atajo ->
            FilterChip(
                selected = atajo.rango() == rango,
                onClick = { onAtajo(atajo) },
                label = { Text(atajo.etiqueta) },
            )
        }
    }
}

@Composable
private fun EncabezadoRango(
    rango: Rango?,
    inicio: LocalDate?,
    movimientos: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = rango,
        transitionSpec = {
            ((fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { -it / 3 })) using SizeTransform(clip = false)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla + 4.dp, vertical = 12.dp),
        label = "rango",
    ) { actual ->
        Column {
            Text(
                when {
                    actual != null -> rangoLargo(actual)
                    inicio != null -> "Desde el ${fechaLarga(inicio).replaceFirstChar { it.lowercase(LocaleMx) }}"
                    else -> "Elige un periodo"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (actual != null) {
                    "${actual.dias} ${if (actual.dias == 1) "día" else "días"} · $movimientos movimientos"
                } else {
                    "Ahora toca el día en que termina"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TarjetaCalendario(
    pager: PagerState,
    inicio: LocalDate?,
    fin: LocalDate?,
    nivelDe: (LocalDate) -> Int,
    onElegir: (LocalDate) -> Unit,
    onMes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(
                Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = pager.currentPage,
                    transitionSpec = {
                        val adelante = targetState > initialState
                        (slideInVertically { if (adelante) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (adelante) -it else it } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "mes",
                ) { pagina ->
                    Text(mesAnio(mesesDisponibles[pagina]), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { onMes(pager.currentPage - 1) }, enabled = pager.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                IconButton(
                    onClick = { onMes(pager.currentPage + 1) },
                    enabled = pager.currentPage < mesesDisponibles.lastIndex,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Mes siguiente")
                }
            }

            Row(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                repeat(7) { i ->
                    Text(
                        primerDiaSemana.plus(i.toLong())
                            .getDisplayName(java.time.format.TextStyle.NARROW, LocaleMx)
                            .uppercase(LocaleMx),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { pagina ->
                MesCalendario(mesesDisponibles[pagina], inicio, fin, nivelDe, onElegir)
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            LeyendaIntensidad(Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun MesCalendario(
    mes: YearMonth,
    inicio: LocalDate?,
    fin: LocalDate?,
    nivelDe: (LocalDate) -> Int,
    onElegir: (LocalDate) -> Unit,
) {
    val vacios = (mes.atDay(1).dayOfWeek.value - primerDiaSemana.value + 7) % 7
    Column {
        // Siempre 6 filas, para que el calendario no cambie de alto al pasar de mes.
        repeat(6) { fila ->
            Row {
                repeat(7) { columna ->
                    val numero = fila * 7 + columna - vacios + 1
                    val fecha = if (numero in 1..mes.lengthOfMonth()) mes.atDay(numero) else null
                    CeldaDia(
                        fecha = fecha,
                        columna = columna,
                        inicio = inicio,
                        fin = fin,
                        nivel = fecha?.let(nivelDe) ?: 0,
                        onElegir = onElegir,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CeldaDia(
    fecha: LocalDate?,
    columna: Int,
    inicio: LocalDate?,
    fin: LocalDate?,
    nivel: Int,
    onElegir: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        if (fecha == null) return@Box

        val esquema = MaterialTheme.colorScheme
        val esInicio = fecha == inicio
        val esFin = fecha == fin
        val extremo = esInicio || esFin
        val dentro = inicio != null && fin != null && !fecha.isBefore(inicio) && !fecha.isAfter(fin)
        val futuro = fecha.isAfter(Hoy)

        // La banda del rango aparece en "barrido": cada dia un poco despues del anterior.
        val retraso = if (dentro && inicio != null) {
            (ChronoUnit.DAYS.between(inicio, fecha).toInt() * 14).coerceAtMost(420)
        } else {
            0
        }
        val banda by animateFloatAsState(
            targetValue = if (dentro && inicio != fin) 1f else 0f,
            animationSpec = tween(220, delayMillis = retraso),
            label = "banda",
        )
        val circulo by animateFloatAsState(
            targetValue = if (extremo) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
            label = "circulo",
        )
        val colorBanda = esquema.primaryContainer
        val colorCirculo = esquema.primary
        val esHoy = fecha == Hoy

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    if (banda > 0f) {
                        val alto = 40.dp.toPx()
                        val arriba = (size.height - alto) / 2
                        val izquierda = if (esInicio) size.width / 2 else 0f
                        val derecha = if (esFin) size.width / 2 else size.width
                        val radio = CornerRadius(alto / 2)
                        // La banda se redondea donde termina una semana o el rango.
                        val redondoIzq = columna == 0 && !esInicio
                        val redondoDer = columna == 6 && !esFin
                        val forma = RoundRect(
                            left = izquierda,
                            top = arriba,
                            right = derecha,
                            bottom = arriba + alto,
                            topLeftCornerRadius = if (redondoIzq) radio else CornerRadius.Zero,
                            bottomLeftCornerRadius = if (redondoIzq) radio else CornerRadius.Zero,
                            topRightCornerRadius = if (redondoDer) radio else CornerRadius.Zero,
                            bottomRightCornerRadius = if (redondoDer) radio else CornerRadius.Zero,
                        )
                        drawPath(Path().apply { addRoundRect(forma) }, colorBanda.copy(alpha = banda))
                    }
                    if (circulo > 0f) {
                        drawCircle(colorCirculo, radius = 20.dp.toPx() * circulo)
                    } else if (esHoy) {
                        drawCircle(colorCirculo, radius = 20.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
                .clickable(
                    enabled = !futuro,
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = 22.dp),
                ) { onElegir(fecha) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    fecha.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (extremo || esHoy) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        extremo -> esquema.onPrimary
                        futuro -> esquema.outline.copy(alpha = 0.6f)
                        dentro -> esquema.onPrimaryContainer
                        else -> esquema.onSurface
                    },
                )
                // Punto de intensidad: cuanto se gasto ese dia.
                val base = if (extremo) esquema.onPrimary else esquema.primary
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .background(
                            when (nivel) {
                                0 -> Color.Transparent
                                1 -> base.copy(alpha = 0.35f)
                                2 -> base.copy(alpha = 0.65f)
                                else -> base
                            },
                            CircleShape,
                        )
                )
            }
        }
    }
}

@Composable
private fun LeyendaIntensidad(modifier: Modifier = Modifier) {
    val primario = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Gasto por día",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text("Menos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0.35f, 0.65f, 1f).forEach { alfa ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .background(primario.copy(alpha = alfa), CircleShape)
            )
        }
        Text("Más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResumenDelRango(rango: Rango, gastos: List<Gasto>, modifier: Modifier = Modifier) {
    val total = gastos.sumOf { it.monto }
    val diasTranscurridos = rango.fechas().count { !it.isAfter(Hoy) }.coerceAtLeast(1)
    val porDia = gastos.totalesPorDia()
    val diaMasAlto = porDia.maxByOrNull { it.value }

    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Total del periodo", style = MaterialTheme.typography.labelMedium)
                    MontoAnimado(total, MaterialTheme.typography.headlineSmall, desdeCero = false)
                }
                Column(Modifier.weight(1f)) {
                    Text("Promedio diario", style = MaterialTheme.typography.labelMedium)
                    MontoAnimado(total / diasTranscurridos, MaterialTheme.typography.headlineSmall, desdeCero = false)
                }
            }
            Spacer(Modifier.height(16.dp))
            GraficaDias(rango = rango, porDia = porDia)
            if (diaMasAlto != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Día más alto: ${fechaCorta(diaMasAlto.key)} · ${pesos(diaMasAlto.value)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Barras por dia del rango; crecen desde abajo cada vez que cambia el rango. */
@Composable
private fun GraficaDias(rango: Rango, porDia: Map<LocalDate, Double>) {
    val fechas = rango.fechas().filter { !it.isAfter(Hoy) }
    if (fechas.isEmpty()) return
    val valores = fechas.map { porDia[it] ?: 0.0 }
    val maximo = valores.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val indiceMaximo = valores.indexOf(valores.max())

    val crecimiento = remember { Animatable(0f) }
    LaunchedEffect(rango) {
        crecimiento.snapTo(0f)
        crecimiento.animateTo(1f, tween(800, easing = EmphasizedDecelerate))
    }

    val colorBarra = MaterialTheme.colorScheme.primary
    val colorMaximo = MaterialTheme.colorScheme.tertiary
    val colorVacio = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        val n = valores.size
        val hueco = (size.width / n * 0.3f).coerceIn(1.dp.toPx(), 6.dp.toPx())
        val ancho = (size.width - hueco * (n - 1)) / n
        valores.forEachIndexed { i, valor ->
            val alto = ((valor / maximo).toFloat() * size.height * crecimiento.value)
                .coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = when {
                    valor <= 0.0 -> colorVacio
                    i == indiceMaximo -> colorMaximo
                    else -> colorBarra
                },
                topLeft = Offset(i * (ancho + hueco), size.height - alto),
                size = Size(ancho, alto),
                cornerRadius = CornerRadius(minOf(ancho / 2, 6.dp.toPx())),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(fechaCorta(fechas.first()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(fechaCorta(fechas.last()), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CategoriasDelRango(gastos: List<Gasto>, modifier: Modifier = Modifier) {
    val total = gastos.sumOf { it.monto }
    if (total <= 0.0) return
    val top = gastos.groupBy { it.categoria }
        .mapValues { (_, lista) -> lista.sumOf { it.monto } }
        .toList()
        .sortedByDescending { it.second }
        .take(3)

    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("En qué se fue", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            top.forEachIndexed { i, (categoria, monto) ->
                val proporcion = (monto / total).toFloat()
                val animada = animarDesdeCero(proporcion, retraso = 200 + i * 90)
                Row(
                    Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoTonal(categoria.icono, tamano = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(categoria.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(pesos(monto), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animada },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${(proporcion * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PistaFinDelRango(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla + 4.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoTonal(Icons.Rounded.TouchApp, tamano = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            "Elige el día final en el calendario para ver cuánto gastaste en ese periodo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarraAplicar(rango: Rango?, onAplicar: (Rango) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = { rango?.let(onAplicar) },
                enabled = rango != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                AnimatedContent(
                    targetState = rango?.dias,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "boton",
                ) { dias ->
                    Text(
                        when (dias) {
                            null -> "Elige el día final"
                            1 -> "Ver 1 día"
                            else -> "Ver $dias días"
                        }
                    )
                }
            }
        }
    }
}
