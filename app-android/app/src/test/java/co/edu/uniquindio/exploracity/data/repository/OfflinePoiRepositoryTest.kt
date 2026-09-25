package co.edu.uniquindio.exploracity.data.repository

import androidx.room.Room
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Con Room en memoria: lo que se guarda con red es lo que se ve sin ella (12.a). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflinePoiRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-24T15:00:00Z"), ZoneOffset.UTC)
    private val connectivity = FakeConnectivity()
    private val cafe = "cafe-las-acacias"
    private lateinit var database: ExploraDatabase

    /** Como el de la app: sin padre. backgroundScope no sirve, advanceUntilIdle no avanza su trabajo. */
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        // Room corre en el mismo planificador de la prueba: avanzar el tiempo virtual también termina lo guardado.
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ExploraDatabase::class.java)
            .setQueryCoroutineContext(dispatcher)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.close()
    }

    private fun repository(remote: PoiRepository = FakePoiRepository(clock = clock)) =
        OfflinePoiRepository(remote, database.savedPlacesDao(), connectivity, appScope, clock)

    private suspend fun failsOffline(block: suspend () -> Unit) =
        assertTrue("Debía fallar por falta de red", runCatching { block() }.exceptionOrNull() is OfflineException)

    @Test
    fun `con red guarda la primera página del feed y el detalle de cada lugar`() = runTest(dispatcher) {
        val repository = repository()

        val page = repository.feedPage(FeedQuery(), 0)
        advanceUntilIdle()

        val saved = requireNotNull(repository.savedPlaces())
        assertEquals(page.items, saved.items)
        assertEquals(clock.instant(), saved.savedAt)
        assertEquals(page.items.map { it.id }.toSet(), saved.withDetails)
    }

    @Test
    fun `sin red el detalle sale de lo guardado y lo que necesita al servidor avisa`() = runTest(dispatcher) {
        val repository = repository()
        repository.feedPage(FeedQuery(), 0)
        advanceUntilIdle()
        val online: PoiDetails = requireNotNull(repository.poiDetails(cafe))
        assertNull(online.savedAt)

        connectivity.online = false

        val offline = requireNotNull(repository.poiDetails(cafe))
        assertEquals(clock.instant(), offline.savedAt)
        assertEquals(online, offline.copy(savedAt = null))
        failsOffline { repository.feedPage(FeedQuery(), 0) }
        failsOffline { repository.comments(cafe) }
        failsOffline { repository.setVote(cafe, voted = true) }
    }

    @Test
    fun `sin red y sin nada guardado, el detalle avisa que falta la conexión`() = runTest(dispatcher) {
        val repository = repository()
        connectivity.online = false

        failsOffline { repository.poiDetails(cafe) }
        assertNull(repository.savedPlaces())
    }

    @Test
    fun `una primera página nueva reemplaza lo guardado y las siguientes se suman hasta 60`() = runTest(dispatcher) {
        val many = (0 until 100).map { i -> samplePois[i % samplePois.size].copy(id = "poi-$i") }
        val repository = repository(FakePoiRepository(pois = many, clock = clock))

        repeat(4) { page -> repository.feedPage(FeedQuery(), page) }
        advanceUntilIdle()
        val saved = requireNotNull(repository.savedPlaces())
        assertEquals((0 until SAVED_PLACES_LIMIT).map { "poi-$it" }, saved.items.map { it.id })
        assertEquals(SAVED_PLACES_LIMIT, saved.withDetails.size)

        repository.feedPage(FeedQuery(FeedFilters(categories = setOf(Category.NATURE))), 0)
        advanceUntilIdle()
        val nature = requireNotNull(repository.savedPlaces())
        assertTrue(nature.items.isNotEmpty())
        assertTrue(nature.items.all { it.category == Category.NATURE })
        assertEquals(nature.items.map { it.id }.toSet(), nature.withDetails)
    }

    @Test
    fun `buscar por texto no reemplaza lo guardado`() = runTest(dispatcher) {
        val repository = repository()
        repository.feedPage(FeedQuery(), 0)
        repository.feedPage(FeedQuery(text = "museo"), 0)
        advanceUntilIdle()

        assertEquals(FEED_PAGE_SIZE, requireNotNull(repository.savedPlaces()).items.size)
    }

    @Test
    fun `lo que la persona hace con red queda en lo guardado`() = runTest(dispatcher) {
        val repository = repository()
        repository.feedPage(FeedQuery(), 0)
        advanceUntilIdle()

        val votes = repository.setVote(cafe, voted = true)
        repository.markVisited(cafe, VisitExperience())
        repository.addComment(cafe, "Muy buen café.")
        connectivity.online = false

        val details = requireNotNull(repository.poiDetails(cafe))
        assertTrue(details.voted)
        assertTrue(details.visited)
        assertEquals(votes, details.poi.votes)
        assertEquals(13, details.poi.comments)
    }

    @Test
    fun `con red pero sin respuesta del servidor, el detalle sale de lo guardado`() = runTest(dispatcher) {
        val remote = FlakyRemote(FakePoiRepository(clock = clock))
        val repository = repository(remote)
        repository.feedPage(FeedQuery(), 0)
        advanceUntilIdle()

        remote.failing = true

        assertNotNull(requireNotNull(repository.poiDetails(cafe)).savedAt)
    }

    private class FlakyRemote(private val delegate: PoiRepository) : PoiRepository by delegate {
        var failing = false

        override suspend fun poiDetails(id: String): PoiDetails? {
            if (failing) throw IOException("el servidor no responde")
            return delegate.poiDetails(id)
        }
    }
}
