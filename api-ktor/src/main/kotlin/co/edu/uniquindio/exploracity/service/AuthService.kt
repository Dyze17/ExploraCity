package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.config.JwtSettings
import co.edu.uniquindio.exploracity.config.UserPrincipal
import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.integration.MailDeliveryException
import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.model.LoginRequest
import co.edu.uniquindio.exploracity.model.RegisterRequest
import co.edu.uniquindio.exploracity.model.ResetLinkResponse
import co.edu.uniquindio.exploracity.model.SessionResponse
import co.edu.uniquindio.exploracity.model.iso
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.AccountLinkRepository
import co.edu.uniquindio.exploracity.repository.EmailTakenException
import co.edu.uniquindio.exploracity.repository.LoginAttemptRepository
import co.edu.uniquindio.exploracity.repository.SessionRepository
import co.edu.uniquindio.exploracity.repository.StoredLink
import co.edu.uniquindio.exploracity.repository.UserRecord
import co.edu.uniquindio.exploracity.repository.UserRepository
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Componente de Usuarios (SAD) · Sesión y recuperación de la contraseña (3, 4, 5, 6 y 6C). D1: token de acceso de 15
 * minutos y token de renovación de 30 días que cambia en cada uso. A1: tras 5 fallos en 15 minutos, el correo queda
 * bloqueado 15 minutos.
 */
class AuthService(
    private val database: Database,
    private val users: UserRepository,
    private val sessions: SessionRepository,
    private val links: AccountLinkRepository,
    private val attempts: LoginAttemptRepository,
    private val security: SecurityService,
    private val passwords: PasswordHasher,
    private val jwt: JwtConfig,
    private val jwtSettings: JwtSettings,
    private val mail: AccountMail,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    /** 4 · Crea la cuenta y abre la sesión. Si el correo de bienvenida falla, la cuenta queda creada igual. */
    suspend fun register(request: RegisterRequest): SessionResponse {
        val name = request.name.trim()
        val email = AccountRules.normalizeEmail(request.email)
        if (!AccountRules.isValidName(name)) throw ApiException.badRequest("invalid_name")
        if (!AccountRules.isValidEmail(email)) throw ApiException.badRequest("invalid_email")
        if (!AccountRules.isValidNewPassword(request.password)) throw ApiException.badRequest("weak_password")
        // Antes de calcular el hash, que tarda; al guardar, el índice único lo vuelve a comprobar.
        if (database.query { users.findByEmail(email) } != null) throw emailTaken()
        val hash = passwords.hash(request.password)
        val session = try {
            database.query {
                val user = users.create(email, hash, name, request.residency, security.roleFor(email), clock.instant())
                attempts.clear(email)
                issue(user).second
            }
        } catch (e: EmailTakenException) {
            throw emailTaken()
        }
        val sent = try {
            mail.welcome(email, name)
            true
        } catch (e: MailDeliveryException) {
            log.warn("No salió el correo de bienvenida; la cuenta quedó creada.", e)
            false
        }
        return session.copy(welcomeEmailSent = sent)
    }

    /**
     * 3 · Abre la sesión. Responde lo mismo si el correo no tiene cuenta o si la contraseña no coincide. Con el correo
     * bloqueado (A1) responde too_many_attempts aunque la contraseña sea la correcta.
     */
    suspend fun login(request: LoginRequest): SessionResponse {
        val email = AccountRules.normalizeEmail(request.email)
        val now = clock.instant()
        val user = database.query {
            attempts.lockedUntil(email, now)?.let { until -> throw tooManyAttempts(Duration.between(now, until)) }
            users.findByEmail(email)
        }
        if (!passwords.verify(request.password, user?.passwordHash)) {
            val lockedUntil = database.query {
                attempts.recordFailure(email, clock.instant(), MAX_FAILURES, FAILURE_WINDOW, LOCK_DURATION)
            }
            if (lockedUntil != null) log.info("Inicio de sesión bloqueado {} min para un correo tras {} fallos.", LOCK_DURATION.toMinutes(), MAX_FAILURES)
            throw ApiException.unauthorized(INVALID_CREDENTIALS)
        }
        return database.query {
            attempts.clear(email)
            issue(checkNotNull(user)).second
        }
    }

    /**
     * Renueva la sesión: el token usado deja de servir y llega otro par. Si llega uno que ya se había cambiado, alguien
     * copió la sesión: se cierran todas las de la cuenta.
     */
    suspend fun refresh(refreshToken: String): SessionResponse {
        val now = clock.instant()
        val renewed = database.query {
            val stored = sessions.findForUpdate(SecretTokens.hash(refreshToken)) ?: return@query null
            when {
                stored.replacedBy != null -> {
                    val closed = sessions.revokeAll(stored.userId, now)
                    log.warn("Llegó un token de renovación ya usado: se cerraron las {} sesiones abiertas de la cuenta.", closed)
                    null
                }
                stored.revokedAt != null || !now.isBefore(stored.expiresAt) -> null
                else -> {
                    val user = users.findById(stored.userId) ?: return@query null
                    val (replacement, session) = issue(user)
                    sessions.markReplaced(stored.id, replacement, now)
                    session
                }
            }
        }
        // Fuera de la transacción: el cierre de las sesiones ya quedó guardado.
        return renewed ?: throw ApiException.unauthorized("invalid_refresh_token")
    }

    /** 29A · Cierra esta sesión. Un token desconocido o ya cerrado no es un error: el resultado es el mismo. */
    suspend fun logout(refreshToken: String) {
        database.query {
            sessions.findForUpdate(SecretTokens.hash(refreshToken))?.let { sessions.revoke(it.id, clock.instant()) }
        }
    }

    /**
     * 5 · Envía el enlace para crear una contraseña nueva. Sin cuenta, o con un enlace enviado hace menos de 60 s, no
     * envía nada y responde igual: nunca revela qué correos tienen cuenta. Solo si el correo de una cuenta no sale, lo
     * dice (email_delivery_failed), como pide la pantalla 5.
     */
    suspend fun requestPasswordReset(rawEmail: String) {
        val email = AccountRules.normalizeEmail(rawEmail)
        if (!AccountRules.isValidEmail(email)) throw ApiException.badRequest("invalid_email")
        val now = clock.instant()
        val user = database.query {
            users.findByEmail(email)?.takeUnless { user -> waiting(user.id, LinkPurpose.PASSWORD_RESET, now) }
        } ?: return
        val token = SecretTokens.generate()
        try {
            mail.passwordReset(user.email, user.name, token)
        } catch (e: MailDeliveryException) {
            log.warn("No salió el correo de recuperación.", e)
            throw deliveryFailed()
        }
        database.query {
            links.insert(user.id, LinkPurpose.PASSWORD_RESET, SecretTokens.hash(token), user.email, now.plus(AccountRules.RESET_LINK_TTL), now)
        }
    }

    /** 6.b · Para quién es el enlace y hasta cuándo vale. Vencido o usado: link_expired con el correo (6C). */
    suspend fun openResetLink(token: String): ResetLinkResponse {
        val link = database.query { usable(links.find(SecretTokens.hash(token), LinkPurpose.PASSWORD_RESET), clock.instant()) }
        return ResetLinkResponse(link.email, link.expiresAt.iso())
    }

    /**
     * 6.b · Guarda la contraseña nueva. El enlace y los demás de recuperación dejan de servir, se cierran todas las
     * sesiones y el correo sale de cualquier bloqueo (A1).
     */
    suspend fun resetPassword(token: String, password: String) {
        val tokenHash = SecretTokens.hash(token)
        // Primero el enlace, que es lo que la app explica (6C); luego la regla y el hash, que tarda.
        database.query { usable(links.find(tokenHash, LinkPurpose.PASSWORD_RESET), clock.instant()) }
        if (!AccountRules.isValidNewPassword(password)) throw ApiException.badRequest("weak_password")
        val hash = passwords.hash(password)
        database.query {
            val now = clock.instant()
            val link = usable(links.find(tokenHash, LinkPurpose.PASSWORD_RESET, lock = true), now)
            users.updatePassword(link.userId, hash)
            links.markUsed(link.id, now)
            links.expireAll(link.userId, LinkPurpose.PASSWORD_RESET, now)
            sessions.revokeAll(link.userId, now)
            users.findById(link.userId)?.let { attempts.clear(it.email) }
        }
    }

    /** Dentro de una transacción: una sesión nueva para [user]. Devuelve el id del token de renovación y la sesión. */
    private fun issue(user: UserRecord): Pair<UUID, SessionResponse> {
        val now = clock.instant()
        val refreshToken = SecretTokens.generate()
        val id = sessions.insert(user.id, SecretTokens.hash(refreshToken), now.plus(jwtSettings.refreshTtl), now)
        val session = SessionResponse(
            accessToken = jwt.accessToken(UserPrincipal(user.id, user.role)),
            refreshToken = refreshToken,
            expiresIn = jwtSettings.accessTtl.seconds,
            userId = user.id.toString(),
            role = user.role,
            email = user.email,
            pendingEmail = user.pendingEmail,
        )
        return id to session
    }

    private fun waiting(userId: UUID, purpose: LinkPurpose, now: Instant): Boolean =
        links.lastSentAt(userId, purpose)?.let { now.isBefore(it.plus(AccountRules.RESEND_WAIT)) } == true

    companion object {
        /** A1 · Tras 5 fallos en una ventana de 15 minutos, el correo queda bloqueado 15 minutos. */
        const val MAX_FAILURES = 5
        val FAILURE_WINDOW: Duration = Duration.ofMinutes(15)
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)

        const val INVALID_CREDENTIALS = "invalid_credentials"

        fun emailTaken() = ApiException.conflict("email_taken")

        fun deliveryFailed() = ApiException(HttpStatusCode.ServiceUnavailable, "email_delivery_failed")

        fun tooManyAttempts(wait: Duration) = ApiException(HttpStatusCode.TooManyRequests, "too_many_attempts", retryAfter = wait)

        /**
         * 6C · Un enlace que no existe no dice de quién es: sin correo, la app abre 5 vacía. Uno vencido o usado lleva el
         * correo al que se envió.
         */
        fun usable(link: StoredLink?, now: Instant): StoredLink {
            if (link == null) throw ApiException(HttpStatusCode.Gone, LINK_EXPIRED)
            if (!link.usableAt(now)) throw ApiException(HttpStatusCode.Gone, LINK_EXPIRED, email = link.email)
            return link
        }

        private const val LINK_EXPIRED = "link_expired"
    }
}
