package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PhotoRules
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.Rejection
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/** Las publicaciones de la persona de la sesión (SAD: API de Publicaciones y Feed). */
interface PublicationRepository {
    /** 22 · Todas, de la más reciente a la más antigua. Lanza excepción si falla la red. */
    suspend fun myPublications(): List<OwnPublication>

    /** 24 · Una publicación propia; null si ya no existe. Lanza excepción si falla la red. */
    suspend fun publication(id: String): OwnPublication?

    /** 22–24 · La borra con sus fotos, comentarios y votos, sin vuelta atrás. Lanza excepción si falla la red. */
    suspend fun delete(id: String)

    /**
     * 23 · Guarda título, categoría y descripción. La publicación vuelve a verificación: una verificada deja de verse en
     * el feed hasta que un moderador la apruebe. Devuelve cómo quedó. Lanza excepción si falla la red.
     */
    suspend fun update(id: String, changes: PublicationChanges): OwnPublication

    /**
     * 19 → 20 · Envía la publicación a verificación, o reenvía una rechazada ([PublicationSubmission.resubmitId]). Van
     * las fotos ya subidas (al menos una); las demás se agregan después con [addPhoto]. Lanza excepción si falla la red.
     */
    suspend fun submit(submission: PublicationSubmission): SubmitResult

    /** 19 · Una foto que terminó de subir después del envío (se reintentan en segundo plano). */
    suspend fun addPhoto(publicationId: String, photoUrl: String)
}

/**
 * Temporal hasta que exista la API: las publicaciones de Ana. Las públicas son sus lugares del servidor de lugares
 * ([pois]), con votos y comentarios al día; las pendientes y rechazadas, las de sampleHiddenPublications. Borrar una
 * pública la quita también del feed.
 */
class FakePublicationRepository(
    private val pois: FakePoiRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val currentUser: Author = sampleCurrentUser,
    private val latency: Duration = 700.milliseconds,
    private val actionLatency: Duration = 300.milliseconds,
) : PublicationRepository {

    private val hidden = MutableStateFlow(sampleHiddenPublications(clock.instant()))

    /** Estados de las que el feed no muestra: el perfil (26) las cuenta junto a las públicas. */
    val hiddenStatuses: List<PublicationStatus> get() = hidden.value.map { it.publication.status }

    /** Las pendientes, sin la latencia: la búsqueda de parecidos (17) también las compara, y la cola de moderación (32). */
    internal fun pendingOnes(): List<OwnPublication> =
        hidden.value.map { it.publication }.filter { it.status == PublicationStatus.PENDING }

    /**
     * Temporal: un moderador verificó una pendiente de la persona (34). Sale de las ocultas y entra al feed como suya,
     * con los puntos de verificarla. Con la API esto lo hace el backend. Devuelve null si ya no estaba pendiente.
     */
    internal fun markVerified(id: String): OwnPublication? {
        val pending = pendingOnes().firstOrNull { it.id == id } ?: return null
        hidden.update { list -> list.filterNot { it.publication.id == id } }
        pois.publish(pending.toPublicDetails(currentUser))
        verifiedNow[id] = clock.instant()
        return pending.copy(status = PublicationStatus.VERIFIED, pointsEarned = VERIFIED_POINTS)
    }

    /**
     * Temporal: un moderador rechazó una pendiente de la persona (35). Queda rechazada con el motivo, como la ve en 24;
     * con duplicado, [duplicateOfId] es el original. Devuelve cómo quedó, o null si ya no estaba pendiente.
     */
    internal fun markRejected(id: String, rejection: Rejection, duplicateOfId: String?): OwnPublication? {
        val pending = pendingOnes().firstOrNull { it.id == id } ?: return null
        val rejected = pending.copy(status = PublicationStatus.REJECTED, rejection = rejection)
        hidden.update { list -> list.filterNot { it.publication.id == id } + PublicationSeed(rejected, duplicateOfId) }
        return all().first { it.id == id }
    }

    /** Las rechazadas de la persona, con el original si fue por duplicado («Resueltas»). */
    internal fun rejectedOnes(): List<OwnPublication> = all().filter { it.status == PublicationStatus.REJECTED }

    /**
     * Temporal: un moderador la devolvió a pendiente (36). Sale del feed y vuelve a la cola como recién enviada, sin
     * votos ni comentarios. Devuelve null si no es una pública de la persona.
     */
    internal fun markReopened(id: String): OwnPublication? {
        val public = publicOnes().firstOrNull { it.id == id } ?: return null
        pois.remove(id)
        verifiedNow -= id
        val pending = public.copy(status = PublicationStatus.PENDING, submittedAt = clock.instant(), votes = 0, comments = 0, pointsEarned = 0)
        hidden.update { list -> list.filterNot { it.publication.id == id } + PublicationSeed(pending) }
        return pending
    }

    /** Historial con la moderación (33): las públicas (verificadas o finalizadas) y las rechazadas. */
    internal fun publicCount(): Int = publicOnes().size

    internal fun rejectedCount(): Int = hidden.value.count { it.publication.status == PublicationStatus.REJECTED }

    // Cuándo se verificó cada una en esta sesión: 22 la ordena por esa fecha y suma sus puntos.
    private val verifiedNow = mutableMapOf<String, Instant>()

    override suspend fun myPublications(): List<OwnPublication> {
        delay(latency)
        return all().sortedByDescending { it.submittedAt }
    }

    override suspend fun publication(id: String): OwnPublication? {
        delay(latency)
        return all().firstOrNull { it.id == id }
    }

    override suspend fun delete(id: String) {
        delay(actionLatency)
        when {
            hidden.value.any { it.publication.id == id } -> hidden.update { list -> list.filterNot { it.publication.id == id } }
            publicOnes().any { it.id == id } -> pois.remove(id)
            else -> error("Publicación desconocida: $id")
        }
    }

    override suspend fun update(id: String, changes: PublicationChanges): OwnPublication {
        delay(actionLatency)
        val clean = changes.trimmed()
        require(clean.isValid) { "Hay campos fuera de los límites" }
        require(clean.photos.all { it.uploaded }) { "Las fotos nuevas deben terminar de subir antes de guardar" }
        val hiddenOne = hidden.value.firstOrNull { it.publication.id == id }?.publication
        val updated = if (hiddenOne != null) {
            check(hiddenOne.status == PublicationStatus.PENDING) { "Solo se editan las pendientes y las verificadas: $id" }
            hiddenOne.applying(clean)
        } else {
            val public = publicOnes().firstOrNull { it.id == id } ?: error("Publicación desconocida: $id")
            check(public.status == PublicationStatus.VERIFIED) { "Solo se editan las pendientes y las verificadas: $id" }
            // Vuelve a verificación: sale del feed y se envía de nuevo ahora.
            pois.remove(id)
            public.applying(clean).copy(
                status = PublicationStatus.PENDING,
                submittedAt = clock.instant(),
                votes = 0,
                comments = 0,
            )
        }
        hidden.update { list -> list.filterNot { it.publication.id == id } + PublicationSeed(updated) }
        return updated
    }

    override suspend fun submit(submission: PublicationSubmission): SubmitResult {
        delay(actionLatency)
        require(submission.photos.count { it.uploaded } >= PhotoRules.MIN) { "Hace falta al menos una foto subida" }
        val resubmitId = submission.resubmitId
        if (resubmitId != null) {
            val rejected = hidden.value.firstOrNull { it.publication.id == resubmitId }?.publication
            check(rejected?.rejection?.canResubmit == true) { "No se puede reenviar: $resubmitId" }
        }
        val first = all().isEmpty()
        val id = resubmitId ?: "${submission.title.toSlug()}-${clock.millis()}"
        val publication = OwnPublication(
            id = id,
            title = submission.title,
            category = submission.category,
            status = PublicationStatus.PENDING,
            location = submission.location,
            photos = submission.photos.mapNotNull { photo -> photo.remoteUrl?.let { PublishedPhoto(photo.id, it) } },
            submittedAt = clock.instant(),
            description = submission.description,
            hours = submission.hours,
            price = submission.price,
            possibleDuplicate = submission.possibleDuplicate,
            similarIds = submission.duplicateCheck?.similarIds.orEmpty(),
            duplicateNote = submission.duplicateCheck?.note?.trim()?.ifEmpty { null },
        )
        // Reenviar reemplaza a la rechazada: vuelve a pendiente, sin el motivo.
        hidden.update { list -> list.filterNot { it.publication.id == id } + PublicationSeed(publication) }
        return SubmitResult(id, firstPublicationPoints = if (first) FIRST_PUBLICATION_POINTS else null)
    }

    override suspend fun addPhoto(publicationId: String, photoUrl: String) {
        delay(actionLatency)
        hidden.update { list ->
            list.map { seed ->
                if (seed.publication.id != publicationId) {
                    seed
                } else {
                    val photo = PublishedPhoto(photoUrl.substringAfterLast('/').substringBeforeLast('.'), photoUrl)
                    PublicationSeed(seed.publication.copy(photos = seed.publication.photos + photo), seed.duplicateOfId)
                }
            }
        }
    }

    private fun all(): List<OwnPublication> {
        val feed = pois.places()
        val hiddenOnes = hidden.value.map { seed ->
            val publication = seed.publication
            val original = seed.duplicateOfId?.let { originalId -> feed.firstOrNull { it.id == originalId } }
            if (original == null) publication else publication.copy(rejection = publication.rejection?.copy(duplicateOf = original))
        }
        return publicOnes(feed) + hiddenOnes
    }

    private fun publicOnes(feed: List<Poi> = pois.places()): List<OwnPublication> =
        feed.filter { pois.detailsOf(it).author?.id == currentUser.id }.map { poi ->
            val details = pois.detailsOf(poi)
            val submission = samplePublicSubmissions[poi.id]
            val verifiedAt = verifiedNow[poi.id]
            OwnPublication(
                id = poi.id,
                title = poi.title,
                category = poi.category,
                status = poi.status,
                location = poi.location,
                photos = samplePublishedPhotos(poi.id, details.photos.size),
                hours = details.hours,
                price = poi.price,
                submittedAt = verifiedAt ?: (clock.instant() - (submission?.submittedAgo ?: Duration.ZERO).toJavaDuration()),
                description = details.description,
                photoUrl = poi.photoUrl,
                votes = poi.votes,
                comments = poi.comments,
                pointsEarned = submission?.pointsEarned ?: if (verifiedAt != null) VERIFIED_POINTS else 0,
            )
        }
}

/**
 * Ver, listar, editar o borrar publicaciones propias necesita red: sin ella se avisa sin intentar. Nada de esto se
 * encola: borrar es destructivo y deliberado, como reportar un perfil (31A), y una edición en cola podría chocar con la
 * revisión del moderador.
 */
class OnlineOnlyPublicationRepository(
    private val remote: PublicationRepository,
    private val connectivity: ConnectivityObserver,
) : PublicationRepository {
    override suspend fun myPublications(): List<OwnPublication> {
        requireOnline()
        return remote.myPublications()
    }

    override suspend fun publication(id: String): OwnPublication? {
        requireOnline()
        return remote.publication(id)
    }

    override suspend fun delete(id: String) {
        requireOnline()
        remote.delete(id)
    }

    override suspend fun update(id: String, changes: PublicationChanges): OwnPublication {
        requireOnline()
        return remote.update(id, changes)
    }

    override suspend fun submit(submission: PublicationSubmission): SubmitResult {
        requireOnline()
        return remote.submit(submission)
    }

    override suspend fun addPhoto(publicationId: String, photoUrl: String) {
        requireOnline()
        remote.addPhoto(publicationId, photoUrl)
    }

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }
}

/**
 * 23 · La publicación con los cambios guardados. Si se movió el pin, la marca de posible duplicado es la de la
 * búsqueda del lugar nuevo (17A/17B); si no, se conserva la que tenía.
 */
private fun OwnPublication.applying(changes: PublicationChanges): OwnPublication = copy(
    title = changes.title,
    category = changes.category,
    description = changes.description,
    location = changes.location,
    hours = changes.openingHours,
    price = changes.price,
    photos = changes.photos.mapNotNull { photo -> photo.remoteUrl?.let { PublishedPhoto(photo.id, it) } },
    possibleDuplicate = if (changes.location == location) possibleDuplicate else changes.duplicateCheck?.possibleDuplicate == true,
    similarIds = if (changes.location == location) similarIds else changes.duplicateCheck?.similarIds.orEmpty(),
    duplicateNote = if (changes.location == location) duplicateNote else changes.duplicateCheck?.note?.trim()?.ifEmpty { null },
)

/** README · «primera publicación +20 (insignia)». */
private const val FIRST_PUBLICATION_POINTS = 20

/** README · «publicación verificada +15». */
internal const val VERIFIED_POINTS = 15

/** La pendiente ya verificada como lugar del feed, con el detalle que verá la comunidad (13). */
internal fun OwnPublication.toPublicDetails(author: Author): PoiDetails = PoiDetails(
    poi = Poi(
        id = id,
        title = title,
        category = category,
        status = PublicationStatus.VERIFIED,
        location = location,
        distanceMeters = 0,
        votes = 0,
        comments = 0,
        photoUrl = photos.firstOrNull()?.url,
        price = price,
        summary = description.substringBefore('.').take(80),
    ),
    description = description,
    photos = photos.mapIndexed { i, _ -> PoiPhoto(url = null, description = "Foto ${i + 1} de $title") },
    address = "Bogotá",
    hours = hours,
    author = author,
    voted = false,
    visited = false,
)

private val diacriticMarks = Regex("\\p{Mn}+")
private val nonSlug = Regex("[^a-z0-9]+")

/** «Café Las Acacias» → «cafe-las-acacias», como los ids de los lugares de prueba. */
private fun String.toSlug(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(diacriticMarks, "").replace(nonSlug, "-").trim('-')

