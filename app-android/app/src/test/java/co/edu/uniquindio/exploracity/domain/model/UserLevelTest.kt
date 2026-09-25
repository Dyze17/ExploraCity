package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserLevelTest {

    @Test
    fun `los rangos del README son 0–99, 100–249, 250–499 y 500 o más`() {
        assertEquals(UserLevel.TOURIST, UserLevel.fromPoints(0))
        assertEquals(UserLevel.TOURIST, UserLevel.fromPoints(99))
        assertEquals(UserLevel.EXPLORER, UserLevel.fromPoints(100))
        assertEquals(UserLevel.EXPLORER, UserLevel.fromPoints(249))
        assertEquals(UserLevel.ADVENTURER, UserLevel.fromPoints(250))
        assertEquals(UserLevel.ADVENTURER, UserLevel.fromPoints(499))
        assertEquals(UserLevel.LOCAL_AMBASSADOR, UserLevel.fromPoints(500))
        assertEquals(UserLevel.LOCAL_AMBASSADOR, UserLevel.fromPoints(10_000))
    }

    @Test
    fun `el autor toma el nivel de sus puntos`() {
        assertEquals(UserLevel.ADVENTURER, Author("camilo-r", "Camilo R.", points = 320).level)
    }

    @Test
    fun `cada nivel conoce el siguiente y dónde termina su rango (27)`() {
        assertEquals(UserLevel.EXPLORER, UserLevel.TOURIST.next)
        assertEquals(99, UserLevel.TOURIST.maxPoints)
        assertEquals(499, UserLevel.ADVENTURER.maxPoints)
        assertNull(UserLevel.LOCAL_AMBASSADOR.next)
        assertNull(UserLevel.LOCAL_AMBASSADOR.maxPoints)
    }
}
