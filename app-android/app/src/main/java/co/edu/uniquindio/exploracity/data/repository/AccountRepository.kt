package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.DataExport
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserLevel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** La cuenta de la sesión (SAD: componente de Usuarios): el correo y los datos personales (Ley 1581). */
interface AccountRepository {
    /** Llega con el inicio de sesión y se guarda con la sesión: no necesita red. */
    fun account(): Account

    /** 29 · «Descargar mis datos»: el servidor arma el archivo. Lanza excepción si falla la red. */
    suspend fun exportData(): DataExport
}

/**
 * Temporal hasta que exista la API: arma el archivo con el perfil ([users]), las publicaciones ([publications]) y lo
 * que la persona hizo en el feed ([pois]). Las claves y los valores van en español: el archivo es para leerlo ella.
 */
class FakeAccountRepository(
    private val pois: FakePoiRepository,
    private val publications: FakePublicationRepository,
    private val users: UserRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val account: Account = sampleAccount,
    private val latency: Duration = 900.milliseconds,
) : AccountRepository {

    override fun account(): Account = account

    override suspend fun exportData(): DataExport {
        delay(latency)
        val profile = users.ownProfile()
        val activity = pois.activity()
        val export = ExportFile(
            generatedAt = clock.instant().toString(),
            account = ExportAccount(
                email = account.email,
                name = profile.author.name,
                residency = if (profile.residency == Residency.RESIDENT) "Residente" else "Turista",
                city = profile.city,
                memberSince = profile.memberSince.toString(),
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

/** El correo es de la sesión; el archivo necesita al servidor. */
class OnlineOnlyAccountRepository(
    private val remote: AccountRepository,
    private val connectivity: ConnectivityObserver,
) : AccountRepository {
    override fun account(): Account = remote.account()

    override suspend fun exportData(): DataExport {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.exportData()
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
