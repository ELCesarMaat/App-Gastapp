package com.binc.gastapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import com.binc.gastapp.R

/** Baloo se queda solo para las cifras grandes: es lo que da identidad sin pelear con el sistema. */
val Baloo = FontFamily(Font(R.font.baloo_regular))

private val GastappTypography = Typography()

/** Estilo para montos protagonistas (total del dia, deuda total...). */
val amountLarge: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.displaySmall.copy(fontFamily = Baloo)

val amountMedium: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.headlineSmall.copy(fontFamily = Baloo)

/**
 * Tema de la app: Material 3 con la paleta de marca. Con [dynamicColor] la paleta sale
 * del fondo de pantalla (Material You); queda apagado por defecto y se prende desde
 * Ajustes. Los colores de estado no cambian con el.
 */
@Composable
fun GastappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // minSdk 31: el color dinamico siempre esta disponible.
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GastappTypography,
            content = content,
        )
    }
}
