package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.LinkPurpose
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Solo en desarrollo · El buzón de la API que leen los botones de prueba de 6.a y «Cambiar correo». */
class DevMailboxApiTest {

    @Test
    fun `el buzón de desarrollo da el token o nada`() = runTest {
        val api = FakeApi { sent ->
            if ("latest" in sent.path) example("dev-link.json") else error(HttpStatusCode.NotFound, "account_not_found")
        }
        val mailbox = DevMailboxApi(api.client())

        assertEquals("token-del-enlace", mailbox.latestLink("ana.rios@correo.com", LinkPurpose.PASSWORD_RESET))
        assertNull(mailbox.expiredLink("nadie@correo.com", LinkPurpose.PASSWORD_RESET))
        assertEquals("""{"email":"ana.rios@correo.com","purpose":"PASSWORD_RESET"}""", api.requests.first().body)
    }
}
