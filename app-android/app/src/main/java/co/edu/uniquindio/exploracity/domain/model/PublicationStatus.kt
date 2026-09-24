package co.edu.uniquindio.exploracity.domain.model

/** Ciclo de una publicación: PENDING → VERIFIED / REJECTED → FINALIZED; al editarla vuelve a PENDING. */
enum class PublicationStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    FINALIZED,
}
