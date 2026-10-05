package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.ApiSession
import co.edu.uniquindio.exploracity.data.remote.DevMailboxApi
import co.edu.uniquindio.exploracity.data.remote.dto.LinkPurpose
import kotlinx.coroutines.flow.first

/**
 * Solo en compilaciones de desarrollo, contra una API con DEV_MAILBOX=true: los enlaces que llegarían al correo, para
 * abrirlos sin un correo de verdad (6.a y «Confirma tu correo nuevo»). Cada uno da el token del enlace, o null si no
 * llegó ninguno. Lanzan excepción si falla la red.
 */
interface DevMailbox {
    /** El último enlace de recuperación que llegó a [email] y aún sirve; null si no llegó ninguno (sin cuenta, no llega). */
    suspend fun latestResetLink(email: String): String?

    /** Un enlace de recuperación para [email] que ya venció; null si el correo no tiene cuenta. */
    suspend fun expiredResetLink(email: String): String?

    /** El enlace del cambio de correo pendiente; null si no hay ninguno. */
    suspend fun latestEmailChangeLink(): String?

    /** Un enlace del cambio pendiente que ya venció; null si no hay cambio pendiente. */
    suspend fun expiredEmailChangeLink(): String?
}

/** El buzón de desarrollo de la API. El del cambio de correo es el del correo nuevo, que la sesión tiene pendiente. */
class ApiDevMailbox(private val api: DevMailboxApi, private val session: ApiSession) : DevMailbox {
    override suspend fun latestResetLink(email: String): String? = api.latestLink(email.trim(), LinkPurpose.PASSWORD_RESET)

    override suspend fun expiredResetLink(email: String): String? = api.expiredLink(email.trim(), LinkPurpose.PASSWORD_RESET)

    override suspend fun latestEmailChangeLink(): String? = pendingEmail()?.let { api.latestLink(it, LinkPurpose.EMAIL_CHANGE) }

    override suspend fun expiredEmailChangeLink(): String? = pendingEmail()?.let { api.expiredLink(it, LinkPurpose.EMAIL_CHANGE) }

    private suspend fun pendingEmail(): String? = session.account.first()?.account?.pendingEmail
}
