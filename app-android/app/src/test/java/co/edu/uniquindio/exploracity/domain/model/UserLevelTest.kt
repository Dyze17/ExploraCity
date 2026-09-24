package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
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
}
