package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Dónde vive un borrador (15–19). La publicación nueva y la corrección de cada rechazada (24) no se pisan: corregir
 * una no borra lo que se estaba escribiendo de otra.
 */
sealed interface DraftKey {
    val value: String

    data object New : DraftKey {
        override val value = "nueva"
    }

    data class Resubmit(val publicationId: String) : DraftKey {
        override val value = "reenvio:$publicationId"
    }
}

/** Borradores de publicación en el teléfono: sobreviven al cierre de la app y a la falta de red (README 15). */
interface DraftRepository {
    suspend fun load(key: DraftKey): PublicationDraft?

    suspend fun save(key: DraftKey, draft: PublicationDraft)

    suspend fun clear(key: DraftKey)
}

/** En DataStore (SAD: data/local), como JSON: lo que agreguen los pasos 3 a 5 no rompe lo ya guardado. */
class DataStoreDraftRepository(private val dataStore: DataStore<Preferences>) : DraftRepository {

    override suspend fun load(key: DraftKey): PublicationDraft? {
        val json = dataStore.data.first()[preferenceKey(key)] ?: return null
        return try {
            draftJson.decodeFromString<SavedDraft>(json).toDomain()
        } catch (e: SerializationException) {
            // Un borrador ilegible (de una versión anterior, por ejemplo) no debe romper el formulario.
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(key: DraftKey, draft: PublicationDraft) {
        dataStore.edit { it[preferenceKey(key)] = draftJson.encodeToString(draft.toSaved()) }
    }

    override suspend fun clear(key: DraftKey) {
        dataStore.edit { it.remove(preferenceKey(key)) }
    }

    private fun preferenceKey(key: DraftKey) = stringPreferencesKey("borrador:${key.value}")
}

@Serializable
internal data class SavedDraft(
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val categoryOrigin: CategoryOrigin? = null,
    val location: SavedPoint? = null,
    val duplicateCheck: SavedDuplicateCheck? = null,
    val step: PublishStep = PublishStep.BASICS,
)

@Serializable
internal data class SavedPoint(val latitude: Double, val longitude: Double)

@Serializable
internal data class SavedDuplicateCheck(
    val location: SavedPoint,
    val similarIds: List<String> = emptyList(),
    val note: String = "",
    val failed: Boolean = false,
)

private val draftJson = Json { ignoreUnknownKeys = true }

private fun GeoPoint.toSaved() = SavedPoint(latitude, longitude)

private fun SavedPoint.toDomain() = GeoPoint(latitude, longitude)

private fun PublicationDraft.toSaved() = SavedDraft(
    title = title,
    description = description,
    category = category,
    categoryOrigin = categoryOrigin,
    location = location?.toSaved(),
    duplicateCheck = duplicateCheck?.let { SavedDuplicateCheck(it.location.toSaved(), it.similarIds, it.note, it.failed) },
    step = step,
)

private fun SavedDraft.toDomain() = PublicationDraft(
    title = title,
    description = description,
    category = category,
    categoryOrigin = categoryOrigin,
    location = location?.toDomain(),
    duplicateCheck = duplicateCheck?.let { DuplicateCheck(it.location.toDomain(), it.similarIds, it.note, it.failed) },
    step = step,
)
