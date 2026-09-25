package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.PendingActionEntity
import co.edu.uniquindio.exploracity.data.local.PendingActionsDao
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.data.local.QueuedComment
import co.edu.uniquindio.exploracity.data.local.QueuedVote
import co.edu.uniquindio.exploracity.data.local.SavedPlacesDao
import co.edu.uniquindio.exploracity.data.local.payloadAs
import co.edu.uniquindio.exploracity.data.local.toQueued
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.data.sync.PendingScheduler
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import co.edu.uniquindio.exploracity.domain.model.VoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Clock
import java.time.Instant

/**
 * Envuelve al repositorio del servidor ([remote]) y guarda en Room lo necesario para ver sin conexión (12.a): lo último
 * que cargó el feed, hasta [SAVED_PLACES_LIMIT] lugares, y en segundo plano el detalle de cada uno (opción A de Daniel).
 *
 * Sin red, el detalle sale de lo guardado y el feed y el mapa piden [savedPlaces]. Votar, marcar la visita y comentar
 * quedan en la cola de envío ([pending]) y [scheduler] pide a WorkManager enviarla cuando vuelva la red; lo demás que
 * solo puede hacer el servidor (buscar, cargar comentarios…) lanza [OfflineException] sin intentarlo.
 */
class OfflinePoiRepository(
    private val remote: PoiRepository,
    private val dao: SavedPlacesDao,
    private val pending: PendingActionsDao,
    private val scheduler: PendingScheduler,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope,
    /** Autor de los comentarios que esperan en la cola. */
    private val currentUser: Author,
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
                saveDetails(withPending(details))
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
        }?.let { withPending(it) }
        details?.let { saveDetails(it) }
        return details
    }

    /**
     * Lo que sigue en la cola se ve aunque el servidor todavía no lo tenga. Al volver la red, el detalle se recarga antes
     * de que WorkManager envíe la cola: sin esto, el voto «desaparecía» unos segundos (visto en el S20+).
     */
    private suspend fun withPending(details: PoiDetails): PoiDetails {
        val id = details.poi.id
        val vote = pending.find(id, PendingType.VOTE).lastOrNull()?.payloadAs<QueuedVote>()?.voted
        var result = details
        if (vote != null && vote != details.voted) {
            result = result.copy(voted = vote, poi = result.poi.copy(votes = result.poi.votes + if (vote) 1 else -1))
        }
        if (pending.find(id, PendingType.VISIT).isNotEmpty()) result = result.copy(visited = true)
        return result
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

    // Votar, visitar y comentar dejan al día lo guardado, para que sin red se vea lo que la persona hizo. Sin red, en
    // vez de fallar quedan en la cola de envío.

    override suspend fun setVote(id: String, voted: Boolean): VoteResult {
        if (!connectivity.isOnline.value) return queueVote(id, voted)
        val result = remote.setVote(id, voted)
        ignoringStorageErrors { dao.saveVote(id, voted, result.votes) }
        return result
    }

    /** Quitar un voto que aún no se envió lo cancela: el servidor nunca se enteró de él. */
    private suspend fun queueVote(id: String, voted: Boolean): VoteResult {
        val place = dao.place(id) ?: throw OfflineException()
        val votes = place.votes + if (voted) 1 else -1
        val waiting = pending.find(id, PendingType.VOTE)
        if (waiting.isEmpty()) enqueue(PendingType.VOTE, id, Json.encodeToString(QueuedVote(voted))) else waiting.forEach { pending.delete(it.id) }
        dao.saveVote(id, voted, votes)
        return VoteResult(votes, queued = waiting.isEmpty())
    }

    override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
        if (!connectivity.isOnline.value) {
            if (dao.place(id) == null) throw OfflineException()
            enqueue(PendingType.VISIT, id, Json.encodeToString(experience.toQueued()))
            ignoringStorageErrors { dao.markVisited(id) }
            return VisitResult(pointsAwarded = 0, queued = true)
        }
        val result = remote.markVisited(id, experience)
        ignoringStorageErrors { dao.markVisited(id) }
        return result
    }

    override suspend fun comments(poiId: String, cursor: String?, pageSize: Int): CommentsPage? {
        requireOnline()
        return remote.comments(poiId, cursor, pageSize)
    }

    override suspend fun addComment(poiId: String, text: String): Comment {
        if (!connectivity.isOnline.value) return enqueue(PendingType.COMMENT, poiId, Json.encodeToString(QueuedComment(text))).toPendingComment()
        val comment = remote.addComment(poiId, text)
        ignoringStorageErrors { dao.addComment(poiId) }
        return comment
    }

    override fun pendingComments(poiId: String): Flow<List<Comment>> =
        pending.comments(poiId).map { actions -> actions.map { it.toPendingComment() } }

    private suspend fun enqueue(type: PendingType, poiId: String, payload: String): PendingActionEntity {
        val action = PendingActionEntity(type = type, poiId = poiId, payload = payload, createdAtMillis = clock.millis())
        val id = pending.insert(action)
        scheduler.schedule()
        return action.copy(id = id)
    }

    private fun PendingActionEntity.toPendingComment() = Comment(
        id = "pending-$id",
        author = currentUser,
        text = payloadAs<QueuedComment>().text,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        mine = true,
        pending = true,
    )

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
