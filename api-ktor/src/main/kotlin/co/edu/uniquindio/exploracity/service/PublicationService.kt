package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.integration.CategoryClassifier
import co.edu.uniquindio.exploracity.integration.ClassifierException
import co.edu.uniquindio.exploracity.integration.ImageType
import co.edu.uniquindio.exploracity.integration.MediaStore
import co.edu.uniquindio.exploracity.integration.MediaStoreException
import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.ChangesRequest
import co.edu.uniquindio.exploracity.model.DuplicateCheckRequest
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.HoursResponse
import co.edu.uniquindio.exploracity.model.PhotoResponse
import co.edu.uniquindio.exploracity.model.PriceRange
import co.edu.uniquindio.exploracity.model.PublicationResponse
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.RejectionReason
import co.edu.uniquindio.exploracity.model.SimilarPlaceResponse
import co.edu.uniquindio.exploracity.model.SubmissionRequest
import co.edu.uniquindio.exploracity.model.SubmitResponse
import co.edu.uniquindio.exploracity.model.SuggestionResponse
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.OwnPlaceRow
import co.edu.uniquindio.exploracity.repository.PlaceFields
import co.edu.uniquindio.exploracity.repository.PlaceRepository
import co.edu.uniquindio.exploracity.repository.PublicationRepository
import co.edu.uniquindio.exploracity.repository.StoredPhoto
import co.edu.uniquindio.exploracity.repository.UserRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Componente de Puntos de Interés (SAD: API de Publicaciones), con la Detección de Duplicados (ADR-13 y ADR-14) y la
 * Clasificación IA (ADR-11 y ADR-12): el formulario de publicación (15–20) y las publicaciones propias (22–24).
 */
class PublicationService(
    private val database: Database,
    private val publications: PublicationRepository,
    private val places: PlaceRepository,
    private val users: UserRepository,
    private val reputation: ReputationService,
    private val views: PublicationViews,
    private val classifier: CategoryClassifier,
    private val media: MediaStore,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(PublicationService::class.java)

    /** 19 · Sube una foto del formulario (JPEG, PNG o WebP de hasta 8 MB). Queda sin publicación hasta el envío. */
    suspend fun uploadPhoto(userId: UUID, bytes: ByteArray): PhotoResponse {
        val type = ImageType.detect(bytes) ?: throw ApiException(HttpStatusCode.UnsupportedMediaType, "unsupported_photo_type")
        database.query { user(userId) }
        val stored = try {
            media.upload(bytes, type, PHOTO_FOLDER)
        } catch (e: MediaStoreException) {
            log.warn("No se pudo subir la foto de una publicación.", e)
            throw ApiException(HttpStatusCode.BadGateway, "photo_upload_failed")
        }
        val id = try {
            database.query { publications.addPhoto(userId, stored.url, stored.publicId, clock.instant()) }
        } catch (e: Exception) {
            discard(listOf(stored.publicId))
            throw e
        }
        return PhotoResponse(id.toString(), stored.url)
    }

    /**
     * 16 · La categoría más probable para el título y la descripción. Sin una clara, sin categoría. Si el servicio de IA
     * falla o tarda, 503: la app sigue a mano y ofrece reintentar (ADR-12).
     */
    suspend fun suggestCategory(title: String, description: String): SuggestionResponse {
        if (title.isBlank() || title.length > TITLE_MAX || description.length > DESCRIPTION_MAX) throw invalid()
        return try {
            SuggestionResponse(classifier.classify(title, description))
        } catch (e: ClassifierException) {
            log.warn("La sugerencia de categoría no respondió.", e)
            throw ApiException(HttpStatusCode.ServiceUnavailable, "suggestion_unavailable")
        }
    }

    /** 17 · Hasta 3 lugares a 50 m o menos del pin con un título parecido, del más cercano al más lejano. */
    suspend fun similar(userId: UUID, title: String, at: GeoPoint, excludeId: String?): List<SimilarPlaceResponse> {
        if (title.isBlank()) return emptyList()
        val exclude = excludeId?.let { uuidOrNull(it) }
        return database.query { publications.similar(title, at, userId, exclude, SIMILAR_RADIUS_METERS, SIMILAR_MAX) }.map { row ->
            SimilarPlaceResponse(row.id.toString(), row.title, row.category, row.status, GeoPoint(row.latitude, row.longitude), row.distanceMeters, row.cover)
        }
    }

    /**
     * 20 · Envía la publicación a verificación, o reenvía una rechazada que el moderador permitió corregir. La primera
     * publicación de la persona gana +20 al enviarla (D1). Con un [SubmissionRequest.clientId] que ya llegó, devuelve la
     * que estaba y `false`.
     */
    suspend fun submit(userId: UUID, request: SubmissionRequest): Pair<SubmitResponse, Boolean> {
        val fields = fieldsOf(request.title, request.description, request.category, request.location, request.address, request.hours, request.price)
        val urls = photoUrls(request.photos)
        val clientId = request.clientId?.let { uuidOrNull(it) ?: throw ApiException.badRequest() }
        val resubmitId = request.resubmitId?.let { uuidOrNull(it) ?: throw notFound() }
        var removed = emptyList<String>()
        val result = database.query {
            user(userId)
            clientId?.let { publications.byClientId(userId, it) }?.let { existing ->
                // Un reenvío repetido no vuelve a anunciar los puntos de la primera publicación.
                val first = existing != resubmitId && publications.find(existing)?.firstPublication == true
                return@query SubmitResponse(existing.toString(), if (first) FIRST_PUBLICATION_POINTS else null) to false
            }
            val photos = ownedPhotos(userId, urls, allowedPlace = resubmitId)
            val duplicate = duplicateOf(userId, request.title, request.location, request.duplicateCheck, excludeId = resubmitId)
            val now = clock.instant()
            if (resubmitId != null) {
                val place = own(userId, resubmitId, lock = true)
                val decision = publications.latestDecision(place.id)
                val allowed = place.status == PublicationStatus.REJECTED && decision?.canResubmit == true &&
                    decision.rejectionReason != RejectionReason.DUPLICATE
                if (!allowed) throw ApiException.conflict("cannot_resubmit")
                // Con el clientId del reenvío, repetirlo responde la misma en vez de cannot_resubmit.
                publications.update(place.id, fields, PublicationStatus.PENDING, duplicate.flag, duplicate.note, now, request.categoryOrigin, clientId)
                publications.setSimilar(place.id, duplicate.similarIds)
                removed = replacePhotos(place.id, photos)
                SubmitResponse(place.id.toString()) to true
            } else {
                val first = publications.countByAuthor(userId) == 0
                val id = publications.insert(
                    authorId = userId,
                    clientId = clientId,
                    fields = fields,
                    categoryOrigin = request.categoryOrigin,
                    possibleDuplicate = duplicate.flag,
                    duplicateNote = duplicate.note,
                    pointsEarned = if (first) FIRST_PUBLICATION_POINTS else 0,
                    firstPublication = first,
                    at = now,
                )
                publications.setSimilar(id, duplicate.similarIds)
                publications.assignPhotos(id, photos.map { it.id })
                reputation.awardBadges(userId)
                SubmitResponse(id.toString(), if (first) FIRST_PUBLICATION_POINTS else null) to true
            }
        }
        discard(removed)
        return result
    }

    /** 22 · Todas las propias, de la más reciente a la más antigua. */
    suspend fun mine(userId: UUID): List<PublicationResponse> = database.query {
        user(userId)
        publications.byAuthor(userId).map { views.publicationOf(it) }
    }

    /** 24 · Una propia; 404 si no existe o es de otra persona. */
    suspend fun one(userId: UUID, publicationId: String): PublicationResponse = database.query {
        views.publicationOf(own(userId, idOf(publicationId)))
    }

    /**
     * 23 · Guarda los cambios de una pendiente o una verificada. Vuelve a verificación: una verificada sale del feed y
     * entra a la cola como recién enviada. Si se movió el pin, la marca de posible duplicado es la de la búsqueda del
     * lugar nuevo; si no, se conserva.
     */
    suspend fun update(userId: UUID, publicationId: String, request: ChangesRequest): PublicationResponse {
        val id = idOf(publicationId)
        val fields = fieldsOf(request.title, request.description, request.category, request.location, request.address, request.hours, request.price)
        val urls = photoUrls(request.photos)
        var removed = emptyList<String>()
        val result = database.query {
            val place = own(userId, id, lock = true)
            if (place.status != PublicationStatus.PENDING && place.status != PublicationStatus.VERIFIED) throw ApiException.conflict("not_editable")
            val photos = ownedPhotos(userId, urls, allowedPlace = id)
            val moved = GeoPoint(place.fields.latitude, place.fields.longitude) != request.location
            val duplicate = if (moved) {
                duplicateOf(userId, request.title, request.location, request.duplicateCheck, excludeId = id)
            } else {
                Duplicate(place.possibleDuplicate, publications.similarIds(id), place.duplicateNote)
            }
            // Con el pin en su sitio y sin dirección nueva, queda la que tenía: la app no la conoce.
            val kept = if (moved || fields.address != null) fields else fields.copy(address = place.fields.address)
            val resubmittedAt = if (place.status == PublicationStatus.VERIFIED) clock.instant() else null
            publications.update(id, kept, PublicationStatus.PENDING, duplicate.flag, duplicate.note, resubmittedAt)
            if (moved) publications.setSimilar(id, duplicate.similarIds)
            removed = replacePhotos(id, photos)
            views.publicationOf(checkNotNull(publications.find(id)))
        }
        discard(removed)
        return result
    }

    /** 23 · La borra con sus fotos (también del almacén), comentarios y votos. Sus puntos dejan de contar. */
    suspend fun delete(userId: UUID, publicationId: String) {
        val id = idOf(publicationId)
        val photos = database.query {
            own(userId, id)
            val ids = publications.photosOf(id).map { it.publicId }
            publications.delete(id)
            ids
        }
        discard(photos)
    }

    /** 19 · Una foto que terminó de subir después del envío se agrega al final. Agregarla otra vez no cambia nada. */
    suspend fun addPhoto(userId: UUID, publicationId: String, url: String) {
        val id = idOf(publicationId)
        database.query {
            own(userId, id, lock = true)
            val photo = publications.ownPhotos(userId, listOf(url)).singleOrNull() ?: throw invalidPhotos()
            if (photo.placeId == id) return@query
            if (photo.placeId != null) throw invalidPhotos()
            val current = publications.photosOf(id)
            if (current.size >= PHOTOS_MAX) throw ApiException.conflict("too_many_photos")
            publications.assignPhotos(id, current.map { it.id } + photo.id)
        }
    }

    private class Duplicate(val flag: Boolean, val similarIds: List<UUID>, val note: String?)

    /**
     * ADR-14 · Con la búsqueda que se hizo en el teléfono para este mismo pin, valen los parecidos que la persona dijo
     * que son otro lugar y su nota. Si no hubo búsqueda, falló o el pin se movió después, la API la repite. La
     * advertencia nunca bloquea: el moderador ve la marca.
     */
    private fun duplicateOf(userId: UUID, title: String, location: GeoPoint, check: DuplicateCheckRequest?, excludeId: UUID?): Duplicate {
        val note = check?.note?.trim()?.ifEmpty { null }
        if ((note?.length ?: 0) > NOTE_MAX) throw invalid()
        if (check != null && !check.failed && check.location == location) {
            val ids = check.similarIds.mapNotNull { uuidOrNull(it) }
            val known = places.findPublic(ids, location).map { it.id }
            return Duplicate(known.isNotEmpty(), known, note.takeIf { known.isNotEmpty() })
        }
        // El moderador compara con lo publicado (33A): los pendientes propios no cuentan como posible duplicado.
        val found = publications.similar(title, location, userId, excludeId, SIMILAR_RADIUS_METERS, SIMILAR_MAX)
            .filter { it.status == PublicationStatus.VERIFIED || it.status == PublicationStatus.FINALIZED }
            .map { it.id }
        return Duplicate(found.isNotEmpty(), found, null)
    }

    /** Las fotos de [urls], en ese orden: todas de la persona y sin otra publicación (o de [allowedPlace]). */
    private fun ownedPhotos(userId: UUID, urls: List<String>, allowedPlace: UUID?): List<StoredPhoto> {
        val byUrl = publications.ownPhotos(userId, urls).associateBy { it.url }
        return urls.map { url ->
            val photo = byUrl[url] ?: throw invalidPhotos()
            if (photo.placeId != null && photo.placeId != allowedPlace) throw invalidPhotos()
            photo
        }
    }

    /** Las fotos de [placeId] pasan a ser [photos]; las que salieron se borran. Devuelve sus ids en el almacén. */
    private fun replacePhotos(placeId: UUID, photos: List<StoredPhoto>): List<String> {
        val keep = photos.map { it.id }.toSet()
        val removed = publications.photosOf(placeId).filter { it.id !in keep }
        publications.assignPhotos(placeId, photos.map { it.id })
        publications.deletePhotos(removed.map { it.id })
        return removed.map { it.publicId }
    }

    private fun fieldsOf(
        title: String,
        description: String,
        category: Category,
        location: GeoPoint,
        address: String?,
        hours: HoursResponse?,
        price: PriceRange?,
    ): PlaceFields {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        if (cleanTitle.length !in TITLE_MIN..TITLE_MAX || cleanDescription.length !in DESCRIPTION_MIN..DESCRIPTION_MAX) throw invalid()
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) throw invalid()
        val cleanAddress = address?.trim()?.ifEmpty { null }?.take(ADDRESS_MAX)
        val (days, opens, closes) = hours?.let(::hoursOf) ?: Triple(null, null, null)
        return PlaceFields(cleanTitle, cleanDescription, category, location.latitude, location.longitude, cleanAddress, price, days, opens, closes)
    }

    /** 18 · Al menos un día y el cierre después de la apertura. */
    private fun hoursOf(hours: HoursResponse): Triple<Int, LocalTime, LocalTime> {
        val days = hours.days.map { name -> DayOfWeek.entries.firstOrNull { it.name == name } ?: throw invalid() }.toSet()
        val opens = time(hours.opens)
        val closes = time(hours.closes)
        if (days.isEmpty() || !closes.isAfter(opens)) throw invalid()
        return Triple(days.sumOf { 1 shl it.ordinal }, opens, closes)
    }

    private fun time(text: String): LocalTime = try {
        LocalTime.parse(text)
    } catch (e: DateTimeParseException) {
        throw invalid()
    }

    /** 19 · De 1 a 5, sin repetir. */
    private fun photoUrls(urls: List<String>): List<String> {
        val clean = urls.map { it.trim() }
        if (clean.size !in PHOTOS_MIN..PHOTOS_MAX || clean.toSet().size != clean.size) throw invalidPhotos()
        return clean
    }

    /** Dentro de una transacción. La cuenta del token ya no existe: la sesión no vale (401). */
    private fun user(userId: UUID) = users.findById(userId) ?: throw ApiException.unauthorized()

    /** Dentro de una transacción: la publicación de [userId]; 404 si no existe o es de otra persona. */
    private fun own(userId: UUID, id: UUID, lock: Boolean = false): OwnPlaceRow {
        val place = publications.find(id, lock) ?: throw notFound()
        if (place.authorId != userId) throw notFound()
        return place
    }

    private fun idOf(text: String): UUID = uuidOrNull(text) ?: throw notFound()

    private fun uuidOrNull(text: String): UUID? = runCatching { UUID.fromString(text) }.getOrNull()

    /** Borrar del almacén no debe tumbar lo que ya se guardó: si falla, queda en el registro. */
    private suspend fun discard(publicIds: List<String>) {
        publicIds.forEach { publicId ->
            try {
                media.delete(publicId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("La foto {} quedó en el almacén sin usarse.", publicId, e)
            }
        }
    }

    private fun invalid() = ApiException.badRequest("invalid_publication")

    private fun invalidPhotos() = ApiException.badRequest("invalid_photos")

    private fun notFound() = ApiException.notFound("publication_not_found")

    companion object {
        /** G2 · D1: la primera publicación de una persona, al enviarla. */
        const val FIRST_PUBLICATION_POINTS = 20

        /** README · Publicación: título 5–60 y descripción 30–600. */
        const val TITLE_MIN = 5
        const val TITLE_MAX = 60
        const val DESCRIPTION_MIN = 30
        const val DESCRIPTION_MAX = 600

        /** README 19: de 1 a 5 fotos. */
        const val PHOTOS_MIN = 1
        const val PHOTOS_MAX = 5

        /** 17B · «¿En qué se diferencia?». */
        const val NOTE_MAX = 200

        private const val ADDRESS_MAX = 200

        /** ADR-13 · Radio y máximo de parecidos (17A). */
        const val SIMILAR_RADIUS_METERS = 50.0
        const val SIMILAR_MAX = 3

        /** Carpeta del almacén para las fotos de las publicaciones. */
        const val PHOTO_FOLDER = "lugares"
    }
}
