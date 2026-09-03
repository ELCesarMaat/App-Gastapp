package com.binc.gastapp.wo.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.text.NumberFormat
import java.util.Locale

data class ConfirmationData(
    val amount: Double,
    val title: String,
    val categoryName: String?,
    val needsReview: Boolean
)

/**
 * Se muestra cuando el dictado no trae "monto + concepto". Deja claro que falto y
 * recuerda el formato antes de volver a pedir el dictado.
 */
@Composable
fun QuickAddErrorScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.error,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Di monto y concepto",
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "Ej: \"\$20 en Comida\"",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun ConfirmationScreen(data: ConfirmationData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (data.needsReview) {
            Text(
                text = "Guardado sin monto",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Corrígelo en el teléfono",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = formatearMonto(data.amount),
                style = MaterialTheme.typography.display3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = data.categoryName ?: data.title,
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// Construir un NumberFormat es caro (arrastra ICU) y esto se llama una vez por fila
// de la lista y por recomposicion, asi que se arman una sola vez.
// No son thread-safe: usarlos solo desde la UI.
private val localeMx = Locale.forLanguageTag("es-MX")
private val formatoSinDecimales = NumberFormat.getCurrencyInstance(localeMx)
    .apply { maximumFractionDigits = 0 }
private val formatoConDecimales = NumberFormat.getCurrencyInstance(localeMx)
    .apply { maximumFractionDigits = 2 }

internal fun formatearMonto(monto: Double): String =
    if (monto % 1.0 == 0.0) formatoSinDecimales.format(monto) else formatoConDecimales.format(monto)
