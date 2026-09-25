package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import co.edu.uniquindio.exploracity.domain.model.VoteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

/**
 * Se comporta como OfflinePoiRepository sin Room: sin red, lo guardado es [saved] y [savedDetails], votar y marcar la
 * visita quedan en la cola y los comentarios esperan en [pendingComments] hasta [sendQueued]. Con [serverDown] hay red
 * pero el servidor no responde al feed.
 */
class FakeOfflineRepository(
    private val connectivity: FakeConnectivity,
    var saved: SavedPlaces? = null,
    var savedDetails: Map<String, PoiDetails> = emptyMap(),
    private val delegate: FakePoiRepository = FakePoiRepository(),
) : PoiRepository by delegate {
    var serverDown = false
    var feedCalls = 0
        private set

    private val queuedComments = MutableStateFlow<List<Pair<String, Comment>>>(emptyList())
    private var nextPendingId = 0

    override suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int): FeedPage {
        requireOnline()
        feedCalls++
        if (serverDown) throw IOException("el servidor no responde")
        return delegate.feedPage(query, page, pageSize)
    }

    override suspend fun savedPlaces(): SavedPlaces? = saved

    override suspend fun poiDetails(id: String): PoiDetails? =
        if (connectivity.online) delegate.poiDetails(id) else savedDetails[id] ?: throw OfflineException()

    override suspend fun count(query: FeedQuery): Int {
        requireOnline()
        return delegate.count(query)
    }

    override suspend fun mapArea(query: FeedQuery, bounds: GeoBounds, limit: Int): MapArea {
        requireOnline()
        return delegate.mapArea(query, bounds, limit)
    }

    override suspend fun setVote(id: String, voted: Boolean): VoteResult {
        if (connectivity.online) return delegate.setVote(id, voted)
        val details = savedDetails[id] ?: throw OfflineException()
        return VoteResult(details.poi.votes + if (voted) 1 else -1, queued = true)
    }

    override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
        if (connectivity.online) return delegate.markVisited(id, experience)
        if (id !in savedDetails) throw OfflineException()
        return VisitResult(pointsAwarded = 0, queued = true)
    }

    override suspend fun comments(poiId: String, cursor: String?, pageSize: Int): CommentsPage? {
        requireOnline()
        return delegate.comments(poiId, cursor, pageSize)
    }

    override suspend fun addComment(poiId: String, text: String): Comment {
        if (connectivity.online) return delegate.addComment(poiId, text)
        val comment = Comment("pending-${++nextPendingId}", sampleCurrentUser, text, Instant.now(), mine = true, pending = true)
        queuedComments.value += poiId to comment
        return comment
    }

    override fun pendingComments(poiId: String): Flow<List<Comment>> =
        queuedComments.map { queued -> queued.filter { it.first == poiId }.map { it.second }.reversed() }

    /** Como el worker de WorkManager: publica los comentarios pendientes y vacía la cola. */
    suspend fun sendQueued() {
        queuedComments.value.forEach { (poiId, comment) -> delegate.addComment(poiId, comment.text) }
        queuedComments.value = emptyList()
    }

    private fun requireOnline() {
        if (!connectivity.online) throw OfflineException()
    }
}
