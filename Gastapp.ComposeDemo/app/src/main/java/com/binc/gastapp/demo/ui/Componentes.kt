package com.binc.gastapp.demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Estado
import com.binc.gastapp.demo.datos.Gasto
import com.binc.gastapp.demo.ui.theme.EstadoColor
import com.binc.gastapp.demo.ui.theme.LocalColoresEstado
import kotlinx.coroutines.delay

/** Margen lateral comun de todas las pantallas. */
val MargenPantalla = 16.dp

/** Curvas "emphasized" de Material 3: entradas que frenan suave y salidas que aceleran. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// --------------------------------------------------------------- Animaciones

/**
 * Monto que "cuenta" hasta su valor: desde cero la primera vez y desde el valor
 * anterior cuando cambia (al cambiar de dia, editar un gasto...).
 */
@Composable
fun MontoAnimado(
    valor: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    desdeCero: Boolean = true,
) {
    val animado = remember { Animatable(if (desdeCero) 0f else valor.toFloat()) }
    LaunchedEffect(valor) {
        animado.animateTo(valor.toFloat(), tween(durationMillis = 750, easing = FastOutSlowInEasing))
    }
    // Al terminar se muestra el valor exacto: el Float de la animacion pierde centavos.
    val texto = if (!animado.isRunning && animado.value == valor.toFloat()) pesos(valor)
    else pesos(animado.value.toDouble())
    Text(texto, style = style, modifier = modifier, color = color, maxLines = 1)
}

/** Valor que crece desde cero al aparecer (barras de progreso, porcentajes). */
@Composable
fun animarDesdeCero(objetivo: Float, retraso: Int = 150, duracion: Int = 900): Float {
    val valor = remember { Animatable(0f) }
    LaunchedEffect(objetivo) {
        valor.animateTo(objetivo, tween(duracion, delayMillis = retraso, easing = EmphasizedDecelerate))
    }
    return valor.value
}

/** true durante el primer instante de la pantalla; sirve para animar solo la entrada. */
@Composable
fun recienAbierta(duracionMs: Long = 900): Boolean {
    var valor by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(duracionMs)
        valor = false
    }
    return valor
}

/**
 * Entrada escalonada: cada bloque sube y aparece un poco despues del anterior.
 * Con [activo] en false no hace nada, para no repetirla al regresar haciendo scroll.
 */
fun Modifier.aparecer(indice: Int, activo: Boolean = true): Modifier = composed {
    if (!activo) return@composed Modifier
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * indice)
        progreso.animateTo(1f, tween(480, easing = EmphasizedDecelerate))
    }
    Modifier.graphicsLayer {
        alpha = progreso.value
        translationY = (1f - progreso.value) * 28.dp.toPx()
    }
}

/** Se encoge un poco mientras se mantiene presionado: da sensacion de algo fisico. */
fun Modifier.escalaAlPresionar(fuente: InteractionSource): Modifier = composed {
    val presionado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "presion",
    )
    Modifier.graphicsLayer {
        scaleX = escala
        scaleY = escala
    }
}

/** Para encoger el FAB al bajar y extenderlo al subir, como en Gmail. */
@Composable
fun LazyListState.seDesplazaHaciaArriba(): Boolean {
    var indiceAnterior by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var desplazamientoAnterior by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (indiceAnterior != firstVisibleItemIndex) {
                indiceAnterior > firstVisibleItemIndex
            } else {
                desplazamientoAnterior >= firstVisibleItemScrollOffset
            }.also {
                indiceAnterior = firstVisibleItemIndex
                desplazamientoAnterior = firstVisibleItemScrollOffset
            }
        }
    }.value
}

// ------------------------------------------------------------- Componentes

@Composable
fun EncabezadoSeccion(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    accion: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = MargenPantalla + 4.dp, end = MargenPantalla, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        accion?.invoke()
    }
}

@Composable
fun colorDeEstado(estado: Estado): EstadoColor {
    val colores = LocalColoresEstado.current
    return when (estado) {
        Estado.Vencido -> colores.vencido
        Estado.Pronto -> colores.pronto
        Estado.AlCorriente -> colores.ok
    }
}

/** Estado neutro (pausada, en prueba) tomado del tema, para que no compita con los de color. */
@Composable
fun estadoNeutro(): EstadoColor = EstadoColor(
    fuerte = MaterialTheme.colorScheme.outline,
    contenedor = MaterialTheme.colorScheme.surfaceContainerHighest,
    sobreContenedor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Chip de estado con su punto de color. Con [pulsar] el punto late suave para llamar
 * la atencion sobre lo urgente (vencido, vence pronto).
 */
@Composable
fun EstadoChip(
    texto: String,
    estado: EstadoColor,
    modifier: Modifier = Modifier,
    pulsar: Boolean = false,
) {
    val alfaPunto: State<Float> = if (pulsar) {
        rememberInfiniteTransition(label = "pulso").animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
            label = "alfa",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Surface(
        color = estado.contenedor,
        contentColor = estado.sobreContenedor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = alfaPunto.value }
                    .background(estado.fuerte, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun IconoTonal(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    contenedor: Color = MaterialTheme.colorScheme.secondaryContainer,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    tamano: Dp = 40.dp,
) {
    Box(
        modifier
            .size(tamano)
            .clip(CircleShape)
            .background(contenedor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(tamano * 0.55f))
    }
}

/** Avatar circular con la inicial, para servicios y tarjetas. */
@Composable
fun AvatarInicial(texto: String, color: Color, modifier: Modifier = Modifier, tamano: Dp = 40.dp) {
    Box(
        modifier
            .size(tamano)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Grupo de filas dentro de una tarjeta, como las listas de Ajustes de Android.
 * Los separadores van solo entre filas.
 */
@Composable
fun <T> GrupoLista(
    elementos: List<T>,
    modifier: Modifier = Modifier,
    fila: @Composable (T) -> Unit,
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            elementos.forEachIndexed { indice, elemento ->
                fila(elemento)
                if (indice < elementos.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 72.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * Una fila suelta que, junto con sus vecinas, se ve igual que [GrupoLista]. Existe para
 * poder poner cada fila como item propio de un LazyColumn y animar altas y bajas.
 */
@Composable
fun FilaAgrupada(
    indice: Int,
    total: Int,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val radio = 24.dp
    val primera = indice == 0
    val ultima = indice == total - 1
    val forma = RoundedCornerShape(
        topStart = if (primera) radio else 0.dp,
        topEnd = if (primera) radio else 0.dp,
        bottomStart = if (ultima) radio else 0.dp,
        bottomEnd = if (ultima) radio else 0.dp,
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MargenPantalla)
            .clip(forma)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(top = if (primera) 4.dp else 0.dp, bottom = if (ultima) 4.dp else 0.dp)
    ) {
        if (!primera) {
            HorizontalDivider(
                Modifier.padding(start = 72.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        contenido()
    }
}

val ColoresFilaTransparente: ListItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
fun FilaGasto(gasto: Gasto, onClick: (() -> Unit)? = null) {
    val detalle = buildString {
        append(gasto.categoria.nombre)
        append(" · ")
        append(gasto.tarjeta ?: gasto.metodo.etiqueta)
    }
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ColoresFilaTransparente,
        leadingContent = { IconoTonal(gasto.categoria.icono) },
        headlineContent = { Text(gasto.titulo) },
        supportingContent = { Text(detalle) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    pesos(gasto.monto),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(hora(gasto.hora), style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

fun PaddingValues.conExtra(arriba: Dp = 0.dp, abajo: Dp = 0.dp): PaddingValues = PaddingValues(
    top = calculateTopPadding() + arriba,
    bottom = calculateBottomPadding() + abajo,
)
