package co.edu.uniquindio.exploracity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class SavedPlacesDao {

    @Query("SELECT * FROM saved_pois ORDER BY position")
    abstract suspend fun places(): List<SavedPoiEntity>

    @Query("SELECT * FROM saved_pois WHERE id = :id")
    abstract suspend fun place(id: String): SavedPoiEntity?

    @Query("SELECT * FROM saved_details WHERE poiId = :id")
    abstract suspend fun details(id: String): SavedDetailsEntity?

    @Query("SELECT poiId FROM saved_details")
    abstract suspend fun idsWithDetails(): List<String>

    @Query("SELECT savedAtMillis FROM cache_info WHERE `key` = :key")
    abstract suspend fun savedAt(key: String = FEED_KEY): Long?

    @Query("DELETE FROM saved_pois")
    protected abstract suspend fun clearPlaces()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaces(places: List<SavedPoiEntity>)

    @Query("UPDATE saved_pois SET votes = :votes, comments = :comments WHERE id = :id")
    protected abstract suspend fun updateCounts(id: String, votes: Int, comments: Int)

    @Upsert
    protected abstract suspend fun upsertDetails(details: SavedDetailsEntity)

    @Upsert
    protected abstract suspend fun upsertInfo(info: CacheInfoEntity)

    @Query("SELECT COUNT(*) FROM saved_pois")
    protected abstract suspend fun count(): Int

    /** Primera página del feed: reemplaza todo lo guardado (los detalles se van con sus lugares). */
    @Transaction
    open suspend fun replaceFeed(places: List<SavedPoiEntity>, savedAtMillis: Long) {
        clearPlaces()
        insertPlaces(places)
        upsertInfo(CacheInfoEntity(FEED_KEY, savedAtMillis))
    }

    /** Páginas siguientes: van después de lo guardado, hasta [limit] lugares. Devuelve los que entraron. */
    @Transaction
    open suspend fun appendFeed(places: List<SavedPoiEntity>, limit: Int): List<SavedPoiEntity> {
        val saved = count()
        val added = places.take((limit - saved).coerceAtLeast(0)).mapIndexed { i, place -> place.copy(position = saved + i) }
        insertPlaces(added)
        return added
    }

    /**
     * Guarda el detalle solo si el lugar está entre los guardados (lo que no vino del feed no se guarda) y deja al día
     * sus votos y comentarios. Devuelve si se guardó.
     */
    @Transaction
    open suspend fun saveDetails(details: SavedDetailsEntity, votes: Int, comments: Int): Boolean {
        if (place(details.poiId) == null) return false
        upsertDetails(details)
        updateCounts(details.poiId, votes, comments)
        return true
    }

    @Query("UPDATE saved_pois SET votes = :votes WHERE id = :id")
    protected abstract suspend fun updateVotes(id: String, votes: Int)

    @Query("UPDATE saved_details SET voted = :voted WHERE poiId = :id")
    protected abstract suspend fun updateVoted(id: String, voted: Boolean)

    /** Voto de la persona confirmado por el servidor: sin red se verá igual. */
    @Transaction
    open suspend fun saveVote(id: String, voted: Boolean, votes: Int) {
        updateVotes(id, votes)
        updateVoted(id, voted)
    }

    @Query("UPDATE saved_details SET visited = 1 WHERE poiId = :id")
    abstract suspend fun markVisited(id: String)

    @Query("UPDATE saved_pois SET comments = comments + 1 WHERE id = :id")
    abstract suspend fun addComment(id: String)

    companion object {
        const val FEED_KEY = "feed"
    }
}
