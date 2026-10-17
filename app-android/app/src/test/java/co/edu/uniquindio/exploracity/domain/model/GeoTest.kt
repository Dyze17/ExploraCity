package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `un grado de longitud en el ecuador mide unos 111 km`() {
        assertEquals(111_195, GeoPoint(0.0, 0.0).distanceTo(GeoPoint(0.0, 1.0)))
    }

    @Test
    fun `la distancia es simétrica y cero en el mismo punto`() {
        val plaza = GeoPoint(4.5981, -74.0760)
        val museo = GeoPoint(4.6019, -74.0721)
        assertEquals(plaza.distanceTo(museo), museo.distanceTo(plaza))
        assertEquals(0, plaza.distanceTo(plaza))
        // Plaza de Bolívar → Museo del Oro: unos 600 m a pie de mapa.
        assertTrue(plaza.distanceTo(museo) in 550..650)
    }

    @Test
    fun `el área contiene sus bordes y nada fuera de ellos`() {
        val bounds = GeoBounds(GeoPoint(4.59, -74.08), GeoPoint(4.61, -74.06))
        assertTrue(GeoPoint(4.60, -74.07) in bounds)
        assertTrue(GeoPoint(4.59, -74.08) in bounds)
        assertFalse(GeoPoint(4.62, -74.07) in bounds)
        assertFalse(GeoPoint(4.60, -74.05) in bounds)
    }
}
