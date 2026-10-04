package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.GeoBounds
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.PriceRange
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.UserPoints
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.model.Visits
import co.edu.uniquindio.exploracity.model.Votes
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToInt

/** Criterios de una búsqueda de lugares públicos (7, 8, 9 y 31), ya validados. */
data class PlaceFilter(
    val categories: Set<Category> = emptySet(),
    /** «Cercanos»: a 5 km o menos del punto de la búsqueda. */
    val nearbyOnly: Boolean = false,
    /** «Solo verificados»: sin las finalizadas. */
    val verifiedOnly: Boolean = false,
    val text: String = "",
    /** 31 · Solo los de esta persona. */
    val authorId: UUID? = null,
    /** 8 · Solo los del área visible. */
    val bounds: GeoBounds? = null,
)

/** Un lugar público con lo que muestran su tarjeta y su detalle. */
data class PlaceRow(
    val id: UUID,
    val authorId: UUID?,
    val title: String,
    val description: String,
    val category: Category,
    val status: PublicationStatus,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val votes: Int,
    val comments: Int,
    val cover: String?,
    val price: PriceRange?,
    val address: String?,
    /** Días de atención como bits: lunes = 1 … domingo = 64. */
    val hoursDays: Int?,
    val opens: LocalTime?,
    val closes: LocalTime?,
)

/** Quien firma un lugar o un comentario, con sus puntos (G2). */
data class AuthorRow(val id: UUID, val name: String, val points: Int)

data class CommentRow(val id: UUID, val author: AuthorRow?, val text: String, val createdAt: Instant)

/**
 * Lugares públicos (verificados y finalizados) y lo que la comunidad hace con ellos: votos, visitas y comentarios.
 * Las búsquedas por distancia van en SQL explícito sobre places.location (PostGIS). Corre dentro de la transacción de
 * quien llama.
 */
class PlaceRepository {

    /** Los que cumplen [filter], del más cercano a [from] al más lejano. */
    fun search(filter: PlaceFilter, from: GeoPoint, limit: Int, offset: Int = 0): List<PlaceRow> {
        val sql = Sql()
        sql.append("SELECT $COLUMNS, ST_Distance(p.location, ").point(from).append(") AS distance FROM places p ")
        where(sql, filter, from)
        sql.append(" ORDER BY distance, p.id LIMIT ").param(IntegerColumnType(), limit).append(" OFFSET ").param(IntegerColumnType(), offset)
        return sql.query(::placeRow)
    }

    fun count(filter: PlaceFilter, from: GeoPoint): Int {
        val sql = Sql()
        sql.append("SELECT count(*) AS total FROM places p ")
        where(sql, filter, from)
        return sql.query { it.getInt("total") }.single()
    }

    /** El lugar [id] si es público; null si no existe o no se ve (pendiente o rechazado). */
    fun find(id: UUID, from: GeoPoint): PlaceRow? {
        val sql = Sql()
        sql.append("SELECT $COLUMNS, ST_Distance(p.location, ").point(from).append(") AS distance FROM places p ")
        sql.append("WHERE p.status IN $PUBLIC AND p.id = ").param(UUIDColumnType(), id)
        return sql.query(::placeRow).singleOrNull()
    }

    /** Los públicos de [ids] que aún existen, medidos desde [from], del más cercano al más lejano. */
    fun findPublic(ids: Collection<UUID>, from: GeoPoint): List<PlaceRow> {
        if (ids.isEmpty()) return emptyList()
        val sql = Sql()
        sql.append("SELECT $COLUMNS, ST_Distance(p.location, ").point(from).append(") AS distance FROM places p ")
        sql.append("WHERE p.status IN $PUBLIC AND p.id IN (")
        ids.forEachIndexed { i, id ->
            if (i > 0) sql.append(", ")
            sql.param(UUIDColumnType(), id)
        }
        sql.append(") ORDER BY distance, p.id")
        return sql.query(::placeRow)
    }

    /** 35 · Los públicos a [radiusMeters] o menos de [from], del más cercano al más lejano, sin [excludeId]. */
    fun nearby(from: GeoPoint, radiusMeters: Double, limit: Int, excludeId: UUID): List<PlaceRow> {
        val sql = Sql()
        sql.append("SELECT $COLUMNS, ST_Distance(p.location, ").point(from).append(") AS distance FROM places p ")
        sql.append("WHERE p.status IN $PUBLIC AND p.id <> ").param(UUIDColumnType(), excludeId)
        sql.append(" AND ST_DWithin(p.location, ").point(from).append(", ").param(DoubleColumnType(), radiusMeters).append(")")
        sql.append(" ORDER BY distance, p.id LIMIT ").param(IntegerColumnType(), limit)
        return sql.query(::placeRow)
    }

    fun photos(placeId: UUID): List<String> =
        Photos.select(Photos.url).where { Photos.placeId eq placeId }.orderBy(Photos.position).map { it[Photos.url] }

    /** null si la cuenta ya no existe. */
    fun author(userId: UUID?): AuthorRow? {
        userId ?: return null
        return Users.join(UserPoints, JoinType.LEFT, Users.id, UserPoints.userId)
            .select(Users.id, Users.name, UserPoints.points)
            .where { Users.id eq userId }
            .singleOrNull()
            ?.let { AuthorRow(it[Users.id], it[Users.name], it[UserPoints.points]) }
    }

    fun hasVoted(placeId: UUID, userId: UUID): Boolean =
        Votes.selectAll().where { (Votes.placeId eq placeId) and (Votes.userId eq userId) }.count() > 0

    fun hasVisited(placeId: UUID, userId: UUID): Boolean =
        Visits.selectAll().where { (Visits.placeId eq placeId) and (Visits.userId eq userId) }.count() > 0

    /** Un voto por persona: votar dos veces no suma. Devuelve true si el voto es nuevo. */
    fun addVote(placeId: UUID, userId: UUID, at: Instant): Boolean = Votes.insertIgnore {
        it[Votes.placeId] = placeId
        it[Votes.userId] = userId
        it[createdAt] = at.atOffset(ZoneOffset.UTC)
    }.insertedCount > 0

    fun removeVote(placeId: UUID, userId: UUID) {
        Votes.deleteWhere { (Votes.placeId eq placeId) and (Votes.userId eq userId) }
    }

    fun votes(placeId: UUID): Int = Votes.selectAll().where { Votes.placeId eq placeId }.count().toInt()

    /**
     * 14.b · La primera visita se guarda con [points]; marcarla otra vez solo cambia la experiencia. Devuelve los puntos
     * que ganó con esta llamada.
     */
    fun visit(placeId: UUID, userId: UUID, recommends: Boolean?, text: String?, showName: Boolean, points: Int, at: Instant): Int {
        val updated = Visits.update({ (Visits.placeId eq placeId) and (Visits.userId eq userId) }) {
            it[Visits.recommends] = recommends
            it[Visits.text] = text
            it[Visits.showName] = showName
        }
        if (updated > 0) return 0
        Visits.insert {
            it[Visits.placeId] = placeId
            it[Visits.userId] = userId
            it[Visits.recommends] = recommends
            it[Visits.text] = text
            it[Visits.showName] = showName
            it[pointsAwarded] = points
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
        return points
    }

    /** 14 · Comentarios del más reciente al más antiguo, después de [after] (fecha e id del último de la página anterior). */
    fun comments(placeId: UUID, after: Pair<Instant, UUID>?, limit: Int): List<CommentRow> {
        val query = Comments
            .join(Users, JoinType.LEFT, Comments.authorId, Users.id)
            .join(UserPoints, JoinType.LEFT, Comments.authorId, UserPoints.userId)
            .select(Comments.id, Comments.text, Comments.createdAt, Comments.authorId, Users.name, UserPoints.points)
            .where {
                val ofPlace = Comments.placeId eq placeId
                if (after == null) {
                    ofPlace
                } else {
                    val (time, id) = after
                    val at = time.atOffset(ZoneOffset.UTC)
                    ofPlace and ((Comments.createdAt less at) or ((Comments.createdAt eq at) and (Comments.id less id)))
                }
            }
            .orderBy(Comments.createdAt to SortOrder.DESC, Comments.id to SortOrder.DESC)
            .limit(limit)
        return query.map { row ->
            val authorId = row[Comments.authorId]
            CommentRow(
                id = row[Comments.id],
                author = authorId?.let { AuthorRow(it, row[Users.name], row[UserPoints.points]) },
                text = row[Comments.text],
                createdAt = row[Comments.createdAt].toInstant(),
            )
        }
    }

    fun commentCount(placeId: UUID): Int = Comments.selectAll().where { Comments.placeId eq placeId }.count().toInt()

    /**
     * Guarda el comentario. Con [clientId], uno que ya llegó con ese id (un reenvío de la cola sin conexión) no se
     * duplica. Devuelve su id y si es nuevo.
     */
    fun addComment(placeId: UUID, authorId: UUID, clientId: UUID?, text: String, at: Instant): Pair<UUID, Boolean> {
        val statement = Comments.insertIgnore {
            it[Comments.placeId] = placeId
            it[Comments.authorId] = authorId
            it[Comments.clientId] = clientId
            it[Comments.text] = text
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
        if (statement.insertedCount > 0) return statement[Comments.id] to true
        val existing = Comments.select(Comments.id)
            .where { (Comments.authorId eq authorId) and (Comments.clientId eq checkNotNull(clientId)) }
            .single()
        return existing[Comments.id] to false
    }

    fun comment(id: UUID): CommentRow? = Comments
        .join(Users, JoinType.LEFT, Comments.authorId, Users.id)
        .join(UserPoints, JoinType.LEFT, Comments.authorId, UserPoints.userId)
        .select(Comments.id, Comments.text, Comments.createdAt, Comments.authorId, Users.name, UserPoints.points)
        .where { Comments.id eq id }
        .singleOrNull()
        ?.let { row ->
            CommentRow(
                id = row[Comments.id],
                author = row[Comments.authorId]?.let { AuthorRow(it, row[Users.name], row[UserPoints.points]) },
                text = row[Comments.text],
                createdAt = row[Comments.createdAt].toInstant(),
            )
        }

    private fun where(sql: Sql, filter: PlaceFilter, from: GeoPoint) {
        sql.append("WHERE p.status IN ").append(if (filter.verifiedOnly) "('VERIFIED')" else PUBLIC)
        if (filter.categories.isNotEmpty()) {
            sql.append(" AND p.category IN (")
            filter.categories.forEachIndexed { i, category ->
                if (i > 0) sql.append(", ")
                sql.param(TextColumnType(), category.name)
            }
            sql.append(")")
        }
        if (filter.nearbyOnly) {
            sql.append(" AND ST_DWithin(p.location, ").point(from).append(", ").param(DoubleColumnType(), NEARBY_RADIUS_METERS).append(")")
        }
        if (filter.text.isNotBlank()) {
            sql.append(" AND search_text(p.title) LIKE '%' || search_text(").param(TextColumnType(), escapeLike(filter.text.trim()))
            sql.append(") || '%' ESCAPE '\\'")
        }
        filter.authorId?.let { sql.append(" AND p.author_id = ").param(UUIDColumnType(), it) }
        filter.bounds?.let { bounds ->
            sql.append(" AND p.latitude BETWEEN ").param(DoubleColumnType(), bounds.southwest.latitude)
            sql.append(" AND ").param(DoubleColumnType(), bounds.northeast.latitude)
            sql.append(" AND p.longitude BETWEEN ").param(DoubleColumnType(), bounds.southwest.longitude)
            sql.append(" AND ").param(DoubleColumnType(), bounds.northeast.longitude)
        }
    }

    private fun placeRow(rs: ResultSet) = PlaceRow(
        id = rs.getObject("id", UUID::class.java),
        authorId = rs.getObject("author_id", UUID::class.java),
        title = rs.getString("title"),
        description = rs.getString("description"),
        category = Category.valueOf(rs.getString("category")),
        status = PublicationStatus.valueOf(rs.getString("status")),
        latitude = rs.getDouble("latitude"),
        longitude = rs.getDouble("longitude"),
        distanceMeters = rs.getDouble("distance").roundToInt(),
        votes = rs.getInt("votes"),
        comments = rs.getInt("comments"),
        cover = rs.getString("cover"),
        price = rs.getString("price")?.let(PriceRange::valueOf),
        address = rs.getString("address"),
        hoursDays = rs.getInt("hours_days").takeUnless { rs.wasNull() },
        opens = rs.getObject("hours_opens", LocalTime::class.java),
        closes = rs.getObject("hours_closes", LocalTime::class.java),
    )

    private companion object {
        /** 9 · Radio de «Cercanos». */
        const val NEARBY_RADIUS_METERS = 5_000.0

        /** Lo que el feed muestra: verificadas y finalizadas. */
        const val PUBLIC = "('VERIFIED', 'FINALIZED')"

        const val COLUMNS = "p.id, p.author_id, p.title, p.description, p.category, p.status, p.latitude, p.longitude, " +
            "p.price, p.address, p.hours_days, p.hours_opens, p.hours_closes, " +
            "(SELECT count(*) FROM votes v WHERE v.place_id = p.id) AS votes, " +
            "(SELECT count(*) FROM comments c WHERE c.place_id = p.id) AS comments, " +
            "(SELECT ph.url FROM photos ph WHERE ph.place_id = p.id ORDER BY ph.position LIMIT 1) AS cover"

        /** El texto de la persona se busca tal cual: sin comodines de LIKE. */
        fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
