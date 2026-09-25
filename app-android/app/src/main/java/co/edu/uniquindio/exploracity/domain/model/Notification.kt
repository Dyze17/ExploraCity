package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant

/**
 * 25 · Aviso dentro de la app (SAD: sin notificaciones push). Cada tipo lleva lo que su texto necesita; la pantalla
 * arma la frase y decide el destino.
 */
sealed interface Notification {
    val id: String
    val createdAt: Instant
    val read: Boolean

    /** «Quinta de Bolívar quedó verificada» → 13. [points] los decide el servidor; 0 si no hay. */
    data class Verified(
        override val id: String,
        override val createdAt: Instant,
        override val read: Boolean,
        val poiId: String,
        val poiTitle: String,
        val points: Int,
    ) : Notification

    /** «… pasó a finalizada» → 13. */
    data class Finalized(
        override val id: String,
        override val createdAt: Instant,
        override val read: Boolean,
        val poiId: String,
        val poiTitle: String,
    ) : Notification

    /** «Laura G. comentó en …: «…»» → 14. */
    data class Commented(
        override val id: String,
        override val createdAt: Instant,
        override val read: Boolean,
        val poiId: String,
        val poiTitle: String,
        val authorName: String,
        val excerpt: String,
    ) : Notification

    /** «… fue rechazada: [reason]» → 24. */
    data class Rejected(
        override val id: String,
        override val createdAt: Instant,
        override val read: Boolean,
        val publicationId: String,
        val title: String,
        val reason: String,
    ) : Notification

    /** Rechazo por duplicado: «Tu publicación «…» ya existía como «…»» → 24; «Ir al lugar existente» → 13. */
    data class DuplicateRejected(
        override val id: String,
        override val createdAt: Instant,
        override val read: Boolean,
        val publicationId: String,
        val title: String,
        val existingPoiId: String,
        val existingTitle: String,
    ) : Notification

    /** «Nuevo logro: 3 lugares verificados. Te faltan 7 para la insignia de …» → 27. */
    data class Achievement(
        override val id: String,
        override val createdAt: Instant,
        override val read: Boolean,
        val achievement: String,
        val nextBadge: String?,
        val remaining: Int?,
    ) : Notification
}

fun Notification.markedRead(): Notification = when (this) {
    is Notification.Verified -> copy(read = true)
    is Notification.Finalized -> copy(read = true)
    is Notification.Commented -> copy(read = true)
    is Notification.Rejected -> copy(read = true)
    is Notification.DuplicateRejected -> copy(read = true)
    is Notification.Achievement -> copy(read = true)
}
