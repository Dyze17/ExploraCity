package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.SessionAccount
import co.edu.uniquindio.exploracity.data.remote.DevMailboxApi
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.domain.model.Account
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.a y «Confirma tu correo nuevo» · El buzón de desarrollo de la API detrás de los botones de prueba. */
class ApiDevMailboxTest {

    private val stores = TestSession()
    private val api = FakeApi { example("dev-link.json") }
    private val mailbox = ApiDevMailbox(DevMailboxApi(api.sessionClient(stores)), stores.session)

    @Test
    fun `los enlaces para recuperar la contraseña se piden con el correo escrito`() = runTest {
        assertEquals("token-del-enlace", mailbox.latestResetLink(" ana@correo.com "))
        mailbox.expiredResetLink("ana@correo.com")

        assertEquals(listOf("/v1/dev/mailbox/latest-link", "/v1/dev/mailbox/expired-link"), api.requests.map { it.path })
        assertEquals("""{"email":"ana@correo.com","purpose":"PASSWORD_RESET"}""", api.requests.first().body)
    }

    @Test
    fun `los del cambio de correo son los del correo nuevo pendiente, y sin cambio pendiente no se piden`() = runTest {
        assertNull(mailbox.latestEmailChangeLink())
        assertTrue(api.requests.isEmpty())

        stores.accounts.save(SessionAccount("ana-rios", Account("ana@correo.com", pendingEmail = "ana.nueva@correo.com")))
        mailbox.latestEmailChangeLink()
        mailbox.expiredEmailChangeLink()

        assertEquals(List(2) { """{"email":"ana.nueva@correo.com","purpose":"EMAIL_CHANGE"}""" }, api.requests.map { it.body })
    }
}
