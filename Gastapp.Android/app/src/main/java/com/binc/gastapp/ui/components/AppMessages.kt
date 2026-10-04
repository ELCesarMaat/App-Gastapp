package com.binc.gastapp.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.binc.gastapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Avisos (Snackbar) de toda la app. Viven arriba de las pantallas para que un aviso
 * sobreviva al cambio de pantalla: borrar un gasto desde su detalle regresa a Resumen y
 * el "Deshacer" sigue ahi. Reemplaza los Toast y AlertHelper de MAUI para lo que no
 * necesita confirmacion.
 */
@Stable
class AppMessages internal constructor(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val undoLabel: String,
) {
    /** Muestra [message]; con [actionLabel], [onAction] corre si el usuario lo toca. */
    fun show(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        onAction: () -> Unit = {},
    ) {
        hostState.currentSnackbarData?.dismiss()
        scope.launch {
            val result = hostState.showSnackbar(message, actionLabel = actionLabel, duration = duration, withDismissAction = actionLabel != null)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    /** "Gasto eliminado · Deshacer". */
    fun showUndo(message: String, onUndo: () -> Unit) = show(message, actionLabel = undoLabel, onAction = onUndo)
}

@Composable
fun rememberAppMessages(): AppMessages {
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo)
    return remember(undoLabel) { AppMessages(SnackbarHostState(), scope, undoLabel) }
}

val LocalAppMessages = staticCompositionLocalOf<AppMessages> { error("Falta proveer AppMessages") }

/**
 * Host de los avisos para el Scaffold de cada pantalla. Solo se pinta en la pantalla
 * que esta al frente: durante una transicion las dos estan compuestas y el aviso saldria
 * dos veces.
 */
@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        SnackbarHost(LocalAppMessages.current.hostState, modifier)
    }
}
