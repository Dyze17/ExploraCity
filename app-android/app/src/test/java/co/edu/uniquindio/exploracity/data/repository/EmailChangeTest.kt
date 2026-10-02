package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Cambiar correo»: el pedido con la contraseña, el enlace de 30 minutos y la cuenta que pasa al correo nuevo. */
class EmailChangeTest {

    private val clock = MutableClock()
    private val auth = FakeAuthRepository(mapOf("ana@correo.com" to UserRole.USER, "mod@correo.com" to UserRole.MODERATOR), clock = clock)
    private val pois = FakePoiRepository()
    private val publications = FakePublicationRepository(pois)
    private val accounts = FakeAccountRepository(pois, publications, FakeUserRepository(pois, publications), auth, clock, Account("ana@correo.com"))

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val goodPassword = "a".repeat(7) + "1"

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    /** El enlace que llegó al correo nuevo. */
    private fun link(): String = checkNotNull(accounts.latestEmailChangeLink())

    @Test
    fun `el pedido deja el correo nuevo pendiente y manda el enlace`() = runTest {
        accounts.requestEmailChange(" Ana.Nueva@correo.com ", goodPassword)

        assertEquals(Account("ana@correo.com", pendingEmail = "ana.nueva@correo.com"), accounts.account.value)
        assertNotNull(accounts.latestEmailChangeLink())
    }

    @Test
    fun `sin la contraseña o con un correo que ya tiene cuenta no se pide`() = runTest {
        assertTrue(failure { accounts.requestEmailChange("ana.nueva@correo.com", "a".repeat(3)) } is InvalidCredentialsException)
        assertTrue(failure { accounts.requestEmailChange("MOD@correo.com", goodPassword) } is EmailTakenException)
        assertNull(accounts.account.value.pendingEmail)
        assertNull(accounts.latestEmailChangeLink())
    }

    @Test
    fun `si el correo no sale se dice y no queda nada pendiente`() = runTest {
        val failing = FakeAccountRepository(pois, publications, FakeUserRepository(pois, publications), auth, clock, Account("ana@correo.com"), emailFails = true)

        assertTrue(failure { failing.requestEmailChange("ana.nueva@correo.com", goodPassword) } is EmailDeliveryException)
        assertNull(failing.account.value.pendingEmail)
    }

    @Test
    fun `al abrir el enlace la cuenta pasa al correo nuevo y se entra con él`() = runTest {
        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)

        val email = accounts.confirmEmailChange(link())

        assertEquals("ana.nueva@correo.com", email)
        assertEquals(Account("ana.nueva@correo.com"), accounts.account.value)
        assertEquals(UserRole.USER, auth.signIn("ana.nueva@correo.com", goodPassword))
        assertTrue(failure { auth.signIn("ana@correo.com", goodPassword) } is InvalidCredentialsException)
    }

    @Test
    fun `el enlace vale 30 minutos y una sola vez`() = runTest {
        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)
        val token = link()

        clock.advanceMinutes(30)
        val expired = failure { accounts.confirmEmailChange(token) }
        assertEquals("ana.nueva@correo.com", (expired as ExpiredLinkException).email)

        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)
        val fresh = link()
        accounts.confirmEmailChange(fresh)
        assertTrue(failure { accounts.confirmEmailChange(fresh) } is ExpiredLinkException)
    }

    @Test
    fun `pedir otra vez o reenviar deja sin efecto el enlace anterior`() = runTest {
        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)
        val first = link()

        accounts.resendEmailChange()
        val second = link()
        assertNotEquals(first, second)
        assertTrue(failure { accounts.confirmEmailChange(first) } is ExpiredLinkException)

        accounts.requestEmailChange("ana.otra@correo.com", goodPassword)
        assertTrue(failure { accounts.confirmEmailChange(second) } is ExpiredLinkException)
        assertEquals("ana.otra@correo.com", accounts.confirmEmailChange(link()))
    }

    @Test
    fun `si alguien se registró con ese correo mientras tanto no se cambia`() = runTest {
        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)
        val token = link()
        auth.moveAccount("mod@correo.com", "ana.nueva@correo.com")

        assertTrue(failure { accounts.confirmEmailChange(token) } is EmailTakenException)
        assertEquals("ana@correo.com", accounts.account.value.email)
    }

    @Test
    fun `el enlace vencido de prueba lleva a pedir otro con el correo nuevo`() = runTest {
        assertNull(accounts.expiredEmailChangeLink())
        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)

        val expired = accounts.expiredEmailChangeLink()!!

        assertEquals("ana.nueva@correo.com", (failure { accounts.confirmEmailChange(expired) } as ExpiredLinkException).email)
    }

    @Test
    fun `eliminar la cuenta deja sin efecto el cambio pendiente`() = runTest {
        accounts.requestEmailChange("ana.nueva@correo.com", goodPassword)
        val token = link()

        accounts.deleteAccount()

        assertNull(accounts.account.value.pendingEmail)
        assertTrue(failure { accounts.confirmEmailChange(token) } is ExpiredLinkException)
    }

    @Test
    fun `sin red no se pide, no se reenvía ni se confirma`() = runTest {
        val offline = OnlineOnlyAccountRepository(accounts, FakeConnectivity(online = false))

        assertTrue(failure { offline.requestEmailChange("ana.nueva@correo.com", goodPassword) } is OfflineException)
        assertTrue(failure { offline.resendEmailChange() } is OfflineException)
        assertTrue(failure { offline.confirmEmailChange("enlace") } is OfflineException)
    }
}
