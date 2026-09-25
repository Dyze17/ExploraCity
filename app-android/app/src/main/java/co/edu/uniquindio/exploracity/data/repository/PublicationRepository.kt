package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Las publicaciones de la persona de la sesión (SAD: API de Publicaciones y Feed). 22 agregará la lista. */
interface PublicationRepository {
    /** 24 · Una publicación propia; null si ya no existe. Lanza excepción si falla la red. */
    suspend fun publication(id: String): OwnPublication?

    /** 23 y 24 · La borra con sus fotos, sin vuelta atrás. Lanza excepción si falla la red. */
    suspend fun delete(id: String)
}

/**
 * Temporal hasta que exista la API: las pendientes y rechazadas de Ana. El lugar original de un duplicado se toma del
 * servidor de lugares ([pois]) para que sus votos estén al día.
 */
class FakePublicationRepository(
    private val pois: PoiRepository,
    clock: Clock = Clock.systemUTC(),
    private val latency: Duration = 700.milliseconds,
    private val actionLatency: Duration = 300.milliseconds,
) : PublicationRepository {

    private val hidden = MutableStateFlow(sampleHiddenPublications(clock.instant()))

    /** Estados de las que el feed no muestra: el perfil (26) las cuenta junto a las públicas. */
    val hiddenStatuses: List<PublicationStatus> get() = hidden.value.map { it.publication.status }

    override suspend fun publication(id: String): OwnPublication? {
        delay(latency)
        val seed = hidden.value.firstOrNull { it.publication.id == id } ?: return null
        val publication = seed.publication
        val originalId = seed.duplicateOfId ?: return publication
        val original = pois.feedPage(FeedQuery(), 0, pageSize = Int.MAX_VALUE).items.firstOrNull { it.id == originalId }
        return publication.copy(rejection = publication.rejection?.copy(duplicateOf = original))
    }

    override suspend fun delete(id: String) {
        delay(actionLatency)
        check(hidden.value.any { it.publication.id == id }) { "Publicación desconocida: $id" }
        hidden.update { list -> list.filterNot { it.publication.id == id } }
    }
}

/**
 * Ver o borrar una publicación propia necesita red: sin ella se avisa sin intentar. Borrar no se encola: es una acción
 * destructiva y deliberada, como reportar un perfil (31A).
 */
class OnlineOnlyPublicationRepository(
    private val remote: PublicationRepository,
    private val connectivity: ConnectivityObserver,
) : PublicationRepository {
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
