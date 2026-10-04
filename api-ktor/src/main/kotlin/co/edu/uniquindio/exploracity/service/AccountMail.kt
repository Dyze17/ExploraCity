package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.integration.MailClient
import co.edu.uniquindio.exploracity.integration.MailMessage
import co.edu.uniquindio.exploracity.model.LinkPurpose

/**
 * Los correos de la cuenta, en español, con texto plano y HTML simple. Los enlaces abren la app con su token
 * («exploracity://enlace/restablecer?token=…»). Lanzan MailDeliveryException si el correo no sale.
 */
class AccountMail(private val client: MailClient, private val linkBaseUrl: String) {

    /** 4 · Bienvenida al crear la cuenta. */
    suspend fun welcome(to: String, name: String) = send(
        to = to,
        subject = "Tu cuenta de ExploraCity está lista",
        name = name,
        paragraphs = listOf(
            "Tu cuenta de ExploraCity quedó lista. Ya puedes explorar los lugares que comparte la comunidad y publicar los tuyos.",
            "Si no fuiste tú quien la creó, puedes ignorar este mensaje.",
        ),
    )

    /** 5 · El enlace para crear una contraseña nueva (6.b). */
    suspend fun passwordReset(to: String, name: String, token: String) = send(
        to = to,
        subject = "Crea una contraseña nueva para ExploraCity",
        name = name,
        paragraphs = listOf(
            "Recibimos una solicitud para crear una contraseña nueva. Abre este enlace en tu teléfono, con la app de ExploraCity instalada:",
        ),
        link = link(LinkPurpose.PASSWORD_RESET, token),
        after = listOf(
            "El enlace vence en ${AccountRules.RESET_LINK_TTL.toMinutes()} minutos y sirve una sola vez. Si no lo pediste, " +
                "ignora este correo: tu contraseña sigue igual.",
        ),
    )

    /** «Cambiar correo» · El enlace que confirma el correo nuevo; llega a ese correo. */
    suspend fun emailChange(to: String, name: String, token: String) = send(
        to = to,
        subject = "Confirma tu correo nuevo en ExploraCity",
        name = name,
        paragraphs = listOf(
            "Pediste usar este correo en tu cuenta de ExploraCity. Para confirmarlo, abre este enlace en tu teléfono, con la " +
                "sesión iniciada en la app:",
        ),
        link = link(LinkPurpose.EMAIL_CHANGE, token),
        after = listOf(
            "El enlace vence en ${AccountRules.EMAIL_CHANGE_LINK_TTL.toMinutes()} minutos. Hasta que lo confirmes, sigues " +
                "entrando con tu correo anterior. Si no lo pediste, ignora este mensaje.",
        ),
    )

    fun link(purpose: LinkPurpose, token: String): String = "${prefix(purpose)}$token"

    /** El token del enlace de [purpose] que trae [message]; null si no trae ninguno. Lo usa el buzón de desarrollo. */
    fun tokenIn(message: MailMessage, purpose: LinkPurpose): String? =
        Regex(Regex.escape(prefix(purpose)) + "([A-Za-z0-9_-]+)").find(message.text)?.groupValues?.get(1)

    private fun prefix(purpose: LinkPurpose): String {
        val path = when (purpose) {
            LinkPurpose.PASSWORD_RESET -> "restablecer"
            LinkPurpose.EMAIL_CHANGE -> "confirmar-correo"
        }
        return "$linkBaseUrl/$path?token="
    }

    private suspend fun send(
        to: String,
        subject: String,
        name: String,
        paragraphs: List<String>,
        link: String? = null,
        after: List<String> = emptyList(),
    ) {
        val greeting = "Hola, $name:"
        val text = buildString {
            append(greeting).append("\n\n")
            paragraphs.forEach { append(it).append("\n\n") }
            link?.let { append(it).append("\n\n") }
            after.forEach { append(it).append("\n\n") }
            append("— ExploraCity")
        }
        val html = buildString {
            append("<p>").append(escape(greeting)).append("</p>")
            paragraphs.forEach { append("<p>").append(escape(it)).append("</p>") }
            link?.let { append("<p><a href=\"").append(escape(it)).append("\">").append(escape(it)).append("</a></p>") }
            after.forEach { append("<p>").append(escape(it)).append("</p>") }
            append("<p>— ExploraCity</p>")
        }
        client.send(MailMessage(to, subject, text, html))
    }

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
