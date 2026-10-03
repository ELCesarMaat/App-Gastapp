package com.binc.gastapp.demo.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Como entra una pantalla apilada. */
enum class EntradaCapa {
    /** Desde la derecha, para pantallas de detalle (Mis tarjetas, Suscripciones). */
    Lateral,

    /** Desde abajo, para pantallas tipo dialogo completo (Explorar periodo). */
    DesdeAbajo,
}

private class Recuerdo<T> {
    var valor: T? = null
}

/**
 * Pantalla principal con una capa encima que se cierra con el gesto de "atras
 * predictivo" de Android 14+: mientras el dedo arrastra, la capa se encoge, se
 * redondea, se recorre hacia el lado del gesto y deja ver la pantalla de abajo
 * atenuada (como en Gmail). Si se suelta antes del umbral, regresa a su lugar.
 *
 * En la app real esto lo resuelve Navigation Compose con sus transiciones de "pop";
 * aqui va a mano para controlar exactamente como se ve.
 */
@Composable
fun <T : Any> PilaConAtrasPredictivo(
    capa: T?,
    onAtras: () -> Unit,
    entrada: (T) -> EntradaCapa,
    base: @Composable () -> Unit,
    contenido: @Composable (T) -> Unit,
) {
    // La capa se sigue pintando durante su animacion de salida, cuando capa ya es null.
    val recuerdo = remember { Recuerdo<T>() }
    if (capa != null) recuerdo.valor = capa
    val mostrada = capa ?: recuerdo.valor

    var progreso by remember { mutableFloatStateOf(0f) }
    var desdeIzquierda by remember { mutableStateOf(true) }
    var dedoY by remember { mutableFloatStateOf(0f) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(capa) {
        if (capa != null) {
            progreso = 0f
            dedoY = 0f
            desdeIzquierda = true
        }
    }

    PredictiveBackHandler(enabled = capa != null) { eventos ->
        var inicioY = Float.NaN
        try {
            eventos.collect { evento ->
                if (inicioY.isNaN()) inicioY = evento.touchY
                desdeIzquierda = evento.swipeEdge != BackEventCompat.EDGE_RIGHT
                progreso = evento.progress
                dedoY = evento.touchY - inicioY
            }
            // Gesto confirmado: la capa sale desde donde la dejo el dedo.
            onAtras()
        } catch (cancelado: CancellationException) {
            // Gesto cancelado: regresa a pantalla completa con un resorte.
            val progresoInicial = progreso
            val dedoInicial = dedoY
            alcance.launch {
                animate(1f, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { f, _ ->
                    progreso = progresoInicial * f
                    dedoY = dedoInicial * f
                }
            }
            throw cancelado
        }
    }

    val abierta by animateFloatAsState(
        targetValue = if (capa != null) 1f else 0f,
        animationSpec = tween(450, easing = EmphasizedDecelerate),
        label = "abierta",
    )
    val tipo = mostrada?.let(entrada) ?: EntradaCapa.Lateral

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // La pantalla de abajo se hace un poco a un lado al abrir la capa y
                    // regresa a su sitio siguiendo el gesto, como las del sistema.
                    if (tipo == EntradaCapa.Lateral) {
                        translationX = -size.width * 0.08f * abierta * (1f - progreso)
                    }
                }
        ) {
            base()
        }

        AnimatedVisibility(
            visible = capa != null,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            label = "capa",
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    progreso = 0f
                    dedoY = 0f
                    recuerdo.valor = null
                }
            }

            // Velo sobre la pantalla de abajo; es lo que se ve atenuado durante el gesto.
            Box(
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(enter = fadeIn(tween(300)), exit = fadeOut(tween(300)))
                    .background(Color.Black.copy(alpha = 0.32f))
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = if (tipo == EntradaCapa.Lateral) {
                            slideInHorizontally(tween(420, easing = EmphasizedDecelerate)) { it / 4 } +
                                fadeIn(tween(260))
                        } else {
                            slideInVertically(tween(420, easing = EmphasizedDecelerate)) { it / 6 } +
                                fadeIn(tween(260))
                        },
                        exit = if (tipo == EntradaCapa.Lateral) {
                            slideOutHorizontally(tween(260, easing = EmphasizedAccelerate)) {
                                if (desdeIzquierda) it / 4 else -it / 4
                            } + fadeOut(tween(220))
                        } else {
                            slideOutVertically(tween(260, easing = EmphasizedAccelerate)) { it / 6 } +
                                fadeOut(tween(220))
                        },
                    )
                    .graphicsLayer {
                        val p = progreso
                        val escala = 1f - 0.1f * p
                        scaleX = escala
                        scaleY = escala
                        // Se recorre hacia el lado del gesto hasta quedar a 8dp del borde contrario.
                        val margen = 8.dp.toPx()
                        val maxX = (size.width * 0.05f - margen).coerceAtLeast(0f)
                        translationX = if (desdeIzquierda) maxX * p else -maxX * p
                        // Y sigue un poco al dedo en vertical.
                        val maxY = (size.height * 0.05f - margen).coerceAtLeast(0f)
                        translationY = (dedoY * 0.5f).coerceIn(-maxY, maxY) * p
                        // Las esquinas se redondean en cuanto empieza el gesto.
                        shape = RoundedCornerShape((32f * (p * 4f).coerceAtMost(1f)).dp)
                        clip = p > 0f
                        shadowElevation = 16.dp.toPx() * p
                    }
            ) {
                if (mostrada != null) contenido(mostrada)
            }
        }
    }
}
