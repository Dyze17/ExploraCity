package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.MemoryDataStore
import co.edu.uniquindio.exploracity.data.remote.CityApi
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.failure
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.domain.model.City
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** B1 · La ciudad de la API, guardada en el teléfono para abrir sin conexión. */
class CityRepositoryTest {

    private val store = MemoryDataStore()
    private lateinit var api: FakeApi

    private fun cities(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): CityRepository {
        api = FakeApi(handler)
        return CityRepository(CityApi(api.client()), store)
    }

    private val armenia = City("Armenia", GeoPoint(4.5339, -75.6811), GeoBounds(GeoPoint(4.47, -75.76), GeoPoint(4.6, -75.62)))

    @Test
    fun `la primera vez la trae de la API y la guarda, y después no la vuelve a pedir`() = runTest {
        val cities = cities { example("city.json") }

        assertEquals(armenia, cities.ensure())
        assertEquals(armenia, cities.ensure())
        assertEquals(armenia, cities.current)
        assertEquals(listOf("/v1/city"), api.requests.map { it.path })
    }

    @Test
    fun `al volver a abrir la app sale de lo guardado, sin red`() = runTest {
        cities { example("city.json") }.ensure()

        val reopened = cities { throw IOException("Sin red") }

        assertEquals(armenia, reopened.current)
        assertEquals(armenia, reopened.ensure())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `refrescar trae la de la API aunque haya una guardada`() = runTest {
        cities { example("city.json") }.ensure()
        val moved = """{"name":"Calarcá","center":{"latitude":4.53,"longitude":-75.64},""" +
            """"bounds":{"southwest":{"latitude":4.5,"longitude":-75.67},"northeast":{"latitude":4.55,"longitude":-75.61}}}"""

        val cities = cities { json(moved) }
        cities.refresh()

        assertEquals("Calarcá", cities.current.name)
        assertEquals("Calarcá", cities { throw IOException("Sin red") }.current.name)
    }

    @Test
    fun `sin ciudad guardada y sin red no hay ciudad`() = runTest {
        val cities = cities { throw IOException("Sin red") }

        assertTrue(failure { cities.ensure() } is IOException)
        assertTrue(runCatching { cities.current }.exceptionOrNull() is IllegalStateException)
    }
}
