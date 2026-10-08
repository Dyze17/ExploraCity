package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.integration.MailClient
import co.edu.uniquindio.exploracity.integration.MailMessage
import co.edu.uniquindio.exploracity.model.LinkPurpose

/**
 * Los correos de la cuenta, en español, con la forma de [MailLayout]: HTML con los colores de la app y el enlace como
 * botón, y el mismo texto en texto plano. Los enlaces abren la app con su token: en desarrollo
 * «exploracity://enlace/restablecer?token=…», y en producción la misma ruta en https, en la dirección de la API (App
 * Links, con la página de routes/AppLinkRoutes.kt si no abren la app). Lanzan MailDeliveryException si el correo no
 * sale.
 */
class AccountMail(private val client: MailClient, private val linkBaseUrl: String) {

    /** 4 · Bienvenida al crear la cuenta. */
    suspend fun welcome(to: String, name: String) = send(
        to,
        MailContent(
            subject = "Tu cuenta de ExploraCity está lista",
            preview = "Ya puedes explorar y publicar los lugares de tu ciudad.",
            greeting = greeting(name),
            paragraphs = listOf(
                "Tu cuenta de ExploraCity quedó lista. Ya puedes explorar los lugares que comparte la comunidad y publicar los tuyos.",
                "Si no fuiste tú quien la creó, puedes ignorar este mensaje.",
            ),
            reason = "Recibes este correo porque se creó una cuenta de ExploraCity con esta dirección.",
        ),
    )

    /** 5 · El enlace para crear una contraseña nueva (6.b). */
    suspend fun passwordReset(to: String, name: String, token: String) = send(
        to,
        MailContent(
            subject = "Crea una contraseña nueva para ExploraCity",
            preview = "El enlace vence en ${AccountRules.RESET_LINK_TTL.toMinutes()} minutos.",
            greeting = greeting(name),
            paragraphs = listOf(
                "Recibimos una solicitud para crear una contraseña nueva. Abre el enlace en tu teléfono, con la app de " +
                    "ExploraCity instalada.",
            ),
            link = MailLink(link(LinkPurpose.PASSWORD_RESET, token), "Crear contraseña nueva"),
            after = listOf(
                "El enlace vence en ${AccountRules.RESET_LINK_TTL.toMinutes()} minutos y sirve una sola vez. Si no lo pediste, " +
                    "ignora este correo: tu contraseña sigue igual.",
            ),
            reason = "Recibes este correo porque se pidió una contraseña nueva para tu cuenta de ExploraCity.",
        ),
    )

    /** «Cambiar correo» · El enlace que confirma el correo nuevo; llega a ese correo. */
    suspend fun emailChange(to: String, name: String, token: String) = send(
        to,
        MailContent(
            subject = "Confirma tu correo nuevo en ExploraCity",
            preview = "Confírmalo para empezar a usarlo en tu cuenta.",
            greeting = greeting(name),
            paragraphs = listOf(
                "Pediste usar este correo en tu cuenta de ExploraCity. Para confirmarlo, abre el enlace en tu teléfono, con " +
                    "la sesión iniciada en la app.",
            ),
            link = MailLink(link(LinkPurpose.EMAIL_CHANGE, token), "Confirmar correo nuevo"),
            after = listOf(
                "El enlace vence en ${AccountRules.EMAIL_CHANGE_LINK_TTL.toMinutes()} minutos. Hasta que lo confirmes, sigues " +
                    "entrando con tu correo anterior. Si no lo pediste, ignora este mensaje.",
            ),
            reason = "Recibes este correo porque se pidió usar esta dirección en una cuenta de ExploraCity.",
        ),
    )

    fun link(purpose: LinkPurpose, token: String): String = "${prefix(purpose)}$token"

    /** El token del enlace de [purpose] que trae [message]; null si no trae ninguno. Lo usa el buzón de desarrollo. */
    fun tokenIn(message: MailMessage, purpose: LinkPurpose): String? =
        Regex(Regex.escape(prefix(purpose)) + "([A-Za-z0-9_-]+)").find(message.text)?.groupValues?.get(1)

    private fun prefix(purpose: LinkPurpose): String = "$linkBaseUrl/${purpose.path}?token="

    private fun greeting(name: String) = "Hola, $name:"

    private suspend fun send(to: String, content: MailContent) =
        client.send(MailMessage(to, content.subject, MailLayout.text(content), MailLayout.html(content)))
}
