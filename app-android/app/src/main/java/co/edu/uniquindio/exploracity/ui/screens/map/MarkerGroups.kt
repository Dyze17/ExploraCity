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
 * Agrupa los lugares que en pantalla quedarían a menos de [radiusPx] entre sí. Primero reúne alrededor de cada
 * lugar los que tiene cerca; luego funde los grupos cuyos centros aún se encimarían, hasta que ninguno se toque.
 * Recorre [pois] en orden (el más cercano a la persona primero), así el resultado es estable entre consultas.
 * El lugar [selectedId] nunca se agrupa: su marcador crece y debe seguir a la vista. Los lugares sin posición en
 * pantalla ([screenPoint] null) quedan sueltos.
 */
fun groupMarkers(
    pois: List<Poi>,
    screenPoint: (Poi) -> ScreenPoint?,
    radiusPx: Float,
    selectedId: String? = null,
): List<MapMarker> {
    val points = pois.map(screenPoint)
    val radiusSquared = radiusPx * radiusPx
    val loose = mutableListOf<Int>()
    val clusters = mutableListOf<Cluster>()
    val taken = BooleanArray(pois.size)
    for (i in pois.indices) {
        if (taken[i]) continue
        taken[i] = true
        val point = points[i]
        if (point == null || pois[i].id == selectedId) {
            loose += i
            continue
        }
        val cluster = Cluster(mutableListOf(i), point.x, point.y)
        for (j in i + 1 until pois.size) {
            val other = points[j] ?: continue
            if (taken[j] || pois[j].id == selectedId) continue
            if (squaredDistance(point.x, point.y, other.x, other.y) < radiusSquared) {
                taken[j] = true
                cluster.add(j, other)
            }
        }
        clusters += cluster
    }
    mergeOverlapping(clusters, radiusSquared)

    val markers = clusters.map { cluster ->
        val members = cluster.members.sorted().map { pois[it] }
        cluster.members.min() to if (members.size == 1) MapMarker.Place(members.single()) else MapMarker.Group(members)
    } + loose.map { it to MapMarker.Place(pois[it]) }
    return markers.sortedBy { it.first }.map { it.second }
}

/** Grupo en construcción: índices de sus lugares y su centro en pantalla (promedio de sus puntos). */
private class Cluster(val members: MutableList<Int>, var x: Float, var y: Float) {
    fun add(index: Int, point: ScreenPoint) = absorb(listOf(index), point.x, point.y, 1)

    fun absorb(other: Cluster) = absorb(other.members, other.x, other.y, other.members.size)

    private fun absorb(indices: List<Int>, ox: Float, oy: Float, count: Int) {
        val total = members.size + count
        x = (x * members.size + ox * count) / total
        y = (y * members.size + oy * count) / total
        members += indices
    }
}

/** Funde el par de grupos más cercano mientras sus centros queden a menos del radio. */
private fun mergeOverlapping(clusters: MutableList<Cluster>, radiusSquared: Float) {
    while (true) {
        var best: Pair<Int, Int>? = null
        var bestDistance = radiusSquared
        for (a in clusters.indices) for (b in a + 1 until clusters.size) {
            val distance = squaredDistance(clusters[a].x, clusters[a].y, clusters[b].x, clusters[b].y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = a to b
            }
        }
        val (a, b) = best ?: return
        clusters[a].absorb(clusters[b])
        clusters.removeAt(b)
    }
}

private fun squaredDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return dx * dx + dy * dy
}
