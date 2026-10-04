package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.remote.ProfileApi
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Personas con la API (SAD: componente de Usuarios y Reputación): el perfil propio, el público y los reportes. Lo propio
 * para ver sin conexión lo guarda OfflineUserRepository, que envuelve a este.
 */
class ApiUserRepository(private val api: ProfileApi, private val location: LocationProvider) : UserRepository {

    /** 31 · null si la persona ya no existe. Las distancias de sus lugares se miden desde la ubicación de la persona. */
    override suspend fun publicProfile(userId: String): PublicProfile? = api.publicProfile(userId, near())

    override suspend fun reportUser(userId: String, reason: ReportReason) = api.reportUser(userId, reason)

    override suspend fun ownProfile(): OwnProfile = api.ownProfile()

    /**
     * 28 · Primero la foto (subirla es lo que más puede fallar) y después los datos. Si la foto falla, nada cambia; si
     * fallan los datos, la foto nueva ya quedó.
     */
    override suspend fun updateProfile(update: ProfileUpdate): OwnProfile {
        when (val photo = update.photo) {
            PhotoChange.Keep -> Unit
            PhotoChange.Remove -> api.removePhoto()
            is PhotoChange.Replace -> api.uploadPhoto(File(photo.path))
        }
        return api.updateProfile(update.name, update.bio, update.residency)
    }

    private suspend fun near(): GeoPoint? = try {
        location.currentLocation()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
