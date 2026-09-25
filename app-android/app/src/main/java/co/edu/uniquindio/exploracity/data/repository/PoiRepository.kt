package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import co.edu.uniquindio.exploracity.domain.model.VoteResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Tamaño de página del feed (README: paginación de 20). */
const val FEED_PAGE_SIZE = 20

/** Criterios del feed: filtros de la hoja 9 y texto de búsqueda. */
data class FeedQuery(
    val filters: FeedFilters = FeedFilters.DEFAULT,
    val text: String = "",
) {
    val hasCriteria: Boolean get() = !filters.isDefault || text.isNotBlank()
}

/** Máximo de marcadores en el mapa (README 8: «hasta 200 marcadores visibles con agrupamiento»). */
const val MAP_MARKER_LIMIT = 200

data class FeedPage(val items: List<Poi>, val total: Int, val hasMore: Boolean)

/** Área visible del mapa: [items] son los más cercanos (como máximo el límite) y [total], todos los del área. */
data class MapArea(val items: List<Poi>, val total: Int)

/** Máximo de lugares guardados para ver sin conexión (opción A de Daniel: lo último que cargó el feed). */
const val SAVED_PLACES_LIMIT = 60

/**
 * Lo guardado para ver sin conexión (12.a): los lugares en el orden del feed, cuándo se guardaron y cuáles tienen
 * también su detalle (los demás no alcanzaron a descargarlo).
 */
data class SavedPlaces(val items: List<Poi>, val savedAt: Instant, val withDetails: Set<String>)

/** Tamaño de página de 14 · Comentarios: el diseño no la fija; se usa la del feed. */
const val COMMENTS_PAGE_SIZE = 20

/**
 * Comentarios del más reciente al más antiguo. [poiTitle] y [total] son para la barra («Café Las Acacias · 12»);
 * [nextCursor] pide la página siguiente, null si no hay más.
 */
data class CommentsPage(val poiTitle: String, val items: List<Comment>, val total: Int, val nextCursor: String?)

interface PoiRepository {
    /** Página [page] (desde 0) del feed público, ordenado por cercanía. Lanza excepción si falla la red. */
    suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int = FEED_PAGE_SIZE): FeedPage

    /** Cuántos lugares cumplen [query]: el conteo en vivo del botón de la hoja 9. Lanza excepción si falla la red. */
    suspend fun count(query: FeedQuery): Int

    /** Lugares de [query] dentro de [bounds], para los marcadores del mapa (8). Lanza excepción si falla la red. */
    suspend fun mapArea(query: FeedQuery, bounds: GeoBounds, limit: Int = MAP_MARKER_LIMIT): MapArea

    /**
     * 13 · Detalle; null si el lugar ya no existe. Sin red devuelve el guardado ([PoiDetails.savedAt]) o lanza
     * OfflineException si no hay. Lanza excepción si falla la red.
     */
    suspend fun poiDetails(id: String): PoiDetails?

    /** 12.a · Lo guardado para ver sin conexión; null si no hay nada. */
    suspend fun savedPlaces(): SavedPlaces?

    /**
     * Voto «Es importante» de la persona; devuelve el total resultante. Sin red queda en la cola de envío
     * ([VoteResult.queued]). Lanza excepción si falla la red.
     */
    suspend fun setVote(id: String, voted: Boolean): VoteResult

    /** 14.b · Marca el lugar como visitado con la experiencia (opcional). Sin red queda en la cola de envío. */
    suspend fun markVisited(id: String, experience: VisitExperience): VisitResult

    /**
     * 14 · Comentarios de [poiId] después de [cursor] (null: los más recientes); null si el lugar ya no existe.
     * Con cursor y no con número de página: los comentarios nuevos entran arriba y correrían las páginas.
     * Lanza excepción si falla la red.
     */
    suspend fun comments(poiId: String, cursor: String? = null, pageSize: Int = COMMENTS_PAGE_SIZE): CommentsPage?

    /**
     * 14 · Publica el comentario de la persona. Comentar no da puntos (Daniel, 24/09/2026). Sin red queda en la cola de
     * envío y vuelve con [Comment.pending]. Lanza excepción si falla la red.
     */
    suspend fun addComment(poiId: String, text: String): Comment

    /** 14 · Comentarios de la persona en [poiId] que esperan la red; cambia a medida que se envían. */
    fun pendingComments(poiId: String): Flow<List<Comment>>
}
