package com.binc.gastapp.demo.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.binc.gastapp.demo.R

/** Baloo se queda solo para las cifras grandes: es lo que da identidad sin pelear con el sistema. */
val Baloo = FontFamily(Font(R.font.baloo_regular))

private val TipografiaGastapp = Typography()

/** Estilo para montos protagonistas (total del dia, deuda total...). */
val cifraGrande: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.displaySmall.copy(fontFamily = Baloo)

val cifraMediana: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.headlineSmall.copy(fontFamily = Baloo)

@Composable
fun GastappTheme(
    oscuro: Boolean,
    dinamico: Boolean,
    content: @Composable () -> Unit,
) {
    val contexto = LocalContext.current
    val esquema = when {
        // Material You: la paleta sale del fondo de pantalla (Android 12+).
        dinamico && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        oscuro -> EsquemaOscuro
        else -> EsquemaClaro
    }

    CompositionLocalProvider(
        LocalColoresEstado provides if (oscuro) ColoresEstadoOscuro else ColoresEstadoClaro
    ) {
        MaterialTheme(
            colorScheme = esquema,
            typography = TipografiaGastapp,
            content = content,
        )
    }
}
