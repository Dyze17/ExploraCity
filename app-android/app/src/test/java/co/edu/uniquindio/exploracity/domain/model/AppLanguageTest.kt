package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `la etiqueta del sistema se lee por su idioma, y uno que la app no tiene sigue al teléfono`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.of("es-CO"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of("fr-FR"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of(null))
    }
}
