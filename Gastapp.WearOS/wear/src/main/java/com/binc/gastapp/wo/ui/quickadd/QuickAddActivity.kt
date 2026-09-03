package com.binc.gastapp.wo.ui.quickadd

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.wear.input.RemoteInputIntentHelper
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.domain.ExpenseParser
import com.binc.gastapp.wo.domain.InvalidReason
import com.binc.gastapp.wo.domain.ParseResult
import com.binc.gastapp.wo.presentation.theme.GastappTheme
import com.binc.gastapp.wo.sync.SyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Captura un gasto por voz. No dibuja nada hasta tener el resultado del dictado:
 * el flujo entero debe caber en un gesto de muñeca.
 *
 * El dictado exige el formato "monto + concepto" ("$20 en Comida"). Si no se cumple
 * no se guarda nada: se muestra el error y se vuelve a pedir el dictado.
 */
class QuickAddActivity : ComponentActivity() {

    private companion object {
        const val CLAVE_ENTRADA = "gasto"
        const val MILIS_CONFIRMACION = 2000L
        const val MILIS_ERROR = 2600L

        /** Ejemplo que se muestra en el dictado y en los errores. */
        const val EJEMPLO = "Ej: \"\$20 en Comida\""
    }

    private sealed interface UiState {
        data object Idle : UiState
        data class Confirm(val data: ConfirmationData) : UiState
        data class Error(val message: String) : UiState
    }

    private val estado = mutableStateOf<UiState>(UiState.Idle)

    private val dictadoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) {
            // El usuario cancelo: no se crea nada.
            finish()
            return@registerForActivityResult
        }

        val texto = RemoteInput.getResultsFromIntent(resultado.data)
            ?.getCharSequence(CLAVE_ENTRADA)
            ?.toString()

        if (texto.isNullOrBlank()) {
            // Sin dictado no hay nada que reintentar: se sale para dar una salida.
            finish()
            return@registerForActivityResult
        }

        procesar(texto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GastappTheme {
                when (val actual = estado.value) {
                    is UiState.Confirm -> ConfirmationScreen(actual.data)
                    is UiState.Error -> QuickAddErrorScreen(actual.message)
                    UiState.Idle -> Unit
                }
            }
        }

        lanzarDictado()
    }

    private fun lanzarDictado() {
        val entrada = RemoteInput.Builder(CLAVE_ENTRADA)
            // El label guia el formato; sin esto el usuario dicta cualquier cosa y el
            // gasto se rechaza sin que sepa por que.
            .setLabel("Monto y concepto. $EJEMPLO")
            .build()

        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(entrada))
        dictadoLauncher.launch(intent)
    }

    private fun procesar(texto: String) {
        when (val resultado = ExpenseParser.parse(texto)) {
            is ParseResult.Success -> guardarGasto(resultado)
            is ParseResult.Invalid -> mostrarError(resultado.reason)
        }
    }

    private fun guardarGasto(resultado: ParseResult.Success) {
        val app = application as GastappApp

        lifecycleScope.launch {
            val (gasto, categoria) = app.repository.saveLocally(resultado.expense)

            // Encolar y confirmar sin esperar a la red: con la API dormida el usuario
            // tendria que mirar la pantalla casi un minuto.
            SyncWorker.enqueue(this@QuickAddActivity)

            // Avisar al telefono para que lo notifique. Va en el appScope y no aqui,
            // porque esta Activity se cierra en dos segundos y se llevaria el envio
            // por delante.
            app.notifyExpenseToPhone(gasto.amount, categoria?.categoryName ?: gasto.title)

            estado.value = UiState.Confirm(
                ConfirmationData(
                    amount = gasto.amount,
                    title = gasto.title,
                    categoryName = categoria?.categoryName,
                    needsReview = gasto.needsReview
                )
            )

            delay(MILIS_CONFIRMACION)
            finish()
        }
    }

    private fun mostrarError(reason: InvalidReason) {
        val mensaje = when (reason) {
            InvalidReason.NO_AMOUNT -> "Falta el monto"
            InvalidReason.NO_TITLE -> "Falta el concepto"
            InvalidReason.EMPTY -> "No te entendí"
        }

        estado.value = UiState.Error(mensaje)

        lifecycleScope.launch {
            // El error se deja ver un momento y se vuelve a pedir el dictado, para que
            // el usuario corrija sin tener que reabrir la app.
            delay(MILIS_ERROR)
            estado.value = UiState.Idle
            lanzarDictado()
        }
    }
}
