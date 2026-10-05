package co.edu.uniquindio.exploracity.navigation

import java.net.URI
import java.net.URLDecoder

/**
 * Un enlace del correo de la API: `exploracity://enlace/restablecer?token=…` (6.b) o
 * `exploracity://enlace/confirmar-correo?token=…` («Cambiar correo»). Con un dominio verificado llegarán como https;
 * por ahora solo este esquema.
 */
sealed interface EmailLink {
    val token: String

    data class ResetPassword(override val token: String) : EmailLink

    data class ConfirmEmail(override val token: String) : EmailLink

    companion object {
        /** null si no es un enlace del correo o le falta el token. */
        fun parse(link: String?): EmailLink? {
            val uri = runCatching { URI(link ?: return null) }.getOrNull() ?: return null
            if (uri.scheme != SCHEME || uri.host != HOST) return null
            val token = uri.rawQuery.orEmpty().split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == TOKEN }
                // Con el nombre del juego de caracteres: la versión con Charset llegó en Android 13.
                ?.let { URLDecoder.decode(it[1], "UTF-8") }
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return when (uri.path) {
                RESET_PATH -> ResetPassword(token)
                CONFIRM_EMAIL_PATH -> ConfirmEmail(token)
                else -> null
            }
        }

        private const val SCHEME = "exploracity"
        private const val HOST = "enlace"
        private const val TOKEN = "token"
        private const val RESET_PATH = "/restablecer"
        private const val CONFIRM_EMAIL_PATH = "/confirmar-correo"
    }
}
