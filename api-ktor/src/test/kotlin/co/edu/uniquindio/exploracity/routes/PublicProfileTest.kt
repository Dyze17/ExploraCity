package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** 31 · El perfil público: sin correo, y de sus lugares solo lo verificado y lo finalizado. */
class PublicProfileTest : ApiTest() {

    @Test
    fun `el perfil público responde como dice el contrato`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/users/${Fixtures.LAURA}?near=4.5402,-75.6721", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("public-profile.json", response.json())
    }

    @Test
    fun `no muestra lo pendiente ni lo rechazado`() = apiTest {
        Fixtures.anaWithActivity(database)

        val places = get("/v1/users/${Fixtures.ANA}", accessToken(Fixtures.LAURA)).json().jsonObject["places"]!!.jsonArray

        assertEquals(listOf("Mirador de la Secreta"), places.map { it.jsonObject["title"]!!.jsonPrimitive.content })
    }

    @Test
    fun `una persona que no existe no tiene perfil`() = apiTest {
        val token = accessToken(UUID.randomUUID())

        for (id in listOf(UUID.randomUUID().toString(), "no-es-un-id")) {
            val response = get("/v1/users/$id", token)
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("""{"code":"user_not_found"}""", response.bodyAsText())
        }
    }
}
