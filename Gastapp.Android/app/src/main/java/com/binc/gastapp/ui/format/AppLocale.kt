package com.binc.gastapp.ui.format

import android.content.Context
import android.content.res.Resources
import com.binc.gastapp.R
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/** "Español", "English": el idioma de los textos, escrito en ese idioma. */
fun languageName(): String =
    AppLocale.locale.getDisplayLanguage(AppLocale.locale).replaceFirstChar { it.titlecase(AppLocale.locale) }

/** "Peso mexicano (MXN)", "Euro (EUR)" */
fun currencyName(): String {
    val name = AppLocale.currency.getDisplayName(AppLocale.locale).replaceFirstChar { it.titlecase(AppLocale.locale) }
    return "$name (${AppLocale.currency.currencyCode})"
}

/** Con lo que se formateaba todo antes de traducir la app; las pruebas se quedan aqui. */
val MexicoLocale: Locale = Locale.forLanguageTag("es-MX")

private val MexicanPeso: Currency = Currency.getInstance("MXN")

/**
 * Idioma, region y moneda con que se formatean montos y fechas.
 *
 * - El idioma es el que resolvio Android para los textos (values/ = ingles, values-es/ =
 *   espanol): un telefono en frances ve la app en ingles, y sus fechas tambien.
 * - La region es la del telefono, aunque el usuario le haya puesto otro idioma solo a la
 *   app (Android 13+): de ahi salen la moneda y los separadores.
 * - Los montos no se convierten: la moneda solo cambia como se muestran.
 *
 * Lo actualizan GastappApplication y MainActivity (al arrancar y al cambiar el idioma). Sin
 * eso (pruebas locales) se queda en es-MX con pesos, como era la app antes.
 */
object AppLocale {
    @Volatile
    var locale: Locale = MexicoLocale
        private set

    @Volatile
    var currency: Currency = MexicanPeso
        private set

    /** "es" o "en": el idioma en que salen los textos. */
    val language: String get() = locale.language

    /** Decimales que lleva la moneda (2 para pesos, euros o dolares; 0 para yenes). */
    val currencyDigits: Int get() = currency.defaultFractionDigits.coerceAtLeast(0)

    val decimalSeparator: Char get() = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    fun update(context: Context) {
        val language = context.getString(R.string.locale_language)
        val region = phoneRegion(context)
        set(Locale.Builder().setLanguage(language).setRegion(region).build(), currencyOf(region))
    }

    /** Para las pruebas de los formatos. */
    fun set(locale: Locale, currency: Currency) {
        this.locale = locale
        this.currency = currency
        FormatCache.clear()
    }

    /**
     * La region de los ajustes del sistema; la configuracion de la app puede traer solo el
     * idioma que se eligio para ella ("es" sin pais).
     */
    private fun phoneRegion(context: Context): String {
        val system = Resources.getSystem().configuration.locales
        val app = context.resources.configuration.locales
        return sequenceOf(system, app)
            .flatMap { list -> (0 until list.size()).asSequence().map { list[it].country } }
            .firstOrNull { it.length == 2 }
            ?: "MX"
    }

    /** Regiones sin moneda propia (Antartida) o desconocidas se quedan en pesos. */
    private fun currencyOf(region: String): Currency = runCatching {
        Currency.getInstance(Locale.Builder().setRegion(region).build())
    }.getOrNull() ?: MexicanPeso
}
