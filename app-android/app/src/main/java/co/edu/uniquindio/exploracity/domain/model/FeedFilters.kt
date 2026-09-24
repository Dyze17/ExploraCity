package co.edu.uniquindio.exploracity.domain.model

/** Alcance de ubicación del feed (9): alrededor de la persona o toda la ciudad. */
enum class LocationScope {
    NEARBY,
    CITY,
}

/** Filtros de la hoja 9, compartidos por la lista (7) y el mapa (8). «Limpiar» vuelve a [DEFAULT]. */
data class FeedFilters(
    val categories: Set<Category> = emptySet(),
    val scope: LocationScope = LocationScope.CITY,
    val verifiedOnly: Boolean = false,
) {
    /** Filtros distintos del valor inicial; cada categoría cuenta por separado. */
    val activeCount: Int
        get() = categories.size + (if (scope != DEFAULT.scope) 1 else 0) + (if (verifiedOnly) 1 else 0)

    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        /**
         * Valor inicial: todas las categorías, toda la ciudad y todos los lugares públicos.
         * «Cercanos» no puede venir activado: necesita el permiso de ubicación, que el diseño pide al aplicar.
         */
        val DEFAULT = FeedFilters()

        /** Radio de «Cercanos» (README 9). */
        const val NEARBY_RADIUS_METERS = 5_000
    }
}
