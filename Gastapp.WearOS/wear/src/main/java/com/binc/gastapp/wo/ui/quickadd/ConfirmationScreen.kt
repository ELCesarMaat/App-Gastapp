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

internal fun formatearMonto(monto: Double): String {
    val formato = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    formato.maximumFractionDigits = if (monto % 1.0 == 0.0) 0 else 2
    return formato.format(monto)
}
