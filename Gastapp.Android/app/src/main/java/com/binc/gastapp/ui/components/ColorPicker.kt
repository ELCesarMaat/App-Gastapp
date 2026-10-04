package com.binc.gastapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.binc.gastapp.ui.theme.parseColorHex

/** Nombre de cada color de la paleta de tarjetas y suscripciones, para TalkBack. */
private val ColorNames = mapOf(
    "#126E63" to "Esmeralda",
    "#1A73E8" to "Azul",
    "#D97706" to "Ámbar",
    "#7C3AED" to "Violeta",
    "#1F2937" to "Grafito",
    "#E11D48" to "Rubí",
)

/** Circulos de color para elegir el de una tarjeta o suscripcion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { hex ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                Modifier
                    .size(44.dp)
                    .background(parseColorHex(hex) ?: Color.Gray, CircleShape)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(hex) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = "Color ${ColorNames[hex.uppercase()] ?: hex}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}
