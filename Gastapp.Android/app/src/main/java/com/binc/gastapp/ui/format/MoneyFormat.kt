package com.binc.gastapp.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Formatos armados para el idioma y la moneda vigentes. NumberFormat no es seguro entre
 * hilos: cada formato vive en un ThreadLocal, y AppLocale vacia el cache al cambiar.
 */
internal object FormatCache {
    private val cache = ConcurrentHashMap<String, ThreadLocal<NumberFormat>>()

    fun get(key: String, create: () -> NumberFormat): NumberFormat =
        cache.getOrPut(key) { ThreadLocal.withInitial(create) }.get()!!

    fun clear() = cache.clear()
}

private fun currencyFormat(decimals: Int): NumberFormat = FormatCache.get("money$decimals") {
    NumberFormat.getCurrencyInstance(AppLocale.locale).apply {
        currency = AppLocale.currency
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_EVEN
    }
}

/** "$1,234.56" en Mexico, "1234,56 €" en Espana (redondeo bancario, igual que el resto de la app). */
fun formatMoney(value: BigDecimal): String = currencyFormat(AppLocale.currencyDigits).format(value)

/** "$1,235": sin centavos, para montos de referencia (mensualidades de MSI). */
fun formatMoneyWhole(value: BigDecimal): String = currencyFormat(0).format(value)

/** Numero con [decimals] decimales fijos y separador de miles ("12.5", "1,234.0"). */
fun formatDecimal(value: BigDecimal, decimals: Int): String = FormatCache.get("decimal$decimals") {
    NumberFormat.getNumberInstance(AppLocale.locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_EVEN
    }
}.format(value)

/** "15.38%" ("15,38 %" donde el decimal es coma): hasta [maxDecimals] decimales, sin ceros de sobra. */
fun formatPercent(value: BigDecimal, maxDecimals: Int = 2): String {
    val number = FormatCache.get("percent$maxDecimals") {
        NumberFormat.getNumberInstance(AppLocale.locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = maxDecimals
            roundingMode = RoundingMode.HALF_EVEN
        }
    }.format(value)
    return if (AppLocale.language == "es" && AppLocale.decimalSeparator == ',') "$number %" else "$number%"
}

/** El simbolo de la moneda para los campos de monto ("$", "€"). */
val currencySymbol: String get() = AppLocale.currency.getSymbol(AppLocale.locale)

/**
 * Lee un monto tecleado: acepta el simbolo de la moneda, espacios y separadores de miles
 * ("$12,500.50", "12.500,50 €"). Devuelve null si no es un numero. No redondea: eso lo
 * decide quien lo usa.
 */
fun parseAmountInput(text: String): BigDecimal? {
    val decimal = AppLocale.decimalSeparator
    val grouping = if (decimal == ',') '.' else ','
    val clean = text.filter { it.isDigit() || it == decimal || it == grouping || it == '-' }
        .replace(grouping.toString(), "")
        .replace(decimal, '.')
    if (clean.isEmpty()) return null
    return clean.toBigDecimalOrNull()
}

/**
 * Filtro para los campos de monto: solo digitos y un separador decimal con hasta
 * [decimals] decimales. Devuelve null si el texto nuevo no se debe aceptar.
 *
 * Donde el decimal es punto, la coma se toma como separador de miles y se quita, como
 * siempre. Donde es coma, el punto tambien vale como decimal (el teclado numerico a veces
 * solo trae punto) y se muestra como coma.
 */
fun filterAmountInput(text: String, decimals: Int = AppLocale.currencyDigits): String? {
    val decimal = AppLocale.decimalSeparator
    val clean = text.replace(" ", "").let { if (decimal == ',') it.replace(',', '.') else it.replace(",", "") }
    if (clean.isEmpty()) return ""
    val pattern = if (decimals > 0) Regex("^\\d{0,9}(\\.\\d{0,$decimals})?$") else Regex("^\\d{0,9}$")
    return if (pattern.matches(clean)) clean.replace('.', decimal) else null
}

/** "1250.50": el monto con los decimales de la moneda, para llenar un campo (montos rapidos). */
fun BigDecimal.toFixedInputText(): String =
    setScale(AppLocale.currencyDigits, RoundingMode.HALF_EVEN).toPlainString().replace('.', AppLocale.decimalSeparator)

/** "0.00" (o "0,00", o "0" en monedas sin centavos): el ejemplo de los campos de monto. */
val amountPlaceholder: String get() = BigDecimal.ZERO.toFixedInputText()

/** Para mostrar un monto en un campo editable: "1250.5" y no "1,250.50" ("1250,5" donde el decimal es coma). */
fun BigDecimal.toInputText(): String =
    stripTrailingZeros().toPlainString().let { if (it == "0") "" else it.replace('.', AppLocale.decimalSeparator) }
