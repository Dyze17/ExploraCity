package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnProfileTest {

    @Test
    fun `la barra mide los puntos sobre el umbral del siguiente nivel (26 · 340 de 500 es 68 %)`() {
        val progress = LevelProgress(340)

        assertEquals(UserLevel.ADVENTURER, progress.level)
        assertEquals(UserLevel.LOCAL_AMBASSADOR, progress.next)
        assertEquals(160, progress.remaining)
        assertEquals(68, progress.percent)
        assertEquals(0.68f, progress.fraction, 0.001f)
    }

    @Test
    fun `al empezar no hay avance y en el nivel máximo la barra queda llena`() {
        assertEquals(0, LevelProgress(0).percent)
        assertEquals(UserLevel.EXPLORER, LevelProgress(0).next)

        val max = LevelProgress(610)
        assertNull(max.next)
        assertEquals(0, max.remaining)
        assertEquals(100, max.percent)
        assertEquals(1f, max.fraction, 0f)
    }

    @Test
    fun `primero las desbloqueadas y luego las bloqueadas de la más avanzada a la menos (27 · a)`() {
        val badges = listOf(
            badge("comentarios", progress = 12, target = 50),
            badge("primera", progress = 1, target = 1),
            badge("cero", progress = 0, target = 5),
            badge("verificadas", progress = 3, target = 10),
            badge("verde", progress = 1, target = 1),
        )

        assertEquals(listOf("primera", "verde", "verificadas", "comentarios", "cero"), badges.inDisplayOrder().map { it.id })
    }

    @Test
    fun `una insignia con más avance que la meta está desbloqueada y su barra no se pasa`() {
        val badge = badge("primera", progress = 7, target = 1)

        assertEquals(true, badge.unlocked)
        assertEquals(1f, badge.fraction, 0f)
    }

    @Test
    fun `las cifras cuentan cada estado y el total incluye las rechazadas (22 · Todas)`() {
        val counts = PublicationCounts.of(
            listOf(PublicationStatus.VERIFIED, PublicationStatus.VERIFIED, PublicationStatus.FINALIZED, PublicationStatus.PENDING, PublicationStatus.REJECTED),
        )

        assertEquals(2, counts[PublicationStatus.VERIFIED])
        assertEquals(1, counts[PublicationStatus.FINALIZED])
        assertEquals(1, counts[PublicationStatus.PENDING])
        assertEquals(1, counts[PublicationStatus.REJECTED])
        assertEquals(5, counts.total)
    }

    private fun badge(id: String, progress: Int, target: Int) =
        Badge(id, id, BadgeMetric.VERIFIED_PLACES, progress, target, howTo = "Cómo se obtiene")
}
