package co.edu.uniquindio.exploracity.domain.model

/** Punto de interés tal como se muestra en el feed público (solo verificados y finalizados). */
data class Poi(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPoint,
    /** Distancia desde la persona; la calcula quien entrega el lugar (hoy el repositorio, luego la API). */
    val distanceMeters: Int,
    val votes: Int,
    val comments: Int,
    val photoUrl: String? = null,
    /** Rango del paso 4 (18); null si no se indicó precio. */
    val price: PriceRange? = null,
    /** Según el horario publicado; null si no hay horario. Lo calcula la API. */
    val openNow: Boolean? = null,
    /** Frase corta para la tarjeta del mapa. */
    val summary: String? = null,
)
