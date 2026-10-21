package co.edu.uniquindio.exploracity.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DraftHours
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.DraftSchedule
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.HoursMode
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime

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

    /** 30 · Al eliminar la cuenta no queda ningún borrador. */
    suspend fun clearAll()
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

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
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
    /** Lo que guardó la 1.2: un tramo y la casilla «No tengo el horario exacto». Desde la 1.3.0 se usa [schedule]. */
    val hours: SavedDraftHours? = null,
    val hoursUnknown: Boolean = false,
    val schedule: SavedDraftSchedule? = null,
    val price: PriceRange? = null,
    val photos: List<SavedDraftPhoto> = emptyList(),
    val step: PublishStep = PublishStep.BASICS,
)

/** Días como 1 (lunes) a 7 (domingo) y horas en minutos desde la medianoche: sin tipos de java.time en el JSON. */
@Serializable
internal data class SavedDraftHours(val days: List<Int> = emptyList(), val opensMinute: Int? = null, val closesMinute: Int? = null)

/** v1.3.0 · Cómo se cuenta el horario y sus tramos, cada uno a medio elegir. */
@Serializable
internal data class SavedDraftSchedule(val mode: HoursMode = HoursMode.HOURS, val blocks: List<SavedDraftHours> = emptyList())

@Serializable
internal data class SavedDraftPhoto(val id: String, val path: String, val name: String, val remoteUrl: String? = null)

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

/**
 * El borrador como JSON, en el mismo formato que DataStore. La edición (23) guarda así su formulario en el estado de la
 * pantalla, para que sobreviva si Android cierra la app.
 */
object DraftJson {
    fun encode(draft: PublicationDraft): String = draftJson.encodeToString(draft.toSaved())

    /** null si no se puede leer (de una versión anterior, por ejemplo). */
    fun decode(json: String): PublicationDraft? = try {
        draftJson.decodeFromString<SavedDraft>(json).toDomain()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun GeoPoint.toSaved() = SavedPoint(latitude, longitude)

private fun SavedPoint.toDomain() = GeoPoint(latitude, longitude)

private fun LocalTime.toMinute() = hour * 60 + minute

private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)

private fun PublicationDraft.toSaved() = SavedDraft(
    title = title,
    description = description,
    category = category,
    categoryOrigin = categoryOrigin,
    location = location?.toSaved(),
    duplicateCheck = duplicateCheck?.let { SavedDuplicateCheck(it.location.toSaved(), it.similarIds, it.note, it.failed) },
    schedule = SavedDraftSchedule(schedule.mode, schedule.blocks.map { h -> SavedDraftHours(h.days.map { it.value }.sorted(), h.opens?.toMinute(), h.closes?.toMinute()) }),
    price = price,
    photos = photos.map { SavedDraftPhoto(it.id, it.path, it.name, it.remoteUrl) },
    step = step,
)

private fun SavedDraft.toDomain() = PublicationDraft(
    title = title,
    description = description,
    category = category,
    categoryOrigin = categoryOrigin,
    location = location?.toDomain(),
    duplicateCheck = duplicateCheck?.let { DuplicateCheck(it.location.toDomain(), it.similarIds, it.note, it.failed) },
    schedule = schedule?.let { saved -> DraftSchedule(saved.mode, saved.blocks.map { it.toDomain() }.ifEmpty { listOf(DraftHours()) }) }
        ?: DraftSchedule(if (hoursUnknown) HoursMode.UNKNOWN else HoursMode.HOURS, listOf(hours?.toDomain() ?: DraftHours())),
    price = price,
    photos = photos.map { DraftPhoto(it.id, it.path, it.name, it.remoteUrl) },
    step = step,
)

private fun SavedDraftHours.toDomain() = DraftHours(days.map(DayOfWeek::of).toSet(), opensMinute?.toTime(), closesMinute?.toTime())
