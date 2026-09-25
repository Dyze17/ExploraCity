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
        assertNull(repository.comments("no-existe"))
    }

    @Test
    fun `los comentarios del café empiezan por los del lienzo y van del más reciente al más antiguo`() = runTest {
        val page = repository.comments(cafe)!!

        assertEquals("Café Las Acacias", page.poiTitle)
        assertEquals(12, page.total)
        assertEquals(listOf("María Paula", "Juan David"), page.items.take(2).map { it.author.name })
        assertEquals(page.items.sortedByDescending { it.createdAt }, page.items)
        assertNull(page.nextCursor)
    }

    @Test
    fun `las páginas siguen el cursor sin repetir comentarios`() = runTest {
        val first = repository.comments("museo-del-oro")!!
        val second = repository.comments("museo-del-oro", cursor = first.nextCursor)!!

        assertEquals(20, first.items.size)
        assertEquals(88, first.total)
        assertEquals(first.items.last().id, first.nextCursor)
        assertTrue(first.items.last().createdAt > second.items.first().createdAt)
        assertTrue(first.items.none { it in second.items })
    }

    @Test
    fun `comentar suma en el feed y en el detalle y el comentario queda primero`() = runTest {
        val before = repository.feedPage(FeedQuery(), 0, pageSize = 50).items.first { it.id == cafe }.comments

        val comment = repository.addComment(cafe, "El capuchino, muy bueno.")

        assertEquals(before + 1, repository.feedPage(FeedQuery(), 0, pageSize = 50).items.first { it.id == cafe }.comments)
        assertEquals(before + 1, repository.poiDetails(cafe)!!.poi.comments)
        assertEquals(comment, repository.comments(cafe)!!.items.first())
        assertTrue(comment.mine)
    }

    @Test
    fun `Galería Santa Fe no tiene comentarios`() = runTest {
        val page = repository.comments("galeria-santa-fe")!!

        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
    }
}
