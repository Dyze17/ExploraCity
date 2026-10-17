package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.Category

/**
 * 16 · Sugerencia de categoría a partir del título y la descripción. La hace el backend (SAD: Componente de
 * Clasificación IA con OpenRouter); la clave del modelo nunca está en la app.
 */
interface CategorySuggester {
    /** La categoría más probable, o null si no hay una clara. Lanza excepción si falla la red. */
    suspend fun suggest(title: String, description: String): Category?
}
