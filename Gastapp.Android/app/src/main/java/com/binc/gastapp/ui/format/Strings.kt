package com.binc.gastapp.ui.format

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Textos traducidos para lo que no es Compose: ViewModels, repositorios, avisos y
 * notificaciones. En Compose se usa stringResource directo. Los textos estan en
 * res/values (ingles) y res/values-es (espanol).
 */
interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String

    /** Plural con [count] como primer argumento si no se pasan otros. */
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

/**
 * Lee los recursos en cada llamada: si el usuario cambia el idioma, la Application recibe
 * la configuracion nueva y el siguiente texto ya sale en el idioma nuevo.
 */
class ResourceStrings(private val context: Context) : Strings {
    // Sin argumentos no se formatea: "10% de..." no es un formato.
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.resources.getString(id) else context.resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        if (args.isEmpty()) context.resources.getQuantityString(id, count, count)
        else context.resources.getQuantityString(id, count, *args)
}

/** Para pasar textos de Compose a funciones que reciben [Strings]. */
@Composable
fun rememberStrings(): Strings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { ResourceStrings(context) }
}
