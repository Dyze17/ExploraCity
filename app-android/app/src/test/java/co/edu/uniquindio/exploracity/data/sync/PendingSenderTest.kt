package co.edu.uniquindio.exploracity.data.sync

import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.data.local.PendingActionEntity
import co.edu.uniquindio.exploracity.data.local.NOTIFICATIONS_TARGET
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.data.local.QueuedRead
import co.edu.uniquindio.exploracity.data.local.QueuedVote
import co.edu.uniquindio.exploracity.data.photos.FakePhotoUploader
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.repository.FakeNotificationRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.OfflinePoiRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.time.Duration

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

    private val notificationServer = FakeNotificationRepository(clock)
    private val publicationServer = FakePublicationRepository(server, clock = clock)
    private val photoFiles = DeletedPhotos()

    private fun sender(remote: PoiRepository = server) = PendingSender(
        remote,
        notificationServer,
        database.pendingActionsDao(),
        database.savedPlacesDao(),
        PublicationDelivery(publicationServer, FakePhotoUploader(connectivity, duration = Duration.ZERO), photoFiles),
    )

    private fun outbox() = RoomPublicationOutbox(database.pendingActionsDao(), scheduler = {}, clock = clock)

    private val submission = PublicationSubmission(
        title = "Café Las Acacias",
        description = "Café de barrio con tostión propia y un patio interior lleno de matas.",
        category = Category.GASTRONOMY,
        categoryOrigin = CategoryOrigin.SUGGESTED,
        location = GeoPoint(4.6383, -74.0656),
        hours = OpeningHours(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(7, 0), LocalTime.of(19, 0)),
        price = PriceRange.LOW,
        photos = listOf(DraftPhoto("foto-1", "/fotos/foto-1.jpg", "patio.jpg"), DraftPhoto("foto-2", "/fotos/foto-2.jpg", "barra.jpg")),
        duplicateCheck = DuplicateCheck(GeoPoint(4.6383, -74.0656), listOf("cafe-las-acacias"), "Es el local del lado."),
    )

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

    @Test
    fun `envía los avisos leídos sin red`() = runTest(dispatcher) {
        val before = notificationServer.unreadCount.value
        database.pendingActionsDao().insert(
            PendingActionEntity(type = PendingType.NOTIFICATION_READ, poiId = NOTIFICATIONS_TARGET, payload = Json.encodeToString(QueuedRead("n-verificada")), createdAtMillis = 0),
        )
        database.pendingActionsDao().insert(
            PendingActionEntity(type = PendingType.NOTIFICATION_READ, poiId = NOTIFICATIONS_TARGET, payload = Json.encodeToString(QueuedRead(null)), createdAtMillis = 1),
        )

        assertTrue(sender().flush())

        assertTrue(before > 0)
        assertEquals(0, notificationServer.unreadCount.value)
        assertEquals(0, database.pendingActionsDao().count())
    }

    @Test
    fun `una publicación enviada sin red llega al volver la red con sus fotos, y se borran los archivos`() = runTest(dispatcher) {
        outbox().enqueue(submission)

        assertTrue(sender().flush())

        val sent = publicationServer.myPublications().first()
        assertEquals("Café Las Acacias", sent.title)
        assertEquals(2, sent.photos)
        assertTrue("La marca de posible duplicado viaja en la cola", sent.possibleDuplicate)
        assertEquals(listOf("foto-1", "foto-2"), photoFiles.deleted)
        assertEquals(0, database.pendingActionsDao().count())
    }

    @Test
    fun `si la red vuelve a fallar, la publicación sigue en la cola con sus archivos`() = runTest(dispatcher) {
        outbox().enqueue(submission)
        connectivity.online = false

        assertFalse(sender().flush())

        assertEquals(1, database.pendingActionsDao().count())
        assertTrue(photoFiles.deleted.isEmpty())
    }

    @Test
    fun `las fotos que no alcanzaron a subir se agregan después a la publicación`() = runTest(dispatcher) {
        val first = submission.photos[0].copy(remoteUrl = "fake://foto-1")
        val result = publicationServer.submit(submission.copy(photos = listOf(first)))
        outbox().enqueuePhotos(result.publicationId, listOf(submission.photos[1]))

        assertTrue(sender().flush())

        assertEquals(2, publicationServer.publication(result.publicationId)?.photos)
        assertEquals(listOf("foto-2"), photoFiles.deleted)
    }

    /** Archivos del teléfono: solo anota cuáles se borran. */
    private class DeletedPhotos : PhotoStore {
        val deleted = mutableListOf<String>()

        override suspend fun import(uri: String, fallbackName: String?): DraftPhoto? = null

        override fun newCameraShot(): String = ""

        override suspend fun delete(photo: DraftPhoto) {
            deleted += photo.id
        }
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
