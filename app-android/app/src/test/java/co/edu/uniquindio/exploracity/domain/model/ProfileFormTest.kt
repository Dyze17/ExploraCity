package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 28 · Nombre de 2 a 40 caracteres (sin contar espacios alrededor) y «Sobre mí» hasta 150. */
class ProfileFormTest {

    private val form = ProfileForm("Ana Ríos", "", Residency.RESIDENT)

    @Test
    fun `el nombre cuenta sin espacios alrededor`() {
        assertEquals(1, form.copy(name = "  A  ").nameMissing)
        assertEquals(0, form.copy(name = "Al").nameMissing)
        assertEquals(1, form.copy(name = "a".repeat(41)).nameExcess)
        assertEquals(0, form.copy(name = " " + "a".repeat(40) + " ").nameExcess)
    }

    @Test
    fun `sobre mí cuenta lo escrito, como el contador`() {
        assertEquals(0, form.copy(bio = "a".repeat(150)).bioExcess)
        assertEquals(12, form.copy(bio = "a".repeat(162)).bioExcess)
    }

    @Test
    fun `solo es válido dentro de las reglas`() {
        assertTrue(form.isValid)
        assertFalse(form.copy(name = "A").isValid)
        assertFalse(form.copy(bio = "a".repeat(151)).isValid)
    }

    @Test
    fun `se guarda sin espacios de sobra`() {
        assertEquals(ProfileForm("Ana", "Cafés", Residency.RESIDENT), ProfileForm(" Ana ", " Cafés\n", Residency.RESIDENT).trimmed())
    }
}
