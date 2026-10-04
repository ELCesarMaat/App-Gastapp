package com.binc.gastapp.wo.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.binc.gastapp.wo.R
import java.text.NumberFormat
import java.util.Currency
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
            text = stringResource(R.string.say_amount_and_concept),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = stringResource(R.string.voice_example),
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
                text = stringResource(R.string.saved_without_amount),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.fix_on_phone),
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
// de la lista y por recomposicion, asi que se arman una vez por hilo y por idioma (los
// usan la UI y el tile, y NumberFormat no es thread-safe).
private class FormatosMonto(val locale: Locale, val sinDecimales: NumberFormat, val conDecimales: NumberFormat)

private val formatos = ThreadLocal<FormatosMonto>()

/**
 * Con la moneda de la region del reloj ("$85", "$85.50", "85 €"); si el reloj no tiene
 * region, en pesos, como antes.
 */
private fun formatosActuales(): FormatosMonto {
    val locale = Locale.getDefault()
    formatos.get()?.takeIf { it.locale == locale }?.let { return it }
    val moneda = runCatching { Currency.getInstance(locale) }.getOrNull() ?: Currency.getInstance("MXN")
    fun formato(decimales: Int) = NumberFormat.getCurrencyInstance(locale).apply {
        currency = moneda
        minimumFractionDigits = decimales
        maximumFractionDigits = decimales
    }
    return FormatosMonto(locale, formato(0), formato(2)).also(formatos::set)
}

internal fun formatearMonto(monto: Double): String = formatosActuales().let {
    if (monto % 1.0 == 0.0) it.sinDecimales.format(monto) else it.conDecimales.format(monto)
}
