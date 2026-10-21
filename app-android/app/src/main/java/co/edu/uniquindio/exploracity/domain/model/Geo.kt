package co.edu.uniquindio.exploracity.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Coordenada geográfica en grados. Sin tipos de Google Maps: el dominio no depende del SDK. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    /** Distancia en metros sobre la superficie terrestre (fórmula del semiverseno). */
    fun distanceTo(other: GeoPoint): Int {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(other.longitude - longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return (2 * EARTH_RADIUS_METERS * asin(sqrt(h))).roundToInt()
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

/** Rectángulo de coordenadas: el área visible del mapa (8). No cruza el antimeridiano (no hace falta en Colombia). */
data class GeoBounds(val southwest: GeoPoint, val northeast: GeoPoint) {
    operator fun contains(point: GeoPoint): Boolean =
        point.latitude in southwest.latitude..northeast.latitude &&
            point.longitude in southwest.longitude..northeast.longitude

    companion object {
        /** 17.b · Unos 11 km a cada lado: un municipio entero y sus alrededores. */
        const val TOWN_SPAN_DEGREES = 0.1

        /** Un rectángulo de [span] grados a cada lado de [center]. */
        fun around(center: GeoPoint, span: Double = TOWN_SPAN_DEGREES) = GeoBounds(
            southwest = GeoPoint(center.latitude - span, center.longitude - span),
            northeast = GeoPoint(center.latitude + span, center.longitude + span),
        )
    }
}
