package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.datos.Categoria
import com.binc.gastapp.demo.datos.Gasto
import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.Metodo
import com.binc.gastapp.demo.datos.RepositorioDemo
import com.binc.gastapp.demo.datos.tarjetas
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.etiquetaFecha
import com.binc.gastapp.demo.ui.fechaDesdeMillisUtc
import com.binc.gastapp.demo.ui.millisUtc
import com.binc.gastapp.demo.ui.pesos
import com.binc.gastapp.demo.ui.theme.Baloo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private val plazosMsi = listOf(3, 6, 9, 12, 18, 24)

/**
 * Hoja para crear o editar un gasto (la misma, como en la app actual). Con
 * [gastoInicial] null es un gasto nuevo; si no, llega precargada y con "Eliminar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioGastoSheet(
    gastoInicial: Gasto?,
    fechaPorDefecto: LocalDate,
    onGuardar: (Gasto) -> Unit,
    onEliminar: (() -> Unit)?,
    onCerrar: () -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    val haptica = LocalHapticFeedback.current
    val esNuevo = gastoInicial == null

    var monto by remember {
        mutableStateOf(gastoInicial?.let { String.format(Locale.US, "%.2f", it.monto) } ?: "")
    }
    var errorMonto by remember { mutableStateOf(false) }
    var categoria by remember { mutableStateOf(gastoInicial?.categoria ?: Categoria.Comida) }
    var metodo by remember { mutableStateOf(gastoInicial?.metodo ?: Metodo.Efectivo) }
    var tarjeta by remember { mutableStateOf(gastoInicial?.tarjeta ?: tarjetas.first().etiqueta) }
    var msi by remember { mutableStateOf(false) }
    var plazo by remember { mutableIntStateOf(3) }
    var titulo by remember { mutableStateOf(gastoInicial?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(gastoInicial?.descripcion ?: "") }
    var fecha by remember { mutableStateOf(gastoInicial?.fecha ?: fechaPorDefecto) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    val sacudida = remember { Animatable(0f) }

    // Primero baja la hoja con su animacion y despues se aplica el cambio: asi se ve
    // como la lista reacciona (el gasto entra, sale o cambia el total).
    fun cerrarY(accion: () -> Unit) {
        alcance.launch { estadoHoja.hide() }.invokeOnCompletion {
            if (!estadoHoja.isVisible) {
                accion()
                onCerrar()
            }
        }
    }

    fun guardar() {
        val valor = monto.replace(",", "").toDoubleOrNull()
        if (valor == null || valor <= 0.0) {
            errorMonto = true
            haptica.performHapticFeedback(HapticFeedbackType.Reject)
            alcance.launch {
                sacudida.animateTo(
                    0f,
                    keyframes {
                        durationMillis = 420
                        -14f at 60
                        12f at 120
                        -9f at 180
                        6f at 240
                        -3f at 300
                    },
                )
            }
            return
        }
        haptica.performHapticFeedback(HapticFeedbackType.Confirm)
        val gasto = Gasto(
            id = gastoInicial?.id ?: RepositorioDemo.nuevoId(),
            fecha = fecha,
            titulo = titulo.trim().ifEmpty { categoria.nombre },
            categoria = categoria,
            monto = valor,
            hora = gastoInicial?.hora ?: LocalTime.now().withSecond(0).withNano(0),
            metodo = metodo,
            tarjeta = if (metodo == Metodo.Credito) tarjeta else null,
            descripcion = descripcion.trim(),
        )
        cerrarY { onGuardar(gasto) }
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estadoHoja) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(bottom = 16.dp),
        ) {
            // Guardar arriba, como en la hoja actual: queda visible aunque el teclado tape el resto.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (esNuevo) "Nuevo gasto" else "Editar gasto",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { guardar() }) { Text("Guardar") }
            }
            Spacer(Modifier.height(16.dp))

            val ayudaMonto: (@Composable () -> Unit)? = if (errorMonto) {
                { Text("Escribe una cantidad mayor a cero") }
            } else {
                null
            }
            OutlinedTextField(
                value = monto,
                onValueChange = { nuevo ->
                    monto = nuevo.filter { it.isDigit() || it == '.' }
                    errorMonto = false
                },
                label = { Text("Cantidad") },
                placeholder = { Text("0.00") },
                prefix = { Text("$ ") },
                isError = errorMonto,
                supportingText = ayudaMonto,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = Baloo),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = sacudida.value.dp.toPx() },
            )

            Etiqueta("Categoría")
            val estadoCategorias = rememberLazyListState(
                initialFirstVisibleItemIndex = Categoria.entries.indexOf(categoria).coerceAtLeast(0),
            )
            LazyRow(state = estadoCategorias, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Categoria.entries) { c ->
                    FilterChip(
                        selected = c == categoria,
                        onClick = {
                            categoria = c
                            haptica.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        label = { Text(c.nombre) },
                        leadingIcon = { Icon(c.icono, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Etiqueta("Método de pago")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Metodo.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == metodo,
                        onClick = { metodo = m },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = Metodo.entries.size),
                        icon = {},
                    ) { Text(m.etiqueta, maxLines = 1) }
                }
            }

            // La parte de tarjeta y MSI se despliega solo con "Crédito".
            AnimatedVisibility(
                visible = metodo == Metodo.Credito,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    Etiqueta("Tarjeta")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        items(tarjetas) { t ->
                            FilterChip(
                                selected = t.etiqueta == tarjeta,
                                onClick = { tarjeta = t.etiqueta },
                                label = { Text(t.etiqueta) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .background(t.color, CircleShape)
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                        ListItem(
                            colors = ColoresFilaTransparente,
                            headlineContent = { Text("Meses sin intereses") },
                            supportingContent = { Text("Diferir el gasto en mensualidades fijas") },
                            trailingContent = { Switch(checked = msi, onCheckedChange = { msi = it }) },
                        )
                        AnimatedVisibility(
                            visible = msi,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(plazosMsi) { meses ->
                                        FilterChip(
                                            selected = meses == plazo,
                                            onClick = { plazo = meses },
                                            label = { Text("$meses meses") },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                val mensual = (monto.toDoubleOrNull() ?: 0.0) / plazo
                                AnimatedContent(
                                    targetState = plazo to mensual,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "mensualidad",
                                ) { (meses, cuanto) ->
                                    Text(
                                        "$meses mensualidades de ${pesos(cuanto)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Etiqueta("Detalle")
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                placeholder = { Text("Ej. Supermercado, café...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Agrega una nota…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedCard(onClick = { mostrarCalendario = true }, shape = RoundedCornerShape(16.dp)) {
                ListItem(
                    colors = ColoresFilaTransparente,
                    leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                    overlineContent = { Text("Fecha del gasto") },
                    headlineContent = {
                        AnimatedContent(
                            targetState = fecha,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "fecha",
                        ) { Text(etiquetaFecha(it)) }
                    },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                )
            }

            if (onEliminar != null) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        haptica.performHapticFeedback(HapticFeedbackType.LongPress)
                        cerrarY(onEliminar)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar gasto")
                }
            }
        }
    }

    if (mostrarCalendario) {
        val hoyMillis = Hoy.millisUtc()
        val estadoFecha = rememberDatePickerState(
            initialSelectedDateMillis = fecha.millisUtc(),
            selectableDates = object : SelectableDates {
                // No se registran gastos a futuro.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= hoyMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoFecha.selectedDateMillis?.let { fecha = fechaDesdeMillisUtc(it) }
                    mostrarCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = estadoFecha)
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
