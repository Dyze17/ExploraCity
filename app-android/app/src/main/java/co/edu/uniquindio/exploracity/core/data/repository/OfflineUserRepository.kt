package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.data.local.ProfileDao
import co.edu.uniquindio.exploracity.core.data.local.toDomain
import co.edu.uniquindio.exploracity.core.data.local.toEntity
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.repository.UserRepository
import java.io.IOException
import java.time.Clock

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
