package com.binc.gastapp.update

import java.text.Normalizer
import java.util.Locale

/**
 * Las notas del release de GitHub tal como se ven en el dialogo de actualizacion: solo
 * la seccion del idioma de la app y sin los simbolos de Markdown.
 *
 * El release trae una seccion por idioma, cada una con un titulo de nivel 2 que es el
 * nombre del idioma escrito en ese idioma ("## Español", "## English", "## Português");
 * el siguiente titulo de nivel 2 la cierra (lo que no es de un idioma, como
 * "## Instalación", no se muestra en la app). Se toma la de [language]; si no esta, la
 * de ingles (el idioma base de la app) y si tampoco, la primera. Las notas sin secciones
 * por idioma (releases viejos) se muestran completas.
 */
fun releaseNotesFor(notes: String, language: String): String {
    val lines = notes.replace("\r\n", "\n").lines()
    val sections = mutableListOf<Pair<String, MutableList<String>>>()
    var current: MutableList<String>? = null
    for (line in lines) {
        val title = levelTwoTitle(line)
        if (title != null) {
            current = languageOfTitle(title)?.let { code -> mutableListOf<String>().also { sections += code to it } }
        } else {
            current?.add(line)
        }
    }
    val chosen = sections.firstOrNull { it.first == language }
        ?: sections.firstOrNull { it.first == "en" }
        ?: sections.firstOrNull()
    val text = chosen?.second?.joinToString("\n") ?: notes
    return plainText(text)
}

/** "## Español" -> "Español"; null si la linea no es un titulo de nivel 2. */
private fun levelTwoTitle(line: String): String? {
    val trimmed = line.trim()
    return if (trimmed.startsWith("## ")) trimmed.removePrefix("## ").trim() else null
}

/** El codigo del idioma cuyo nombre (en ese mismo idioma) es [title], como "es" para "Español". */
private fun languageOfTitle(title: String): String? {
    // "Português (Brasil)" tambien vale.
    val name = normalize(title.substringBefore("("))
    if (name.isEmpty()) return null
    return Locale.getISOLanguages().firstOrNull { code ->
        val locale = Locale.forLanguageTag(code)
        normalize(locale.getDisplayLanguage(locale)) == name
    }
}

/** Minusculas, sin acentos y sin espacios a los lados: "Português " -> "portugues". */
private fun normalize(text: String): String =
    Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** El dialogo muestra texto plano: titulos sin "#", vinetas con "•" y sin negritas ni codigo. */
private fun plainText(markdown: String): String =
    markdown.lines()
        .joinToString("\n") { line ->
            line.trimEnd()
                .replace(Regex("^#+\\s*"), "")
                .replace(Regex("^(\\s*)[-*]\\s+"), "$1• ")
                .replace("**", "")
                .replace("`", "")
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
