package com.binc.gastapp.demo.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.binc.gastapp.demo.ui.ColoresFilaTransparente
import com.binc.gastapp.demo.ui.EncabezadoSeccion
import com.binc.gastapp.demo.ui.GrupoLista
import com.binc.gastapp.demo.ui.IconoTonal
import com.binc.gastapp.demo.ui.conExtra

/** Preferencia de tema de la app. */
enum class PreferenciaTema(val etiqueta: String) { Sistema("Sistema"), Claro("Claro"), Oscuro("Oscuro") }

private class Opcion(
    val icono: ImageVector,
    val titulo: String,
    val detalle: String? = null,
    /** Contenido bajo el titulo (en lugar del detalle). */
    val extra: (@Composable () -> Unit)? = null,
    /** Control a la derecha, normalmente un Switch. */
    val contenido: (@Composable () -> Unit)? = null,
)

@Composable
fun AjustesScreen(
    contentPadding: PaddingValues,
    tema: PreferenciaTema,
    onTema: (PreferenciaTema) -> Unit,
    dinamico: Boolean,
    onDinamico: (Boolean) -> Unit,
) {
    var recordatorios by remember { mutableStateOf(true) }
    var avisosTarjetas by remember { mutableStateOf(true) }

    val notificaciones = listOf(
        Opcion(Icons.Rounded.Notifications, "Recordatorios de gasto", "Te avisamos para que registres lo del día") {
            Switch(checked = recordatorios, onCheckedChange = { recordatorios = it })
        },
        Opcion(Icons.Rounded.Schedule, "Frecuencia", "Cada 4 horas"),
        Opcion(Icons.Rounded.CreditCard, "Avisos de tarjetas", "Corte, fecha límite y día de pago") {
            Switch(checked = avisosTarjetas, onCheckedChange = { avisosTarjetas = it })
        },
    )

    val apariencia = listOf(
        Opcion(
            Icons.Rounded.DarkMode,
            "Tema",
            extra = {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        PreferenciaTema.entries.forEachIndexed { i, opcion ->
                            SegmentedButton(
                                selected = opcion == tema,
                                onClick = { onTema(opcion) },
                                shape = SegmentedButtonDefaults.itemShape(i, PreferenciaTema.entries.size),
                            ) { Text(opcion.etiqueta) }
                        }
                    }
                }
            },
        ),
        Opcion(Icons.Rounded.Palette, "Colores del fondo de pantalla", "Usa la paleta de tu teléfono (Material You)") {
            Switch(checked = dinamico, onCheckedChange = onDinamico)
        },
    )

    val datos = listOf(
        Opcion(Icons.Rounded.CloudDone, "Sincronizado", "Todo está en la nube · hace 5 min"),
        Opcion(Icons.Rounded.CloudUpload, "Exportar respaldo", "Guarda una copia en un archivo"),
        Opcion(Icons.Rounded.Restore, "Restaurar respaldo", "Desde un archivo de Gastapp"),
    )

    val otros = listOf(
        Opcion(Icons.Rounded.Watch, "Reloj vinculado", "Pixel Watch 4 · último envío hace 2 min"),
        Opcion(Icons.Rounded.Info, "Acerca de", "Demo de interfaz nativa · Material 3"),
    )

    LazyColumn(contentPadding = contentPadding.conExtra(abajo = 24.dp)) {
        item { EncabezadoSeccion("Notificaciones") }
        item { GrupoLista(notificaciones) { FilaOpcion(it) } }
        item { EncabezadoSeccion("Apariencia") }
        item { GrupoLista(apariencia) { FilaOpcion(it) } }
        item { EncabezadoSeccion("Tus datos") }
        item { GrupoLista(datos) { FilaOpcion(it) } }
        item { EncabezadoSeccion("Más") }
        item { GrupoLista(otros) { FilaOpcion(it) } }
    }
}

@Composable
private fun FilaOpcion(opcion: Opcion) {
    ListItem(
        colors = ColoresFilaTransparente,
        modifier = Modifier.clickable { },
        leadingContent = { IconoTonal(opcion.icono) },
        headlineContent = { Text(opcion.titulo) },
        supportingContent = opcion.extra ?: opcion.detalle?.let { detalle ->
            @Composable { Text(detalle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = opcion.contenido,
    )
}
