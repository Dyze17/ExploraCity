package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.CategoryOrigin
import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.DecisionAction
import co.edu.uniquindio.exploracity.model.FinalizeReason
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.ModerationDecisions
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.PlaceSimilar
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PriceRange
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.RejectionReason
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.model.Votes
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToInt

/** Lo que se escribe de un lugar al enviarlo o editarlo (15–19 y 23). */
data class PlaceFields(
    val title: String,
    val description: String,
    val category: Category,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val price: PriceRange?,
    /** Días de atención como bits: lunes = 1 … domingo = 64; null sin horario. */
    val hoursDays: Int?,
    val opens: LocalTime?,
    val closes: LocalTime?,
)

/** Un lugar en cualquier estado, como lo ven su autor (22–24) y la moderación (33). */
data class OwnPlaceRow(
    val id: UUID,
    val authorId: UUID?,
    val fields: PlaceFields,
    val categoryOrigin: CategoryOrigin,
    val status: PublicationStatus,
    val possibleDuplicate: Boolean,
    val duplicateNote: String?,
    val pointsEarned: Int,
    val firstPublication: Boolean,
    val submittedAt: Instant,
)

data class StoredPhoto(val id: UUID, val url: String, val publicId: String, val placeId: UUID?)

/** Una decisión de moderación, con el nombre de quien la tomó (null si su cuenta ya no existe). */
data class DecisionRow(
    val placeId: UUID,
    val moderatorName: String?,
    val action: DecisionAction,
    val note: String?,
    val rejectionReason: RejectionReason?,
    val rejectionMessage: String?,
    val canResubmit: Boolean?,
    val duplicateOf: UUID?,
    val finalizeReason: FinalizeReason?,
    val createdAt: Instant,
)

/** 17A · Un lugar parecido al que se publica. */
data class SimilarRow(
    val id: UUID,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val cover: String?,
)

/**
 * Publicaciones (SAD: API de Publicaciones): lo que la persona envía, sus fotos, sus parecidos y lo que decidió la
 * moderación. Corre dentro de la transacción de quien llama.
 */
class PublicationRepository {

    fun addPhoto(ownerId: UUID, url: String, publicId: String, at: Instant): UUID = Photos.insert {
        it[Photos.ownerId] = ownerId
        it[Photos.url] = url
        it[Photos.publicId] = publicId
        it[createdAt] = at.atOffset(ZoneOffset.UTC)
    }[Photos.id]

    /** Las fotos de [ownerId] con esas direcciones; las que no son suyas no vienen. */
    fun ownPhotos(ownerId: UUID, urls: Collection<String>): List<StoredPhoto> {
        if (urls.isEmpty()) return emptyList()
        return Photos.selectAll().where { (Photos.ownerId eq ownerId) and (Photos.url inList urls) }.map { it.toPhoto() }
    }

    fun photosOf(placeId: UUID): List<StoredPhoto> =
        Photos.selectAll().where { Photos.placeId eq placeId }.orderBy(Photos.position).map { it.toPhoto() }

    /** Las fotos de [placeId] pasan a ser [photoIds], en ese orden: la primera es la portada. */
    fun assignPhotos(placeId: UUID, photoIds: List<UUID>) {
        // Primero sin lugar, para que ninguna posición choque con la del orden nuevo.
        Photos.update({ Photos.placeId eq placeId }) {
            it[Photos.placeId] = null
            it[position] = null
        }
        photoIds.forEachIndexed { i, id ->
            Photos.update({ Photos.id eq id }) {
                it[Photos.placeId] = placeId
                it[position] = i.toShort()
            }
        }
    }

    fun deletePhotos(ids: Collection<UUID>) {
        if (ids.isNotEmpty()) Photos.deleteWhere { Photos.id inList ids }
    }

    /** Las fotos que subió y nunca quedaron en una publicación. */
    fun unattachedPhotos(ownerId: UUID): List<StoredPhoto> =
        Photos.selectAll().where { (Photos.ownerId eq ownerId) and Photos.placeId.isNull() }.map { it.toPhoto() }

    fun countByAuthor(authorId: UUID): Int = Places.selectAll().where { Places.authorId eq authorId }.count().toInt()

    /** La publicación que ya llegó con [clientId]: un reenvío de la cola. */
    fun byClientId(authorId: UUID, clientId: UUID): UUID? = Places.select(Places.id)
        .where { (Places.authorId eq authorId) and (Places.clientId eq clientId) }
        .singleOrNull()
        ?.get(Places.id)

    fun insert(
        authorId: UUID,
        clientId: UUID?,
        fields: PlaceFields,
        categoryOrigin: CategoryOrigin,
        possibleDuplicate: Boolean,
        duplicateNote: String?,
        pointsEarned: Int,
        firstPublication: Boolean,
        at: Instant,
    ): UUID = Places.insert {
        it[Places.authorId] = authorId
        it[Places.clientId] = clientId
        it[Places.categoryOrigin] = categoryOrigin
        it[Places.possibleDuplicate] = possibleDuplicate
        it[Places.duplicateNote] = duplicateNote
        it[Places.pointsEarned] = pointsEarned
        it[Places.firstPublication] = firstPublication
        it[status] = PublicationStatus.PENDING
        it[submittedAt] = at.atOffset(ZoneOffset.UTC)
        it[createdAt] = at.atOffset(ZoneOffset.UTC)
        write(it, fields)
    }[Places.id]

    /** Cambia lo editable; con [resubmittedAt] vuelve a la cola como recién enviada. */
    fun update(
        id: UUID,
        fields: PlaceFields,
        status: PublicationStatus,
        possibleDuplicate: Boolean,
        duplicateNote: String?,
        resubmittedAt: Instant?,
        categoryOrigin: CategoryOrigin? = null,
        clientId: UUID? = null,
    ) {
        Places.update({ Places.id eq id }) {
            write(it, fields)
            it[Places.status] = status
            it[Places.possibleDuplicate] = possibleDuplicate
            it[Places.duplicateNote] = duplicateNote
            categoryOrigin?.let { origin -> it[Places.categoryOrigin] = origin }
            clientId?.let { client -> it[Places.clientId] = client }
            resubmittedAt?.let { at -> it[submittedAt] = at.atOffset(ZoneOffset.UTC) }
        }
    }

    fun setStatus(id: UUID, status: PublicationStatus, resubmittedAt: Instant? = null) {
        Places.update({ Places.id eq id }) {
            it[Places.status] = status
            resubmittedAt?.let { at -> it[submittedAt] = at.atOffset(ZoneOffset.UTC) }
        }
    }

    fun setPoints(id: UUID, points: Int, firstPublication: Boolean) {
        Places.update({ Places.id eq id }) {
            it[pointsEarned] = points
            it[Places.firstPublication] = firstPublication
        }
    }

    /** Una publicación en cualquier estado; con [lock], bloqueada hasta el final de la transacción. */
    fun find(id: UUID, lock: Boolean = false): OwnPlaceRow? = Places.selectAll()
        .where { Places.id eq id }
        .let { if (lock) it.forUpdate(ForUpdateOption.ForUpdate) else it }
        .singleOrNull()
        ?.toOwnPlace()

    /** 22 · Las de [authorId], de la más reciente a la más antigua. */
    fun byAuthor(authorId: UUID): List<OwnPlaceRow> =
        Places.selectAll().where { Places.authorId eq authorId }.orderBy(Places.submittedAt, SortOrder.DESC).map { it.toOwnPlace() }

    /**
     * 32 · Las pendientes de la más antigua a la más reciente, sin las de [excludeAuthor] (F1) ni las que se quedaron sin
     * autor: no hay a quién avisar ni quién corrija.
     */
    fun pending(excludeAuthor: UUID): List<OwnPlaceRow> = Places.selectAll()
        .where { (Places.status eq PublicationStatus.PENDING) and Places.authorId.isNotNull() }
        .orderBy(Places.submittedAt to SortOrder.ASC, Places.id to SortOrder.ASC)
        .map { it.toOwnPlace() }
        .filter { it.authorId != excludeAuthor }

    fun delete(id: UUID) {
        Places.deleteWhere { Places.id eq id }
    }

    /** 17B · Los parecidos que el autor dijo que son otro lugar. */
    fun setSimilar(placeId: UUID, similarIds: Collection<UUID>) {
        PlaceSimilar.deleteWhere { PlaceSimilar.placeId eq placeId }
        PlaceSimilar.batchInsert(similarIds.filter { it != placeId }.distinct()) { similar ->
            this[PlaceSimilar.placeId] = placeId
            this[PlaceSimilar.similarId] = similar
        }
    }

    fun similarIds(placeId: UUID): List<UUID> =
        PlaceSimilar.select(PlaceSimilar.similarId).where { PlaceSimilar.placeId eq placeId }.map { it[PlaceSimilar.similarId] }

    fun votes(placeId: UUID): Int = Votes.selectAll().where { Votes.placeId eq placeId }.count().toInt()

    fun comments(placeId: UUID): Int = Comments.selectAll().where { Comments.placeId eq placeId }.count().toInt()

    /** La última decisión de moderación sobre [placeId]. */
    fun latestDecision(placeId: UUID): DecisionRow? = decisions()
        .where { ModerationDecisions.placeId eq placeId }
        .orderBy(ModerationDecisions.createdAt to SortOrder.DESC, ModerationDecisions.id to SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.toDecision()

    /** ¿Algún moderador la verificó alguna vez? Los +15 se ganan una sola vez. */
    fun everVerified(placeId: UUID): Boolean = ModerationDecisions.selectAll()
        .where { (ModerationDecisions.placeId eq placeId) and (ModerationDecisions.action eq DecisionAction.VERIFIED) }
        .count() > 0

    /** 33A · Cuándo quedó publicado: la primera vez que un moderador lo verificó. */
    fun firstVerifiedAt(placeId: UUID): Instant? = ModerationDecisions.select(ModerationDecisions.createdAt)
        .where { (ModerationDecisions.placeId eq placeId) and (ModerationDecisions.action eq DecisionAction.VERIFIED) }
        .orderBy(ModerationDecisions.createdAt to SortOrder.ASC)
        .limit(1)
        .singleOrNull()
        ?.get(ModerationDecisions.createdAt)
        ?.toInstant()

    /**
     * 17 · Lugares a [radiusMeters] o menos de [at] con un título parecido (pg_trgm, sin mayúsculas ni tildes), del más
     * cercano al más lejano: los públicos y los pendientes de [viewer], nunca los pendientes de otras personas.
     */
    fun similar(title: String, at: GeoPoint, viewer: UUID, excludeId: UUID?, radiusMeters: Double, limit: Int): List<SimilarRow> {
        val sql = Sql()
        sql.append("SELECT p.id, p.title, p.category, p.status, p.latitude, p.longitude, ST_Distance(p.location, ").point(at)
        sql.append(") AS distance, (SELECT ph.url FROM photos ph WHERE ph.place_id = p.id ORDER BY ph.position LIMIT 1) AS cover ")
        sql.append("FROM places p WHERE (p.status IN ('VERIFIED', 'FINALIZED') OR (p.status = 'PENDING' AND p.author_id = ")
        sql.param(UUIDColumnType(), viewer).append(")) AND ST_DWithin(p.location, ").point(at).append(", ")
        sql.param(DoubleColumnType(), radiusMeters).append(") AND similarity(search_text(p.title), search_text(")
        sql.param(TextColumnType(), title.trim()).append(")) >= ").param(DoubleColumnType(), TITLE_SIMILARITY)
        excludeId?.let { sql.append(" AND p.id <> ").param(UUIDColumnType(), it) }
        sql.append(" ORDER BY distance, p.id LIMIT ").param(IntegerColumnType(), limit)
        return sql.query { rs ->
            SimilarRow(
                id = rs.getObject("id", UUID::class.java),
                title = rs.getString("title"),
                category = Category.valueOf(rs.getString("category")),
                status = PublicationStatus.valueOf(rs.getString("status")),
                latitude = rs.getDouble("latitude"),
                longitude = rs.getDouble("longitude"),
                distanceMeters = rs.getDouble("distance").roundToInt(),
                cover = rs.getString("cover"),
            )
        }
    }

    /** 33 · Historial del autor con la moderación: cuántas públicas (verificadas o finalizadas) y cuántas rechazadas. */
    fun authorHistory(authorId: UUID): Pair<Int, Int> {
        val count = Places.id.count()
        val byStatus = Places.select(Places.status, count)
            .where { Places.authorId eq authorId }
            .groupBy(Places.status)
            .associate { it[Places.status] to it[count].toInt() }
        val public = (byStatus[PublicationStatus.VERIFIED] ?: 0) + (byStatus[PublicationStatus.FINALIZED] ?: 0)
        return public to (byStatus[PublicationStatus.REJECTED] ?: 0)
    }

    /** El nombre de una cuenta; null si ya no existe. */
    fun userName(userId: UUID?): String? = userId?.let { id ->
        Users.select(Users.name).where { Users.id eq id }.singleOrNull()?.get(Users.name)
    }

    private fun decisions(): Query = ModerationDecisions
        .join(Users, JoinType.LEFT, ModerationDecisions.moderatorId, Users.id)
        .selectAll()

    private fun write(it: UpdateBuilder<*>, fields: PlaceFields) {
        it[Places.title] = fields.title
        it[Places.description] = fields.description
        it[Places.category] = fields.category
        it[Places.latitude] = fields.latitude
        it[Places.longitude] = fields.longitude
        it[Places.address] = fields.address
        it[Places.price] = fields.price
        it[Places.hoursDays] = fields.hoursDays?.toShort()
        it[Places.hoursOpens] = fields.opens
        it[Places.hoursCloses] = fields.closes
    }

    private fun ResultRow.toPhoto() = StoredPhoto(this[Photos.id], this[Photos.url], this[Photos.publicId], this[Photos.placeId])

    private fun ResultRow.toOwnPlace() = OwnPlaceRow(
        id = this[Places.id],
        authorId = this[Places.authorId],
        fields = PlaceFields(
            title = this[Places.title],
            description = this[Places.description],
            category = this[Places.category],
            latitude = this[Places.latitude],
            longitude = this[Places.longitude],
            address = this[Places.address],
            price = this[Places.price],
            hoursDays = this[Places.hoursDays]?.toInt(),
            opens = this[Places.hoursOpens],
            closes = this[Places.hoursCloses],
        ),
        categoryOrigin = this[Places.categoryOrigin],
        status = this[Places.status],
        possibleDuplicate = this[Places.possibleDuplicate],
        duplicateNote = this[Places.duplicateNote],
        pointsEarned = this[Places.pointsEarned],
        firstPublication = this[Places.firstPublication],
        submittedAt = this[Places.submittedAt].toInstant(),
    )

    private fun ResultRow.toDecision() = DecisionRow(
        placeId = this[ModerationDecisions.placeId],
        moderatorName = getOrNull(Users.name),
        action = this[ModerationDecisions.action],
        note = this[ModerationDecisions.note],
        rejectionReason = this[ModerationDecisions.rejectionReason],
        rejectionMessage = this[ModerationDecisions.rejectionMessage],
        canResubmit = this[ModerationDecisions.canResubmit],
        duplicateOf = this[ModerationDecisions.duplicateOf],
        finalizeReason = this[ModerationDecisions.finalizeReason],
        createdAt = this[ModerationDecisions.createdAt].toInstant(),
    )

    private companion object {
        /** El umbral por omisión de pg_trgm: con él, «Mirador Secreto» se parece a «Mirador de la Secreta». */
        const val TITLE_SIMILARITY = 0.3
    }
}
