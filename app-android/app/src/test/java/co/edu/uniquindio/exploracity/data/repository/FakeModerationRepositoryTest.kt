package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.ReviewUrgency
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 32–36 · La cola de prueba, sus posibles duplicados, lo que pasa al decidir (D1) y lo ya resuelto. */
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

    @Test
    fun `los parecidos traen autor, fecha y fotos para compararlos lado a lado`() = runTest {
        val museo = moderation.item("sala-botero-biblioteca")!!.duplicate!!.candidates.first { it.poi.id == "museo-botero" }

        assertNotNull(museo.author)
        assertTrue(museo.publishedAt!! < clock.instant())
        assertTrue(museo.photoCount > 0)
    }

    @Test
    fun `sin parecidos marcados se ofrecen como original los lugares cercanos`() = runTest {
        val options = moderation.duplicateOptions("iglesia-la-candelaria")

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.distanceMeters <= 500 })
        assertEquals(options.sortedBy { it.distanceMeters }, options)
        assertEquals(listOf("chorro-de-quevedo"), moderation.duplicateOptions("panaderia-la-candelaria").map { it.poi.id })
    }

    @Test
    fun `rechazar una de Ana por duplicado la deja en 24 con el original y le llega el aviso`() = runTest {
        val decision = RejectDecision(RejectionReason.DUPLICATE, "", canResubmit = true, originalId = "chorro-de-quevedo")

        moderation.reject("panaderia-la-candelaria", decision)

        val mine = publications.myPublications().first { it.id == "panaderia-la-candelaria" }
        assertEquals(PublicationStatus.REJECTED, mine.status)
        val rejection = mine.rejection!!
        assertEquals("chorro-de-quevedo", rejection.duplicateOf?.id)
        assertFalse(rejection.canResubmit)
        assertEquals(SAMPLE_MODERATOR_NAME, rejection.reviewerName)
        val notice = notifications.notifications().items.first() as Notification.DuplicateRejected
        assertEquals("chorro-de-quevedo", notice.existingPoiId)
        assertEquals(6, moderation.pendingCount.value)
    }

    @Test
    fun `rechazar por la foto pide corregirla y el aviso dice el motivo`() = runTest {
        moderation.reject("murales-calle-26", RejectDecision(RejectionReason.PHOTO, "", canResubmit = true))

        val rejection = publications.publication("murales-calle-26")!!.rejection!!
        assertTrue(rejection.canResubmit)
        assertEquals(listOf(FixKind.PHOTOS), rejection.fixes.map { it.kind })
        assertEquals("La foto no permite reconocer el lugar.", rejection.message)
        val notice = notifications.notifications().items.first() as Notification.Rejected
        assertEquals("la foto no permite reconocer el lugar", notice.reason)
    }

    @Test
    fun `rechazar la de otra persona la saca de la cola y queda en resueltas`() = runTest {
        val decision = RejectDecision(RejectionReason.OTHER, "El patio ya no existe: ahora es un parqueadero.", canResubmit = false)

        moderation.reject("cafe-el-patio", decision)

        assertNull(moderation.item("cafe-el-patio"))
        val resolved = moderation.resolved().first()
        assertEquals("cafe-el-patio", resolved.id)
        assertEquals(PublicationStatus.REJECTED, resolved.status)
        assertEquals(RejectionReason.OTHER, resolved.rejectionReason)
        assertEquals(ModerationWork(verified = 0, rejected = 1, finalized = 0), moderation.todayWork())
        assertTrue(runCatching { moderation.reject("cafe-el-patio", decision) }.exceptionOrNull() is AlreadyReviewedException)
    }

    @Test
    fun `resueltas va de lo más reciente a lo más antiguo, con las rechazadas de Ana`() = runTest {
        val items = moderation.resolved()

        val expected = listOf("quinta-de-bolivar", "puerta-falsa-tamales", "mirador-de-la-pena", "museo-botero", "casa-independencia", "sendero-la-vieja")
        assertEquals(expected, items.map { it.id })
        val casa = items.first { it.id == "casa-independencia" }
        assertEquals(PublicationStatus.FINALIZED, casa.status)
        assertEquals(FinalizeReason.CLOSED, casa.finalizeReason)
        assertEquals(RejectionReason.DUPLICATE, items.first { it.id == "puerta-falsa-tamales" }.rejectionReason)
        assertFalse(items.first { it.id == "mirador-de-la-pena" }.canChangeState)
        assertEquals("Revisé el horario con la página del museo.", items.first { it.id == "museo-botero" }.note)
    }

    @Test
    fun `finalizar una de Ana la deja en el feed como finalizada y le avisa con el motivo`() = runTest {
        moderation.finalize("quinta-de-bolivar", FinalizeReason.CLOSED)

        assertEquals(PublicationStatus.FINALIZED, pois.places().first { it.id == "quinta-de-bolivar" }.status)
        assertEquals(PublicationStatus.FINALIZED, publications.myPublications().first { it.id == "quinta-de-bolivar" }.status)
        val notice = notifications.notifications().items.first() as Notification.Finalized
        assertEquals("el lugar cerró de forma permanente", notice.reason)
        assertEquals(FinalizeReason.CLOSED, moderation.resolvedItem("quinta-de-bolivar")?.finalizeReason)
        assertEquals(ModerationWork(verified = 0, rejected = 0, finalized = 1), moderation.todayWork())
        val again = runCatching { moderation.finalize("quinta-de-bolivar", FinalizeReason.MERGED) }.exceptionOrNull()
        assertTrue(again is StateChangedException)
    }

    @Test
    fun `volver a pendiente una de Ana la saca del feed y la pone al final de la cola`() = runTest {
        moderation.reopen("casa-independencia", "Reabrió como museo con otro horario.")

        assertTrue(pois.places().none { it.id == "casa-independencia" })
        assertEquals(PublicationStatus.PENDING, publications.myPublications().first { it.id == "casa-independencia" }.status)
        assertEquals("casa-independencia", moderation.queue().items.last().id)
        assertEquals(8, moderation.pendingCount.value)
        assertNull(moderation.resolvedItem("casa-independencia"))
    }

    @Test
    fun `la de otra persona vuelve a la cola y, verificada otra vez, está una sola vez en el feed`() = runTest {
        moderation.reopen("museo-botero", "Cambió la entrada y hay que revisar las fotos.")
        assertTrue(pois.places().none { it.id == "museo-botero" })
        assertNotNull(moderation.item("museo-botero"))

        moderation.verify("museo-botero", note = null)

        assertEquals(1, pois.places().count { it.id == "museo-botero" })
        assertEquals(PublicationStatus.VERIFIED, pois.places().first { it.id == "museo-botero" }.status)
        assertTrue(runCatching { moderation.reopen("nada", "No existe este lugar en el feed.") }.exceptionOrNull() is StateChangedException)
    }
}
