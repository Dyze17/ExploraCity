package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.remote.ApiException
import co.edu.uniquindio.exploracity.core.data.remote.PoiApi
import co.edu.uniquindio.exploracity.domain.model.PlaceFullException
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.repository.ContributionRepository

/**
 * v1.4.0 · Los reportes van a la API, que los guarda sin quién los hizo; las fotos agregadas esperan allí a un
 * moderador.
 */
class ApiContributionRepository(private val api: PoiApi) : ContributionRepository {
    /** Ya no caben (409 too_many_photos): [PlaceFullException]. */
    override suspend fun addPhotos(placeId: String, urls: List<String>) {
        try {
            api.addPhotos(placeId, urls)
        } catch (e: ApiException) {
            if (e.code == TOO_MANY_PHOTOS) throw PlaceFullException()
            throw e
        }
    }

    override suspend fun reportPlace(placeId: String, reason: PlaceReportReason, detail: String?) = api.report(placeId, reason, detail)

    private companion object {
        const val TOO_MANY_PHOTOS = "too_many_photos"
    }
}
