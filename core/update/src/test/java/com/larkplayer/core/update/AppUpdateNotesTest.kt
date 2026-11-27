package com.larkplayer.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

/** Release notes shown to users must be plain language, not GitHub markup. */
class AppUpdateNotesTest {

    @Test
    fun `markdown and technical metadata do not appear in user notes`() {
        val raw = """
            ## Novedades

            - **Más estable** al reproducir canciones
            - Las letras se sincronizan mejor

            versionCode: 56
            sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
        """.trimIndent()

        assertEquals(
            "Novedades\nMás estable al reproducir canciones\nLas letras se sincronizan mejor",
            AppUpdate.sanitizeNotes(raw),
        )
    }

    @Test
    fun `inline code markers are removed from notes`() {
        assertEquals(
            "Se corrige el botón de aleatorio",
            AppUpdate.sanitizeNotes("- Se corrige el botón de `aleatorio`"),
        )
    }
}
