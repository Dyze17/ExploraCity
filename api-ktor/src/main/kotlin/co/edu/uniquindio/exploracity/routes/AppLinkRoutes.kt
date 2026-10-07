package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.config.AndroidAppSettings
import co.edu.uniquindio.exploracity.model.LinkPurpose
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** Una declaración de /.well-known/assetlinks.json (Digital Asset Links). */
@Serializable
data class AssetLink(val relation: List<String>, val target: AssetLinkTarget)

@Serializable
data class AssetLinkTarget(
    val namespace: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("sha256_cert_fingerprints") val fingerprints: List<String>,
)

/**
 * C1 · Los enlaces https del correo, con APP_LINK_BASE_URL = la dirección de la API + /enlace:
 * - GET /.well-known/assetlinks.json, solo con las huellas de la firma: Android verifica que la app es dueña de estos
 *   enlaces y los abre directo, sin pasar por el navegador.
 * - GET /enlace/restablecer y /enlace/confirmar-correo: si el enlace se abre en el navegador (en un computador, o antes
 *   de la verificación), una página con el botón «Abrir en ExploraCity», que le pasa el token a la app por su esquema
 *   propio (exploracity://enlace/…, el que ya recibe su intent-filter).
 */
fun Route.appLinkRoutes(android: AndroidAppSettings) {
    if (android.certFingerprints.isNotEmpty()) {
        val target = AssetLinkTarget(namespace = "android_app", packageName = android.packageName, fingerprints = android.certFingerprints)
        val links = listOf(AssetLink(relation = listOf(HANDLE_ALL_URLS), target = target))
        get("/.well-known/assetlinks.json") {
            call.respond(links)
        }
    }
    LinkPurpose.entries.forEach { purpose ->
        get("/enlace/${purpose.path}") {
            // El token va en la dirección: que no quede en cachés, en el Referer de otra página ni en un buscador.
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("Referrer-Policy", "no-referrer")
            call.response.header("X-Robots-Tag", "noindex")
            call.response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
            val token = call.request.queryParameters["token"]?.trim().orEmpty()
            if (token.isEmpty()) {
                call.respondText(incompletePage(), HTML, HttpStatusCode.BadRequest)
            } else {
                call.respondText(openAppPage(purpose, token, android.packageName), HTML)
            }
        }
    }
}

private const val HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls"

private val HTML = ContentType.Text.Html.withCharset(Charsets.UTF_8)

private fun openAppPage(purpose: LinkPurpose, token: String, packageName: String): String {
    // Codificado, el token solo trae letras, números y «%._-*+»: nada que escapar dentro del atributo.
    val query = "token=" + URLEncoder.encode(token, Charsets.UTF_8)
    // Chrome y Samsung Internet abren la app con un intent://; con el paquete, solo ExploraCity puede recibirlo.
    val intent = "intent://enlace/${purpose.path}?$query#Intent;scheme=exploracity;package=$packageName;end"
    val (title, instruction) = when (purpose) {
        LinkPurpose.PASSWORD_RESET ->
            "Crea tu contraseña nueva" to "Toca el botón en el teléfono donde tienes ExploraCity. La app abrirá «Nueva contraseña»."
        LinkPurpose.EMAIL_CHANGE ->
            "Confirma tu correo nuevo" to
                "Toca el botón en el teléfono donde tienes ExploraCity, con la sesión iniciada. La app confirmará el correo."
    }
    return page(
        title = title,
        body = """
            <p>$instruction</p>
            <a class="button" href="$intent">Abrir en ExploraCity</a>
            <p class="hint">¿Estás en un computador? Abre este correo en el teléfono. Si el enlace venció, pide otro desde la app.</p>
        """,
    )
}

private fun incompletePage(): String = page(
    title = "Este enlace está incompleto",
    body = """<p class="hint">Copia el enlace completo del correo o pide otro desde la app.</p>""",
)

/** Con los colores de la app (ui/theme/Color.kt): claro u oscuro según el teléfono. */
private fun page(title: String, body: String): String = """
    |<!doctype html>
    |<html lang="es">
    |<head>
    |<meta charset="utf-8">
    |<meta name="viewport" content="width=device-width, initial-scale=1">
    |<meta name="color-scheme" content="light dark">
    |<meta name="robots" content="noindex">
    |<title>$title · ExploraCity</title>
    |<style>
    |  :root { --bg: #FDF8F5; --text: #231613; --muted: #5E4740; --primary: #A9442A; --on-primary: #FFFFFF; }
    |  @media (prefers-color-scheme: dark) {
    |    :root { --bg: #16110F; --text: #F2E4DE; --muted: #D6C1B8; --primary: #FFB59D; --on-primary: #5C1B08; }
    |  }
    |  body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.5 system-ui, sans-serif; }
    |  main { max-width: 28rem; margin: 0 auto; padding: 3rem 1rem; }
    |  .brand { color: var(--primary); font-weight: 700; letter-spacing: .02em; }
    |  h1 { font-size: 1.6rem; line-height: 1.25; margin: .5rem 0 1rem; }
    |  .button { display: block; margin: 1.5rem 0; padding: .9rem 1.5rem; border-radius: 999px; background: var(--primary);
    |    color: var(--on-primary); font-weight: 600; text-align: center; text-decoration: none; }
    |  .hint { color: var(--muted); }
    |</style>
    |</head>
    |<body>
    |<main>
    |<div class="brand">ExploraCity</div>
    |<h1>$title</h1>
    |${body.trimIndent()}
    |</main>
    |</body>
    |</html>
""".trimMargin()
