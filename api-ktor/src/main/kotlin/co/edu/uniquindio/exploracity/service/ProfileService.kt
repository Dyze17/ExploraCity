package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.integration.ImageType
import co.edu.uniquindio.exploracity.integration.MediaStore
import co.edu.uniquindio.exploracity.integration.MediaStoreException
import co.edu.uniquindio.exploracity.model.AuthorResponse
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.ProfileResponse
import co.edu.uniquindio.exploracity.model.ProfileUpdateRequest
import co.edu.uniquindio.exploracity.model.PublicProfileResponse
import co.edu.uniquindio.exploracity.model.ReportReason
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.PlaceFilter
import co.edu.uniquindio.exploracity.repository.PlaceRepository
import co.edu.uniquindio.exploracity.repository.UserRecord
import co.edu.uniquindio.exploracity.repository.UserRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Componente de Usuarios y Reputación (SAD) · El perfil propio (26 y 28), el perfil público (31) y los reportes de
 * perfiles (31A).
 */
class ProfileService(
    private val database: Database,
    private val users: UserRepository,
    private val reputation: ReputationService,
    private val places: PlaceRepository,
    private val cards: PlaceCards,
    private val city: CitySettings,
    private val media: MediaStore,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ProfileService::class.java)

    suspend fun profile(userId: UUID): ProfileResponse = database.query { reputation.profileOf(user(userId)) }

    /** 28 · Nombre de 2 a 40 caracteres y «Sobre mí» hasta 150; vacío se guarda como null. */
    suspend fun update(userId: UUID, request: ProfileUpdateRequest): ProfileResponse {
        val name = request.name.trim()
        val bio = request.bio?.trim()?.ifEmpty { null }
        if (!AccountRules.isValidName(name)) throw ApiException.badRequest("invalid_name")
        if ((bio?.length ?: 0) > AccountRules.BIO_MAX) throw ApiException.badRequest("bio_too_long")
        return database.query {
            user(userId)
            users.updateProfile(userId, name, bio, request.residency)
            reputation.profileOf(user(userId))
        }
    }

    /**
     * 28 · Sube la foto nueva (JPEG, PNG o WebP, ya revisado el tamaño) y borra la anterior del almacén. El formato se
     * reconoce por el contenido del archivo.
     */
    suspend fun replacePhoto(userId: UUID, bytes: ByteArray): ProfileResponse {
        val type = ImageType.detect(bytes) ?: throw ApiException(HttpStatusCode.UnsupportedMediaType, "unsupported_photo_type")
        database.query { user(userId) }
        val stored = try {
            media.upload(bytes, type, PHOTO_FOLDER)
        } catch (e: MediaStoreException) {
            log.warn("No se pudo subir la foto de perfil.", e)
            throw ApiException(HttpStatusCode.BadGateway, "photo_upload_failed")
        }
        val (previous, profile) = try {
            database.query {
                val before = user(userId).photoPublicId
                users.updatePhoto(userId, stored.url, stored.publicId)
                before to reputation.profileOf(user(userId))
            }
        } catch (e: Exception) {
            // La foto nueva no quedó en el perfil: no se queda en el almacén.
            discard(stored.publicId)
            throw e
        }
        previous?.let { discard(it) }
        return profile
    }

    /** 28 · Quita la foto: el perfil vuelve a las iniciales. */
    suspend fun removePhoto(userId: UUID): ProfileResponse {
        val (previous, profile) = database.query {
            val before = user(userId).photoPublicId
            users.updatePhoto(userId, null, null)
            before to reputation.profileOf(user(userId))
        }
        previous?.let { discard(it) }
        return profile
    }

    /**
     * 31 · Lo público de una persona: sin correo, y de sus lugares solo los verificados y finalizados, con el formato del
     * feed. Una cuenta eliminada ya no tiene perfil (404).
     */
    suspend fun publicProfile(profileId: String, near: GeoPoint?): PublicProfileResponse {
        val id = runCatching { UUID.fromString(profileId) }.getOrNull() ?: throw userNotFound()
        return database.query {
            val user = users.findById(id) ?: throw userNotFound()
            val owned = places.search(PlaceFilter(authorId = id), cards.origin(near), limit = PUBLIC_PLACES_MAX)
            PublicProfileResponse(
                author = AuthorResponse(id.toString(), user.name, reputation.points(user)),
                residency = user.residency,
                city = city.name,
                bio = user.bio,
                photo = user.photoUrl,
                places = owned.map(cards::summaryOf),
                badges = reputation.unlockedCount(id),
            )
        }
    }

    /** 31A · Reporte anónimo de otro perfil; el propio no se puede reportar. */
    suspend fun report(reporterId: UUID, reportedId: String, reason: ReportReason) {
        val reported = runCatching { UUID.fromString(reportedId) }.getOrNull() ?: throw userNotFound()
        if (reported == reporterId) throw ApiException.badRequest("cannot_report_self")
        database.query {
            user(reporterId)
            users.findById(reported) ?: throw userNotFound()
            users.report(reported, reporterId, reason, clock.instant())
        }
    }

    /** Dentro de una transacción. La cuenta del token ya no existe: la sesión no vale (401). */
    private fun user(userId: UUID): UserRecord = users.findById(userId) ?: throw ApiException.unauthorized()

    /** Borrar del almacén no debe tumbar lo que ya se guardó: si falla, queda en el registro. */
    private suspend fun discard(publicId: String) {
        try {
            media.delete(publicId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("La foto {} quedó en el almacén sin usarse.", publicId, e)
        }
    }

    private fun userNotFound() = ApiException.notFound("user_not_found")

    companion object {
        /** 28 · Hasta 8 MB, como cada foto de una publicación (19). */
        const val MAX_PHOTO_BYTES = 8L * 1024 * 1024

        /** Carpeta del almacén para las fotos de perfil. */
        const val PHOTO_FOLDER = "perfil"

        /** 31 · Los lugares que muestra un perfil público. */
        private const val PUBLIC_PLACES_MAX = 200
    }
}
