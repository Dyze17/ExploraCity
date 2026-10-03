package co.edu.uniquindio.exploracity.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// La última cola de moderación que llegó (32.c): sin conexión se lee con su antigüedad y se abre cada detalle (33).
// Cada pendiente va entera en JSON: solo se lee para mostrarla, nunca se consulta por sus campos.

@Entity(tableName = "saved_reviews")
data class SavedReviewEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val json: String,
)

@Dao
abstract class ReviewsDao {

    @Query("SELECT * FROM saved_reviews ORDER BY position")
    abstract suspend fun all(): List<SavedReviewEntity>

    @Query("SELECT id FROM saved_reviews ORDER BY position")
    abstract suspend fun ids(): List<String>

    @Query("SELECT * FROM saved_reviews WHERE id = :id")
    abstract suspend fun item(id: String): SavedReviewEntity?

    @Query("SELECT COUNT(*) FROM saved_reviews")
    abstract fun count(): Flow<Int>

    @Query("SELECT savedAtMillis FROM cache_info WHERE `key` = :key")
    abstract suspend fun savedAt(key: String = KEY): Long?

    /** Ya se decidió: sale de la cola guardada. */
    @Query("DELETE FROM saved_reviews WHERE id = :id")
    abstract suspend fun remove(id: String)

    @Query("DELETE FROM saved_reviews")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insertAll(items: List<SavedReviewEntity>)

    @Upsert
    protected abstract suspend fun upsertInfo(info: CacheInfoEntity)

    /** Lo que trajo el servidor reemplaza lo guardado. */
    @Transaction
    open suspend fun replace(items: List<SavedReviewEntity>, savedAtMillis: Long) {
        clear()
        insertAll(items)
        upsertInfo(CacheInfoEntity(KEY, savedAtMillis))
    }

    companion object {
        const val KEY = "moderation_queue"
    }
}

@Serializable
private class SavedReview(
    val id: String,
    val title: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val description: String,
    val photos: List<SavedReviewPhoto>,
    val hours: SavedHours?,
    val price: PriceRange?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val submittedAtMillis: Long,
    val authorId: String,
    val authorName: String,
    val authorPoints: Int,
    val authorVerified: Int,
    val authorRejected: Int,
    val reportReason: String? = null,
    val candidates: List<SavedCandidate>? = null,
    val authorNote: String? = null,
)

@Serializable
private class SavedReviewPhoto(val id: String, val url: String)

@Serializable
private class SavedHours(val days: List<DayOfWeek>, val opens: String, val closes: String)

@Serializable
private class SavedCandidate(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val latitude: Double,
    val longitude: Double,
    /** Distancia a la pendiente (33A). */
    val distanceMeters: Int,
    /** La del lugar tal como llegó del servidor. */
    val placeDistanceMeters: Int,
    val votes: Int,
    val comments: Int,
    val photoUrl: String? = null,
    val price: PriceRange? = null,
    val openNow: Boolean? = null,
    val summary: String? = null,
    /** Quién lo publicó, cuándo y cuántas fotos tiene (33A); null en lo guardado antes de comparar lado a lado. */
    val authorId: String? = null,
    val authorName: String? = null,
    val authorPoints: Int = 0,
    val publishedAtMillis: Long? = null,
    val photoCount: Int = 0,
)

private val reviewJson = Json { ignoreUnknownKeys = true }

fun ReviewItem.toEntity(position: Int): SavedReviewEntity {
    val saved = SavedReview(
        id = id,
        title = title,
        category = category,
        categoryOrigin = categoryOrigin,
        description = description,
        photos = photos.map { SavedReviewPhoto(it.id, it.url) },
        hours = hours?.let { SavedHours(it.days.sorted(), it.opens.toString(), it.closes.toString()) },
        price = price,
        address = address,
        latitude = location.latitude,
        longitude = location.longitude,
        submittedAtMillis = submittedAt.toEpochMilli(),
        authorId = author.author.id,
        authorName = author.author.name,
        authorPoints = author.author.points,
        authorVerified = author.verified,
        authorRejected = author.rejected,
        reportReason = reportReason,
        candidates = duplicate?.candidates?.map { candidate ->
            val poi = candidate.poi
            SavedCandidate(
                id = poi.id,
                title = poi.title,
                category = poi.category,
                status = poi.status,
                latitude = poi.location.latitude,
                longitude = poi.location.longitude,
                distanceMeters = candidate.distanceMeters,
                placeDistanceMeters = poi.distanceMeters,
                votes = poi.votes,
                comments = poi.comments,
                photoUrl = poi.photoUrl,
                price = poi.price,
                openNow = poi.openNow,
                summary = poi.summary,
                authorId = candidate.author?.id,
                authorName = candidate.author?.name,
                authorPoints = candidate.author?.points ?: 0,
                publishedAtMillis = candidate.publishedAt?.toEpochMilli(),
                photoCount = candidate.photoCount,
            )
        },
        authorNote = duplicate?.authorNote,
    )
    return SavedReviewEntity(id, position, reviewJson.encodeToString(SavedReview.serializer(), saved))
}

fun SavedReviewEntity.toDomain(): ReviewItem {
    val saved = reviewJson.decodeFromString(SavedReview.serializer(), json)
    return ReviewItem(
        id = saved.id,
        title = saved.title,
        category = saved.category,
        categoryOrigin = saved.categoryOrigin,
        description = saved.description,
        photos = saved.photos.map { PublishedPhoto(it.id, it.url) },
        hours = saved.hours?.let { OpeningHours(it.days.toSet(), LocalTime.parse(it.opens), LocalTime.parse(it.closes)) },
        price = saved.price,
        address = saved.address,
        location = GeoPoint(saved.latitude, saved.longitude),
        submittedAt = Instant.ofEpochMilli(saved.submittedAtMillis),
        author = ReviewAuthor(Author(saved.authorId, saved.authorName, saved.authorPoints), saved.authorVerified, saved.authorRejected),
        reportReason = saved.reportReason,
        duplicate = saved.candidates?.let { candidates ->
            DuplicateSuspicion(
                candidates.map { c ->
                    val poi = Poi(
                        id = c.id,
                        title = c.title,
                        category = c.category,
                        status = c.status,
                        location = GeoPoint(c.latitude, c.longitude),
                        distanceMeters = c.placeDistanceMeters,
                        votes = c.votes,
                        comments = c.comments,
                        photoUrl = c.photoUrl,
                        price = c.price,
                        openNow = c.openNow,
                        summary = c.summary,
                    )
                    val author = if (c.authorId != null && c.authorName != null) Author(c.authorId, c.authorName, c.authorPoints) else null
                    DuplicateCandidate(poi, c.distanceMeters, author, c.publishedAtMillis?.let(Instant::ofEpochMilli), c.photoCount)
                },
                saved.authorNote,
            )
        },
    )
}
