package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.DevLinkDto
import co.edu.uniquindio.exploracity.data.remote.dto.DevLinkRequest
import co.edu.uniquindio.exploracity.data.remote.dto.LinkPurpose
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

/**
 * Solo en compilaciones de desarrollo, contra una API con DEV_MAILBOX=true: el buzón que leen los botones de prueba de
 * 6.a y «Cambiar correo». Devuelven null si no hay enlace o cuenta.
 */
class DevMailboxApi(private val client: HttpClient) {
    /** El token del último enlace que llegó a [email] y aún sirve. */
    suspend fun latestLink(email: String, purpose: LinkPurpose): String? = link("v1/dev/mailbox/latest-link", email, purpose)

    /** Un enlace ya vencido: de la cuenta de [email] para recuperar la contraseña, o del correo nuevo pendiente. */
    suspend fun expiredLink(email: String, purpose: LinkPurpose): String? = link("v1/dev/mailbox/expired-link", email, purpose)

    private suspend fun link(path: String, email: String, purpose: LinkPurpose): String? = try {
        client.post(path) {
            withoutSession()
            jsonBody(DevLinkRequest(email, purpose))
        }.body<DevLinkDto>().token
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound.value) null else throw e
    }
}
