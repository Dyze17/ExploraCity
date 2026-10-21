package co.edu.uniquindio.exploracity.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTest {

    private val spanish = spanishFormats.locale

    @Test
    fun `menos de un kilómetro se muestra en metros`() {
        assertEquals("850 m", formatDistance(850, spanish))
    }

    @Test
    fun `desde un kilómetro usa coma decimal`() {
        assertEquals("1,0 km", formatDistance(1000, spanish))
        assertEquals("1,2 km", formatDistance(1234, spanish))
        assertEquals("12,5 km", formatDistance(12_480, spanish))
    }

    @Test
    fun `las cifras llevan punto de miles, como el avance de Voz de la comunidad (27)`() {
        assertEquals("209", formatCount(209, spanish))
        assertEquals("1.000", formatCount(1000, spanish))
    }

    @Test
    fun `en inglés, punto decimal y coma de miles`() {
        val english = englishFormats.locale
        assertEquals("850 m", formatDistance(850, english))
        assertEquals("1.2 km", formatDistance(1234, english))
        assertEquals("1,000", formatCount(1000, english))
    }
}
