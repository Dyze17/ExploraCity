package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant

/** v1.4.0 · Motivos de reporte de un lugar (13), los mismos de la API. Con [OTHER], el detalle es obligatorio. */
enum class PlaceReportReason { CLOSED, WRONG_LOCATION, OUTDATED_INFO, BAD_PHOTOS, INAPPROPRIATE, DUPLICATE, OTHER }

/** v1.4.0 · Un reporte de un lugar como lo ve la moderación: sin quién lo hizo, porque es anónimo. */
data class PlaceReport(val reason: PlaceReportReason, val detail: String?, val createdAt: Instant) {
    companion object {
        /** Como los demás textos de la comunidad (README 14): hasta 300 caracteres. */
        const val DETAIL_MAX = 300
    }
}
