package co.edu.uniquindio.exploracity.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniquindio.exploracity.domain.model.Notification
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Los últimos avisos que llegaron (25): sin conexión, la lista queda en su último estado conocido con su antigüedad.

enum class NotificationType { VERIFIED, FINALIZED, COMMENTED, REJECTED, DUPLICATE_REJECTED, ACHIEVEMENT }

/** Un aviso en una sola tabla: cada tipo usa sus columnas y deja las demás en null. */
@Entity(tableName = "saved_notifications")
data class SavedNotificationEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val type: NotificationType,
    val createdAtMillis: Long,
    val read: Boolean,
    val poiId: String? = null,
    /** Título del lugar o de la publicación. */
    val title: String? = null,
    val publicationId: String? = null,
    val points: Int? = null,
    val authorName: String? = null,
    val excerpt: String? = null,
    val reason: String? = null,
    val existingPoiId: String? = null,
    val existingTitle: String? = null,
    val achievement: String? = null,
    val nextBadge: String? = null,
    val remaining: Int? = null,
)

@Dao
abstract class NotificationsDao {

    @Query("SELECT * FROM saved_notifications ORDER BY position")
    abstract suspend fun all(): List<SavedNotificationEntity>

    @Query("SELECT COUNT(*) FROM saved_notifications WHERE read = 0")
    abstract fun unreadCount(): Flow<Int>

    @Query("SELECT savedAtMillis FROM cache_info WHERE `key` = :key")
    abstract suspend fun savedAt(key: String = KEY): Long?

    @Query("UPDATE saved_notifications SET read = 1 WHERE id = :id")
    abstract suspend fun markRead(id: String)

    @Query("UPDATE saved_notifications SET read = 1")
    abstract suspend fun markAllRead()

    @Query("DELETE FROM saved_notifications")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insertAll(notifications: List<SavedNotificationEntity>)

    @Upsert
    protected abstract suspend fun upsertInfo(info: CacheInfoEntity)

    /** Lo que trajo el servidor reemplaza lo guardado. */
    @Transaction
    open suspend fun replace(notifications: List<SavedNotificationEntity>, savedAtMillis: Long) {
        clear()
        insertAll(notifications)
        upsertInfo(CacheInfoEntity(KEY, savedAtMillis))
    }

    companion object {
        const val KEY = "notifications"
    }
}

fun Notification.toEntity(position: Int): SavedNotificationEntity {
    val base = SavedNotificationEntity(id, position, NotificationType.VERIFIED, createdAt.toEpochMilli(), read)
    return when (this) {
        is Notification.Verified -> base.copy(type = NotificationType.VERIFIED, poiId = poiId, title = poiTitle, points = points)
        is Notification.Finalized -> base.copy(type = NotificationType.FINALIZED, poiId = poiId, title = poiTitle)
        is Notification.Commented ->
            base.copy(type = NotificationType.COMMENTED, poiId = poiId, title = poiTitle, authorName = authorName, excerpt = excerpt)
        is Notification.Rejected -> base.copy(type = NotificationType.REJECTED, publicationId = publicationId, title = title, reason = reason)
        is Notification.DuplicateRejected -> base.copy(
            type = NotificationType.DUPLICATE_REJECTED,
            publicationId = publicationId,
            title = title,
            existingPoiId = existingPoiId,
            existingTitle = existingTitle,
        )
        is Notification.Achievement ->
            base.copy(type = NotificationType.ACHIEVEMENT, achievement = achievement, nextBadge = nextBadge, remaining = remaining)
    }
}

fun SavedNotificationEntity.toDomain(): Notification {
    val createdAt = Instant.ofEpochMilli(createdAtMillis)
    return when (type) {
        NotificationType.VERIFIED -> Notification.Verified(id, createdAt, read, poiId!!, title!!, points ?: 0)
        NotificationType.FINALIZED -> Notification.Finalized(id, createdAt, read, poiId!!, title!!)
        NotificationType.COMMENTED -> Notification.Commented(id, createdAt, read, poiId!!, title!!, authorName!!, excerpt!!)
        NotificationType.REJECTED -> Notification.Rejected(id, createdAt, read, publicationId!!, title!!, reason!!)
        NotificationType.DUPLICATE_REJECTED ->
            Notification.DuplicateRejected(id, createdAt, read, publicationId!!, title!!, existingPoiId!!, existingTitle!!)
        NotificationType.ACHIEVEMENT -> Notification.Achievement(id, createdAt, read, achievement!!, nextBadge, remaining)
    }
}
