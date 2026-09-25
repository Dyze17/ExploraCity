package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakeUserRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class OwnProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakeUserRepository(FakePoiRepository())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(users: UserRepository = server, savedState: SavedStateHandle = SavedStateHandle()) =
        OwnProfileViewModel(users, connectivity, savedState)

    private val OwnProfileViewModel.profile: OwnProfile
        get() = requireNotNull(state.value.profile) { "El perfil debería estar cargado: ${state.value.content}" }

    @Test
    fun `carga el perfil propio`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(OwnProfileContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals("Ana Ríos", vm.profile.author.name)
        assertEquals(9, vm.profile.badges.size)
    }

    @Test
    fun `tocar una insignia abre su hoja, que sobrevive a la rotación, y Entendido la cierra`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()
        val badge = vm.profile.badges.first { !it.unlocked }

        vm.onOpenBadge(badge.id)
        assertEquals(badge, vm.state.value.openBadge)

        val recreated = viewModel(savedState = savedState)
        advanceUntilIdle()
        assertEquals(badge, recreated.state.value.openBadge)

        recreated.onDismissBadge()
        assertNull(recreated.state.value.openBadge)
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FlakyRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(OwnProfileContent.Error, vm.state.value.content)

        repository.failing = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals("Ana Ríos", vm.profile.author.name)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakeUserRepository(FakePoiRepository(), latency = 10.seconds))

        advanceTimeBy(7.9.seconds)
        assertEquals(OwnProfileContent.Loading, vm.state.value.content)
        advanceTimeBy(0.2.seconds)
        assertEquals(OwnProfileContent.Error, vm.state.value.content)
    }

    @Test
    fun `sin red y sin nada guardado lo dice, y al volver la red carga solo`() = runTest(dispatcher) {
        val repository = FlakyRepository(failing = false, offline = true)
        connectivity.online = false
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(OwnProfileContent.Offline, vm.state.value.content)
        assertTrue(vm.state.value.offline)

        repository.offline = false
        connectivity.online = true
        advanceUntilIdle()
        assertEquals("Ana Ríos", vm.profile.author.name)
        assertFalse(vm.state.value.offline)
    }

    @Test
    fun `al volver a la pantalla se pone al día sin silueta, y si falla se queda lo que había`() = runTest(dispatcher) {
        val repository = FlakyRepository(failing = false)
        val vm = viewModel(repository)
        advanceUntilIdle()
        val before = vm.profile

        repository.failing = true
        vm.onResumed()
        assertTrue(vm.state.value.content is OwnProfileContent.Loaded)
        advanceUntilIdle()
        assertEquals(before, vm.profile)

        repository.failing = false
        repository.points = 355
        vm.onResumed()
        advanceUntilIdle()
        assertEquals(355, vm.profile.author.points)
    }

    @Test
    fun `lo guardado se muestra con su fecha`() = runTest(dispatcher) {
        val savedAt = Instant.parse("2026-09-25T13:00:00Z")
        val vm = viewModel(FlakyRepository(failing = false, savedAt = savedAt))
        advanceUntilIdle()

        assertEquals(savedAt, vm.profile.savedAt)
    }

    private inner class FlakyRepository(
        var failing: Boolean = true,
        var offline: Boolean = false,
        private val savedAt: Instant? = null,
    ) : UserRepository by server {
        var points: Int? = null

        override suspend fun ownProfile(): OwnProfile {
            if (offline) throw OfflineException()
            if (failing) throw IOException("sin red")
            val profile = server.ownProfile().copy(savedAt = savedAt)
            return points?.let { profile.copy(author = profile.author.copy(points = it)) } ?: profile
        }
    }
}
