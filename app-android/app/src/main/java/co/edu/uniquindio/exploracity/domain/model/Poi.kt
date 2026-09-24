package co.edu.uniquindio.exploracity.domain.model

/** Punto de interés tal como se muestra en el feed público (solo verificados y finalizados). */
data class Poi(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val distanceMeters: Int,
    val votes: Int,
    val comments: Int,
    val photoUrl: String? = null,
)
