package co.edu.uniquindio.exploracity.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 17 · La dirección aproximada a partir de lo que entrega el Geocoder de Android. */
class ApproximateAddressTest {

    @Test
    fun `usa la calle como la escribe Google y el barrio`() {
        val address = AddressParts("Cl. 45 #19-32, Bogotá, Colombia", "Calle 45", "19-32", "Chapinero", "Bogotá").toApproximate()

        assertEquals(ApproximateAddress("Cl. 45 #19-32", "Chapinero", "Bogotá"), address)
        assertEquals("Cl. 45 #19-32, Chapinero", address?.line)
        assertEquals("Chapinero, Bogotá", address?.area)
    }

    @Test
    fun `sin calle en la dirección completa usa la vía y el número`() {
        val address = AddressParts("Bogotá, Colombia", "Carrera 7", "#12-40", null, "Bogotá").toApproximate()

        assertEquals("Carrera 7 # 12-40", address?.street)
        assertEquals("Carrera 7 # 12-40, Bogotá", address?.line)
        assertEquals("Bogotá", address?.area)
    }

    @Test
    fun `un código plus no es una calle`() {
        val address = AddressParts("8FVC+XX Bogotá, Colombia", null, null, "La Candelaria", "Bogotá").toApproximate()

        assertNull(address?.street)
        assertEquals("La Candelaria, Bogotá", address?.line)
    }

    @Test
    fun `sin nada útil no hay dirección`() {
        assertNull(AddressParts(null, "Unnamed Road", null, null, null).toApproximate())
    }
}
