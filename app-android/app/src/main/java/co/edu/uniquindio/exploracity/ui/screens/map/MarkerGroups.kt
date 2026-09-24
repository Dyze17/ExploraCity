package co.edu.uniquindio.exploracity.ui.screens.map

import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Poi

/** Lo que se dibuja en el mapa: un lugar suelto o un grupo de lugares que en pantalla se tocarían. */
sealed interface MapMarker {
    data class Place(val poi: Poi) : MapMarker

    data class Group(val pois: List<Poi>) : MapMarker {
        /** Punto medio del grupo: ahí va el círculo con el número. */
        val center: GeoPoint = GeoPoint(pois.map { it.location.latitude }.average(), pois.map { it.location.longitude }.average())

        /** Al tocar el grupo la cámara se acerca hasta que todos se vean. */
        val bounds: GeoBounds = GeoBounds(
            southwest = GeoPoint(pois.minOf { it.location.latitude }, pois.minOf { it.location.longitude }),
            northeast = GeoPoint(pois.maxOf { it.location.latitude }, pois.maxOf { it.location.longitude }),
        )
    }
}

/** Posición en pantalla, en píxeles. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Agrupa los lugares cuya posición en pantalla queda a menos de [radiusPx] de otro ya elegido como centro del
 * grupo. Recorre [pois] en orden (el más cercano a la persona primero), así el resultado es estable entre
 * consultas. El lugar [selectedId] nunca se agrupa: su marcador crece y debe seguir a la vista. Los lugares sin
 * posición en pantalla ([screenPoint] null) quedan sueltos.
 */
fun groupMarkers(
    pois: List<Poi>,
    screenPoint: (Poi) -> ScreenPoint?,
    radiusPx: Float,
    selectedId: String? = null,
): List<MapMarker> {
    val points = pois.map { it to screenPoint(it) }
    val grouped = BooleanArray(points.size)
    val markers = mutableListOf<MapMarker>()
    val radiusSquared = radiusPx * radiusPx
    for (i in points.indices) {
        if (grouped[i]) continue
        grouped[i] = true
        val (poi, point) = points[i]
        if (point == null || poi.id == selectedId) {
            markers += MapMarker.Place(poi)
            continue
        }
        val members = mutableListOf(poi)
        for (j in i + 1 until points.size) {
            val (other, otherPoint) = points[j]
            if (grouped[j] || otherPoint == null || other.id == selectedId) continue
            val dx = otherPoint.x - point.x
            val dy = otherPoint.y - point.y
            if (dx * dx + dy * dy < radiusSquared) {
                grouped[j] = true
                members += other
            }
        }
        markers += if (members.size == 1) MapMarker.Place(poi) else MapMarker.Group(members)
    }
    return markers
}
