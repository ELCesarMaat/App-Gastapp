package com.binc.gastapp.domain.money

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Precision de las divisiones. El decimal de .NET guarda 28-29 digitos; aqui se usan
 * 34 para no perder nada frente a la version MAUI.
 */
val DivisionPrecision: MathContext = MathContext.DECIMAL128

val Cent: BigDecimal = BigDecimal("0.01")

private val Hundred = BigDecimal(100)

/** Math.Round(valor, decimales) de .NET: por defecto redondea al par (bancario). */
fun BigDecimal.roundHalfEven(decimals: Int): BigDecimal = setScale(decimals, RoundingMode.HALF_EVEN)

/** Division sin perder precision (equivale al operador / con decimal en C#). */
infix fun BigDecimal.dividedBy(divisor: BigDecimal): BigDecimal = divide(divisor, DivisionPrecision)

infix fun BigDecimal.dividedBy(divisor: Int): BigDecimal = divide(BigDecimal(divisor), DivisionPrecision)

/** Porcentaje que representa una parte del total (sin redondear). */
fun BigDecimal.percentOf(total: BigDecimal): BigDecimal = (this dividedBy total).multiply(Hundred)

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> BigDecimal): BigDecimal =
    fold(BigDecimal.ZERO) { acc, item -> acc.add(selector(item)) }

/** Room guarda el dinero como centavos enteros: las sumas en SQL quedan exactas. */
fun BigDecimal.toCents(): Long = setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()

fun Long.centsToMoney(): BigDecimal = BigDecimal.valueOf(this, 2)

private val LocaleMx: Locale = Locale.Builder().setLanguage("es").setRegion("MX").build()

/** Formato N2 de .NET en es-MX ("1,234.56"); sin signo de pesos. */
fun BigDecimal.formatN2(): String {
    val formato = NumberFormat.getNumberInstance(LocaleMx)
    formato.minimumFractionDigits = 2
    formato.maximumFractionDigits = 2
    formato.roundingMode = RoundingMode.HALF_EVEN
    return formato.format(this)
}
