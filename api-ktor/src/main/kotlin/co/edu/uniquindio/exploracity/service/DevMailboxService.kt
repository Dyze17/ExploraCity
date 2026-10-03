package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.integration.DevMailbox
import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.repository.AccountLinkRepository
import co.edu.uniquindio.exploracity.repository.UserRepository
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.time.Duration

/**
 * Solo en desarrollo (DEV_MAILBOX=true y sin SendGrid): lo que llegaría al correo, para los botones de prueba de 6.a y
 * «Cambiar correo». Los tokens salen de los correos del buzón; en la base de datos solo está su hash.
 */
class DevMailboxService(
    private val database: Database,
    private val mailbox: DevMailbox,
    private val mail: AccountMail,
    private val users: UserRepository,
    private val links: AccountLinkRepository,
    private val clock: Clock,
) {
    /** El token del último enlace de [purpose] que llegó a [email] y aún sirve; null si no hay ninguno. */
    suspend fun latestLink(email: String, purpose: LinkPurpose): String? {
        val tokens = mailbox.inbox(AccountRules.normalizeEmail(email)).mapNotNull { mail.tokenIn(it, purpose) }
        if (tokens.isEmpty()) return null
        return database.query {
            val now = clock.instant()
            tokens.firstOrNull { token -> links.find(SecretTokens.hash(token), purpose)?.usableAt(now) == true }
        }
    }

    /**
     * Un enlace de [purpose] para [email] que ya venció (6C). Recuperar la contraseña: [email] es el de la cuenta.
     * Cambiar correo: es el correo nuevo pendiente. null si no hay cuenta o cambio pendiente con ese correo.
     */
    suspend fun expiredLink(email: String, purpose: LinkPurpose): String? = database.query {
        val address = AccountRules.normalizeEmail(email)
        val user = when (purpose) {
            LinkPurpose.PASSWORD_RESET -> users.findByEmail(address)
            LinkPurpose.EMAIL_CHANGE -> users.findByPendingEmail(address)
        } ?: return@query null
        val token = SecretTokens.generate()
        val expiresAt = clock.instant().minus(Duration.ofMinutes(1))
        // Enviado hace más de lo que dura: no cuenta para la espera de 60 s.
        links.insert(user.id, purpose, SecretTokens.hash(token), address, expiresAt, expiresAt.minus(AccountRules.RESET_LINK_TTL))
        token
    }
}
