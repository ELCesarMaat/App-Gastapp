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

/** Numero con [decimals] decimales fijos y separador de miles ("12.5", "1,234.0"). */
fun formatDecimal(value: BigDecimal, decimals: Int): String {
    val format = NumberFormat.getNumberInstance(MexicoLocale)
    format.minimumFractionDigits = decimals
    format.maximumFractionDigits = decimals
    format.roundingMode = java.math.RoundingMode.HALF_EVEN
    return format.format(value)
}

/**
 * Lee un monto tecleado: acepta "$", espacios y comas de miles ("$12,500.50"). Devuelve
 * null si no es un numero. No redondea: eso lo decide quien lo usa.
 */
fun parseAmountInput(text: String): BigDecimal? {
    val clean = text.trim().replace("$", "").replace(",", "").replace(" ", "")
    if (clean.isEmpty()) return null
    return clean.toBigDecimalOrNull()
}

/**
 * Filtro para los campos de monto: solo digitos y un punto con hasta [decimals]
 * decimales. Devuelve null si el texto nuevo no se debe aceptar.
 */
fun filterAmountInput(text: String, decimals: Int = 2): String? {
    val clean = text.replace(",", "").replace(" ", "")
    if (clean.isEmpty()) return ""
    val pattern = if (decimals > 0) Regex("^\\d{0,9}(\\.\\d{0,$decimals})?$") else Regex("^\\d{0,9}$")
    return if (pattern.matches(clean)) clean else null
}

/** Para mostrar un monto en un campo editable: "1250.5" y no "1,250.50". */
fun BigDecimal.toInputText(): String = stripTrailingZeros().toPlainString().let { if (it == "0") "" else it }
