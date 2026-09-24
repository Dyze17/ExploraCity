package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Poi

/** Tamaño de página del feed (README: paginación de 20). */
const val FEED_PAGE_SIZE = 20

/** Criterios del feed: categorías (vacío = todas) y texto de búsqueda. */
data class FeedQuery(
    val categories: Set<Category> = emptySet(),
    val text: String = "",
) {
    val hasCriteria: Boolean get() = categories.isNotEmpty() || text.isNotBlank()
}

data class FeedPage(val items: List<Poi>, val total: Int, val hasMore: Boolean)

interface PoiRepository {
    /** Página [page] (desde 0) del feed público, ordenado por cercanía. Lanza excepción si falla la red. */
    suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int = FEED_PAGE_SIZE): FeedPage
}
