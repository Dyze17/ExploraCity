package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Personas (SAD: componente de Usuarios y Reputación, aparte de Puntos de Interés). */
interface UserRepository {
    /** 31 · Perfil público; null si la persona ya no existe. Lanza excepción si falla la red. */
    suspend fun publicProfile(userId: String): PublicProfile?

    /** 31A · Reporte anónimo: lo revisa un moderador. Lanza excepción si falla la red. */
    suspend fun reportUser(userId: String, reason: ReportReason)
}

/**
 * Temporal hasta que exista la API: los perfiles de prueba con los lugares que publicó cada persona, tomados del
 * servidor de lugares ([pois]) para que sus votos y comentarios estén al día.
 */
class FakeUserRepository(
    private val pois: PoiRepository,
    private val latency: Duration = 700.milliseconds,
    private val actionLatency: Duration = 300.milliseconds,
) : UserRepository {

    /** Reportes recibidos, para las pruebas: la moderación (32) los leerá cuando exista. */
    val reports = mutableListOf<Pair<String, ReportReason>>()

    override suspend fun publicProfile(userId: String): PublicProfile? {
        delay(latency)
        val seed = sampleProfiles[userId] ?: return null
        val places = pois.feedPage(FeedQuery(), 0, pageSize = Int.MAX_VALUE).items.filter { poi ->
            sampleDetails(poi).author.id == userId && poi.status in publicStatuses
        }
        return PublicProfile(seed.author, seed.residency, seed.city, seed.bio, places, seed.badges)
    }

    override suspend fun reportUser(userId: String, reason: ReportReason) {
        delay(actionLatency)
        check(userId in sampleProfiles) { "Persona desconocida: $userId" }
        reports += userId to reason
    }

    private companion object {
        /** Lo único que un perfil público muestra de los lugares de otra persona (README 31). */
        val publicStatuses = setOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED)
    }
}

/** Sin red no hay perfiles guardados: se avisa sin intentar (la pantalla se recarga sola al volver). */
class OnlineOnlyUserRepository(
    private val remote: UserRepository,
    private val connectivity: ConnectivityObserver,
) : UserRepository {
    override suspend fun publicProfile(userId: String): PublicProfile? {
        requireOnline()
        return remote.publicProfile(userId)
    }

    override suspend fun reportUser(userId: String, reason: ReportReason) {
        requireOnline()
        remote.reportUser(userId, reason)
    }

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }
}
