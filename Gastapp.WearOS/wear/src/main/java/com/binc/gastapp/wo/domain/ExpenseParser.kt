package com.binc.gastapp.wo.domain

import java.text.Normalizer

/**
 * Resultado de interpretar lo que dicto el usuario.
 *
 * Si [needsReview] es true, el parser no encontro monto. El gasto se guarda igual:
 * perder el registro es peor que registrarlo mal, y el usuario lo corrige desde el
 * telefono.
 */
data class ParsedExpense(
    val amount: Double,
    val title: String,
    val rawInput: String,
    val needsReview: Boolean
)

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

    fun parse(input: String): ParsedExpense {
        val texto = input.trim()

        if (texto.isEmpty()) {
            return ParsedExpense(0.0, "Gasto sin descripcion", texto, needsReview = true)
        }

        val match = MONTO.find(texto)

        val monto = match?.groupValues?.getOrNull(1)?.let { aMonto(it) }

        if (monto == null || monto <= 0.0) {
            return ParsedExpense(0.0, texto.take(50), texto, needsReview = true)
        }

        // La descripcion es todo lo que NO es el monto, venga antes o despues.
        // El usuario dice tanto "350 de comida" como "Tacos $20"; antes se perdia
        // la descripcion en el segundo caso.
        val titulo = limpiar(texto.removeRange(match.range))

        return ParsedExpense(
            amount = monto,
            title = titulo.ifBlank { "Gasto" }.take(50),
            rawInput = texto,
            needsReview = false
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
            .filter { sinAcentos(it.lowercase()) !in RELLENO }
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }

    private fun sinAcentos(valor: String): String =
        ACENTOS.replace(Normalizer.normalize(valor, Normalizer.Form.NFD), "")
}
