package com.binc.gastapp.wo.domain

import com.binc.gastapp.wo.data.local.CategoryEntity
import java.text.Normalizer

/**
 * Elige la categoria del usuario que mejor corresponde al texto dictado.
 *
 * Devuelve null cuando no hay una coincidencia razonable. Eso es correcto y
 * deliberado: el servidor asigna la categoria por defecto cuando recibe null,
 * asi que nunca hay que inventar un categoryId.
 */
object CategoryMatcher {

    // Palabras que suelen dictarse y a que nombre de categoria apuntan.
    // La clave se compara contra el nombre real de las categorias del usuario,
    // asi que funciona aunque cada quien las llame distinto.
    private val SINONIMOS: Map<String, List<String>> = mapOf(
        "alimentos" to listOf("comida", "cafe", "restaurante", "tacos", "desayuno",
            "almuerzo", "cena", "antojo", "antojos", "super", "despensa", "pizza"),
        "transporte" to listOf("uber", "didi", "gasolina", "metro", "camion", "taxi",
            "pasaje", "estacionamiento", "caseta"),
        "entretenimiento" to listOf("cine", "netflix", "spotify", "videojuego", "juego",
            "concierto", "salida"),
        "salud" to listOf("farmacia", "medicina", "doctor", "consulta", "dentista"),
        "hogar" to listOf("renta", "luz", "agua", "internet", "gas", "limpieza"),
        "pagos" to listOf("pago", "tarjeta", "prestamo", "credito", "mensualidad")
    )

    fun match(texto: String, categorias: List<CategoryEntity>): String? {
        if (categorias.isEmpty()) return null

        val normalizado = normalizar(texto)
        if (normalizado.isBlank()) return null

        // 1. Coincidencia directa con el nombre de alguna categoria del usuario.
        categorias.firstOrNull { categoria ->
            val nombre = normalizar(categoria.categoryName)
            nombre.isNotBlank() && normalizado.contains(nombre)
        }?.let { return it.categoryId }

        // 2. Sinonimo dictado que apunta a una categoria que el usuario si tiene.
        for ((destino, palabras) in SINONIMOS) {
            if (palabras.none { contienePalabra(normalizado, it) }) continue

            categorias.firstOrNull { normalizar(it.categoryName) == destino }
                ?.let { return it.categoryId }
        }

        return null
    }

    private fun contienePalabra(texto: String, palabra: String): Boolean =
        Regex("\\b${Regex.escape(palabra)}\\b").containsMatchIn(texto)

    /** Minusculas y sin acentos, para que "cafe" y "café" sean lo mismo. */
    private fun normalizar(valor: String): String =
        Normalizer.normalize(valor.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}
