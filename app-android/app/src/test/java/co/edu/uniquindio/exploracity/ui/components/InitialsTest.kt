package co.edu.uniquindio.exploracity.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test
    fun `dos primeras letras de las dos primeras palabras`() {
        assertEquals("MP", initialsOf("María Paula"))
        assertEquals("CR", initialsOf("Camilo R."))
        assertEquals("AR", initialsOf("ana ríos"))
        assertEquals("JD", initialsOf("  Juan   David  López "))
    }

    @Test
    fun `una sola palabra da una sola inicial`() {
        assertEquals("L", initialsOf("Laura"))
        assertEquals("", initialsOf("   "))
    }
}
