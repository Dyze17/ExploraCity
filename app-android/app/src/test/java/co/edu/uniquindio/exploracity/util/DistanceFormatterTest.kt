package co.edu.uniquindio.exploracity.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTest {

    @Test
    fun `menos de un kilómetro se muestra en metros`() {
        assertEquals("850 m", formatDistance(850))
    }

    @Test
    fun `desde un kilómetro usa coma decimal`() {
        assertEquals("1,0 km", formatDistance(1000))
        assertEquals("1,2 km", formatDistance(1234))
        assertEquals("12,5 km", formatDistance(12_480))
    }

    @Test
    fun `las cifras llevan punto de miles, como el avance de Voz de la comunidad (27)`() {
        assertEquals("209", formatCount(209))
        assertEquals("1.000", formatCount(1000))
    }
}
