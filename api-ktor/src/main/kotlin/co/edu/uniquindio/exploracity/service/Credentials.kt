package co.edu.uniquindio.exploracity.service

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/** Reglas del acceso y de la cuenta (README · Reglas de validación): las mismas que valida la app. */
object AccountRules {
    const val NAME_MIN = 2
    const val NAME_MAX = 40
    const val BIO_MAX = 150
    const val PASSWORD_MIN = 8

    /** Un correo más largo no cabe en una dirección válida (RFC 5321). */
    private const val EMAIL_MAX = 254

    /** 5, 6 y 6C · El enlace para crear una contraseña nueva vence a los 30 minutos. */
    val RESET_LINK_TTL: Duration = Duration.ofMinutes(30)

    /** «Cambiar correo» · El enlace al correo nuevo dura lo mismo que el de recuperación, como en la app. */
    val EMAIL_CHANGE_LINK_TTL: Duration = Duration.ofMinutes(30)

    /** 6.a · Espera antes de enviar otro enlace a la misma cuenta. */
    val RESEND_WAIT: Duration = Duration.ofSeconds(60)

    // Algo antes y después de la @, y un dominio con punto, como la app.
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$")

    /** Así se guarda y se compara un correo: sin espacios alrededor y en minúscula. */
    fun normalizeEmail(text: String): String = text.trim().lowercase()

    fun isValidEmail(email: String): Boolean = email.length <= EMAIL_MAX && emailPattern.matches(email)

    /** Registro (4) y contraseña nueva (6.b): al menos 8 caracteres, con una letra y un número. */
    fun isValidNewPassword(text: String): Boolean =
        text.length >= PASSWORD_MIN && text.any { it.isLetter() } && text.any { it.isDigit() }

    fun isValidName(name: String): Boolean = name.length in NAME_MIN..NAME_MAX
}

/**
 * Tokens que solo conoce su dueño (renovación de la sesión y enlaces del correo): 32 bytes aleatorios en Base64 URL,
 * que caben en un enlace. En la base de datos solo queda su hash.
 */
object SecretTokens {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String = ByteArray(32).also(random::nextBytes).let(encoder::encodeToString)

    /** SHA-256 en hexadecimal. Basta sin sal: el token ya es aleatorio y largo. */
    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * ADR-06 · Contraseñas con BCrypt de costo 12. Más allá de 72 bytes BCrypt no mira, como cualquier BCrypt: el resto se
 * recorta. Las pruebas usan un costo menor para no tardar.
 */
class PasswordHasher(private val cost: Int = DEFAULT_COST) {
    private val hasher = BCrypt.with(VERSION, LongPasswordStrategies.truncate(VERSION))
    private val verifier = BCrypt.verifyer(VERSION, LongPasswordStrategies.truncate(VERSION))

    /** Con qué comparar cuando el correo no tiene cuenta: tarda lo mismo, así el tiempo no delata qué correos existen. */
    private val decoy: String by lazy { hashNow(SecretTokens.generate()) }

    // BCrypt tarda a propósito: fuera de los hilos que atienden peticiones.
    suspend fun hash(password: String): String = withContext(Dispatchers.Default) { hashNow(password) }

    /** false también sin [hash] (cuenta que no existe), después de comparar contra uno de mentira. */
    suspend fun verify(password: String, hash: String?): Boolean = withContext(Dispatchers.Default) {
        val result = verifier.verify(password.toCharArray(), (hash ?: decoy).toCharArray())
        hash != null && result.verified
    }

    private fun hashNow(password: String): String = hasher.hashToString(cost, password.toCharArray())

    companion object {
        const val DEFAULT_COST = 12
        private val VERSION = BCrypt.Version.VERSION_2A
    }
}
