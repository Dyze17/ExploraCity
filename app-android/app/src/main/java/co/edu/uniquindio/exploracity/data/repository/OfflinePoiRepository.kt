package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.SavedPlacesDao
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.time.Clock
import java.time.Instant

/**
 * Envuelve al repositorio del servidor ([remote]) y guarda en Room lo necesario para ver sin conexión (12.a): lo último
 * que cargó el feed, hasta [SAVED_PLACES_LIMIT] lugares, y en segundo plano el detalle de cada uno (opción A de Daniel).
 *
 * Sin red, el detalle sale de lo guardado y el feed y el mapa piden [savedPlaces]; lo que solo puede hacer el servidor
 * (buscar, votar, comentar…) lanza [OfflineException] sin intentarlo.
 */
class OfflinePoiRepository(
    private val remote: PoiRepository,
    private val dao: SavedPlacesDao,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : PoiRepository {

    /** Descargas de detalles de lo guardado ahora; se cancelan cuando el feed guarda otra cosa. */
    private var downloads: CoroutineScope = newDownloads()
    private val parallelDownloads = Semaphore(PARALLEL_DOWNLOADS)

    override suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int): FeedPage {
        requireOnline()
        val result = remote.feedPage(query, page, pageSize)
        // Una búsqueda por texto es de paso: no reemplaza lo guardado. Una página vacía tampoco.
        if (query.text.isBlank() && result.items.isNotEmpty()) save(result.items, replace = page == 0)
        return result
    }

    private suspend fun save(items: List<Poi>, replace: Boolean) = ignoringStorageErrors {
        val saved = if (replace) {
            downloads.cancel()
            downloads = newDownloads()
            val entities = items.take(SAVED_PLACES_LIMIT).mapIndexed { i, poi -> poi.toEntity(i) }
            dao.replaceFeed(entities, clock.millis())
            entities
        } else {
            dao.appendFeed(items.map { it.toEntity(position = 0) }, SAVED_PLACES_LIMIT)
        }
        saved.forEach { download(it.id) }
    }

    private fun newDownloads() = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

    /** Detalle de un lugar guardado, de a pocos a la vez para no saturar la red. */
    private fun download(id: String) {
        downloads.launch {
            parallelDownloads.withPermit {
                if (!connectivity.isOnline.value) return@withPermit
                val details = try {
                    remote.poiDetails(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@withPermit
                saveDetails(details)
            }
        }
    }

    private suspend fun saveDetails(details: PoiDetails) = ignoringStorageErrors {
        dao.saveDetails(details.toEntity(clock.instant()), details.poi.votes, details.poi.comments)
    }

    override suspend fun savedPlaces(): SavedPlaces? {
        val places = dao.places().takeIf { it.isNotEmpty() } ?: return null
        val savedAt = dao.savedAt() ?: return null
        return SavedPlaces(places.map { it.toDomain() }, Instant.ofEpochMilli(savedAt), dao.idsWithDetails().toSet())
    }

    override suspend fun poiDetails(id: String): PoiDetails? {
        if (!connectivity.isOnline.value) return saved(id) ?: throw OfflineException()
        val details = try {
            remote.poiDetails(id)
        } catch (e: IOException) {
            // Hay red pero el servidor no respondió: mejor lo guardado que un error.
            return saved(id) ?: throw e
        }
        details?.let { saveDetails(it) }
        return details
    }

    private suspend fun saved(id: String): PoiDetails? {
        val place = dao.place(id) ?: return null
        return dao.details(id)?.toDomain(place)
    }

    override suspend fun count(query: FeedQuery): Int {
        requireOnline()
        return remote.count(query)
    }

    override suspend fun mapArea(query: FeedQuery, bounds: GeoBounds, limit: Int): MapArea {
        requireOnline()
        return remote.mapArea(query, bounds, limit)
    }

    // Votar, visitar y comentar dejan al día lo guardado, para que sin red se vea lo que la persona hizo.

    override suspend fun setVote(id: String, voted: Boolean): Int {
        requireOnline()
        val votes = remote.setVote(id, voted)
        ignoringStorageErrors { dao.saveVote(id, voted, votes) }
        return votes
    }

    override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
        requireOnline()
        val result = remote.markVisited(id, experience)
        ignoringStorageErrors { dao.markVisited(id) }
        return result
    }

    override suspend fun comments(poiId: String, cursor: String?, pageSize: Int): CommentsPage? {
        requireOnline()
        return remote.comments(poiId, cursor, pageSize)
    }

    override suspend fun addComment(poiId: String, text: String): Comment {
        requireOnline()
        val comment = remote.addComment(poiId, text)
        ignoringStorageErrors { dao.addComment(poiId) }
        return comment
    }

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }

    /** Guardar para después nunca debe tumbar lo que la persona está viendo ahora. */
    private suspend fun ignoringStorageErrors(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin caché no pasa nada grave: sin red, simplemente habrá menos guardado.
        }
    }

    companion object {
        const val PARALLEL_DOWNLOADS = 4
    }
}
