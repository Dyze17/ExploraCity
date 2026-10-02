package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
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

/** Acceso (SAD: componente de Usuarios, JWT emitido por el backend). */
interface AuthRepository {
    /** 3 · El rol de la cuenta. Lanza [InvalidCredentialsException] si no coinciden, o excepción si falla la red. */
    suspend fun signIn(email: String, password: String): UserRole

    /** 1 · Al abrir la app con sesión, el servidor la confirma (con la API, renueva el token). Lanza excepción si falla. */
    suspend fun resumeSession()

    /**
     * 4 · Crea la cuenta, siempre con rol de usuario: los moderadores vienen precargados (SAD). Lanza
     * [EmailTakenException] si el correo ya tiene cuenta. Si falla el correo de bienvenida, la cuenta se crea igual.
     */
    suspend fun register(account: NewAccount): Registration

    /**
     * 5 · Pide el enlace para crear una contraseña nueva. Responde igual exista o no la cuenta, para no revelarlo; lanza
     * [EmailDeliveryException] si el servicio de correo falla.
     */
    suspend fun requestPasswordReset(email: String)

    /** 6.b · Abre el enlace del correo. Lanza [ExpiredLinkException] si venció o ya se usó. */
    suspend fun openResetLink(token: String): ResetLink

    /** 6.b · Guarda la contraseña nueva y el enlace deja de servir. Lanza [ExpiredLinkException] si venció mientras tanto. */
    suspend fun resetPassword(token: String, password: String)
}

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

/** Solo en desarrollo: lo que llegaría al correo, para abrir 6.b y 6C desde 6.a sin un buzón real. */
interface DemoMailbox {
    /** El último enlace de recuperación que llegó a [email] y aún sirve; null si no llegó ninguno (sin cuenta, no llega). */
    fun latestResetLink(email: String): String?

    /** Un enlace para [email] que ya venció; null si el correo no tiene cuenta. */
    fun expiredResetLink(email: String): String?
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
) : AuthRepository, DemoMailbox, FakeCredentials {

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

    override fun latestResetLink(email: String): String? {
        val now = clock.instant()
        return links.entries
            .filter { (_, issued) -> issued.link.email == key(email) && !issued.used && now.isBefore(issued.link.expiresAt) }
            .maxByOrNull { (_, issued) -> issued.link.expiresAt }
            ?.key
    }

    override fun expiredResetLink(email: String): String? {
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

/** Sin red no se intenta: cada pantalla de acceso lo dice antes (3.c) y el arranque ofrece seguir sin conexión (1.c). */
class OnlineOnlyAuthRepository(
    private val remote: AuthRepository,
    private val connectivity: ConnectivityObserver,
) : AuthRepository {
    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }

    override suspend fun signIn(email: String, password: String): UserRole {
        requireOnline()
        return remote.signIn(email, password)
    }

    override suspend fun resumeSession() {
        requireOnline()
        remote.resumeSession()
    }

    override suspend fun register(account: NewAccount): Registration {
        requireOnline()
        return remote.register(account)
    }

    override suspend fun requestPasswordReset(email: String) {
        requireOnline()
        remote.requestPasswordReset(email)
    }

    override suspend fun openResetLink(token: String): ResetLink {
        requireOnline()
        return remote.openResetLink(token)
    }

    override suspend fun resetPassword(token: String, password: String) {
        requireOnline()
        remote.resetPassword(token, password)
    }
}
