package co.edu.uniquindio.exploracity.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.YearMonth

// El último perfil propio que llegó (26 y 27): sin conexión se ve con su antigüedad, como los avisos. Es un solo
// documento, así que se guarda como JSON; lo que agregue el servidor más adelante no rompe lo ya guardado.

@Entity(tableName = "saved_profile")
data class SavedProfileEntity(@PrimaryKey val key: String = KEY, val json: String, val savedAtMillis: Long) {
    companion object {
        const val KEY = "own"
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM saved_profile WHERE `key` = :key")
    suspend fun get(key: String = SavedProfileEntity.KEY): SavedProfileEntity?

    @Upsert
    suspend fun save(profile: SavedProfileEntity)
}

@Serializable
internal data class SavedProfile(
    val authorId: String,
    val name: String,
    val points: Int,
    val residency: Residency,
    val city: String,
    /** «2026-03». */
    val memberSince: String,
    val pending: Int,
    val verified: Int,
    val rejected: Int,
    val finalized: Int,
    val badges: List<SavedBadge>,
)

@Serializable
internal data class SavedBadge(
    val id: String,
    val name: String,
    val metric: BadgeMetric,
    val progress: Int,
    val target: Int,
    val howTo: String,
    val tip: String? = null,
    val category: Category? = null,
)

private val profileJson = Json { ignoreUnknownKeys = true }

fun OwnProfile.toEntity(savedAtMillis: Long): SavedProfileEntity {
    val saved = SavedProfile(
        authorId = author.id,
        name = author.name,
        points = author.points,
        residency = residency,
        city = city,
        memberSince = memberSince.toString(),
        pending = publications.pending,
        verified = publications.verified,
        rejected = publications.rejected,
        finalized = publications.finalized,
        badges = badges.map { SavedBadge(it.id, it.name, it.metric, it.progress, it.target, it.howTo, it.tip, it.category) },
    )
    return SavedProfileEntity(json = profileJson.encodeToString(saved), savedAtMillis = savedAtMillis)
}

fun SavedProfileEntity.toDomain(): OwnProfile {
    val saved = profileJson.decodeFromString<SavedProfile>(json)
    return OwnProfile(
        author = Author(saved.authorId, saved.name, saved.points),
        residency = saved.residency,
        city = saved.city,
        memberSince = YearMonth.parse(saved.memberSince),
        publications = PublicationCounts(saved.pending, saved.verified, saved.rejected, saved.finalized),
        badges = saved.badges.map { Badge(it.id, it.name, it.metric, it.progress, it.target, it.howTo, it.tip, it.category) },
        savedAt = Instant.ofEpochMilli(savedAtMillis),
    )
}
