package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.repository.ContributionRepository

/**
 * Sin red no se intenta ni se encola: reportar es una acción deliberada, como el reporte de un perfil (31A), y las
 * fotos que se agregan ya subieron con red.
 */
class OnlineOnlyContributionRepository(
    private val remote: ContributionRepository,
    private val connectivity: ConnectivityObserver,
) : ContributionRepository {
    override suspend fun addPhotos(placeId: String, urls: List<String>) {
        if (!connectivity.isOnline.value) throw OfflineException()
        remote.addPhotos(placeId, urls)
    }

    override suspend fun reportPlace(placeId: String, reason: PlaceReportReason, detail: String?) {
        if (!connectivity.isOnline.value) throw OfflineException()
        remote.reportPlace(placeId, reason, detail)
    }
}
