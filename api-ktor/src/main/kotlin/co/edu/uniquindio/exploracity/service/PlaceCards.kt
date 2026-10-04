package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.model.AuthorResponse
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.HoursResponse
import co.edu.uniquindio.exploracity.model.PlaceSummaryResponse
import co.edu.uniquindio.exploracity.repository.AuthorRow
import co.edu.uniquindio.exploracity.repository.PlaceRow
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Cómo se cuenta un lugar público en el feed (7), el mapa (8), el detalle (13) y el perfil público (31). */
class PlaceCards(private val city: CitySettings, private val clock: Clock) {

    fun summaryOf(row: PlaceRow) = PlaceSummaryResponse(
        id = row.id.toString(),
        title = row.title,
        category = row.category,
        status = row.status,
        location = GeoPoint(row.latitude, row.longitude),
        distanceMeters = row.distanceMeters,
        votes = row.votes,
        comments = row.comments,
        photo = row.cover,
        price = row.price,
        openNow = openNow(row),
        summary = shortSummary(row.description),
    )

    fun hoursOf(row: PlaceRow): HoursResponse? {
        val bits = row.hoursDays ?: return null
        val opens = row.opens ?: return null
        val closes = row.closes ?: return null
        return HoursResponse(days(bits).map { it.name }, opens.toString(), closes.toString())
    }

    /** Sin un punto en la petición, la distancia se mide desde el centro de la ciudad. */
    fun origin(near: GeoPoint?): GeoPoint = near ?: city.center

    /**
     * Según el horario y la hora de la ciudad. La franja no cruza la medianoche: el formulario pide que el cierre sea
     * después de la apertura (18).
     */
    private fun openNow(row: PlaceRow): Boolean? {
        val bits = row.hoursDays ?: return null
        val opens = row.opens ?: return null
        val closes = row.closes ?: return null
        val now = LocalDateTime.ofInstant(clock.instant(), city.timeZone)
        val time = now.toLocalTime()
        return now.dayOfWeek in days(bits) && !time.isBefore(opens) && time.isBefore(closes)
    }

    companion object {
        /** Lo que cabe en la tarjeta del mapa (8). */
        private const val SUMMARY_MAX = 90

        private val sentenceEnd = Regex("(?<=[.!?])\\s")

        /** Bits de lunes (1) a domingo (64). */
        fun days(bits: Int): List<DayOfWeek> = DayOfWeek.entries.filter { bits and (1 shl it.ordinal) != 0 }

        /**
         * La frase de la tarjeta del mapa: la primera oración de la descripción. Si es muy larga, se corta en la última
         * palabra que cabe.
         */
        fun shortSummary(description: String): String {
            val sentence = description.trim().split(sentenceEnd, limit = 2).first().trim()
            if (sentence.length <= SUMMARY_MAX) return sentence
            val cut = sentence.take(SUMMARY_MAX - 1)
            val lastSpace = cut.lastIndexOf(' ')
            return (if (lastSpace > 0) cut.take(lastSpace) else cut).trimEnd(',', ';', ':', ' ') + "…"
        }

        fun authorOf(row: AuthorRow) = AuthorResponse(row.id.toString(), row.name, row.points)
    }
}
