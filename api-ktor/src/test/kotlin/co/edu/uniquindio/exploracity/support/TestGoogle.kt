package co.edu.uniquindio.exploracity.support

import co.edu.uniquindio.exploracity.integration.JwksGoogleTokenVerifier
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration

/**
 * Un Google de prueba: firma ID tokens con una clave RSA que se genera en cada ejecución (ninguna queda escrita), y
 * [verifier] los comprueba como la API comprueba los de Google, con esa clave pública.
 */
class TestGoogle(private val clock: Clock, val clientId: String = "cliente-web.apps.googleusercontent.com") {
    private val keys: KeyPair = newKeyPair()

    val verifier = JwksGoogleTokenVerifier(clientId, { keyId -> (keys.public as RSAPublicKey).takeIf { keyId == KEY_ID } }, clock)

    /** Un ID token como los de Google; cada parámetro permite romper una de las comprobaciones. */
    fun token(
        subject: String = "google-ana",
        email: String = "ana.rios@gmail.com",
        name: String? = "Ana Ríos",
        emailVerified: Boolean = true,
        audience: String = clientId,
        issuer: String = "https://accounts.google.com",
        expiresIn: Duration = Duration.ofHours(1),
        keyId: String = KEY_ID,
        signingKey: RSAPrivateKey = keys.private as RSAPrivateKey,
    ): String {
        val now = clock.instant()
        val builder = JWT.create()
            .withKeyId(keyId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withClaim("email", email)
            .withClaim("email_verified", emailVerified)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(expiresIn))
        name?.let { builder.withClaim("name", it) }
        return builder.sign(Algorithm.RSA256(null, signingKey))
    }

    companion object {
        const val KEY_ID = "clave-de-prueba"

        fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
