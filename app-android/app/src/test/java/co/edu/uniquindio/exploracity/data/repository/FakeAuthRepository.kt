package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Temporal hasta que exista la API: lo que el servidor falso de la cuenta («Cambiar correo») consulta al de acceso. Con
 * la API es un solo servidor.
 */
interface FakeCredentials {
    fun hasAccount(email: String): Boolean

    /** Sin contraseñas en el repositorio: vale cualquiera que cumpla las reglas del inicio de sesión. */
    fun matches(email: String, password: String): Boolean

    /** El correo de la cuenta cambió: se entra con [to] y ya no con [from]. */
    fun moveAccount(from: String, to: String)
}
/**
 * Temporal hasta que exista la API: las cuentas de prueba ([accounts], correo → rol). El moderador es una cuenta
 * precargada, como dice el SAD. Para no guardar contraseñas en el repositorio, acepta cualquiera que cumpla las reglas;
 * un correo que no es de prueba ni se registró en esta ejecución da el error de 3.c. [welcomeEmailFails] y
 * [resetEmailFails] simulan la caída del servicio de correo.
 */
class FakeAuthRepository(
    private val accounts: Map<String, UserRole> = sampleLogins,
    private val latency: Duration = 1300.milliseconds,
    private val resumeLatency: Duration = 500.milliseconds,
    private val linkLatency: Duration = 600.milliseconds,
    private val clock: Clock = Clock.systemUTC(),
    private val welcomeEmailFails: Boolean = false,
    private val resetEmailFails: Boolean = false,
) : AuthRepository, FakeCredentials {

    /** Un enlace enviado; [used] cuando ya sirvió para cambiar la contraseña. */
    private class IssuedLink(val link: ResetLink, val used: Boolean = false)

    // Las cuentas creadas en esta ejecución (4) y los enlaces enviados (5): se pierden al cerrar la app.
    private val registered = ConcurrentHashMap<String, UserRole>()
    private val links = ConcurrentHashMap<String, IssuedLink>()

    // Correos que dejaron de ser de una cuenta porque cambió («Cambiar correo»).
    private val replaced = ConcurrentHashMap.newKeySet<String>()

    private fun key(email: String) = email.trim().lowercase()

    private fun roleOf(email: String): UserRole? {
        val key = key(email)
        return registered[key] ?: accounts[key]?.takeUnless { key in replaced }
    }

    override fun hasAccount(email: String): Boolean = roleOf(email) != null

    override fun matches(email: String, password: String): Boolean = hasAccount(email) && AuthRules.isValidPassword(password)

    override fun moveAccount(from: String, to: String) {
        val role = roleOf(from) ?: return
        registered.remove(key(from))
        replaced += key(from)
        registered[key(to)] = role
    }

    override suspend fun signIn(email: String, password: String): UserRole {
        delay(latency)
        val role = roleOf(email) ?: throw InvalidCredentialsException()
        if (!AuthRules.isValidPassword(password)) throw InvalidCredentialsException()
        return role
    }

    override suspend fun resumeSession() {
        delay(resumeLatency)
    }

    override suspend fun register(account: NewAccount): Registration {
        delay(latency)
        if (roleOf(account.email) != null) throw EmailTakenException()
        registered[key(account.email)] = UserRole.USER
        return Registration(UserRole.USER, welcomeEmailSent = !welcomeEmailFails)
    }

    override suspend fun requestPasswordReset(email: String) {
        delay(latency)
        if (resetEmailFails) throw EmailDeliveryException()
        // Sin cuenta no llega nada, pero la respuesta es la misma.
        if (roleOf(email) != null) issue(email, clock.instant() + AuthRules.RESET_LINK_DURATION.toJavaDuration())
    }

    override suspend fun openResetLink(token: String): ResetLink {
        delay(linkLatency)
        return usable(token)
    }

    override suspend fun resetPassword(token: String, password: String) {
        delay(latency)
        val link = usable(token)
        require(AuthRules.isValidNewPassword(password)) { "La contraseña no cumple las reglas" }
        // Sin contraseñas guardadas (ver arriba): basta con que el enlace deje de servir.
        links[token] = IssuedLink(link, used = true)
    }

    fun latestResetLink(email: String): String? {
        val now = clock.instant()
        return links.entries
            .filter { (_, issued) -> issued.link.email == key(email) && !issued.used && now.isBefore(issued.link.expiresAt) }
            .maxByOrNull { (_, issued) -> issued.link.expiresAt }
            ?.key
    }

    fun expiredResetLink(email: String): String? {
        if (roleOf(email) == null) return null
        return issue(email, clock.instant() - 1.minutes.toJavaDuration())
    }

    private fun issue(email: String, expiresAt: Instant): String {
        val token = UUID.randomUUID().toString()
        links[token] = IssuedLink(ResetLink(key(email), expiresAt))
        return token
    }

    private fun usable(token: String): ResetLink {
        // Un enlace que no existe no dice de quién es: 6C abre 5 sin correo escrito.
        val issued = links[token] ?: throw ExpiredLinkException(email = "")
        if (issued.used || !clock.instant().isBefore(issued.link.expiresAt)) throw ExpiredLinkException(issued.link.email)
        return issued.link
    }
}
