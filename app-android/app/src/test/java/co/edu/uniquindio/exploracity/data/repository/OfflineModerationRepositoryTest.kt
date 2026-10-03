package co.edu.uniquindio.exploracity.data.repository

import androidx.room.Room
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

/** 32.c · La última cola queda guardada: sin conexión se lee con su antigüedad y no se decide nada. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineModerationRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC)
    private val connectivity = FakeConnectivity()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private val pois = FakePoiRepository(clock = clock)
    private val server = FakeModerationRepository(pois, FakePublicationRepository(pois, clock), FakeNotificationRepository(clock), clock)
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

    private fun repository(remote: ModerationRepository = server) =
        OfflineModerationRepository(remote, database.reviewsDao(), connectivity, appScope, clock)

    @Test
    fun `con red guarda la cola entera, con sus duplicados, y el badge la cuenta`() = runTest(dispatcher) {
        val repository = repository()
        val fresh = repository.queue()
        advanceUntilIdle()

        assertNull(fresh.savedAt)
        assertEquals(7, repository.pendingCount.value)
        connectivity.online = false
        val saved = repository.queue()
        assertEquals(fresh.items, saved.items)
        assertEquals(clock.instant(), saved.savedAt)
    }

    @Test
    fun `sin red y sin nada guardado se dice, y tampoco se puede verificar`() = runTest(dispatcher) {
        connectivity.online = false
        val repository = repository()

        assertTrue(runCatching { repository.queue() }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { repository.verify("mirador-cruz-de-piedra", null) }.exceptionOrNull() is OfflineException)
    }

    @Test
    fun `sin red el detalle sale de lo guardado`() = runTest(dispatcher) {
        val repository = repository()
        val first = repository.queue().items.first()
        connectivity.online = false

        assertEquals(first, repository.item(first.id))
        assertEquals(repository.queue().items.map { it.id }, repository.queueIds())
    }

    @Test
    fun `verificar con red la saca también de lo guardado`() = runTest(dispatcher) {
        val repository = repository()
        repository.queue()

        repository.verify("mirador-cruz-de-piedra", null)
        advanceUntilIdle()

        assertEquals(6, repository.pendingCount.value)
        assertTrue("mirador-cruz-de-piedra" !in repository.queueIds())
    }

    @Test
    fun `rechazar necesita red y, con ella, la saca también de lo guardado`() = runTest(dispatcher) {
        val repository = repository()
        repository.queue()
        val decision = RejectDecision(RejectionReason.INAPPROPRIATE, "", canResubmit = false)
        connectivity.online = false
        assertTrue(runCatching { repository.reject("taller-titeres-macarena", decision) }.exceptionOrNull() is OfflineException)

        connectivity.online = true
        repository.reject("taller-titeres-macarena", decision)
        advanceUntilIdle()

        assertEquals(6, repository.pendingCount.value)
        assertTrue("taller-titeres-macarena" !in repository.queueIds())
    }

    @Test
    fun `resueltas y los cambios de estado necesitan red, y volver a pendiente pone al día el badge`() = runTest(dispatcher) {
        val repository = repository()
        repository.queue()
        connectivity.online = false
        assertTrue(runCatching { repository.resolved() }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { repository.finalize("quinta-de-bolivar", FinalizeReason.CLOSED) }.exceptionOrNull() is OfflineException)

        connectivity.online = true
        repository.reopen("sendero-la-vieja", "Cerraron el sendero por derrumbe.")
        advanceUntilIdle()

        assertEquals(8, repository.pendingCount.value)
        assertTrue("sendero-la-vieja" in repository.queueIds())
    }

    @Test
    fun `si el servidor no responde se muestra lo guardado`() = runTest(dispatcher) {
        repository().queue()
        val failing = repository(FailingModeration())

        val queue = failing.queue()

        assertEquals(7, queue.items.size)
        assertEquals(clock.instant(), queue.savedAt)
    }

    /** Un servidor que tiene red pero no responde. */
    private class FailingModeration : ModerationRepository {
        override val pendingCount: StateFlow<Int> = MutableStateFlow(0)

        override suspend fun summary(): ModerationSummary = throw IOException("500")

        override suspend fun queue(): ReviewQueue = throw IOException("500")

        override suspend fun item(id: String): ReviewItem? = throw IOException("500")

        override suspend fun queueIds(): List<String> = emptyList()

        override suspend fun verify(id: String, note: String?) = throw IOException("500")

        override suspend fun duplicateOptions(id: String): List<DuplicateCandidate> = throw IOException("500")

        override suspend fun reject(id: String, decision: RejectDecision) = throw IOException("500")

        override suspend fun todayWork(): ModerationWork = throw IOException("500")

        override suspend fun resolved(): List<ResolvedPublication> = throw IOException("500")

        override suspend fun resolvedItem(id: String): ResolvedPublication? = throw IOException("500")

        override suspend fun finalize(id: String, reason: FinalizeReason) = throw IOException("500")

        override suspend fun reopen(id: String, reason: String) = throw IOException("500")
    }
}
