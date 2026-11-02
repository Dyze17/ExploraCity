package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.PendingPhotos
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** v1.4.0 · Una foto agregada que espera revisión: de quién es y a qué lugar va. */
data class PendingPhotoRow(
    val photoId: UUID,
    val url: String,
    val publicId: String,
    val ownerId: UUID,
    val placeId: UUID,
    val submittedAt: Instant,
)

/** v1.4.0 · Las fotos que la comunidad agrega a un lugar público (D1). Corre en la transacción de quien llama. */
class PendingPhotoRepository {

    fun add(placeId: UUID, photoIds: List<UUID>, at: Instant) {
        PendingPhotos.batchInsert(photoIds) { id ->
            this[PendingPhotos.photoId] = id
            this[PendingPhotos.placeId] = placeId
            this[PendingPhotos.createdAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /** Las que esperan en [placeId], de la más antigua a la más reciente. */
    fun of(placeId: UUID): List<PendingPhotoRow> = source().selectAll().where { PendingPhotos.placeId eq placeId }.orderBy(PendingPhotos.createdAt, SortOrder.ASC).map(::row)

    /** Las de [placeIds] en una sola consulta (la eliminación de varios lugares). */
    fun of(placeIds: Collection<UUID>): List<PendingPhotoRow> =
        if (placeIds.isEmpty()) emptyList() else source().selectAll().where { PendingPhotos.placeId inList placeIds }.map(::row)

    /** Una que espera; con [lock], bloqueada hasta el final de la transacción. */
    fun find(photoId: UUID, lock: Boolean = false): PendingPhotoRow? = source().selectAll()
        .where { PendingPhotos.photoId eq photoId }
        .let { if (lock) it.forUpdate(ForUpdateOption.ForUpdate) else it }
        .map(::row)
        .singleOrNull()

    /** Cuáles de [photoIds] esperan revisión: no se pueden usar en una publicación. */
    fun pendingAmong(photoIds: Collection<UUID>): Set<UUID> = if (photoIds.isEmpty()) {
        emptySet()
    } else {
        PendingPhotos.select(PendingPhotos.photoId).where { PendingPhotos.photoId inList photoIds }.map { it[PendingPhotos.photoId] }.toSet()
    }

    /**
     * «Fotos por revisar» · Las que esperan en lugares públicos, de la más antigua a la más reciente, sin las que subió
     * [excludeOwner] (F1). Las de un lugar que volvió a pendiente esperan a que vuelva a ser público.
     */
    fun inReview(excludeOwner: UUID): List<PendingPhotoRow> = source()
        .join(Places, JoinType.INNER, PendingPhotos.placeId, Places.id)
        .selectAll()
        .where { (Places.status inList PUBLIC) and (Photos.ownerId neq excludeOwner) }
        .orderBy(PendingPhotos.createdAt to SortOrder.ASC, PendingPhotos.photoId to SortOrder.ASC)
        .map(::row)

    fun remove(photoId: UUID) {
        PendingPhotos.deleteWhere { PendingPhotos.photoId eq photoId }
    }

    private fun source() = PendingPhotos.join(Photos, JoinType.INNER, PendingPhotos.photoId, Photos.id)

    private fun row(it: ResultRow) = PendingPhotoRow(
        photoId = it[PendingPhotos.photoId],
        url = it[Photos.url],
        publicId = it[Photos.publicId],
        ownerId = it[Photos.ownerId],
        placeId = it[PendingPhotos.placeId],
        submittedAt = it[PendingPhotos.createdAt].toInstant(),
    )

    private companion object {
        val PUBLIC = listOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED)
    }
}
