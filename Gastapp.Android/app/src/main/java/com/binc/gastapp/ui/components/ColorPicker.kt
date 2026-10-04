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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.binc.gastapp.R
import com.binc.gastapp.ui.theme.parseColorHex

/** Nombre de cada color de la paleta de tarjetas y suscripciones, para TalkBack. */
private val ColorNames = mapOf(
    "#126E63" to R.string.color_name_emerald,
    "#1A73E8" to R.string.color_name_blue,
    "#D97706" to R.string.color_name_amber,
    "#7C3AED" to R.string.color_name_violet,
    "#1F2937" to R.string.color_name_graphite,
    "#E11D48" to R.string.color_name_ruby,
)

/** Circulos de color para elegir el de una tarjeta o suscripcion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { hex ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            val description = stringResource(R.string.color_description, ColorNames[hex.uppercase()]?.let { stringResource(it) } ?: hex)
            Box(
                Modifier
                    .size(44.dp)
                    .background(parseColorHex(hex) ?: Color.Gray, CircleShape)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(hex) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = description
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}
