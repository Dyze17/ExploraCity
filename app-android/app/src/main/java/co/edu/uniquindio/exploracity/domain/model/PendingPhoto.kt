package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant

/** v1.4.0 · Por qué no se publica una foto agregada a un lugar; los mismos de la API. */
enum class PhotoRejectionReason { NOT_THE_PLACE, INAPPROPRIATE, LOW_QUALITY }

/** v1.4.0 · El lugar al que va una foto agregada, con las fotos que ya tiene para comparar. */
data class PendingPhotoPlace(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    /** null si la cuenta que lo publicó se eliminó. */
    val authorName: String?,
    val photoUrls: List<String>,
)

/** v1.4.0 · «Fotos por revisar» · Una foto que alguien agregó a un lugar público y espera a un moderador (D1). */
data class PendingPhoto(
    val id: String,
    val url: String,
    val submittedAt: Instant,
    /** null si la cuenta que la agregó se eliminó. */
    val contributor: Author?,
    val place: PendingPhotoPlace,
)

/** v1.4.0 · El lugar ya tiene 5 fotos (quien lo publicó lo editó mientras la foto esperaba): solo se puede rechazar. */
class PlaceFullException : Exception("El lugar ya tiene 5 fotos")
