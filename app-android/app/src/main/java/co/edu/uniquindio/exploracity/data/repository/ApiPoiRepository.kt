package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.remote.PoiApi
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import co.edu.uniquindio.exploracity.domain.model.VoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Explorar y Social con la API (SAD: componentes de Puntos de Interés y Social). La distancia y el orden los calcula la
 * API desde la ubicación de [location]; si no hay, desde el centro de la ciudad. Lo de ver y enviar sin conexión lo
 * hace OfflinePoiRepository, que envuelve a este.
 */
class ApiPoiRepository(private val api: PoiApi, private val location: LocationProvider) : PoiRepository {

    override suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int): FeedPage = api.feed(query, near(), page, pageSize)

    override suspend fun count(query: FeedQuery): Int = api.count(query, near())

    override suspend fun mapArea(query: FeedQuery, bounds: GeoBounds, limit: Int): MapArea = api.mapArea(query, near(), bounds, limit)

    override suspend fun poiDetails(id: String): PoiDetails? = api.details(id, near())

    /** La API no guarda nada para ver sin conexión: eso lo hace OfflinePoiRepository en el teléfono. */
    override suspend fun savedPlaces(): SavedPlaces? = null

    override suspend fun setVote(id: String, voted: Boolean): VoteResult = VoteResult(api.setVote(id, voted))

    /** Los puntos los decide la API (G2): 5 la primera vez en un lugar de otra persona, si no 0. */
    override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult = VisitResult(api.visit(id, experience))

    override suspend fun comments(poiId: String, cursor: String?, pageSize: Int): CommentsPage? = api.comments(poiId, cursor, pageSize)

    override suspend fun addComment(poiId: String, text: String, clientId: String): Comment = api.addComment(poiId, text, clientId)

    /** La API no tiene cola: lo pendiente vive en el teléfono (OfflinePoiRepository). */
    override fun pendingComments(poiId: String): Flow<List<Comment>> = flowOf(emptyList())

    /** Sin ubicación se busca igual: la API mide desde el centro de la ciudad. */
    private suspend fun near(): GeoPoint? = try {
        location.currentLocation()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
