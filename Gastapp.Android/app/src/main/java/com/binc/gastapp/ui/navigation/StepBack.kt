package com.binc.gastapp.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException

/**
 * Atras predictivo entre los pasos de un asistente (registro, recuperar contrasena):
 * mientras el dedo arrastra, el paso se encoge y se recorre; al soltar se llama
 * [onBack]. Devuelve la pose del gesto (0 a 1) para [stepBackPose].
 *
 * Con [enabled] en false (primer paso) el gesto lo toma Navigation y cierra la pantalla.
 */
@Composable
fun rememberStepBack(enabled: Boolean, onBack: () -> Unit): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress = it.progress }
            progress = 0f
            onBack()
        } catch (e: CancellationException) {
            progress = 0f
            throw e
        }
    }
    val pose by animateFloatAsState(progress, label = "atras-paso")
    return pose
}

fun Modifier.stepBackPose(pose: Float): Modifier = graphicsLayer {
    val scale = 1f - 0.06f * pose
    scaleX = scale
    scaleY = scale
    translationX = 24.dp.toPx() * pose
    alpha = 1f - 0.3f * pose
}
