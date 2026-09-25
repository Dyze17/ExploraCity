package co.edu.uniquindio.exploracity.data.sync

import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.data.local.PendingActionEntity
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.data.local.QueuedVote
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.OfflinePoiRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import co.edu.uniquindio.exploracity.domain.model.VoteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** Lo que se hizo sin red llega al servidor en orden cuando WorkManager ve conexión. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingSenderTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), ZoneOffset.UTC)
    private val connectivity = FakeConnectivity()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private val server = FakePoiRepository(clock = clock)
    private val cafe = "cafe-las-acacias"
    private lateinit var database: ExploraDatabase

    @Before
    fun setUp() {
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

    private fun repository() = OfflinePoiRepository(
        remote = server,
        dao = database.savedPlacesDao(),
        pending = database.pendingActionsDao(),
        scheduler = {},
        connectivity = connectivity,
        scope = appScope,
        currentUser = sampleCurrentUser,
        clock = clock,
    )

    private fun sender(remote: PoiRepository = server) = PendingSender(remote, database.pendingActionsDao(), database.savedPlacesDao())

    /** Guarda el feed con red y deja el teléfono sin ella, listo para encolar. */
    private suspend fun TestScope.offlineWithSavedFeed(): OfflinePoiRepository {
        val repository = repository()
        repository.feedPage(FeedQuery(), 0)
        advanceUntilIdle()
        connectivity.online = false
        return repository
    }

    @Test
    fun `envía todo en el orden en que se hizo y deja lo guardado al día`() = runTest(dispatcher) {
        val repository = offlineWithSavedFeed()
        repository.markVisited(cafe, VisitExperience(text = "Muy bueno"))
        repository.setVote(cafe, voted = true)
        repository.addComment(cafe, "Volvimos y sigue igual de bueno.")
        val recording = RecordingRemote(server)

        assertTrue(sender(recording).flush())

        assertEquals(listOf("visita", "voto", "comentario"), recording.calls)
        assertEquals(0, database.pendingActionsDao().count())
        val details = requireNotNull(server.poiDetails(cafe))
        assertTrue(details.visited)
        assertTrue(details.voted)
        assertEquals("Volvimos y sigue igual de bueno.", requireNotNull(server.comments(cafe)).items.first().text)
        val saved = requireNotNull(repository.poiDetails(cafe))
        assertEquals(details.poi.votes, saved.poi.votes)
        assertEquals(13, saved.poi.comments)
    }

    @Test
    fun `si la red falla a mitad de camino se detiene y conserva lo que falta`() = runTest(dispatcher) {
        val repository = offlineWithSavedFeed()
        repository.markVisited(cafe, VisitExperience())
        repository.addComment(cafe, "Sin señal en el patio.")
        val flaky = RecordingRemote(server, failComments = true)

        assertFalse(sender(flaky).flush())
        assertEquals(1, database.pendingActionsDao().count())

        flaky.failComments = false
        assertTrue(sender(flaky).flush())
        assertEquals(0, database.pendingActionsDao().count())
        assertEquals(listOf("visita", "comentario", "comentario"), flaky.calls)
    }

    @Test
    fun `lo que el servidor rechaza se descarta y no bloquea lo demás`() = runTest(dispatcher) {
        val repository = offlineWithSavedFeed()
        database.pendingActionsDao().insert(
            PendingActionEntity(type = PendingType.VOTE, poiId = "ya-no-existe", payload = Json.encodeToString(QueuedVote(true)), createdAtMillis = 0),
        )
        repository.markVisited(cafe, VisitExperience())

        assertTrue(sender().flush())

        assertEquals(0, database.pendingActionsDao().count())
        assertTrue(requireNotNull(server.poiDetails(cafe)).visited)
    }

    @Test
    fun `el worker termina bien con la cola enviada y pide reintentar si falla la red`() = runTest(dispatcher) {
        val repository = offlineWithSavedFeed()
        repository.addComment(cafe, "Sin señal en el patio.")
        val flaky = RecordingRemote(server, failComments = true)
        val context = RuntimeEnvironment.getApplication()
        fun worker() = TestListenableWorkerBuilder<SendPendingWorker>(context)
            .setWorkerFactory(ExploraWorkerFactory { sender(flaky) })
            .build()

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())

        flaky.failComments = false
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    /** Anota qué se envía y puede simular que se cae la red al enviar comentarios. */
    private class RecordingRemote(private val delegate: PoiRepository, var failComments: Boolean = false) : PoiRepository by delegate {
        val calls = mutableListOf<String>()

        override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
            calls += "visita"
            return delegate.markVisited(id, experience)
        }

        override suspend fun setVote(id: String, voted: Boolean): VoteResult {
            calls += "voto"
            return delegate.setVote(id, voted)
        }

        override suspend fun addComment(poiId: String, text: String): Comment {
            calls += "comentario"
            if (failComments) throw IOException("sin red")
            return delegate.addComment(poiId, text)
        }
    }
}
