package com.binc.gastapp.ui.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Los montos son pesos mexicanos sin importar el idioma del telefono. */
val MexicoLocale: Locale = Locale.forLanguageTag("es-MX")

// NumberFormat no es seguro entre hilos: uno por hilo.
private val currencyFormat = ThreadLocal.withInitial { NumberFormat.getCurrencyInstance(MexicoLocale) }

/** "$1,234.56" (redondeo bancario, igual que el resto de la app). */
fun formatMoney(value: BigDecimal): String = currencyFormat.get()!!.format(value)
