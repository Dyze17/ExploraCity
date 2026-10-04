package co.edu.uniquindio.exploracity.model

import kotlinx.serialization.Serializable

// Cuerpos de Publicar y Moderar (docs/api).

/** 19 · Una foto subida: [url] es la que se manda al enviar o editar. */
@Serializable
data class PhotoResponse(val id: String, val url: String)

/** 16 · Lo escrito en el paso 1. */
@Serializable
data class SuggestionRequest(val title: String, val description: String)

/** Sin [category], el modelo no vio una clara: se elige a mano. */
@Serializable
data class SuggestionResponse(val category: Category? = null)

/** 17A · Un lugar parecido al que se publica, a [distanceMeters] del pin. */
@Serializable
data class SimilarPlaceResponse(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPoint,
    val distanceMeters: Int,
    val photo: String? = null,
)

/**
 * 17 · La búsqueda de parecidos que se hizo en el teléfono: dónde estaba el pin, los parecidos que la persona dijo que
 * son otro lugar (17B) y su nota. Con [failed] la búsqueda no respondió y la API la repite.
 */
@Serializable
data class DuplicateCheckRequest(
    val location: GeoPoint,
    val similarIds: List<String> = emptyList(),
    val note: String? = null,
    val failed: Boolean = false,
)

/**
 * 20 · Lo que se envía a verificación. [photos] son las direcciones de las fotos ya subidas, en orden (la primera es la
 * portada). Con [resubmitId] reenvía una rechazada. [clientId] lo pone la app: un reenvío de la cola no la duplica.
 */
@Serializable
data class SubmissionRequest(
    val clientId: String? = null,
    val title: String,
    val description: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val location: GeoPoint,
    val address: String? = null,
    val hours: HoursResponse? = null,
    val price: PriceRange? = null,
    val photos: List<String>,
    val duplicateCheck: DuplicateCheckRequest? = null,
    val resubmitId: String? = null,
)

/** 20 · [firstPublicationPoints] solo con la primera publicación de la persona (D1). */
@Serializable
data class SubmitResponse(val publicationId: String, val firstPublicationPoints: Int? = null)

/** 23 · Lo que se puede cambiar de una publicación pendiente o verificada. */
@Serializable
data class ChangesRequest(
    val title: String,
    val category: Category,
    val description: String,
    val location: GeoPoint,
    val address: String? = null,
    val hours: HoursResponse? = null,
    val price: PriceRange? = null,
    val photos: List<String>,
    val duplicateCheck: DuplicateCheckRequest? = null,
)

/** 19 · Una foto que terminó de subir después del envío. */
@Serializable
data class PhotoUrlRequest(val url: String)

/** 24.a · Qué corregir; el texto es del servidor (E1: sale del motivo). */
@Serializable
data class FixResponse(val kind: FixKind, val text: String)

/** 24 · El rechazo como lo ve quien publicó. Con DUPLICATE, [duplicateOf] es el lugar que ya existía. */
@Serializable
data class RejectionResponse(
    val reason: RejectionReason,
    val message: String,
    val reviewerName: String,
    val rejectedAt: String,
    val canResubmit: Boolean,
    val fixes: List<FixResponse> = emptyList(),
    val duplicateOf: PlaceSummaryResponse? = null,
)

/** 22–24 · Una publicación propia en cualquier estado. */
@Serializable
data class PublicationResponse(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPoint,
    val photos: List<PhotoResponse>,
    val submittedAt: String,
    val description: String,
    val photo: String? = null,
    val hours: HoursResponse? = null,
    val price: PriceRange? = null,
    val votes: Int,
    val comments: Int,
    val pointsEarned: Int,
    val possibleDuplicate: Boolean,
    val similarIds: List<String> = emptyList(),
    val duplicateNote: String? = null,
    val rejection: RejectionResponse? = null,
)

/** 7.c · Cuántas esperan y desde hace cuántos días la más antigua. */
@Serializable
data class ModerationSummaryResponse(val pending: Int, val oldestWaitingDays: Int)

/** 33 · Quién publicó y su historial con la moderación. */
@Serializable
data class ReviewAuthorResponse(val author: AuthorResponse, val verified: Int, val rejected: Int)

/** 33A · Un lugar publicado al que se parece la pendiente, con lo que se compara lado a lado. */
@Serializable
data class CandidateResponse(
    val place: PlaceSummaryResponse,
    val distanceMeters: Int,
    val author: AuthorResponse? = null,
    val publishedAt: String? = null,
    val photoCount: Int,
)

@Serializable
data class SuspicionResponse(val candidates: List<CandidateResponse>, val authorNote: String? = null)

/** 33 · Una pendiente con todo lo que hace falta para decidir. */
@Serializable
data class ReviewItemResponse(
    val id: String,
    val title: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val description: String,
    val photos: List<PhotoResponse>,
    val hours: HoursResponse? = null,
    val price: PriceRange? = null,
    val address: String? = null,
    val location: GeoPoint,
    val submittedAt: String,
    val author: ReviewAuthorResponse,
    val reportReason: String? = null,
    val duplicate: SuspicionResponse? = null,
)

@Serializable
data class ReviewQueueResponse(val items: List<ReviewItemResponse>)

/** 34 · La nota interna, que solo ven los moderadores. */
@Serializable
data class VerifyRequest(val note: String? = null)

/** 35 · Sin [message], el mensaje es el del motivo. Con DUPLICATE, [originalId] es obligatorio. */
@Serializable
data class RejectRequest(
    val reason: RejectionReason,
    val message: String = "",
    val canResubmit: Boolean = true,
    val originalId: String? = null,
)

@Serializable
data class FinalizeRequest(val reason: FinalizeReason)

/** 36 · El motivo queda como nota interna. */
@Serializable
data class ReopenRequest(val reason: String)

/** 37 · Lo que decidió este moderador desde la medianoche de la ciudad. */
@Serializable
data class ModerationWorkResponse(val verified: Int, val rejected: Int, val finalized: Int)

/** «Resueltas» · Lo ya decidido. Sin [authorName], la cuenta que lo publicó se eliminó. */
@Serializable
data class ResolvedResponse(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val authorName: String? = null,
    val decidedAt: String,
    val decidedBy: String,
    val note: String? = null,
    val rejectionReason: RejectionReason? = null,
    val finalizeReason: FinalizeReason? = null,
    val votes: Int,
    val comments: Int,
)
