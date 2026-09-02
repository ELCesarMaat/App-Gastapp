package com.binc.gastapp.wo.ui.quickadd

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.wear.input.RemoteInputIntentHelper
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.domain.ExpenseParser
import com.binc.gastapp.wo.presentation.theme.GastappTheme
import com.binc.gastapp.wo.sync.SyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Captura un gasto por voz. No dibuja nada hasta tener el resultado del dictado:
 * el flujo entero debe caber en un gesto de muñeca.
 */
class QuickAddActivity : ComponentActivity() {

    private companion object {
        const val CLAVE_ENTRADA = "gasto"
        const val MILIS_CONFIRMACION = 2000L
    }

    private val confirmacion = mutableStateOf<ConfirmationData?>(null)

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
            finish()
            return@registerForActivityResult
        }

        guardarGasto(texto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GastappTheme {
                confirmacion.value?.let { ConfirmationScreen(it) }
            }
        }

        lanzarDictado()
    }

    private fun lanzarDictado() {
        val entrada = RemoteInput.Builder(CLAVE_ENTRADA)
            .setLabel("¿Cuánto y en qué?")
            .build()

        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(entrada))
        dictadoLauncher.launch(intent)
    }

    private fun guardarGasto(texto: String) {
        val app = application as GastappApp

        lifecycleScope.launch {
            val parsed = ExpenseParser.parse(texto)
            val (gasto, categoria) = app.repository.saveLocally(parsed)

            // Encolar y confirmar sin esperar a la red: con la API dormida el usuario
            // tendria que mirar la pantalla casi un minuto.
            SyncWorker.enqueue(this@QuickAddActivity)

            confirmacion.value = ConfirmationData(
                amount = gasto.amount,
                title = gasto.title,
                categoryName = categoria?.categoryName,
                needsReview = gasto.needsReview
            )

            delay(MILIS_CONFIRMACION)
            finish()
        }
    }
}
