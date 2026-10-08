package co.edu.uniquindio.exploracity.integration

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.util.Locale
import java.util.concurrent.TimeUnit

/** La cuenta de Google que trae un ID token válido. [email] va en minúscula y Google ya lo verificó. */
data class GoogleIdentity(val subject: String, val email: String, val name: String?)

/** El ID token no sirve: firma, emisor, audiencia, vencimiento o correo sin verificar. */
class InvalidGoogleTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** ADR-15 · Comprueba el ID token que la app recibe de «Sign in with Google» (Credential Manager). */
fun interface GoogleTokenVerifier {
    /** Lanza [InvalidGoogleTokenException] si el token no es de Google, no es para esta API o ya venció. */
    suspend fun verify(idToken: String): GoogleIdentity
}

/**
 * Verifica el ID token como pide Google: firma RS256 con una de sus claves públicas (las de [keys], por su `kid`),
 * emitido por accounts.google.com, para [webClientId] (la audiencia), sin vencer y con el correo verificado. Con un
 * correo sin verificar, cualquiera podría crear una cuenta de Google con el correo de otra persona.
 */
class JwksGoogleTokenVerifier(
    private val webClientId: String,
    private val keys: suspend (keyId: String) -> RSAPublicKey?,
    private val clock: Clock,
) : GoogleTokenVerifier {

    override suspend fun verify(idToken: String): GoogleIdentity {
        val token = try {
            JWT.decode(idToken)
        } catch (e: JWTVerificationException) {
            throw InvalidGoogleTokenException("No es un JWT", e)
        }
        val keyId = token.keyId ?: throw InvalidGoogleTokenException("El token no dice con qué clave se firmó")
        val key = try {
            keys(keyId)
        } catch (e: Exception) {
            throw InvalidGoogleTokenException("No se pudo leer la clave $keyId de Google", e)
        } ?: throw InvalidGoogleTokenException("Google no tiene la clave $keyId")
        val verified = try {
            val verifier = JWT.require(Algorithm.RSA256(key, null))
                .withIssuer(*ISSUERS)
                .withAudience(webClientId)
                .acceptLeeway(LEEWAY_SECONDS)
            (verifier as JWTVerifier.BaseVerification).build(clock).verify(token)
        } catch (e: JWTVerificationException) {
            throw InvalidGoogleTokenException("El token no pasó la verificación: ${e.message}", e)
        }
        val email = verified.getClaim("email").asString()?.trim()?.lowercase(Locale.ROOT)
            ?: throw InvalidGoogleTokenException("El token no trae correo")
        if (verified.getClaim("email_verified").asBoolean() != true) throw InvalidGoogleTokenException("Google no verificó el correo")
        val subject = verified.subject ?: throw InvalidGoogleTokenException("El token no trae sub")
        return GoogleIdentity(subject, email, verified.getClaim("name").asString()?.trim()?.takeIf { it.isNotEmpty() })
    }

    companion object {
        /** Las claves con que Google firma los ID tokens; cambian cada pocos días. */
        private const val CERTS = "https://www.googleapis.com/oauth2/v3/certs"
        private val ISSUERS = arrayOf("accounts.google.com", "https://accounts.google.com")

        /** Diferencia de reloj tolerada con Google, en segundos. */
        private const val LEEWAY_SECONDS = 60L

        /** Con las claves de Google: guardadas un día y pedidas como mucho 10 veces por minuto. */
        fun google(webClientId: String, clock: Clock): JwksGoogleTokenVerifier {
            val provider = JwkProviderBuilder(URI(CERTS).toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
            // jwks-rsa pide las claves con una conexión que bloquea.
            return JwksGoogleTokenVerifier(webClientId, { keyId -> withContext(Dispatchers.IO) { provider.get(keyId).publicKey as? RSAPublicKey } }, clock)
        }
    }
}
