package com.binc.gastapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta tonal generada a partir del verde de marca #126E63 (ButtonsGreen en
// Colors.xaml de la app MAUI). El primario claro es exactamente ese verde; el resto
// son los tonos de Material 3 para que todos los componentes combinen solos.
// Es la misma paleta del demo aprobado (Gastapp.ComposeDemo).

val LightColors = lightColorScheme(
    primary = Color(0xFF126E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F0E1),
    onPrimaryContainer = Color(0xFF00201B),
    inversePrimary = Color(0xFF8BD5C6),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF05201B),
    tertiary = Color(0xFF436278),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9E6FF),
    onTertiaryContainer = Color(0xFF001E2F),
    background = Color(0xFFF5FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceTint = Color(0xFF126E63),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FBF8),
    surfaceDim = Color(0xFFD5DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDEE4E1),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5C6),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFA9F0E1),
    inversePrimary = Color(0xFF126E63),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFABCAE4),
    onTertiary = Color(0xFF123348),
    tertiaryContainer = Color(0xFF2B4A5F),
    onTertiaryContainer = Color(0xFFC9E6FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceTint = Color(0xFF8BD5C6),
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B39),
    surfaceDim = Color(0xFF0E1513),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)

/**
 * Un color de estado en sus tres usos: punto o texto fuerte, fondo del chip y texto
 * sobre ese fondo.
 */
@Immutable
data class StatusColor(
    val strong: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Colores de estado de la app: al corriente, vence pronto y vencido (los mismos
 * #126E63, #D97706 y #C62828 de MAUI, con su variante oscura).
 *
 * Van FUERA del esquema de Material a proposito: significan algo, asi que no deben
 * cambiar si el usuario activa los colores del fondo de pantalla.
 */
@Immutable
data class StatusColors(
    val ok: StatusColor,
    val warning: StatusColor,
    val critical: StatusColor,
)

val LightStatusColors = StatusColors(
    ok = StatusColor(Color(0xFF126E63), Color(0xFFD3F2EA), Color(0xFF0B3F38)),
    warning = StatusColor(Color(0xFFD97706), Color(0xFFFFEBCC), Color(0xFF6B3A00)),
    critical = StatusColor(Color(0xFFC62828), Color(0xFFFFE3DF), Color(0xFF7A1410)),
)

val DarkStatusColors = StatusColors(
    ok = StatusColor(Color(0xFF6FD8C4), Color(0xFF0F3B35), Color(0xFFB8F0E4)),
    warning = StatusColor(Color(0xFFFFB74D), Color(0xFF4F2D00), Color(0xFFFFDDB3)),
    critical = StatusColor(Color(0xFFFF8A80), Color(0xFF5C1512), Color(0xFFFFDAD5)),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/**
 * Color guardado como texto ("#126E63" o "#AARRGGBB"), el de tarjetas y suscripciones.
 * Null si no se puede leer.
 */
fun parseColorHex(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    val value = clean.toLongOrNull(16) ?: return null
    return when (clean.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}
