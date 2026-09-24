package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.Poi

/** Tamaño de página del feed (README: paginación de 20). */
const val FEED_PAGE_SIZE = 20

/** Criterios del feed: filtros de la hoja 9 y texto de búsqueda. */
data class FeedQuery(
    val filters: FeedFilters = FeedFilters.DEFAULT,
    val text: String = "",
) {
    val hasCriteria: Boolean get() = !filters.isDefault || text.isNotBlank()
}

data class FeedPage(val items: List<Poi>, val total: Int, val hasMore: Boolean)

interface PoiRepository {
    /** Página [page] (desde 0) del feed público, ordenado por cercanía. Lanza excepción si falla la red. */
    suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int = FEED_PAGE_SIZE): FeedPage

    /** Cuántos lugares cumplen [query]: el conteo en vivo del botón de la hoja 9. Lanza excepción si falla la red. */
    suspend fun count(query: FeedQuery): Int
}
