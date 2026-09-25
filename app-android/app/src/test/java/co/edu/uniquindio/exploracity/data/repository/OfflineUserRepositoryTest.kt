package co.edu.uniquindio.exploracity.data.repository

import androidx.room.Room
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
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
import kotlin.time.Duration

/** 26 y 27 · El perfil propio queda guardado para verlo sin conexión; los de otras personas necesitan red (31). */
@RunWith(RobolectricTestRunner::class)
class OfflineUserRepositoryTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), ZoneOffset.UTC)
    private val connectivity = FakeConnectivity()
    private val server = FakeUserRepository(FakePoiRepository(), latency = Duration.ZERO, actionLatency = Duration.ZERO)
    private lateinit var database: ExploraDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ExploraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    private fun repository(remote: UserRepository = server) = OfflineUserRepository(remote, database.profileDao(), connectivity, clock)

    @Test
    fun `con red trae el perfil del servidor, sin fecha de guardado`() = runTest {
        val profile = repository().ownProfile()

        assertNull(profile.savedAt)
        assertEquals(server.ownProfile(), profile)
    }

    @Test
    fun `sin red se ve el último perfil guardado con su fecha, y sin nada guardado avisa`() = runTest {
        val repository = repository()
        connectivity.online = false
        assertTrue(runCatching { repository.ownProfile() }.exceptionOrNull() is OfflineException)

        connectivity.online = true
        val online = repository.ownProfile()
        connectivity.online = false
        val offline = repository.ownProfile()

        assertEquals(clock.instant(), offline.savedAt)
        assertEquals(online, offline.copy(savedAt = null))
    }

    @Test
    fun `con red pero sin respuesta del servidor se ve lo guardado`() = runTest {
        val flaky = FlakyServer(server)
        val repository = repository(flaky)
        repository.ownProfile()

        flaky.failing = true

        assertEquals(clock.instant(), repository.ownProfile().savedAt)
    }

    @Test
    fun `sin red el perfil de otra persona y el reporte avisan que falta la conexión`() = runTest {
        val repository = repository()
        connectivity.online = false

        assertTrue(runCatching { repository.publicProfile("camilo-r") }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { repository.reportUser("camilo-r", ReportReason.SPAM) }.exceptionOrNull() is OfflineException)
    }

    private class FlakyServer(private val delegate: UserRepository) : UserRepository by delegate {
        var failing = false

        override suspend fun ownProfile(): OwnProfile {
            if (failing) throw IOException("el servidor no responde")
            return delegate.ownProfile()
        }
    }
}
