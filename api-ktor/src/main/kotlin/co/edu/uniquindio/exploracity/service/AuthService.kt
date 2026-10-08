package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.config.JwtSettings
import co.edu.uniquindio.exploracity.config.UserPrincipal
import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.integration.GoogleIdentity
import co.edu.uniquindio.exploracity.integration.GoogleTokenVerifier
import co.edu.uniquindio.exploracity.integration.InvalidGoogleTokenException
import co.edu.uniquindio.exploracity.integration.MailDeliveryException
import co.edu.uniquindio.exploracity.model.GoogleLinkRequest
import co.edu.uniquindio.exploracity.model.GoogleSignInRequest
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
    /** ADR-15 · null sin GOOGLE_WEB_CLIENT_ID. */
    private val google: GoogleTokenVerifier? = null,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * 4 · Crea la cuenta y abre la sesión; el segundo valor dice si la creó. Si el correo de bienvenida falla, la cuenta
     * queda creada igual. Repetir un registro cuya respuesta se perdió (el mismo [RegisterRequest.clientId] y la misma
     * contraseña) abre otra sesión en esa cuenta, sin crearla de nuevo.
     */
    suspend fun register(request: RegisterRequest): Pair<SessionResponse, Boolean> {
        val name = request.name.trim()
        val email = AccountRules.normalizeEmail(request.email)
        if (!AccountRules.isValidName(name)) throw ApiException.badRequest("invalid_name")
        if (!AccountRules.isValidEmail(email)) throw ApiException.badRequest("invalid_email")
        if (!AccountRules.isValidNewPassword(request.password)) throw ApiException.badRequest("weak_password")
        val clientId = request.clientId?.let { runCatching { UUID.fromString(it) }.getOrNull() ?: throw ApiException.badRequest() }
        // Antes de calcular el hash, que tarda; al guardar, el índice único lo vuelve a comprobar.
        database.query { users.findByEmail(email) }?.let { existing ->
            return repeated(existing, clientId, request.password) to false
        }
        val hash = passwords.hash(request.password)
        val session = try {
            database.query {
                val user = users.create(email, hash, name, request.residency, security.roleFor(email), clock.instant(), clientId)
                attempts.clear(email)
                issue(user).second
            }
        } catch (e: EmailTakenException) {
            // Otra petición la creó mientras tanto: puede ser el primer intento de este mismo registro.
            val existing = database.query { users.findByEmail(email) } ?: throw emailTaken()
            return repeated(existing, clientId, request.password) to false
        }
        return session.copy(welcomeEmailSent = welcome(email, name)) to true
    }

    /** 4 · El correo de bienvenida; si no sale, la cuenta queda creada igual. Dice si salió. */
    private suspend fun welcome(email: String, name: String): Boolean = try {
        mail.welcome(email, name)
        true
    } catch (e: MailDeliveryException) {
        log.warn("No salió el correo de bienvenida; la cuenta quedó creada.", e)
        false
    }

    /**
     * ADR-15 · Entrar con Google; el segundo valor dice si creó la cuenta. La cuenta se busca por la cuenta de Google
     * (`sub`), no por el correo: así sigue funcionando aunque la persona cambie de correo en la app o en Google.
     * - Ya vinculada: abre la sesión.
     * - El correo ya tiene una cuenta con contraseña: link_required (B1). Solo se vincula con esa contraseña
     *   ([linkGoogle]): si no, quien registró ese correo antes con una contraseña conservaría el acceso.
     * - Cuenta nueva: sin [GoogleSignInRequest.registration], registration_required con el correo y el nombre de Google
     *   (la app abre 4 en modo Google, C1); con ella, la crea sin contraseña y envía la bienvenida.
     * Repetir una petición cuya respuesta se perdió encuentra la cuenta ya creada y abre otra sesión.
     */
    suspend fun signInWithGoogle(request: GoogleSignInRequest): Pair<SessionResponse, Boolean> {
        val identity = verify(request.idToken)
        database.query { users.findByGoogleSub(identity.subject)?.let { issue(it).second } }?.let { return it to false }
        database.query { users.findByEmail(identity.email) }?.let { existing -> throw notLinked(existing) }
        val registration = request.registration
            ?: throw ApiException(HttpStatusCode.NotFound, REGISTRATION_REQUIRED, email = identity.email, name = identity.name)
        val name = registration.name.trim()
        if (!AccountRules.isValidName(name)) throw ApiException.badRequest("invalid_name")
        val session = try {
            database.query {
                val user = users.create(
                    identity.email, null, name, registration.residency, security.roleFor(identity.email), clock.instant(),
                    googleSub = identity.subject,
                )
                issue(user).second
            }
        } catch (e: EmailTakenException) {
            // Otra petición la creó mientras tanto: si fue con esta misma cuenta de Google, es el mismo registro.
            val existing = database.query { users.findByEmail(identity.email) } ?: throw emailTaken()
            if (existing.googleSub != identity.subject) throw notLinked(existing)
            return database.query { issue(existing).second } to false
        }
        return session.copy(welcomeEmailSent = welcome(identity.email, name)) to true
    }

    /**
     * B1 · Vincula Google a la cuenta con contraseña de ese mismo correo y abre la sesión. Una contraseña equivocada
     * cuenta para el bloqueo de 15 minutos (A1) como en el inicio de sesión, y responde lo mismo: invalid_credentials.
     */
    suspend fun linkGoogle(request: GoogleLinkRequest): SessionResponse {
        val identity = verify(request.idToken)
        val now = clock.instant()
        val user = database.query {
            attempts.lockedUntil(identity.email, now)?.let { until -> throw tooManyAttempts(Duration.between(now, until)) }
            users.findByEmail(identity.email)
        }
        // Ya vinculada (por ejemplo, la respuesta anterior se perdió): basta con abrir la sesión.
        if (user != null && user.googleSub == identity.subject) return database.query { issue(user).second }
        if (user == null || user.googleSub != null || !passwords.verify(request.password, user.passwordHash)) {
            database.query { attempts.recordFailure(identity.email, clock.instant(), MAX_FAILURES, FAILURE_WINDOW, LOCK_DURATION) }
            throw ApiException.unauthorized(INVALID_CREDENTIALS)
        }
        return database.query {
            users.findByGoogleSub(identity.subject)?.let { throw ApiException.conflict(GOOGLE_ACCOUNT_IN_USE) }
            users.linkGoogle(user.id, identity.subject)
            attempts.clear(identity.email)
            issue(checkNotNull(users.findById(user.id))).second
        }
    }

    private suspend fun verify(idToken: String): GoogleIdentity {
        val verifier = google ?: throw ApiException(HttpStatusCode.ServiceUnavailable, "google_sign_in_unavailable")
        return try {
            verifier.verify(idToken)
        } catch (e: InvalidGoogleTokenException) {
            log.info("ID token de Google rechazado: {}", e.message)
            throw ApiException.unauthorized("invalid_google_token")
        }
    }

    /**
     * El correo ya tiene una cuenta sin esta cuenta de Google. Con contraseña, link_required (B1). Si ya tiene otra cuenta
     * de Google vinculada, email_taken: no se reemplaza la vinculada.
     */
    private fun notLinked(existing: UserRecord): ApiException =
        if (existing.googleSub == null) ApiException(HttpStatusCode.Conflict, LINK_REQUIRED, email = existing.email) else emailTaken()

    /**
     * 4 · El correo ya tiene cuenta. Si la creó un registro con el mismo [clientId] y la contraseña coincide, es el mismo
     * intento cuya respuesta se perdió: abre otra sesión, sin `welcomeEmailSent` (la bienvenida la envió el primero).
     * Si no, email_taken. Sin el clientId, que solo conoce el teléfono que lo generó, no sirve para probar contraseñas.
     */
    private suspend fun repeated(existing: UserRecord, clientId: UUID?, password: String): SessionResponse {
        val sameRegistration = clientId != null && existing.clientId == clientId && passwords.verify(password, existing.passwordHash)
        if (!sameRegistration) throw emailTaken()
        return database.query { issue(existing).second }
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
        const val REGISTRATION_REQUIRED = "registration_required"
        const val LINK_REQUIRED = "link_required"
        const val GOOGLE_ACCOUNT_IN_USE = "google_account_in_use"

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
