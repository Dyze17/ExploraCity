package co.edu.uniquindio.exploracity.model

import kotlinx.serialization.Serializable

// Cuerpos de Explorar, Social, Avisos y Perfil público (docs/api).

/**
 * Un lugar público (verificado o finalizado) tal como lo muestran el feed (7), el mapa (8) y el perfil público (31).
 * [distanceMeters] se mide desde el punto `near` de la petición o, sin él, desde el centro de la ciudad.
 */
@Serializable
data class PlaceSummaryResponse(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPoint,
    val distanceMeters: Int,
    val votes: Int,
    val comments: Int,
    /** La portada: la primera foto. */
    val photo: String? = null,
    val price: PriceRange? = null,
    /** Según el horario y la hora de la ciudad; sin horario no viene. */
    val openNow: Boolean? = null,
    /** La frase de la tarjeta del mapa: la primera oración de la descripción. */
    val summary: String? = null,
)

@Serializable
data class FeedPageResponse(val items: List<PlaceSummaryResponse>, val total: Int, val hasMore: Boolean)

@Serializable
data class CountResponse(val count: Int)

/** v1.3.0 · Una ciudad con lugares públicos, para elegirla a mano (7): cuántos tiene y su centro (el promedio). */
@Serializable
data class TownResponse(val name: String, val region: String? = null, val places: Int, val center: GeoPoint)

@Serializable
data class TownsResponse(val items: List<TownResponse>)

/** 8 · Los más cercanos del área visible (como máximo el límite) y cuántos hay en total. */
@Serializable
data class MapAreaResponse(val items: List<PlaceSummaryResponse>, val total: Int)

/** 18 · Días de atención (MONDAY…SUNDAY) y la franja, en hora local («08:00»). */
@Serializable
data class HoursResponse(val days: List<String>, val opens: String, val closes: String)

/** 13 · El detalle. Sin [author], la cuenta que lo publicó se eliminó («Usuario eliminado»). */
@Serializable
data class PlaceDetailsResponse(
    val place: PlaceSummaryResponse,
    val description: String,
    val photos: List<String>,
    val address: String? = null,
    val hours: HoursResponse? = null,
    val author: AuthorResponse? = null,
    val voted: Boolean,
    val visited: Boolean,
)

@Serializable
data class VoteResponse(val votes: Int)

/** 14.b · Todo opcional salvo la marca de visitado. */
@Serializable
data class VisitRequest(val recommends: Boolean? = null, val text: String? = null, val showName: Boolean = false)

/** Los puntos que ganó con esta visita: 5 la primera vez en un lugar de otra persona, si no 0. */
@Serializable
data class VisitResponse(val pointsAwarded: Int)

/** 14 · Un comentario. Sin [author], la cuenta que lo escribió se eliminó («Usuario eliminado»). */
@Serializable
data class CommentResponse(
    val id: String,
    val author: AuthorResponse? = null,
    val text: String,
    val createdAt: String,
    val mine: Boolean,
)

/** Del más reciente al más antiguo; [nextCursor] pide la página siguiente y no viene en la última. */
@Serializable
data class CommentsPageResponse(
    val placeTitle: String,
    val items: List<CommentResponse>,
    val total: Int,
    val nextCursor: String? = null,
)

/** [clientId] lo pone la app: si la cola sin conexión lo reenvía, no se duplica. */
@Serializable
data class CommentRequest(val text: String, val clientId: String? = null)

/**
 * 25 · Un aviso. Cada [type] trae los campos que su frase necesita; los demás no vienen. En COMMENTED, sin
 * [authorName] la cuenta que comentó se eliminó.
 */
@Serializable
data class NotificationResponse(
    val id: String,
    val type: NotificationType,
    val createdAt: String,
    val read: Boolean,
    val placeId: String? = null,
    val placeTitle: String? = null,
    val points: Int? = null,
    val reason: String? = null,
    val authorName: String? = null,
    val excerpt: String? = null,
    val existingPlaceId: String? = null,
    val existingTitle: String? = null,
    val achievement: String? = null,
    val nextBadge: String? = null,
    val remaining: Int? = null,
    /** v1.2.0 · Los códigos de lo de arriba, para que la app lo diga en su idioma: el texto queda para la app de antes. */
    val achievementId: String? = null,
    val nextBadgeId: String? = null,
    val rejectionReason: RejectionReason? = null,
    val finalizeReason: FinalizeReason? = null,
)

@Serializable
data class NotificationsResponse(val items: List<NotificationResponse>, val unread: Int)

/** 31 · Lo público de una persona: sin correo, y de sus lugares solo los verificados y finalizados. */
@Serializable
data class PublicProfileResponse(
    val author: AuthorResponse,
    val residency: Residency,
    val city: String,
    val bio: String? = null,
    val photo: String? = null,
    val places: List<PlaceSummaryResponse>,
    /** Insignias desbloqueadas. */
    val badges: Int,
)
