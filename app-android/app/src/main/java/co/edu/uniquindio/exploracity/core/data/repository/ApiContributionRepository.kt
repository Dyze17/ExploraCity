package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.remote.PoiApi
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.repository.ContributionRepository

/** v1.4.0 · Los reportes de lugares van a la API, que los guarda sin quién los hizo para la moderación. */
class ApiContributionRepository(private val api: PoiApi) : ContributionRepository {
    override suspend fun reportPlace(placeId: String, reason: PlaceReportReason, detail: String?) = api.report(placeId, reason, detail)
}
