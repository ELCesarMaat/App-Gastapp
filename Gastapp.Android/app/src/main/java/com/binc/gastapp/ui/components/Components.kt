package com.binc.gastapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.ui.theme.LocalStatusColors
import com.binc.gastapp.ui.theme.StatusColor

/** Margen lateral comun de todas las pantallas. */
val ScreenMargin = 16.dp

/**
 * La fuente del sistema esta muy grande (150 % o mas). Las filas con una accion a la
 * derecha (un boton) la pasan abajo: a lo ancho no caben y el texto se partiria letra
 * por letra.
 */
val isLargeFontScale: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalDensity.current.fontScale >= 1.5f

/**
 * Texto de una linea que se encoge (hasta [minFontSize]) si no cabe, en vez de cortarse:
 * etiquetas de pestanas, botones segmentados y montos con la fuente al 200 %. Nunca
 * crece mas alla del tamano de su estilo.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 8.sp,
) {
    val max = style.fontSize
    if (!max.isSp) {
        Text(text, modifier, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Text(
        text,
        modifier,
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = if (minFontSize.value < max.value) minFontSize else max,
            maxFontSize = max,
            stepSize = 0.5.sp,
        ),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = ScreenMargin + 4.dp, end = ScreenMargin, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/** Color de un nivel de estado del dominio. El neutro (pausada, en prueba) sale del tema. */
@Composable
fun statusColor(level: StatusLevel): StatusColor {
    val colors = LocalStatusColors.current
    return when (level) {
        StatusLevel.OK -> colors.ok
        StatusLevel.WARNING -> colors.warning
        StatusLevel.CRITICAL -> colors.critical
        StatusLevel.NEUTRAL -> StatusColor(
            strong = MaterialTheme.colorScheme.outline,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chip de estado con su punto de color. Con [pulse] el punto late suave para llamar
 * la atencion sobre lo urgente (vencido, vence pronto).
 */
@Composable
fun StatusChip(
    text: String,
    color: StatusColor,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val dotAlpha: State<Float> = if (pulse) {
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
        color = color.container,
        contentColor = color.onContainer,
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
                    .graphicsLayer { alpha = dotAlpha.value }
                    .background(color.strong, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun TonalIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Avatar circular con la inicial (o las [letters] primeras), para servicios, tarjetas y el perfil. */
@Composable
fun InitialAvatar(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    letters: Int = 1,
    textColor: Color = Color.White,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.take(letters).uppercase(),
            color = textColor,
            style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Grupo de filas dentro de una tarjeta, como las listas de Ajustes de Android.
 * Los separadores van solo entre filas.
 */
@Composable
fun <T> ListGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { index, item ->
                row(item)
                if (index < items.lastIndex) {
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
 * Una fila suelta que, junto con sus vecinas, se ve igual que [ListGroup]. Existe para
 * poder poner cada fila como item propio de un LazyColumn y animar altas y bajas.
 */
@Composable
fun GroupedRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val radius = 24.dp
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp,
        topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp,
        bottomEnd = if (last) radius else 0.dp,
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
    ) {
        if (!first) {
            HorizontalDivider(
                Modifier.padding(start = 72.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        content()
    }
}

/** Filas de lista sin fondo propio, para ponerlas dentro de [ListGroup] o [GroupedRow]. */
val TransparentListItemColors: ListItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Suma espacio extra arriba o abajo al padding que da el Scaffold. */
fun PaddingValues.withExtra(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues = PaddingValues(
    top = calculateTopPadding() + top,
    bottom = calculateBottomPadding() + bottom,
)
