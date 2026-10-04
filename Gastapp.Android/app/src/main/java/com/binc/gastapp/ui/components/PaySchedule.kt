package com.binc.gastapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.format.MexicoLocale
import java.time.DayOfWeek
import java.time.format.TextStyle

// Selectores de frecuencia de ingreso y dias de pago, compartidos por el registro y
// Perfil.

/** Semanal, quincenal o mensual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeTypeSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(IncomeTypes.WEEKLY to "Semanal", IncomeTypes.BIWEEKLY to "Quincenal", IncomeTypes.MONTHLY to "Mensual")
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (id, label) ->
            SegmentedButton(
                selected = selected == id,
                onClick = { onSelect(id) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { FitText(label) }
        }
    }
}

/**
 * Dias de la semana con la numeracion de .NET (0 = domingo), como guarda MAUI: siete
 * celdas iguales de domingo a sabado. [selected] null: ninguno elegido.
 */
@Composable
fun WeekDaySelector(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { index ->
            DayCell(
                label = weekDayShort(index),
                description = weekDayName(index),
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            )
        }
    }
}

/** Los 31 dias del mes en una cuadricula de 7 columnas, como un calendario. */
@Composable
fun MonthDaySelector(selected: List<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..31).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    DayCell(
                        label = day.toString(),
                        description = "Día $day",
                        selected = day in selected,
                        onClick = { onToggle(day) },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                // La ultima semana (29 a 31) conserva el ancho de las celdas.
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Una celda de dia: rellena y en color de marca cuando esta elegida. */
@Composable
private fun DayCell(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "fondo",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "texto",
    )
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier.semantics {
            this.selected = selected
            contentDescription = description
        },
    ) {
        Box(Modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            FitText(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** "Vie" para 5: numeracion de .NET (0 = domingo). */
fun weekDayShort(index: Int): String =
    DayOfWeek.of(if (index == 0) 7 else index).getDisplayName(TextStyle.SHORT, MexicoLocale)
        .replace(".", "")
        .replaceFirstChar { it.titlecase(MexicoLocale) }

/** "viernes" para 5: numeracion de .NET (0 = domingo). */
fun weekDayName(index: Int): String =
    DayOfWeek.of(if (index == 0) 7 else index).getDisplayName(TextStyle.FULL, MexicoLocale)

/**
 * Tocar un dia del mes en [MonthDaySelector]. Quincenal ([limit] 2): el tercero desplaza
 * al que se toco primero. Mensual ([limit] 1): reemplaza. Tocar uno elegido lo quita,
 * salvo que sea el unico.
 */
fun toggledPayDays(current: List<Int>, day: Int, limit: Int): List<Int> = when {
    day in current && current.size > 1 -> current - day
    day in current -> current
    else -> (current + day).takeLast(limit)
}
