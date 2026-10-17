package co.edu.uniquindio.exploracity.navigation

import co.edu.uniquindio.exploracity.BuildConfig
import java.net.URI
import java.net.URLDecoder

/**
 * Un enlace del correo de la API: «Nueva contraseña» (6.b) o «Cambiar correo», con su token. Llega de dos formas:
 * - `exploracity://enlace/restablecer?token=…` y `…/confirmar-correo?token=…`: desde la API del equipo, y desde el
 *   botón «Abrir en ExploraCity» de la página que la API muestra en el navegador.
 * - `https://<API en Cloud Run>/enlace/restablecer?token=…`: el que trae el correo en producción. Con los App Links
 *   verificados (src/release/AndroidManifest.xml), Android lo abre aquí directo.
 */
sealed interface EmailLink {
    val token: String

    data class ResetPassword(override val token: String) : EmailLink

    data class ConfirmEmail(override val token: String) : EmailLink

    companion object {
        /** null si no es un enlace del correo o le falta el token. [httpsHost] es el dominio de los enlaces https. */
        fun parse(link: String?, httpsHost: String = BuildConfig.APP_LINK_HOST): EmailLink? {
            val uri = runCatching { URI(link ?: return null) }.getOrNull() ?: return null
            val path = when {
                uri.scheme == SCHEME && uri.host == HOST -> uri.path
                uri.scheme == HTTPS && uri.host.equals(httpsHost, ignoreCase = true) && uri.path.orEmpty().startsWith("$HTTPS_PREFIX/") ->
                    uri.path.removePrefix(HTTPS_PREFIX)
                else -> return null
            }
            val token = uri.rawQuery.orEmpty().split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == TOKEN }
                // Con el nombre del juego de caracteres: la versión con Charset llegó en Android 13.
                ?.let { URLDecoder.decode(it[1], "UTF-8") }
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return when (path) {
                RESET_PATH -> ResetPassword(token)
                CONFIRM_EMAIL_PATH -> ConfirmEmail(token)
                else -> null
            }
        }

        private const val SCHEME = "exploracity"
        private const val HOST = "enlace"
        private const val HTTPS = "https"
        private const val HTTPS_PREFIX = "/enlace"
        private const val TOKEN = "token"
        private const val RESET_PATH = "/restablecer"
        private const val CONFIRM_EMAIL_PATH = "/confirmar-correo"
    }
}
