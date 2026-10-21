package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.local.MemoryDataStore
import co.edu.uniquindio.exploracity.core.data.location.AddressResolver
import co.edu.uniquindio.exploracity.core.data.location.ApproximateAddress
import co.edu.uniquindio.exploracity.core.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.TownOption
import co.edu.uniquindio.exploracity.domain.repository.ExploredTown
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/** ADR-16 · El municipio que se explora: el de la persona, el elegido a mano (v1.3.0) o ninguno. */
class LocatedTownRepositoryTest {

    private val calarca = Municipality("Calarcá", "Quindío")
    private val salento = TownOption(Municipality("Salento", "Quindío"), GeoPoint(4.637, -75.570), places = 4)
    private val store = MemoryDataStore()
    private val addresses = TownAddresses()
    private var permission = true
    private var clock = 0L

    private fun towns() = LocatedTownRepository(
        location = SimulatedLocationProvider(GeoPoint(4.5196, -75.6436)),
        addresses = addresses,
        townsWithPlaces = { listOf(salento) },
        store = store,
        hasPermission = { permission },
        now = { clock },
    )

    @Test
    fun `sin permiso ni ciudad elegida no se sabe cual explorar, ya no se cae en la de la API`() = runTest {
        permission = false
        val towns = towns()

        towns.refresh()

        assertEquals(ExploredTown.Unknown, towns.current.value)
        assertEquals(0, addresses.asked)
    }

    @Test
    fun `con permiso se explora el municipio de la persona, que queda guardado para la proxima vez`() = runTest {
        val towns = towns()
        assertEquals("Todavía no se sabe", ExploredTown.Unknown, towns.current.value)

        towns.refresh()

        assertEquals(ExploredTown.Located(calarca), towns.current.value)
        addresses.failure = IOException("Sin red")
        assertEquals("Al volver a abrir sale de lo guardado", ExploredTown.Located(calarca), towns().current.value)
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
        assertEquals(ExploredTown.Located(calarca), towns.current.value)
        addresses.failure = IOException("Sin red")
        towns.refresh(force = true)
        assertEquals(ExploredTown.Located(calarca), towns.current.value)
    }

    @Test
    fun `si se quita el permiso hay que elegir la ciudad`() = runTest {
        val towns = towns()
        towns.refresh()

        permission = false
        towns.refresh()

        assertEquals(ExploredTown.Unknown, towns.current.value)
        assertEquals("Sin permiso no se usa lo guardado", ExploredTown.Unknown, towns().current.value)
    }

    @Test
    fun `una ciudad elegida a mano se queda, aunque la persona este en otra, hasta volver a usar la ubicacion`() = runTest {
        val towns = towns()
        towns.refresh()

        towns.choose(salento)
        clock += 5.minutes.inWholeMilliseconds
        towns.refresh(force = true)

        val chosen = ExploredTown.Chosen(salento.municipality, salento.center)
        assertEquals(chosen, towns.current.value)
        assertEquals("Queda guardada", chosen, towns().current.value)
        assertEquals(1, addresses.asked)

        towns.useLocation()
        assertEquals(ExploredTown.Located(calarca), towns.current.value)
        assertEquals("Vuelve a preguntar dónde está", 2, addresses.asked)
        assertEquals(ExploredTown.Located(calarca), towns().current.value)
    }

    @Test
    fun `las ciudades con lugares vienen de la API y la busqueda del Geocoder`() = runTest {
        val towns = towns()

        assertEquals(listOf(salento), towns.withPlaces())
        assertEquals("Salento", towns.search("salento").single().municipality.name)
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

        override suspend fun searchTowns(query: String): List<TownOption> = listOf(salento)
    }
}
