package co.edu.uniquindio.exploracity.data.sync

import co.edu.uniquindio.exploracity.data.local.PendingActionEntity
import co.edu.uniquindio.exploracity.data.local.PendingActionsDao
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.data.local.payloadAs
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.photos.uploadNow
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Publicaciones que esperan la red (20 sin conexión) y fotos que terminan de subir después del envío (19). Viven en la
 * misma cola que votos y comentarios: las envía [PendingSender] cuando WorkManager ve red, también con la app cerrada.
 */
interface PublicationOutbox {
    /** Sin red: la publicación completa, con sus fotos del teléfono. Se guarda hasta que el servidor la confirme. */
    suspend fun enqueue(submission: PublicationSubmission)

    /** Enviada ya: las fotos que no alcanzaron a subir se suben después y se agregan a [publicationId]. */
    suspend fun enqueuePhotos(publicationId: String, photos: List<DraftPhoto>)
}

class RoomPublicationOutbox(
    private val pending: PendingActionsDao,
    private val scheduler: PendingScheduler,
    private val clock: Clock = Clock.systemUTC(),
) : PublicationOutbox {

    override suspend fun enqueue(submission: PublicationSubmission) {
        val target = submission.resubmitId ?: NEW_PUBLICATION_TARGET
        pending.insert(PendingActionEntity(type = PendingType.PUBLICATION, poiId = target, payload = queueJson.encodeToString(submission.toQueued()), createdAtMillis = clock.millis()))
        scheduler.schedule()
    }

    override suspend fun enqueuePhotos(publicationId: String, photos: List<DraftPhoto>) {
        if (photos.isEmpty()) return
        photos.forEach { photo ->
            pending.insert(PendingActionEntity(type = PendingType.PUBLICATION_PHOTO, poiId = publicationId, payload = queueJson.encodeToString(photo.toQueued()), createdAtMillis = clock.millis()))
        }
        scheduler.schedule()
    }
}

/** Destino en la cola de una publicación nueva, que aún no tiene id. */
const val NEW_PUBLICATION_TARGET = "publicacion-nueva"

/** Lo que [PendingSender] necesita para enviar publicaciones: el servidor, la subida de fotos y los archivos locales. */
class PublicationDelivery(
    private val publications: PublicationRepository,
    private val uploader: PhotoUploader,
    private val photos: PhotoStore,
) {
    /** Sube las fotos que falten y envía; los archivos del teléfono se borran solo cuando el servidor confirma. */
    suspend fun send(action: PendingActionEntity) {
        when (action.type) {
            PendingType.PUBLICATION -> {
                val submission = action.payloadAs<QueuedPublication>().toDomain()
                val uploaded = submission.photos.map { if (it.uploaded) it else it.copy(remoteUrl = uploader.uploadNow(it)) }
                publications.submit(submission.copy(photos = uploaded))
                uploaded.forEach { photos.delete(it) }
            }
            PendingType.PUBLICATION_PHOTO -> {
                val photo = action.payloadAs<QueuedPhoto>().toDomain()
                publications.addPhoto(action.poiId, uploader.uploadNow(photo))
                photos.delete(photo)
            }
            else -> error("No es un envío de publicación: ${action.type}")
        }
    }
}

private val queueJson = Json { ignoreUnknownKeys = true }

// Sin tipos de java.time en el JSON: días como 1 (lunes) a 7 y horas en minutos desde la medianoche.

@Serializable
internal data class QueuedPhoto(val id: String, val path: String, val name: String, val remoteUrl: String? = null) {
    fun toDomain() = DraftPhoto(id, path, name, remoteUrl)
}

@Serializable
internal data class QueuedPublication(
    val title: String,
    val description: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val latitude: Double,
    val longitude: Double,
    val days: List<Int>? = null,
    val opensMinute: Int? = null,
    val closesMinute: Int? = null,
    val price: PriceRange? = null,
    val photos: List<QueuedPhoto>,
    val similarIds: List<String> = emptyList(),
    val duplicateNote: String = "",
    val duplicateCheckFailed: Boolean = false,
    val duplicateChecked: Boolean = false,
    val resubmitId: String? = null,
) {
    fun toDomain(): PublicationSubmission {
        val location = GeoPoint(latitude, longitude)
        val opens = opensMinute
        val closes = closesMinute
        return PublicationSubmission(
            title = title,
            description = description,
            category = category,
            categoryOrigin = categoryOrigin,
            location = location,
            hours = if (days != null && opens != null && closes != null) {
                OpeningHours(days.map(DayOfWeek::of).toSet(), opens.toTime(), closes.toTime())
            } else {
                null
            },
            price = price,
            photos = photos.map(QueuedPhoto::toDomain),
            duplicateCheck = if (duplicateChecked) DuplicateCheck(location, similarIds, duplicateNote, duplicateCheckFailed) else null,
            resubmitId = resubmitId,
        )
    }
}

private fun DraftPhoto.toQueued() = QueuedPhoto(id, path, name, remoteUrl)

private fun LocalTime.toMinute() = hour * 60 + minute

private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)

private fun PublicationSubmission.toQueued() = QueuedPublication(
    title = title,
    description = description,
    category = category,
    categoryOrigin = categoryOrigin,
    latitude = location.latitude,
    longitude = location.longitude,
    days = hours?.days?.map { it.value }?.sorted(),
    opensMinute = hours?.opens?.toMinute(),
    closesMinute = hours?.closes?.toMinute(),
    price = price,
    photos = photos.map { it.toQueued() },
    similarIds = duplicateCheck?.similarIds.orEmpty(),
    duplicateNote = duplicateCheck?.note.orEmpty(),
    duplicateCheckFailed = duplicateCheck?.failed == true,
    duplicateChecked = duplicateCheck != null,
    resubmitId = resubmitId,
)
