package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.FakeNotificationRepository
import co.edu.uniquindio.exploracity.data.repository.NotificationList
import co.edu.uniquindio.exploracity.data.repository.NotificationRepository
import co.edu.uniquindio.exploracity.domain.model.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakeNotificationRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: NotificationRepository = server) = NotificationsViewModel(repository, connectivity)

    private val NotificationsViewModel.loaded: NotificationsContent.Loaded
        get() = state.value.content as? NotificationsContent.Loaded ?: error("Los avisos deberían estar cargados: ${state.value.content}")

    @Test
    fun `carga los avisos y los separa en sin leer y anteriores`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(NotificationsContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        val loaded = vm.loaded
        assertTrue(loaded.unread.isNotEmpty())
        assertTrue(loaded.earlier.isNotEmpty())
        assertTrue(loaded.unread.none(Notification::read))
        assertTrue(loaded.earlier.all(Notification::read))
    }

    @Test
    fun `abrir un aviso lo marca como leído al instante y en el servidor`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val first = vm.loaded.unread.first()
        val unreadBefore = server.unreadCount.value

        vm.onOpen(first)
        assertTrue(vm.loaded.items.first { it.id == first.id }.read)
        advanceUntilIdle()

        assertEquals(unreadBefore - 1, server.unreadCount.value)
    }

    @Test
    fun `marcar leídas vacía el grupo y avisa una vez`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMarkAllRead()
        advanceUntilIdle()

        assertTrue(vm.loaded.unread.isEmpty())
        assertTrue(vm.state.value.allReadShown)
        assertEquals(0, server.unreadCount.value)
        vm.onAllReadShown()
        assertFalse(vm.state.value.allReadShown)
    }

    @Test
    fun `sin avisos queda la lista vacía (25 c)`() = runTest(dispatcher) {
        val vm = viewModel(ListRepository(NotificationList(emptyList())))
        advanceUntilIdle()

        assertTrue(vm.loaded.items.isEmpty())
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FlakyRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(NotificationsContent.Error, vm.state.value.content)

        repository.failing = false
        vm.onRetry()
        advanceUntilIdle()
        assertTrue(vm.loaded.items.isNotEmpty())
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakeNotificationRepository(latency = 10.seconds))

        advanceTimeBy(7.9.seconds)
        assertEquals(NotificationsContent.Loading, vm.state.value.content)
        advanceTimeBy(0.2.seconds)
        assertEquals(NotificationsContent.Error, vm.state.value.content)
    }

    @Test
    fun `sin red y sin nada guardado lo dice, y al volver la red carga sola`() = runTest(dispatcher) {
        val repository = FlakyRepository(failing = false, offline = true)
        connectivity.online = false
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(NotificationsContent.Offline, vm.state.value.content)
        assertTrue(vm.state.value.offline)

        repository.offline = false
        connectivity.online = true
        advanceUntilIdle()
        assertTrue(vm.loaded.items.isNotEmpty())
        assertFalse(vm.state.value.offline)
    }

    @Test
    fun `lo guardado se muestra con su fecha`() = runTest(dispatcher) {
        val savedAt = Instant.parse("2026-09-25T13:00:00Z")
        val vm = viewModel(ListRepository(NotificationList(server.notifications().items, savedAt)))
        advanceUntilIdle()

        assertEquals(savedAt, vm.loaded.savedAt)
    }

    /** Devuelve siempre [list]. */
    private inner class ListRepository(private val list: NotificationList) : NotificationRepository by server {
        override suspend fun notifications(): NotificationList {
            delay(100)
            return list
        }
    }

    private inner class FlakyRepository(var failing: Boolean = true, var offline: Boolean = false) : NotificationRepository by server {
        override suspend fun notifications(): NotificationList {
            if (offline) throw OfflineException()
            if (failing) throw IOException("sin red")
            return server.notifications()
        }
    }
}
