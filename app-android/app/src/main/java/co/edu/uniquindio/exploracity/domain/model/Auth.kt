package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** README · Reglas de validación del acceso (3, 4, 5 y 6). */
object AuthRules {
    const val PASSWORD_MIN = 8

    /** El enlace para crear una contraseña nueva vence a los 30 minutos (5, 6 y 6C). */
    val RESET_LINK_DURATION = 30.minutes

    /** Espera antes de poder reenviar el enlace (6.a). */
    val RESEND_WAIT = 60.seconds

    // Algo antes y después de la @, y un dominio con punto: lo demás lo decide el servidor.
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$")

    fun isValidEmail(text: String): Boolean = email.matches(text.trim())

    /** Inicio de sesión (3): solo el largo. Las cuentas que ya existen pueden tener contraseñas de antes de la regla. */
    fun isValidPassword(text: String): Boolean = text.length >= PASSWORD_MIN

    fun hasLetterAndDigit(text: String): Boolean = text.any { it.isLetter() } && text.any { it.isDigit() }

    /** Registro (4) y contraseña nueva (6.b): al menos 8 caracteres, con una letra y un número. */
    fun isValidNewPassword(text: String): Boolean = isValidPassword(text) && hasLetterAndDigit(text)
}

/** El correo o la contraseña no coinciden (3.c). No dice cuál, para no revelar qué correos tienen cuenta. */
class InvalidCredentialsException : Exception("Credenciales incorrectas")

/** 4 · Ya hay una cuenta con ese correo: el registro lo dice junto al campo y ofrece iniciar sesión. */
class EmailTakenException : Exception("El correo ya tiene cuenta")

/**
 * 4 · La API no respondió a tiempo: la cuenta pudo quedar creada después de que la app se rindió. El registro no dice
 * que falló; intentarlo de nuevo es seguro ([NewAccount.clientId]): si ya quedó, entra con ella.
 */
class UnconfirmedRegistrationException(cause: Throwable) : Exception("No se sabe si la cuenta quedó creada", cause)

/** 5 y 6.a · El servicio de correo no pudo enviar el enlace. */
class EmailDeliveryException : Exception("No se pudo enviar el correo")

/**
 * 3 · Tras 5 intentos fallidos en 15 minutos, ese correo queda bloqueado 15 minutos, aunque la contraseña sea la
 * correcta (A1).
 */
class TooManyAttemptsException : Exception("Demasiados intentos")

/** 1 · La sesión ya no vale: se cerró desde otro teléfono, cambió la contraseña o pasaron 30 días sin renovarla. */
class SessionEndedException : Exception("La sesión terminó")

/**
 * 6C · El enlace de recuperación ya no sirve: pasó de los 30 minutos o ya se usó. [email] es la cuenta a la que se
 * envió, para pedir otro con el correo ya escrito.
 */
class ExpiredLinkException(val email: String) : Exception("El enlace venció")

/**
 * 4 · Lo que se escribe al crear la cuenta. [residency] es el «¿Cómo te presentas?». [clientId] es el mismo en cada
 * intento del formulario: si un intento creó la cuenta y su respuesta no llegó, el siguiente, con la misma contraseña,
 * entra con ella en vez de chocar con «Ya hay una cuenta con este correo».
 */
data class NewAccount(
    val name: String,
    val email: String,
    val password: String,
    val residency: Residency,
    val clientId: String = UUID.randomUUID().toString(),
)

/** 4 · La cuenta quedó creada. Si el correo de bienvenida falló, [welcomeEmailSent] es false y se avisa. */
data class Registration(val role: UserRole, val welcomeEmailSent: Boolean)

/** 6.b · Para quién es el enlace del correo y hasta cuándo vale. */
data class ResetLink(val email: String, val expiresAt: Instant)
