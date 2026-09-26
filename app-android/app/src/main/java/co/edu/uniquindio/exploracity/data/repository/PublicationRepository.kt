package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
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
        feed.filter { sampleDetails(it).author.id == currentUser.id }.map { poi ->
            val details = sampleDetails(poi)
            val submission = samplePublicSubmissions[poi.id]
            OwnPublication(
                id = poi.id,
                title = poi.title,
                category = poi.category,
                status = poi.status,
                location = poi.location,
                photos = details.photos.size,
                submittedAt = clock.instant() - (submission?.submittedAgo ?: Duration.ZERO).toJavaDuration(),
                photoUrl = poi.photoUrl,
                votes = poi.votes,
                comments = poi.comments,
                pointsEarned = submission?.pointsEarned ?: 0,
            )
        }
}

/**
 * Ver, listar o borrar publicaciones propias necesita red: sin ella se avisa sin intentar. Borrar no se encola: es una
 * acción destructiva y deliberada, como reportar un perfil (31A).
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

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }
}
