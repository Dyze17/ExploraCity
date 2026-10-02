package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** 3–6C · Las cuentas de prueba, el registro, los enlaces de recuperación y las reglas del formulario. */
class FakeAuthRepositoryTest {

    private val accounts = mapOf("ana@correo.com" to UserRole.USER, "mod@correo.com" to UserRole.MODERATOR)
    private val clock = MutableClock()
    private val auth = FakeAuthRepository(accounts, clock = clock)

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val goodPassword = "a".repeat(7) + "1"

    private fun pedro(email: String = "pedro@correo.com") = NewAccount("Pedro", email, goodPassword, Residency.VISITOR)

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test
    fun `devuelve el rol de la cuenta, sin importar mayúsculas ni espacios en el correo`() = runTest {
        assertEquals(UserRole.USER, auth.signIn(" Ana@Correo.com ", "clave-segura"))
        assertEquals(UserRole.MODERATOR, auth.signIn("mod@correo.com", "clave-segura"))
    }

    @Test
    fun `un correo que no es de prueba o una contraseña fuera de las reglas dan el mismo error`() = runTest {
        assertTrue(failure { auth.signIn("nadie@correo.com", "clave-segura") } is InvalidCredentialsException)
        assertTrue(failure { auth.signIn("ana@correo.com", "corta") } is InvalidCredentialsException)
    }

    @Test
    fun `una cuenta nueva es de usuario y ya puede entrar`() = runTest {
        val registration = auth.register(pedro())

        assertEquals(UserRole.USER, registration.role)
        assertTrue(registration.welcomeEmailSent)
        assertEquals(UserRole.USER, auth.signIn("Pedro@correo.com", goodPassword))
    }

    @Test
    fun `un correo con cuenta no se puede registrar otra vez`() = runTest {
        auth.register(pedro())

        assertTrue(failure { auth.register(pedro(" PEDRO@correo.com")) } is EmailTakenException)
        assertTrue(failure { auth.register(pedro("ana@correo.com")) } is EmailTakenException)
    }

    @Test
    fun `si el correo de bienvenida falla la cuenta se crea igual`() = runTest {
        val failing = FakeAuthRepository(accounts, welcomeEmailFails = true)

        assertFalse(failing.register(pedro()).welcomeEmailSent)
        assertEquals(UserRole.USER, failing.signIn("pedro@correo.com", goodPassword))
    }

    @Test
    fun `pedir el enlace responde igual con o sin cuenta, pero solo llega si la hay`() = runTest {
        auth.requestPasswordReset("Ana@correo.com")
        auth.requestPasswordReset("nadie@correo.com")

        assertNotNull(auth.latestResetLink("ana@correo.com"))
        assertNull(auth.latestResetLink("nadie@correo.com"))
        assertNull(auth.expiredResetLink("nadie@correo.com"))
    }

    @Test
    fun `el enlace vale 30 minutos`() = runTest {
        auth.requestPasswordReset("ana@correo.com")
        val token = auth.latestResetLink("ana@correo.com")!!

        clock.advanceMinutes(29)
        val link = auth.openResetLink(token)
        assertEquals("ana@correo.com", link.email)

        clock.advanceMinutes(1)
        val expired = failure { auth.openResetLink(token) }
        assertEquals("ana@correo.com", (expired as ExpiredLinkException).email)
        assertNull(auth.latestResetLink("ana@correo.com"))
    }

    @Test
    fun `el enlace sirve una sola vez`() = runTest {
        auth.requestPasswordReset("ana@correo.com")
        val token = auth.latestResetLink("ana@correo.com")!!

        auth.resetPassword(token, goodPassword)

        assertTrue(failure { auth.openResetLink(token) } is ExpiredLinkException)
        assertTrue(failure { auth.resetPassword(token, goodPassword) } is ExpiredLinkException)
    }

    @Test
    fun `un enlace vencido de prueba o uno que no existe llevan a 6C`() = runTest {
        val expired = auth.expiredResetLink("ana@correo.com")!!

        assertEquals("ana@correo.com", (failure { auth.openResetLink(expired) } as ExpiredLinkException).email)
        assertEquals("", (failure { auth.openResetLink("inventado") } as ExpiredLinkException).email)
    }

    @Test
    fun `si el servicio de correo falla se dice`() = runTest {
        val failing = FakeAuthRepository(accounts, resetEmailFails = true)

        assertTrue(failure { failing.requestPasswordReset("ana@correo.com") } is EmailDeliveryException)
    }

    @Test
    fun `sin red no se intenta`() = runTest {
        val offline = OnlineOnlyAuthRepository(auth, FakeConnectivity(online = false))

        assertTrue(failure { offline.signIn("ana@correo.com", "clave-segura") } is OfflineException)
        assertTrue(failure { offline.resumeSession() } is OfflineException)
        assertTrue(failure { offline.register(pedro()) } is OfflineException)
        assertTrue(failure { offline.requestPasswordReset("ana@correo.com") } is OfflineException)
        assertTrue(failure { offline.openResetLink("enlace") } is OfflineException)
        assertTrue(failure { offline.resetPassword("enlace", goodPassword) } is OfflineException)
    }

    @Test
    fun `las reglas del formulario`() {
        assertTrue(AuthRules.isValidEmail("ana.rios@correo.com"))
        assertTrue(AuthRules.isValidEmail(" ana@correo.com.co "))
        assertFalse(AuthRules.isValidEmail("ana.rios@correo"))
        assertFalse(AuthRules.isValidEmail("ana rios@correo.com"))
        assertFalse(AuthRules.isValidEmail("@correo.com"))
        assertTrue(AuthRules.isValidPassword("12345678"))
        assertFalse(AuthRules.isValidPassword("1234567"))
        // Registro y contraseña nueva: además, una letra y un número.
        assertTrue(AuthRules.isValidNewPassword("ñandú2026"))
        assertFalse(AuthRules.isValidNewPassword("12345678"))
        assertFalse(AuthRules.isValidNewPassword("abcdefgh"))
        assertFalse(AuthRules.isValidNewPassword("abc1"))
    }
}

/** Un reloj que solo avanza cuando la prueba lo dice. */
private class MutableClock(private var now: Instant = Instant.parse("2026-10-02T12:00:00Z")) : Clock() {
    fun advanceMinutes(minutes: Long) {
        now = now.plus(minutes, ChronoUnit.MINUTES)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
