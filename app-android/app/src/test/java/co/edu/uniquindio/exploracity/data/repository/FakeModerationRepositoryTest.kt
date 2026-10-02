package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReviewUrgency
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 32–34 · La cola de prueba, sus posibles duplicados y lo que pasa al verificar (D1). */
class FakeModerationRepositoryTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC)
    private val pois = FakePoiRepository(clock = clock)
    private val publications = FakePublicationRepository(pois, clock)
    private val notifications = FakeNotificationRepository(clock)
    private val moderation = FakeModerationRepository(pois, publications, notifications, clock)

    @Test
    fun `son 7, de la más antigua a la más reciente, con las dos pendientes de Ana`() = runTest {
        val items = moderation.queue().items

        assertEquals(7, items.size)
        assertEquals(items.sortedBy { it.submittedAt }, items)
        assertEquals("mirador-cruz-de-piedra", items.first().id)
        assertEquals(ReviewUrgency.HIGH, items.first().urgency(clock.instant()))
        val ana = items.filter { it.author.author.id == sampleCurrentUser.id }.map { it.id }.toSet()
        assertEquals(setOf("panaderia-la-candelaria", "murales-calle-26"), ana)
        assertEquals(7, moderation.pendingCount.value)
    }

    @Test
    fun `los posibles duplicados traen sus parecidos a menos de 50 m y la nota del autor`() = runTest {
        val duplicates = moderation.queue().items.filter { it.duplicate != null }

        assertEquals(setOf("sala-botero-biblioteca", "panaderia-la-candelaria"), duplicates.map { it.id }.toSet())
        val sala = duplicates.first { it.id == "sala-botero-biblioteca" }.duplicate!!
        assertEquals(setOf("museo-botero", "biblioteca-luis-angel"), sala.candidates.map { it.poi.id }.toSet())
        assertTrue(sala.candidates.all { it.distanceMeters <= 50 })
        val bakery = duplicates.first { it.id == "panaderia-la-candelaria" }.duplicate!!
        assertEquals(listOf("chorro-de-quevedo"), bakery.candidates.map { it.poi.id })
        assertNotNull(bakery.authorNote)
    }

    @Test
    fun `verificar la de otra persona la saca de la cola y la pone en el feed con su autor`() = runTest {
        moderation.verify("mirador-cruz-de-piedra", note = "Revisé la foto con la del mapa")

        assertNull(moderation.item("mirador-cruz-de-piedra"))
        assertEquals(6, moderation.pendingCount.value)
        val place = pois.places().first { it.id == "mirador-cruz-de-piedra" }
        assertEquals(PublicationStatus.VERIFIED, place.status)
        assertEquals("camilo-r", pois.detailsOf(place).author.id)
        assertEquals(ModerationWork(verified = 1, rejected = 0, finalized = 0), moderation.todayWork())
    }

    @Test
    fun `verificar una de Ana la vuelve pública en 22 y le llega el aviso con sus puntos`() = runTest {
        moderation.verify("murales-calle-26", note = null)

        val mine = publications.myPublications().first { it.id == "murales-calle-26" }
        assertEquals(PublicationStatus.VERIFIED, mine.status)
        assertEquals(VERIFIED_POINTS, mine.pointsEarned)
        assertTrue(pois.places().any { it.id == "murales-calle-26" })
        val notice = notifications.notifications().items.first()
        assertEquals("murales-calle-26", (notice as Notification.Verified).poiId)
        assertEquals(VERIFIED_POINTS, notice.points)
    }

    @Test
    fun `una que ya se decidió no se puede verificar otra vez`() = runTest {
        moderation.verify("iglesia-la-candelaria", note = null)

        val failure = runCatching { moderation.verify("iglesia-la-candelaria", note = null) }.exceptionOrNull()

        assertTrue(failure is AlreadyReviewedException)
    }

    @Test
    fun `el resumen del feed cuenta las que esperan y los días de la más antigua`() = runTest {
        assertEquals(ModerationSummary(pending = 7, oldestWaitingDays = 3), moderation.summary())
    }
}
