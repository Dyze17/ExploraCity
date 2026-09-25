package co.edu.uniquindio.exploracity.data.repository

import androidx.room.Room
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.domain.model.Notification
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

/** 25 · La última lista queda guardada y leer funciona también sin red (va a la cola). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineNotificationRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), ZoneOffset.UTC)
    private val connectivity = FakeConnectivity()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private val server = FakeNotificationRepository(clock)
    private var scheduled = 0
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

    private fun repository(remote: NotificationRepository = server) = OfflineNotificationRepository(
        remote = remote,
        dao = database.notificationsDao(),
        pending = database.pendingActionsDao(),
        scheduler = { scheduled++ },
        connectivity = connectivity,
        scope = appScope,
        clock = clock,
    )

    @Test
    fun `con red trae los avisos, los guarda y el badge cuenta los sin leer`() = runTest(dispatcher) {
        val repository = repository()

        val list = repository.notifications()
        advanceUntilIdle()

        assertNull(list.savedAt)
        assertEquals(server.notifications().items, list.items)
        assertEquals(list.items.count { !it.read }, repository.unreadCount.value)
    }

    @Test
    fun `sin red se ven los guardados con su fecha, y sin nada guardado avisa`() = runTest(dispatcher) {
        val repository = repository()
        connectivity.online = false
        assertTrue(runCatching { repository.notifications() }.exceptionOrNull() is OfflineException)

        connectivity.online = true
        val online = repository.notifications()
        connectivity.online = false
        val offline = repository.notifications()

        assertEquals(clock.instant(), offline.savedAt)
        assertEquals(online.items, offline.items)
    }

    @Test
    fun `sin red leer cambia al instante, va a la cola y no reaparece como nuevo al volver`() = runTest(dispatcher) {
        val repository = repository()
        val unreadBefore = repository.notifications().items.count { !it.read }
        connectivity.online = false

        repository.markRead("n-verificada")
        advanceUntilIdle()

        assertEquals(unreadBefore - 1, repository.unreadCount.value)
        assertEquals(1, database.pendingActionsDao().ofType(PendingType.NOTIFICATION_READ).size)
        assertEquals(1, scheduled)

        // La red vuelve antes de que WorkManager envíe la cola: el servidor aún lo tiene sin leer.
        connectivity.online = true
        val fresh = repository.notifications().items
        assertTrue(fresh.first { it.id == "n-verificada" }.read)
    }

    @Test
    fun `marcar todo sin red deja el badge en cero`() = runTest(dispatcher) {
        val repository = repository()
        repository.notifications()
        connectivity.online = false

        repository.markAllRead()
        advanceUntilIdle()

        assertEquals(0, repository.unreadCount.value)
        assertTrue(repository.notifications().items.all(Notification::read))
    }

    @Test
    fun `con red pero sin respuesta del servidor se ven los guardados`() = runTest(dispatcher) {
        val flaky = FlakyServer(server)
        val repository = repository(flaky)
        repository.notifications()

        flaky.failing = true

        assertNotNull(repository.notifications().savedAt)
    }

    private class FlakyServer(private val delegate: NotificationRepository) : NotificationRepository by delegate {
        var failing = false

        override suspend fun notifications(): NotificationList {
            if (failing) throw IOException("el servidor no responde")
            return delegate.notifications()
        }
    }
}
