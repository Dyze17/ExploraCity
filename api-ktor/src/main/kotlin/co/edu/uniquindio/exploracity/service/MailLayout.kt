package co.edu.uniquindio.exploracity.service

/**
 * Lo que dice un correo de la cuenta: un saludo, párrafos, un enlace opcional con su botón, párrafos después del enlace
 * y por qué le llegó a la persona.
 */
data class MailContent(
    val subject: String,
    /** El resumen que muestran las bandejas junto al asunto; no se ve al abrir el correo. */
    val preview: String,
    val greeting: String,
    val paragraphs: List<String>,
    val link: MailLink? = null,
    val after: List<String> = emptyList(),
    val reason: String,
)

data class MailLink(val url: String, val label: String)

/**
 * La forma de los correos, con los colores de la app (ui/theme/Color.kt): una tarjeta blanca sobre el fondo de la app,
 * el nombre en el color principal y el enlace como botón.
 * - El HTML usa tablas y estilos en línea, lo único que respetan todos los clientes de correo (Gmail quita `<style>` en
 *   algunos casos). Sin imágenes: muchos clientes las bloquean hasta que se aceptan.
 * - El texto plano dice lo mismo, con el enlace completo; el buzón de desarrollo lee el token de ahí.
 */
object MailLayout {

    fun text(content: MailContent): String = buildString {
        append(content.greeting).append("\n\n")
        content.paragraphs.forEach { append(it).append("\n\n") }
        content.link?.let { append(it.url).append("\n\n") }
        content.after.forEach { append(it).append("\n\n") }
        append(SIGNATURE).append("\n\n")
        append(content.reason).append('\n')
        append(ADDRESS)
    }

    fun html(content: MailContent): String = buildString {
        append("<!doctype html>")
        append("<html lang=\"es\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        // Los colores son de un tema claro: que los clientes que respetan esto no los inviertan.
        append("<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">")
        append("<title>").append(escape(content.subject)).append("</title></head>")
        append("<body style=\"margin:0;padding:0;background-color:$BACKGROUND;\">")
        append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">").append(escape(content.preview)).append("</div>")
        append(table("width=\"100%\"", "background-color:$BACKGROUND;"))
        append("<tr><td align=\"center\" style=\"padding:24px 12px;\">")

        // La tarjeta.
        append(table("width=\"100%\"", "max-width:560px;background-color:$CARD;border-radius:16px;"))
        append("<tr><td style=\"padding:28px 32px 4px 32px;$FONT font-size:22px;line-height:28px;font-weight:bold;color:$PRIMARY;\">")
        append("ExploraCity</td></tr>")
        append("<tr><td style=\"padding:16px 32px 0 32px;$FONT font-size:16px;line-height:24px;color:$TEXT;\">")
        append(paragraph(content.greeting, bold = true))
        content.paragraphs.forEach { append(paragraph(it)) }
        append("</td></tr>")
        content.link?.let { link ->
            val url = escape(link.url)
            append("<tr><td style=\"padding:8px 32px 8px 32px;\">")
            append(table("", ""))
            append("<tr><td bgcolor=\"$PRIMARY\" style=\"border-radius:999px;\">")
            append("<a href=\"$url\" style=\"display:inline-block;padding:14px 28px;$FONT font-size:16px;line-height:20px;")
            append("font-weight:bold;color:$ON_PRIMARY;text-decoration:none;border-radius:999px;\">")
            append(escape(link.label)).append("</a></td></tr></table></td></tr>")
            append("<tr><td style=\"padding:12px 32px 0 32px;$FONT font-size:13px;line-height:20px;color:$MUTED;\">")
            append("Si el botón no funciona, copia este enlace en el navegador del teléfono:<br>")
            append("<a href=\"$url\" style=\"color:$PRIMARY;word-break:break-all;\">$url</a></td></tr>")
        }
        append("<tr><td style=\"padding:16px 32px 28px 32px;$FONT font-size:16px;line-height:24px;color:$TEXT;\">")
        content.after.forEach { append(paragraph(it)) }
        append(paragraph(SIGNATURE, last = true))
        append("</td></tr></table>")

        // El pie, fuera de la tarjeta.
        append(table("width=\"100%\"", "max-width:560px;"))
        append("<tr><td align=\"center\" style=\"padding:16px 32px 8px 32px;$FONT font-size:12px;line-height:18px;color:$MUTED;\">")
        append(escape(content.reason)).append("<br>").append(escape(ADDRESS))
        append("</td></tr></table>")

        append("</td></tr></table></body></html>")
    }

    private fun table(attributes: String, style: String): String =
        "<table role=\"presentation\" $attributes cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;$style\">"

    private fun paragraph(text: String, bold: Boolean = false, last: Boolean = false): String {
        val weight = if (bold) "font-weight:bold;" else ""
        val margin = if (last) "0" else "0 0 16px 0"
        return "<p style=\"margin:$margin;$weight\">${escape(text)}</p>"
    }

    fun escape(text: String): String = buildString {
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

    private const val SIGNATURE = "— ExploraCity"
    private const val ADDRESS = "ExploraCity · Universidad del Quindío · Armenia, Colombia"

    // ui/theme/Color.kt (tema claro): surfaceContainer, surfaceContainerLowest, primary, onPrimary, onSurface y
    // onSurfaceVariant. El texto y el gris cumplen 4,5:1 sobre la tarjeta y sobre el fondo.
    private const val BACKGROUND = "#F6EDE7"
    private const val CARD = "#FFFFFF"
    private const val PRIMARY = "#A9442A"
    private const val ON_PRIMARY = "#FFFFFF"
    private const val TEXT = "#231613"
    private const val MUTED = "#5E4740"
    private const val FONT = "font-family:Arial,Helvetica,sans-serif;"
}
