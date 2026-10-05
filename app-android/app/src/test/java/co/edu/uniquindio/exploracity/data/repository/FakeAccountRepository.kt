package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.DataExport
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Temporal hasta que exista la API: arma el archivo con el perfil ([users]), las publicaciones ([publications]) y lo
 * que la persona hizo en el feed ([pois]). Las claves y los valores van en español: el archivo es para leerlo ella.
 * El cambio de correo consulta las cuentas del servidor de acceso ([credentials]); sus enlaces vencen a los 30 minutos
 * y sirven una vez. [emailFails] simula la caída del correo.
 */
class FakeAccountRepository(
    private val pois: FakePoiRepository,
    private val publications: FakePublicationRepository,
    private val users: UserRepository,
    private val credentials: FakeCredentials,
    private val clock: Clock = Clock.systemUTC(),
    account: Account = sampleAccount,
    private val latency: Duration = 900.milliseconds,
    private val emailFails: Boolean = false,
    /** Lo que borra el resto del servidor falso con la cuenta (el perfil y su foto). */
    private val onDeleted: suspend () -> Unit = {},
) : AccountRepository {

    /** Un enlace enviado al correo nuevo; [used] cuando ya confirmó el cambio o lo reemplazó otro. */
    private class EmailLink(val email: String, val expiresAt: Instant, val used: Boolean = false)

    private val current = MutableStateFlow(account)
    override val account: StateFlow<Account> = current.asStateFlow()

    // Los enlaces del cambio de correo; se pierden al cerrar la app, como el resto del servidor falso.
    private val links = ConcurrentHashMap<String, EmailLink>()

    /** Para las pruebas: la cuenta ya se pidió borrar. Con la API, el inicio de sesión dejaría de aceptarla. */
    var deleted = false
        private set

    override suspend fun deleteAccount() {
        delay(latency)
        onDeleted()
        expireLinks()
        current.update { it.copy(pendingEmail = null) }
        deleted = true
    }

    override suspend fun requestEmailChange(newEmail: String, password: String) {
        delay(latency)
        val email = newEmail.trim().lowercase()
        val now = current.value
        if (!credentials.matches(now.email, password)) throw InvalidCredentialsException()
        require(email != now.email.lowercase()) { "Es el mismo correo" }
        if (credentials.hasAccount(email)) throw EmailTakenException()
        if (emailFails) throw EmailDeliveryException()
        issue(email)
        current.update { it.copy(pendingEmail = email) }
    }

    override suspend fun resendEmailChange() {
        delay(latency)
        val pending = checkNotNull(current.value.pendingEmail) { "No hay un cambio pendiente" }
        if (emailFails) throw EmailDeliveryException()
        issue(pending)
    }

    override suspend fun confirmEmailChange(token: String): String {
        delay(latency)
        val link = links[token] ?: throw ExpiredLinkException(email = "")
        if (link.used || !clock.instant().isBefore(link.expiresAt)) throw ExpiredLinkException(link.email)
        // Otra persona pudo registrarse con ese correo mientras tanto.
        if (credentials.hasAccount(link.email)) throw EmailTakenException()
        credentials.moveAccount(current.value.email, link.email)
        links[token] = EmailLink(link.email, link.expiresAt, used = true)
        current.value = Account(link.email)
        return link.email
    }

    fun latestEmailChangeLink(): String? {
        val now = clock.instant()
        return links.entries.firstOrNull { (_, link) -> !link.used && now.isBefore(link.expiresAt) }?.key
    }

    fun expiredEmailChangeLink(): String? {
        val pending = current.value.pendingEmail ?: return null
        val token = UUID.randomUUID().toString()
        links[token] = EmailLink(pending, clock.instant() - 1.minutes.toJavaDuration())
        return token
    }

    /** Un enlace nuevo para [email]; los anteriores dejan de servir. */
    private fun issue(email: String) {
        expireLinks()
        links[UUID.randomUUID().toString()] = EmailLink(email, clock.instant() + AuthRules.RESET_LINK_DURATION.toJavaDuration())
    }

    private fun expireLinks() {
        links.replaceAll { _, link -> EmailLink(link.email, link.expiresAt, used = true) }
    }

    override suspend fun exportData(): DataExport {
        delay(latency)
        val profile = users.ownProfile()
        val activity = pois.activity()
        val export = ExportFile(
            generatedAt = clock.instant().toString(),
            account = ExportAccount(
                email = current.value.email,
                name = profile.author.name,
                residency = if (profile.residency == Residency.RESIDENT) "Residente" else "De visita",
                city = profile.city,
                memberSince = profile.memberSince.toString(),
                bio = profile.bio,
                photo = profile.photo,
            ),
            reputation = ExportReputation(
                points = profile.author.points,
                level = profile.author.level.label,
                badges = profile.badges.map { ExportBadge(it.name, it.progress, it.target, it.unlocked) },
            ),
            publications = publications.myPublications().map { it.toExport() },
            comments = activity.comments.map { (poi, comment) -> ExportComment(poi.title, comment.text, comment.createdAt.toString()) },
            votes = activity.votes.map { it.title },
            visits = activity.visits.map { (poi, experience) ->
                ExportVisit(poi.title, experience.recommends, experience.text.ifBlank { null }, experience.showName)
            },
        )
        val today = LocalDate.now(clock.withZone(BOGOTA))
        return DataExport(fileName = "exploracity-mis-datos-$today.json", content = exportJson.encodeToString(export))
    }

    private companion object {
        val BOGOTA: ZoneId = ZoneId.of("America/Bogota")
        val SPANISH: Locale = Locale.forLanguageTag("es-CO")

        val exportJson = Json {
            prettyPrint = true
            explicitNulls = false
        }

        fun OwnPublication.toExport() = ExportPublication(
            id = id,
            title = title,
            category = category.label,
            status = status.label,
            description = description,
            latitude = location.latitude,
            longitude = location.longitude,
            hours = hours?.let { hours ->
                ExportHours(
                    days = hours.days.sorted().map { it.getDisplayName(TextStyle.FULL, SPANISH) },
                    opens = hours.opens.toString(),
                    closes = hours.closes.toString(),
                )
            },
            price = price?.label,
            photos = photos.map { it.url },
            submittedAt = submittedAt.toString(),
            votes = votes,
            comments = comments,
            possibleDuplicate = possibleDuplicate,
        )

        val Category.label: String
            get() = when (this) {
                Category.GASTRONOMY -> "Gastronomía"
                Category.CULTURE -> "Cultura"
                Category.NATURE -> "Naturaleza"
                Category.ENTERTAINMENT -> "Entretenimiento"
                Category.HISTORY -> "Historia"
            }

        val PublicationStatus.label: String
            get() = when (this) {
                PublicationStatus.PENDING -> "Pendiente de verificación"
                PublicationStatus.VERIFIED -> "Verificada"
                PublicationStatus.REJECTED -> "Rechazada"
                PublicationStatus.FINALIZED -> "Resuelta / finalizada"
            }

        val PriceRange.label: String
            get() = when (this) {
                PriceRange.FREE -> "Entrada libre"
                PriceRange.LOW -> "Hasta 25.000"
                PriceRange.MEDIUM -> "Entre 25.000 y 60.000"
                PriceRange.HIGH -> "Más de 60.000"
            }

        val UserLevel.label: String
            get() = when (this) {
                UserLevel.TOURIST -> "Turista"
                UserLevel.EXPLORER -> "Explorador"
                UserLevel.ADVENTURER -> "Aventurero"
                UserLevel.LOCAL_AMBASSADOR -> "Embajador Local"
            }
    }
}
@Serializable
private class ExportFile(
    @SerialName("generado") val generatedAt: String,
    @SerialName("cuenta") val account: ExportAccount,
    @SerialName("reputacion") val reputation: ExportReputation,
    @SerialName("publicaciones") val publications: List<ExportPublication>,
    @SerialName("comentarios") val comments: List<ExportComment>,
    @SerialName("votos") val votes: List<String>,
    @SerialName("visitados") val visits: List<ExportVisit>,
)
@Serializable
private class ExportAccount(
    @SerialName("correo") val email: String,
    @SerialName("nombre") val name: String,
    @SerialName("comoSePresenta") val residency: String,
    @SerialName("ciudad") val city: String,
    @SerialName("miembroDesde") val memberSince: String,
    @SerialName("sobreMi") val bio: String? = null,
    @SerialName("foto") val photo: String? = null,
)
@Serializable
private class ExportReputation(
    @SerialName("puntos") val points: Int,
    @SerialName("nivel") val level: String,
    @SerialName("insignias") val badges: List<ExportBadge>,
)
@Serializable
private class ExportBadge(
    @SerialName("nombre") val name: String,
    @SerialName("avance") val progress: Int,
    @SerialName("meta") val target: Int,
    @SerialName("desbloqueada") val unlocked: Boolean,
)
@Serializable
private class ExportPublication(
    val id: String,
    @SerialName("titulo") val title: String,
    @SerialName("categoria") val category: String,
    @SerialName("estado") val status: String,
    @SerialName("descripcion") val description: String,
    @SerialName("latitud") val latitude: Double,
    @SerialName("longitud") val longitude: Double,
    @SerialName("horario") val hours: ExportHours?,
    @SerialName("precio") val price: String?,
    @SerialName("fotos") val photos: List<String>,
    @SerialName("enviada") val submittedAt: String,
    @SerialName("votos") val votes: Int,
    @SerialName("comentarios") val comments: Int,
    @SerialName("posibleDuplicado") val possibleDuplicate: Boolean,
)
@Serializable
private class ExportHours(
    @SerialName("dias") val days: List<String>,
    @SerialName("abre") val opens: String,
    @SerialName("cierra") val closes: String,
)
@Serializable
private class ExportComment(
    @SerialName("lugar") val place: String,
    @SerialName("texto") val text: String,
    @SerialName("fecha") val createdAt: String,
)
@Serializable
private class ExportVisit(
    @SerialName("lugar") val place: String,
    @SerialName("loRecomienda") val recommends: Boolean?,
    @SerialName("experiencia") val experience: String?,
    @SerialName("conMiNombre") val showName: Boolean,
)
