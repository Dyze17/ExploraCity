package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 7, 8, 9, 10 y 13 · El feed, el conteo de los filtros, el mapa y el detalle: solo lo verificado y lo finalizado. */
class PlaceRoutesTest : ApiTest() {

    /** Laura busca desde el Café Las Acacias. */
    private val near = "4.5402,-75.6721"

    private suspend fun TestApi.titles(query: String): List<String> {
        val response = get("/v1/places?near=$near&$query", accessToken(Fixtures.LAURA))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.json().jsonObject["items"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }
    }

    @Test
    fun `el feed trae lo público, del más cercano al más lejano, como dice el contrato`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/places?near=$near", accessToken(Fixtures.LAURA))

        assertEquals(HttpStatusCode.OK, response.status)
        // El Café está pendiente: no sale.
        assertMatchesContract("feed-page.json", response.json())
    }

    @Test
    fun `sin la ubicación, la distancia se mide desde el centro de la ciudad`() = apiTest {
        Fixtures.anaWithActivity(database)

        val items = get("/v1/places", accessToken(Fixtures.LAURA)).json().jsonObject["items"]!!.jsonArray

        // La Plaza de Bolívar está en el centro configurado.
        assertEquals("Plaza de Bolívar", items[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(0, items[0].jsonObject["distanceMeters"]!!.jsonPrimitive.int)
    }

    @Test
    fun `los filtros de la hoja 9 y su conteo`() = apiTest {
        Fixtures.anaWithActivity(database)
        Fixtures.place(database, Fixtures.LAURA, "Casa Museo Quimbaya", status = PublicationStatus.FINALIZED, latitude = 4.531, longitude = -75.684)
        // Lejos: más de 5 km al norte.
        Fixtures.place(database, Fixtures.LAURA, "Cascada de Calarcá", Category.NATURE, latitude = 4.6, longitude = -75.64)

        assertEquals(listOf("Plaza de Bolívar", "Casa Museo Quimbaya", "Mirador de la Secreta", "Cascada de Calarcá"), titles(""))
        assertEquals(listOf("Mirador de la Secreta", "Cascada de Calarcá"), titles("categories=NATURE"))
        assertEquals(listOf("Plaza de Bolívar", "Casa Museo Quimbaya"), titles("categories=HISTORY,CULTURE"))
        assertEquals(listOf("Plaza de Bolívar", "Mirador de la Secreta", "Cascada de Calarcá"), titles("verifiedOnly=true"))
        assertEquals(listOf("Plaza de Bolívar", "Casa Museo Quimbaya", "Mirador de la Secreta"), titles("scope=NEARBY"))
        val count = get("/v1/places/count?near=$near&scope=NEARBY&verifiedOnly=true", accessToken(Fixtures.LAURA))
        assertEquals(HttpStatusCode.OK, count.status)
        assertMatchesContract("count.json", count.json())
    }

    @Test
    fun `la búsqueda ignora mayúsculas y tildes, y toma el texto tal cual`() = apiTest {
        Fixtures.anaWithActivity(database)
        Fixtures.place(database, Fixtures.LAURA, "Café 100% de origen", Category.GASTRONOMY)

        assertEquals(listOf("Plaza de Bolívar"), titles("q=BOLIVAR"))
        assertEquals(listOf("Café 100% de origen"), titles("q=cafe"))
        assertEquals(listOf("Café 100% de origen"), titles("q=100%25"))
        // Un comodín de LIKE no encuentra todo.
        assertEquals(emptyList(), titles("q=_"))
        assertEquals(emptyList(), titles("q=secreta%20bolivar"))
    }

    @Test
    fun `el feed se pagina de a 20`() = apiTest {
        repeat(25) { i -> Fixtures.place(database, author = null, title = "Lugar número ${i + 1}", latitude = 4.5339 + i * 0.0001) }

        val first = get("/v1/places?near=4.5339,-75.6811", accessToken(UUID.randomUUID())).json().jsonObject
        val second = get("/v1/places?near=4.5339,-75.6811&page=1", accessToken(UUID.randomUUID())).json().jsonObject

        assertEquals(20, first["items"]!!.jsonArray.size)
        assertEquals(25, first["total"]!!.jsonPrimitive.int)
        assertTrue(first["hasMore"]!!.jsonPrimitive.boolean)
        assertEquals(5, second["items"]!!.jsonArray.size)
        assertEquals(false, second["hasMore"]!!.jsonPrimitive.boolean)
        assertEquals("Lugar número 21", second["items"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `criterios que no se entienden`() = apiTest {
        val token = accessToken(UUID.randomUUID())

        for (query in listOf("categories=PLAYA", "scope=LEJOS", "near=4.5", "near=95,-75", "pageSize=0", "pageSize=51", "page=-1")) {
            val response = get("/v1/places?$query", token)
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals("""{"code":"invalid_query"}""", response.bodyAsText())
        }
        assertEquals(HttpStatusCode.Unauthorized, get("/v1/places").status)
    }

    @Test
    fun `el mapa trae los del área visible, los más cercanos primero, y cuántos hay`() = apiTest {
        Fixtures.anaWithActivity(database)
        Fixtures.place(database, Fixtures.LAURA, "Cascada de Calarcá", Category.NATURE, latitude = 4.6, longitude = -75.64)

        val response = get("/v1/places/map?near=$near&bounds=4.50,-75.70,4.56,-75.65&limit=1", accessToken(Fixtures.LAURA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("map-area.json", response.json())
        assertEquals(HttpStatusCode.BadRequest, get("/v1/places/map?near=$near", accessToken(Fixtures.LAURA)).status)
        assertEquals(HttpStatusCode.BadRequest, get("/v1/places/map?bounds=4.56,-75.70,4.50,-75.65", accessToken(Fixtures.LAURA)).status)
    }

    @Test
    fun `el detalle trae las fotos, el horario, el autor y lo que hizo quien lo abre`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/places/${Fixtures.MIRADOR}?near=$near", accessToken(Fixtures.LAURA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("place.json", response.json())
    }

    @Test
    fun `lo pendiente o lo que no existe no tiene detalle`() = apiTest {
        Fixtures.anaWithActivity(database)
        val token = accessToken(Fixtures.LAURA)

        for (id in listOf(Fixtures.CAFE.toString(), UUID.randomUUID().toString(), "no-es-un-id")) {
            val response = get("/v1/places/$id", token)
            assertEquals(HttpStatusCode.NotFound, response.status, id)
            assertEquals("""{"code":"place_not_found"}""", response.bodyAsText())
        }
    }

    @Test
    fun `si la cuenta que lo publicó se eliminó, el lugar sigue sin autor`() = apiTest {
        Fixtures.anaWithActivity(database)
        transaction(database) { Users.deleteWhere { Users.id eq Fixtures.ANA } }

        val details = get("/v1/places/${Fixtures.MIRADOR}", accessToken(Fixtures.LAURA)).json() as JsonObject

        assertNull(details["author"])
        assertEquals("Mirador de la Secreta", details["place"]!!.jsonObject["title"]!!.jsonPrimitive.content)
    }
}
