package co.edu.uniquindio.exploracity.core.ui.components

import androidx.annotation.StringRes
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason

/** v1.4.0 · El motivo de un reporte de lugar, igual en el diálogo de 13, en «Reportados» y en la revisión (33). */
@get:StringRes
val PlaceReportReason.labelRes: Int
    get() = when (this) {
        PlaceReportReason.CLOSED -> R.string.place_report_reason_closed
        PlaceReportReason.WRONG_LOCATION -> R.string.place_report_reason_wrong_location
        PlaceReportReason.OUTDATED_INFO -> R.string.place_report_reason_outdated
        PlaceReportReason.BAD_PHOTOS -> R.string.place_report_reason_bad_photos
        PlaceReportReason.INAPPROPRIATE -> R.string.place_report_reason_inappropriate
        PlaceReportReason.DUPLICATE -> R.string.place_report_reason_duplicate
        PlaceReportReason.OTHER -> R.string.place_report_reason_other
    }
