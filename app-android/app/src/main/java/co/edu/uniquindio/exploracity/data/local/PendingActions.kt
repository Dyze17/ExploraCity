package co.edu.uniquindio.exploracity.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Cola de envío: lo que la persona hizo sin conexión y se manda al servidor cuando vuelve la red (WorkManager).
// No depende de lo guardado para ver sin conexión: si el feed reemplaza su caché, la cola sigue intacta.

enum class PendingType { VISIT, VOTE, COMMENT }

/** Acción pendiente; [payload] es el JSON de [QueuedVisit], [QueuedVote] o [QueuedComment]. Se envían por [id]. */
@Entity(tableName = "pending_actions", indices = [Index("poiId")])
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PendingType,
    val poiId: String,
    val payload: String,
    val createdAtMillis: Long,
)

@Serializable
data class QueuedVisit(val recommends: Boolean?, val text: String, val showName: Boolean) {
    fun toDomain() = VisitExperience(recommends, text, showName)
}

@Serializable
data class QueuedVote(val voted: Boolean)

@Serializable
data class QueuedComment(val text: String)

fun VisitExperience.toQueued() = QueuedVisit(recommends, text, showName)

inline fun <reified T> PendingActionEntity.payloadAs(): T = Json.decodeFromString(payload)

@Dao
abstract class PendingActionsDao {

    @Insert
    abstract suspend fun insert(action: PendingActionEntity): Long

    /** La más antigua: se envían en el orden en que se hicieron. */
    @Query("SELECT * FROM pending_actions ORDER BY id LIMIT 1")
    abstract suspend fun next(): PendingActionEntity?

    @Query("DELETE FROM pending_actions WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM pending_actions WHERE poiId = :poiId AND type = :type ORDER BY id")
    abstract suspend fun find(poiId: String, type: PendingType): List<PendingActionEntity>

    /** Comentarios de [poiId] que esperan la red, del más reciente al más antiguo; cambia al enviarse cada uno. */
    @Query("SELECT * FROM pending_actions WHERE poiId = :poiId AND type = 'COMMENT' ORDER BY id DESC")
    abstract fun comments(poiId: String): Flow<List<PendingActionEntity>>

    @Query("SELECT COUNT(*) FROM pending_actions")
    abstract suspend fun count(): Int
}
