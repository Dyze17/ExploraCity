package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ProfileDao
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.ProfileLimits
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Personas (SAD: componente de Usuarios y Reputación, aparte de Puntos de Interés). */
interface UserRepository {
    /** 31 · Perfil público; null si la persona ya no existe. Lanza excepción si falla la red. */
    suspend fun publicProfile(userId: String): PublicProfile?

    /** 31A · Reporte anónimo: lo revisa un moderador. Lanza excepción si falla la red. */
    suspend fun reportUser(userId: String, reason: ReportReason)

    /** 26 y 27 · El perfil de la persona de la sesión. Lanza excepción si falla la red y no hay nada guardado. */
    suspend fun ownProfile(): OwnProfile

    /** 28 · Guarda nombre, «Sobre mí», cómo se presenta y la foto (que sube si es nueva). Lanza excepción si falla la red. */
    suspend fun updateProfile(update: ProfileUpdate): OwnProfile
}

/**
 * Temporal hasta que exista la API: los perfiles de prueba con los lugares que publicó cada persona, tomados del
 * servidor de lugares ([pois]) para que sus votos y comentarios estén al día. Las publicaciones que el feed no muestra
 * salen de [publications]: al borrar una, las cifras del perfil bajan.
 */
class FakeUserRepository(
    private val pois: FakePoiRepository,
    private val publications: FakePublicationRepository = FakePublicationRepository(pois),
    private val currentUser: Author = sampleCurrentUser,
    private val latency: Duration = 700.milliseconds,
    private val actionLatency: Duration = 300.milliseconds,
    /** «Sube» la foto de perfil del teléfono y devuelve su dirección; con la API la sube el servidor (Cloudinary). */
    private val photoHost: suspend (String) -> String = { path -> "fake://perfil/${path.substringAfterLast('/')}" },
    /** Borra la foto «subida» al quitarla o al eliminar la cuenta. */
    private val photoRemover: suspend () -> Unit = {},
) : UserRepository {

    /** Reportes recibidos, para las pruebas: la moderación (32) los leerá cuando exista. */
    val reports = mutableListOf<Pair<String, ReportReason>>()

    // Lo que cambie la persona de la sesión en 28 vive aquí, encima de las semillas de prueba.
    private val seeds = sampleProfiles.toMutableMap()
    private val photos = mutableMapOf<String, String>()

    override suspend fun publicProfile(userId: String): PublicProfile? {
        delay(latency)
        val seed = seeds[userId] ?: return null
        val places = placesBy(userId).filter { it.status in publicStatuses }
        return PublicProfile(seed.author, seed.residency, seed.city, seed.bio, places, seed.badges, photos[userId])
    }

    override suspend fun reportUser(userId: String, reason: ReportReason) {
        delay(actionLatency)
        check(userId in seeds) { "Persona desconocida: $userId" }
        reports += userId to reason
    }

    override suspend fun ownProfile(): OwnProfile {
        delay(latency)
        return currentProfile()
    }

    /** Valida como lo hará la API: la pantalla ya no deja enviar nada fuera de las reglas de 28. */
    override suspend fun updateProfile(update: ProfileUpdate): OwnProfile {
        delay(actionLatency)
        require(update.name.length in ProfileLimits.NAME_MIN..ProfileLimits.NAME_MAX) { "Nombre de ${update.name.length} caracteres" }
        require((update.bio?.length ?: 0) <= ProfileLimits.BIO_MAX) { "«Sobre mí» de más de ${ProfileLimits.BIO_MAX} caracteres" }
        when (val photo = update.photo) {
            PhotoChange.Keep -> Unit
            PhotoChange.Remove -> {
                photos.remove(currentUser.id)
                photoRemover()
            }
            is PhotoChange.Replace -> photos[currentUser.id] = photoHost(photo.path)
        }
        val seed = seeds.getValue(currentUser.id)
        seeds[currentUser.id] = seed.copy(author = seed.author.copy(name = update.name), residency = update.residency, bio = update.bio)
        return currentProfile()
    }

    /** 30 · Temporal: lo que hace la API al borrar la cuenta con el perfil. Vuelve a la semilla y sin foto. */
    internal suspend fun deleteOwnAccount() {
        photos.remove(currentUser.id)
        photoRemover()
        seeds[currentUser.id] = sampleProfiles.getValue(currentUser.id)
    }

    /** Sus lugares del feed más los que el feed no muestra (pendientes y rechazados). */
    private suspend fun currentProfile(): OwnProfile {
        val seed = seeds.getValue(currentUser.id)
        val statuses = placesBy(currentUser.id).map { it.status } + publications.hiddenStatuses
        return OwnProfile(
            author = seed.author,
            residency = seed.residency,
            city = seed.city,
            memberSince = sampleMemberSince,
            publications = PublicationCounts.of(statuses),
            badges = sampleBadges,
            bio = seed.bio,
            photo = photos[currentUser.id],
        )
    }

    private suspend fun placesBy(userId: String): List<Poi> =
        pois.feedPage(FeedQuery(), 0, pageSize = Int.MAX_VALUE).items.filter { pois.detailsOf(it).author.id == userId }

    private companion object {
        /** Lo único que un perfil público muestra de los lugares de otra persona (README 31). */
        val publicStatuses = setOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED)
    }
}

/**
 * Los perfiles de otras personas necesitan red: sin ella se avisa sin intentar (la pantalla se recarga sola al
 * volver). El propio se guarda en Room cada vez que llega, para verlo sin conexión con su antigüedad (como Avisos).
 */
class OfflineUserRepository(
    private val remote: UserRepository,
    private val dao: ProfileDao,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock = Clock.systemUTC(),
) : UserRepository {
    override suspend fun publicProfile(userId: String): PublicProfile? {
        requireOnline()
        return remote.publicProfile(userId)
    }

    override suspend fun reportUser(userId: String, reason: ReportReason) {
        requireOnline()
        remote.reportUser(userId, reason)
    }

    override suspend fun ownProfile(): OwnProfile {
        if (!connectivity.isOnline.value) return saved() ?: throw OfflineException()
        val fresh = try {
            remote.ownProfile()
        } catch (e: IOException) {
            // Hay red pero el servidor no respondió: mejor lo guardado que un error.
            return saved() ?: throw e
        }
        dao.save(fresh.toEntity(clock.millis()))
        return fresh
    }

    /** Lo guardado se pone al día con lo que respondió el servidor: sin conexión, 26 ya muestra los cambios. */
    override suspend fun updateProfile(update: ProfileUpdate): OwnProfile {
        requireOnline()
        val fresh = remote.updateProfile(update)
        dao.save(fresh.toEntity(clock.millis()))
        return fresh
    }

    private suspend fun saved(): OwnProfile? = dao.get()?.toDomain()

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }
}
