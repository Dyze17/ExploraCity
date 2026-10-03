package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PriceRange
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.Visits
import co.edu.uniquindio.exploracity.model.Votes
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/** Una publicación propia con lo que el archivo de «Descargar mis datos» cuenta de ella. */
data class OwnPlace(
    val id: UUID,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    /** Días de atención como bits: lunes = 1 … domingo = 64. */
    val hoursDays: Int?,
    val opens: LocalTime?,
    val closes: LocalTime?,
    val price: PriceRange?,
    val photos: List<String>,
    val submittedAt: Instant,
    val votes: Int,
    val comments: Int,
    val possibleDuplicate: Boolean,
)

data class OwnComment(val placeTitle: String, val text: String, val createdAt: Instant)

data class OwnVisit(val placeTitle: String, val recommends: Boolean?, val text: String?, val showName: Boolean)

/**
 * 29 y 30 · Todo lo de una persona: lo que lleva el archivo de sus datos y lo que se borra con la cuenta. Corre dentro
 * de la transacción de quien llama.
 */
class PersonalDataRepository {
    /** Sus publicaciones en cualquier estado, de la más reciente a la más antigua. */
    fun places(userId: UUID): List<OwnPlace> {
        val rows = Places.selectAll().where { Places.authorId eq userId }.orderBy(Places.submittedAt, SortOrder.DESC).toList()
        val ids = rows.map { it[Places.id] }
        val photos = Photos.select(Photos.placeId, Photos.url)
            .where { Photos.placeId inList ids }
            .orderBy(Photos.position)
            .groupBy({ it[Photos.placeId] }, { it[Photos.url] })
        val votes = countBy(Votes.placeId, ids)
        val comments = countBy(Comments.placeId, ids)
        return rows.map { row ->
            val id = row[Places.id]
            OwnPlace(
                id = id,
                title = row[Places.title],
                category = row[Places.category],
                status = row[Places.status],
                description = row[Places.description],
                latitude = row[Places.latitude],
                longitude = row[Places.longitude],
                hoursDays = row[Places.hoursDays]?.toInt(),
                opens = row[Places.hoursOpens],
                closes = row[Places.hoursCloses],
                price = row[Places.price],
                photos = photos[id].orEmpty(),
                submittedAt = row[Places.submittedAt].toInstant(),
                votes = votes[id] ?: 0,
                comments = comments[id] ?: 0,
                possibleDuplicate = row[Places.possibleDuplicate],
            )
        }
    }

    fun comments(userId: UUID): List<OwnComment> = (Comments innerJoin Places)
        .select(Places.title, Comments.text, Comments.createdAt)
        .where { Comments.authorId eq userId }
        .orderBy(Comments.createdAt, SortOrder.DESC)
        .map { OwnComment(it[Places.title], it[Comments.text], it[Comments.createdAt].toInstant()) }

    /** Los títulos de los lugares que votó. */
    fun votes(userId: UUID): List<String> = (Votes innerJoin Places)
        .select(Places.title)
        .where { Votes.userId eq userId }
        .orderBy(Votes.createdAt, SortOrder.DESC)
        .map { it[Places.title] }

    fun visits(userId: UUID): List<OwnVisit> = (Visits innerJoin Places)
        .select(Places.title, Visits.recommends, Visits.text, Visits.showName)
        .where { Visits.userId eq userId }
        .orderBy(Visits.createdAt, SortOrder.DESC)
        .map { OwnVisit(it[Places.title], it[Visits.recommends], it[Visits.text], it[Visits.showName]) }

    /** Los ids en el almacén de medios de todas las fotos que subió. */
    fun photoIds(userId: UUID): List<String> =
        Photos.select(Photos.publicId).where { Photos.ownerId eq userId }.map { it[Photos.publicId] }

    /** 30 · Lo que nadie más ve se va con la cuenta: sus publicaciones pendientes y rechazadas. */
    fun deleteUnpublished(userId: UUID): Int = Places.deleteWhere {
        (Places.authorId eq userId) and (Places.status inList listOf(PublicationStatus.PENDING, PublicationStatus.REJECTED))
    }

    private fun countBy(placeColumn: Column<UUID>, ids: List<UUID>): Map<UUID, Int> {
        if (ids.isEmpty()) return emptyMap()
        val count = placeColumn.count()
        return placeColumn.table.select(placeColumn, count)
            .where { placeColumn inList ids }
            .groupBy(placeColumn)
            .associate { it[placeColumn] to it[count].toInt() }
    }
}
