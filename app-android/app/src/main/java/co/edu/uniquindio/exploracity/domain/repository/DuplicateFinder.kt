package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
/** 17 · Busca lugares que podrían ser el mismo que se está publicando (SAD: Componente de Detección de Duplicados). */
interface DuplicateFinder {
    /**
     * Lugares a menos de 50 m de [location] con un nombre parecido a [title], del más cercano al más lejano (como máximo
     * 3), sin contar [excludeId]: la publicación que se está editando o reenviando no es parecida a sí misma. Lanza
     * excepción si falla la red.
     */
    suspend fun similarPlaces(title: String, location: GeoPoint, excludeId: String? = null): List<SimilarPlace>
}
