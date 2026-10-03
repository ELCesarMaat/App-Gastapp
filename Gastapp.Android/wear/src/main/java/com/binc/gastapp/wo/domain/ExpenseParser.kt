package com.binc.gastapp.wo.domain

import java.text.Normalizer

/**
 * Resultado de interpretar lo que dicto el usuario.
 *
 * [needsReview] sobrevive por compatibilidad con el servidor, pero desde que el reloj
 * exige el formato "monto + concepto" ya no se guardan gastos incompletos: el parser
 * los rechaza y la pantalla vuelve a pedir el dictado. Siempre es false.
 */
data class ParsedExpense(
    val amount: Double,
    val title: String,
    val rawInput: String,
    val needsReview: Boolean = false
)

/** Por que no se pudo interpretar el dictado. Cada motivo tiene su propio mensaje. */
enum class InvalidReason {
    /** No se entendio nada (el reconocedor devolvio vacio). */
    EMPTY,

    /** Hay concepto pero no un monto: "comida", "tacos". */
    NO_AMOUNT,

    /** Hay monto pero no concepto: "$20", "cincuenta pesos". */
    NO_TITLE
}

/** Un dictado interpretado, o el motivo por el que no cumple el formato. */
sealed interface ParseResult {
    data class Success(val expense: ParsedExpense) : ParseResult
    data class Invalid(val reason: InvalidReason) : ParseResult
}

object ExpenseParser {

    /** El monto, con o sin signo de pesos, admitiendo separadores de miles. */
    private val MONTO = Regex("""\$?\s*(\d+(?:[.,]\d+)*)""")

    private val SEPARADORES = Regex("""[\s,.;:\-]+""")

    private val ACENTOS = Regex("""\p{InCombiningDiacriticalMarks}+""")

    /**
     * Palabras que no describen el gasto. Se comparan sin acentos y por palabra
     * completa.
     *
     * No se usa \b en una expresion regular para esto: en Java los acentos no cuentan
     * como caracter de palabra, asi que "pague" o "gaste" nunca casarian.
     */
    private val RELLENO = setOf(
        "gaste", "pague", "pesos", "peso", "mxn", "varos",
        "de", "del", "en", "el", "la", "los", "las", "para", "por", "un", "una"
    )

    /**
     * Exige el formato "monto + concepto" en cualquier orden: "$20 en Comida",
     * "20 para Transporte", "Tacos 50". Si falta el monto o el concepto se rechaza,
     * para que el usuario repita el dictado en vez de guardar un gasto a medias.
     */
    fun parse(input: String): ParseResult {
        val texto = input.trim()

        if (texto.isEmpty()) {
            return ParseResult.Invalid(InvalidReason.EMPTY)
        }

        val match = MONTO.find(texto)
        val monto = match?.groupValues?.getOrNull(1)?.let { aMonto(it) }

        if (match == null || monto == null || monto <= 0.0) {
            return ParseResult.Invalid(InvalidReason.NO_AMOUNT)
        }

        // La descripcion es todo lo que NO es el monto, venga antes o despues.
        // El usuario dice tanto "350 de comida" como "Tacos $20".
        val titulo = limpiar(texto.removeRange(match.range))

        if (titulo.isBlank()) {
            return ParseResult.Invalid(InvalidReason.NO_TITLE)
        }

        return ParseResult.Success(
            ParsedExpense(
                amount = monto,
                title = titulo.take(50),
                rawInput = texto
            )
        )
    }

    /**
     * El reconocedor de voz devuelve tanto "1,250.50" como "1.250,50". El ultimo
     * separador es decimal solo si lo siguen una o dos cifras; cualquier otro es
     * separador de miles y se descarta.
     */
    private fun aMonto(crudo: String): Double? {
        val posicion = maxOf(crudo.lastIndexOf('.'), crudo.lastIndexOf(','))
        if (posicion < 0) return crudo.toDoubleOrNull()

        val decimales = crudo.length - posicion - 1
        if (decimales in 1..2) {
            val entero = crudo.substring(0, posicion).filter { it.isDigit() }
            return "$entero.${crudo.substring(posicion + 1)}".toDoubleOrNull()
        }

        return crudo.filter { it.isDigit() }.toDoubleOrNull()
    }

    private fun limpiar(valor: String): String =
        valor.split(SEPARADORES)
            .filter { it.isNotBlank() }
            // Descarta simbolos sueltos como el "$" que queda de "20$": el signo va
            // despues del numero y la regex del monto solo lo consume cuando va antes.
            .filter { token -> token.any(Char::isLetterOrDigit) }
            .filter { sinAcentos(it.lowercase()) !in RELLENO }
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }

    private fun sinAcentos(valor: String): String =
        ACENTOS.replace(Normalizer.normalize(valor, Normalizer.Form.NFD), "")
}
