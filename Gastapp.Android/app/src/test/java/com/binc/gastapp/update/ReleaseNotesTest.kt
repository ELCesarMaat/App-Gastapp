package com.binc.gastapp.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {

    private val notes = """
        ## Español

        ### Novedades

        - **Plazo libre.** Compra a 4 meses.
        - Avanza `sola`.

        ## English

        ### What's new

        - **Any term.** Buy in 4 months.

        ## Português (Brasil)

        ### Novidades

        - **Prazo livre.** Compre em 4 meses.

        ## Instalación

        | Dispositivo | Archivo |
        |---|---|
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `se ve solo la seccion del idioma de la app y sin Markdown`() {
        assertEquals("Novedades\n\n• Plazo libre. Compra a 4 meses.\n• Avanza sola.", releaseNotesFor(notes, "es"))
        assertEquals("Novidades\n\n• Prazo livre. Compre em 4 meses.", releaseNotesFor(notes, "pt"))
    }

    @Test
    fun `un idioma sin seccion ve la de ingles`() {
        assertEquals("What's new\n\n• Any term. Buy in 4 months.", releaseNotesFor(notes, "fr"))
        assertEquals(releaseNotesFor(notes, "en"), releaseNotesFor(notes, "fr"))
    }

    @Test
    fun `sin ingles se toma la primera y las notas viejas se ven completas`() {
        val onlySpanish = "## Español\n\n- Uno\n\n## Português\n\n- Um"
        assertEquals("• Uno", releaseNotesFor(onlySpanish, "fr"))

        val old = "## Novedades\n\n- **Gastapp en inglés.**\n\n**Es una pre-versión.**"
        assertEquals("Novedades\n\n• Gastapp en inglés.\n\nEs una pre-versión.", releaseNotesFor(old, "en"))
    }
}
