package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 24 · Las publicaciones propias de prueba y lo que pasa al eliminar una. */
class FakePublicationRepositoryTest {

    private val pois = FakePoiRepository()
    private val publications = FakePublicationRepository(pois)

    @Test
    fun `la rechazada por otro motivo trae el mensaje, quién la revisó y qué corregir`() = runTest {
        val publication = requireNotNull(publications.publication("mirador-de-la-pena"))
        val rejection = requireNotNull(publication.rejection)

        assertEquals(PublicationStatus.REJECTED, publication.status)
        assertEquals(RejectionReason.PHOTO, rejection.reason)
        assertTrue(rejection.canResubmit)
        assertEquals(listOf(FixKind.PHOTOS, FixKind.LOCATION), rejection.fixes.map { it.kind })
        assertEquals(3, rejection.firstStepToFix)
    }

    @Test
    fun `la rechazada por duplicado trae el lugar original, a unos 23 m`() = runTest {
        val publication = requireNotNull(publications.publication("puerta-falsa-tamales"))
        val rejection = requireNotNull(publication.rejection)
        val original = requireNotNull(rejection.duplicateOf)

        assertEquals(RejectionReason.DUPLICATE, rejection.reason)
        assertFalse(rejection.canResubmit)
        assertEquals("la-puerta-falsa", original.id)
        assertTrue(publication.location.distanceTo(original.location) in 20..26)
    }

    @Test
    fun `eliminar la quita y las cifras del perfil bajan`() = runTest {
        val users = FakeUserRepository(pois, publications)
        val before = users.ownProfile().publications

        publications.delete("mirador-de-la-pena")

        assertNull(publications.publication("mirador-de-la-pena"))
        assertEquals(before.rejected - 1, users.ownProfile().publications.rejected)
        assertEquals(before.total - 1, users.ownProfile().publications.total)
    }

    @Test
    fun `una publicación que no existe no se encuentra ni se puede eliminar`() = runTest {
        assertNull(publications.publication("no-existe"))
        assertTrue(runCatching { publications.delete("no-existe") }.isFailure)
    }

    @Test
    fun `sin red ni se ve ni se elimina, y no queda nada en cola`() = runTest {
        val repository = OnlineOnlyPublicationRepository(publications, FakeConnectivity(online = false))

        assertTrue(runCatching { repository.publication("mirador-de-la-pena") }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { repository.delete("mirador-de-la-pena") }.exceptionOrNull() is OfflineException)
        assertEquals(PublicationStatus.REJECTED, publications.publication("mirador-de-la-pena")?.status)
    }

    @Test
    fun `mis publicaciones son las siete de Ana, de la más reciente a la más antigua`() = runTest {
        val mine = publications.myPublications()

        assertEquals(7, mine.size)
        assertEquals(mine.sortedByDescending { it.submittedAt }, mine)
        assertEquals(2, mine.count { it.status == PublicationStatus.VERIFIED })
        assertEquals(listOf("panaderia-la-candelaria"), mine.filter { it.possibleDuplicate }.map { it.id })
    }

    @Test
    fun `las públicas traen votos, comentarios y los puntos que dieron, y las demás no`() = runTest {
        val mine = publications.myPublications()
        val quinta = mine.single { it.id == "quinta-de-bolivar" }
        val place = pois.feedPage(FeedQuery(), 0, Int.MAX_VALUE).items.single { it.id == "quinta-de-bolivar" }

        assertTrue(quinta.isPublic)
        assertEquals(place.votes, quinta.votes)
        assertEquals(place.comments, quinta.comments)
        assertEquals(15, quinta.pointsEarned)
        assertTrue(mine.filterNot { it.isPublic }.all { it.votes == 0 && it.comments == 0 && it.pointsEarned == 0 })
    }

    @Test
    fun `eliminar una pública la quita del feed, de la lista y de las cifras del perfil`() = runTest {
        val users = FakeUserRepository(pois, publications)
        val verifiedBefore = users.ownProfile().publications.verified

        publications.delete("sendero-la-vieja")

        assertTrue(pois.feedPage(FeedQuery(), 0, Int.MAX_VALUE).items.none { it.id == "sendero-la-vieja" })
        assertNull(pois.poiDetails("sendero-la-vieja"))
        assertTrue(publications.myPublications().none { it.id == "sendero-la-vieja" })
        assertEquals(verifiedBefore - 1, users.ownProfile().publications.verified)
    }
}
