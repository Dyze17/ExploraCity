package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import java.io.IOException

/**
 * Se comporta como OfflinePoiRepository sin Room: sin red lanza OfflineException y lo guardado es [saved] y
 * [savedDetails]. Con [serverDown] hay red pero el servidor no responde al feed.
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

    override suspend fun setVote(id: String, voted: Boolean): Int {
        requireOnline()
        return delegate.setVote(id, voted)
    }

    override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
        requireOnline()
        return delegate.markVisited(id, experience)
    }

    override suspend fun comments(poiId: String, cursor: String?, pageSize: Int): CommentsPage? {
        requireOnline()
        return delegate.comments(poiId, cursor, pageSize)
    }

    override suspend fun addComment(poiId: String, text: String): Comment {
        requireOnline()
        return delegate.addComment(poiId, text)
    }

    private fun requireOnline() {
        if (!connectivity.online) throw OfflineException()
    }
}
