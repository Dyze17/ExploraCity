package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.support.MutableClock
import co.edu.uniquindio.exploracity.support.TestGoogle
import kotlinx.coroutines.runBlocking
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** ADR-15 · El ID token de Google se acepta solo si es de Google, para esta API, vigente y con el correo verificado. */
class GoogleIdTokenTest {

    private val clock = MutableClock(Instant.parse("2026-10-08T15:00:00Z"))
    private val google = TestGoogle(clock)

    private fun rejected(token: String) = runBlocking<Unit> {
        assertFailsWith<InvalidGoogleTokenException> { google.verifier.verify(token) }
    }

    @Test
    fun `un token válido trae la cuenta de Google con el correo en minúscula`() = runBlocking {
        val identity = google.verifier.verify(google.token(subject = "1234", email = " Ana.Rios@Gmail.com", name = " Ana Ríos "))

        assertEquals(GoogleIdentity("1234", "ana.rios@gmail.com", "Ana Ríos"), identity)
        assertNull(google.verifier.verify(google.token(name = null)).name)
        // Google usa los dos emisores.
        google.verifier.verify(google.token(issuer = "accounts.google.com"))
        Unit
    }

    @Test
    fun `un token para otra app, de otro emisor o vencido no sirve`() {
        rejected(google.token(audience = "otra-app.apps.googleusercontent.com"))
        rejected(google.token(issuer = "https://accounts.google.com.ejemplo.co"))
        rejected(google.token(expiresIn = Duration.ofMinutes(-5)))
    }

    @Test
    fun `un token recién vencido se acepta por la diferencia de relojes, uno de hace rato no`() = runBlocking {
        val token = google.token(expiresIn = Duration.ofMinutes(1))

        clock.advance(Duration.ofSeconds(110))
        google.verifier.verify(token)
        clock.advance(Duration.ofMinutes(1))
        rejected(token)
    }

    @Test
    fun `un correo que Google no verificó no sirve`() {
        rejected(google.token(emailVerified = false))
    }

    @Test
    fun `un token firmado con otra clave, con una clave desconocida o que no es JWT no sirve`() {
        rejected(google.token(signingKey = TestGoogle.newKeyPair().private as RSAPrivateKey))
        rejected(google.token(keyId = "otra-clave"))
        rejected("esto no es un token")
        rejected("")
    }
}
