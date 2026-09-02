package com.binc.gastapp.wo.domain

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

    // Cubre: "350 de comida", "gaste 120 en cafe", "$45.50 gasolina",
    //        "pague 200 pesos por el uber".
    private val REGEX = Regex(
        """(?:gast[eé]|pagu[eé])?\s*\$?\s*(\d+(?:[.,]\d{1,2})?)\s*""" +
            """(?:pesos|mxn|varos)?\s*(?:de|en|para|por|del|en el|en la)?\s*(.*)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): ParsedExpense {
        val texto = input.trim()

        if (texto.isEmpty()) {
            return ParsedExpense(0.0, "Gasto sin descripcion", texto, needsReview = true)
        }

        val match = REGEX.find(texto)
        val montoCrudo = match?.groupValues?.getOrNull(1)
        val resto = match?.groupValues?.getOrNull(2)?.trim().orEmpty()

        // El separador decimal puede venir como coma segun el reconocedor de voz.
        val monto = montoCrudo?.replace(',', '.')?.toDoubleOrNull()

        if (monto == null || monto <= 0.0) {
            return ParsedExpense(0.0, texto, texto, needsReview = true)
        }

        val titulo = if (resto.isBlank()) "Gasto" else resto.replaceFirstChar { it.uppercase() }

        return ParsedExpense(
            amount = monto,
            title = titulo.take(50),
            rawInput = texto,
            needsReview = false
        )
    }
}
