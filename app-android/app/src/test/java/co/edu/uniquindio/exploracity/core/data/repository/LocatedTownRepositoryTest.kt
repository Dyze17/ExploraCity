package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.local.MemoryDataStore
import co.edu.uniquindio.exploracity.core.data.location.AddressResolver
import co.edu.uniquindio.exploracity.core.data.location.ApproximateAddress
import co.edu.uniquindio.exploracity.core.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Municipality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/** ADR-16 · El municipio que se explora: el de la persona o, sin su ubicación, la ciudad de la API. */
class LocatedTownRepositoryTest {

    private val armenia = Municipality("Armenia", "Quindío")
    private val calarca = Municipality("Calarcá", "Quindío")
    private val store = MemoryDataStore()
    private val addresses = TownAddresses()
    private var permission = true
    private var clock = 0L

    private fun towns() = LocatedTownRepository(
        defaultTown = { armenia },
        location = SimulatedLocationProvider(GeoPoint(4.5196, -75.6436)),
        addresses = addresses,
        store = store,
        hasPermission = { permission },
        now = { clock },
    )

    @Test
    fun `sin permiso de ubicación se explora la ciudad de la API`() = runTest {
        permission = false
        val towns = towns()

        towns.refresh()

        assertEquals(armenia, towns.current.value)
        assertEquals(0, addresses.asked)
    }

    @Test
    fun `con permiso se explora el municipio de la persona, que queda guardado para la próxima vez`() = runTest {
        val towns = towns()
        assertEquals(armenia, towns.current.value)

        towns.refresh()

        assertEquals(calarca, towns.current.value)
        addresses.failure = IOException("Sin red")
        assertEquals("Al volver a abrir sale de lo guardado", calarca, towns().current.value)
    }

    @Test
    fun `volver a Explorar seguido no pregunta otra vez, salvo al dar el permiso`() = runTest {
        val towns = towns()
        towns.refresh()
        clock += 30_000

        towns.refresh()
        assertEquals(1, addresses.asked)
        towns.refresh(force = true)
        assertEquals(2, addresses.asked)
        clock += LocatedTownRepository.MIN_INTERVAL.inWholeMilliseconds
        towns.refresh()
        assertEquals(3, addresses.asked)
    }

    @Test
    fun `si el Geocoder falla o el punto no tiene municipio se queda el de antes`() = runTest {
        val towns = towns()
        towns.refresh()
        clock += 5.minutes.inWholeMilliseconds

        addresses.found = null
        towns.refresh()
        assertEquals(calarca, towns.current.value)
        addresses.failure = IOException("Sin red")
        towns.refresh(force = true)
        assertEquals(calarca, towns.current.value)
    }

    @Test
    fun `si se quita el permiso se vuelve a la ciudad de la API`() = runTest {
        val towns = towns()
        towns.refresh()

        permission = false
        towns.refresh()

        assertEquals(armenia, towns.current.value)
        assertEquals("Sin permiso no se usa lo guardado", armenia, towns().current.value)
    }

    @Test
    fun `el permiso se pide solo la primera vez`() = runTest {
        assertTrue(towns().firstAsk())
        assertFalse(towns().firstAsk())
    }

    private inner class TownAddresses : AddressResolver {
        var found: Municipality? = calarca
        var failure: Exception? = null
        var asked = 0

        override suspend fun addressOf(point: GeoPoint): ApproximateAddress? = null

        override suspend fun search(query: String, bounds: GeoBounds): GeoPoint? = null

        override suspend fun municipalityOf(point: GeoPoint): Municipality? {
            asked++
            failure?.let { throw it }
            return found
        }
    }
}
