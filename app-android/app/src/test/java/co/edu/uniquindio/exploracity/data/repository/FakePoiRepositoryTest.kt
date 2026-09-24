package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePoiRepositoryTest {

    private val repository = FakePoiRepository()
    private val cafe = "cafe-las-acacias"

    private suspend fun feedVotes(id: String) = repository.feedPage(FeedQuery(), 0, pageSize = 50).items.first { it.id == id }.votes

    @Test
    fun `el voto se refleja en el feed y en el detalle, y votar dos veces no suma dos`() = runTest {
        val before = feedVotes(cafe)

        assertEquals(before + 1, repository.setVote(cafe, voted = true))
        assertEquals(before + 1, repository.setVote(cafe, voted = true))
        assertEquals(before + 1, feedVotes(cafe))
        assertTrue(repository.poiDetails(cafe)!!.voted)

        assertEquals(before, repository.setVote(cafe, voted = false))
        assertFalse(repository.poiDetails(cafe)!!.voted)
    }

    @Test
    fun `marcar visitado queda en el detalle y no da puntos (opción A)`() = runTest {
        val result = repository.markVisited(cafe, VisitExperience(recommends = true))

        assertEquals(0, result.pointsAwarded)
        assertTrue(repository.poiDetails(cafe)!!.visited)
    }

    @Test
    fun `un lugar desconocido no tiene detalle`() = runTest {
        assertNull(repository.poiDetails("no-existe"))
    }
}
