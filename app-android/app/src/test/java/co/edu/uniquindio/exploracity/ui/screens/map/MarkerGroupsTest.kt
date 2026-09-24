package co.edu.uniquindio.exploracity.ui.screens.map

import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerGroupsTest {

    private val pois = samplePois.take(5)

    /** Posiciones de pantalla inventadas por id, en píxeles. */
    private fun screen(vararg points: Pair<Int, Pair<Float, Float>>): (Poi) -> ScreenPoint? {
        val byId = points.associate { (index, xy) -> pois[index].id to ScreenPoint(xy.first, xy.second) }
        return { byId[it.id] }
    }

    @Test
    fun `los lugares separados quedan sueltos`() {
        val markers = groupMarkers(pois.take(3), screen(0 to (0f to 0f), 1 to (200f to 0f), 2 to (0f to 200f)), radiusPx = 100f)

        assertEquals(pois.take(3).map { MapMarker.Place(it) }, markers)
    }

    @Test
    fun `los que se tocarían en pantalla forman un grupo alrededor del primero`() {
        val markers = groupMarkers(
            pois.take(4),
            screen(0 to (0f to 0f), 1 to (30f to 40f), 2 to (500f to 500f), 3 to (60f to 0f)),
            radiusPx = 100f,
        )

        assertEquals(2, markers.size)
        val group = markers[0] as MapMarker.Group
        assertEquals(listOf(pois[0], pois[1], pois[3]), group.pois)
        assertEquals(MapMarker.Place(pois[2]), markers[1])
    }

    @Test
    fun `el seleccionado nunca se agrupa`() {
        val markers = groupMarkers(
            pois.take(3),
            screen(0 to (0f to 0f), 1 to (10f to 0f), 2 to (20f to 0f)),
            radiusPx = 100f,
            selectedId = pois[1].id,
        )

        assertTrue(MapMarker.Place(pois[1]) in markers)
        assertEquals(MapMarker.Group(listOf(pois[0], pois[2])), markers.first())
    }

    @Test
    fun `sin posición en pantalla el lugar queda suelto`() {
        val markers = groupMarkers(pois.take(2), screen(0 to (0f to 0f)), radiusPx = 100f)

        assertEquals(listOf(MapMarker.Place(pois[0]), MapMarker.Place(pois[1])), markers)
    }

    @Test
    fun `el grupo se centra entre sus lugares y su área los contiene`() {
        val group = MapMarker.Group(pois.take(3))

        assertTrue(pois.take(3).all { it.location in group.bounds })
        assertEquals(pois.take(3).map { it.location.latitude }.average(), group.center.latitude, 1e-9)
    }
}
