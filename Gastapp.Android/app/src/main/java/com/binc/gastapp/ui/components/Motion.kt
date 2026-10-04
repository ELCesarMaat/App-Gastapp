package com.binc.gastapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binc.gastapp.ui.format.formatMoney
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** Curvas "emphasized" de Material 3: entradas que frenan suave y salidas que aceleran. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Monto que "cuenta" hasta su valor: desde cero la primera vez y desde el valor
 * anterior cuando cambia (al cambiar de dia, editar un gasto...).
 */
@Composable
fun AnimatedAmount(
    value: BigDecimal,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fromZero: Boolean = true,
) {
    val animated = remember { Animatable(if (fromZero) 0f else value.toFloat()) }
    LaunchedEffect(value) {
        animated.animateTo(value.toFloat(), tween(durationMillis = 750, easing = FastOutSlowInEasing))
    }
    // Al terminar se muestra el valor exacto: el Float de la animacion pierde centavos.
    val text = if (!animated.isRunning && animated.value == value.toFloat()) formatMoney(value)
    else formatMoney(BigDecimal(animated.value.toDouble()).setScale(2, RoundingMode.HALF_EVEN))
    // Un monto nunca se corta: si no cabe (fuente al 200 %, columnas angostas) se encoge.
    val minFontSize = if (style.fontSize.isSp) style.fontSize * 0.5f else 8.sp
    FitText(text, modifier, style = style, color = color, minFontSize = minFontSize)
}

/** Valor que crece desde cero al aparecer (barras de progreso, porcentajes). */
@Composable
fun animateFromZero(target: Float, delayMillis: Int = 150, durationMillis: Int = 900): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target, tween(durationMillis, delayMillis = delayMillis, easing = EmphasizedDecelerate))
    }
    return value.value
}

/** true durante el primer instante de la pantalla; sirve para animar solo la entrada. */
@Composable
fun rememberJustOpened(durationMillis: Long = 900): Boolean {
    var value by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(durationMillis)
        value = false
    }
    return value
}

/**
 * Entrada escalonada: cada bloque sube y aparece un poco despues del anterior.
 * Con [enabled] en false no hace nada, para no repetirla al regresar haciendo scroll.
 */
fun Modifier.appear(index: Int, enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed Modifier
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index)
        progress.animateTo(1f, tween(480, easing = EmphasizedDecelerate))
    }
    Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/** Se encoge un poco mientras se mantiene presionado: da sensacion de algo fisico. */
fun Modifier.scaleOnPress(interactionSource: InteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "presion",
    )
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Segundos que faltan para [target] (0 si ya paso o es null); se actualiza solo. */
@Composable
fun rememberSecondsUntil(target: Instant?): Int {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(target) {
        now = Instant.now()
        while (target != null && now < target) {
            delay(250)
            now = Instant.now()
        }
    }
    if (target == null) return 0
    val millis = Duration.between(now, target).toMillis()
    return if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()
}

/** Para encoger el FAB al bajar y extenderlo al subir, como en Gmail. */
@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}
