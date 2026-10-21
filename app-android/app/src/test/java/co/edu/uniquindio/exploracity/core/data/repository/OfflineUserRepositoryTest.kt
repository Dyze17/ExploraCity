package co.edu.uniquindio.exploracity.core.data.repository

import androidx.room.Room
import co.edu.uniquindio.exploracity.core.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.core.data.local.SavedProfileEntity
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.repository.UserRepository
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

    @Test
    fun `editar el perfil pone al día lo guardado, también sobre mí y la foto`() = runTest {
        val repository = repository()
        repository.updateProfile(ProfileUpdate("Ana Ríos", "Cafés con patio.", Residency.RESIDENT, PhotoChange.Replace("/fotos/yo.jpg")))
        connectivity.online = false

        val saved = repository.ownProfile()

        assertEquals("Cafés con patio.", saved.bio)
        assertEquals("fake://perfil/yo.jpg", saved.photo)
    }

    @Test
    fun `sin red no se edita el perfil`() = runTest {
        connectivity.online = false

        val failure = runCatching { repository().updateProfile(ProfileUpdate("Ana Ríos", null, Residency.RESIDENT, PhotoChange.Keep)) }

        assertTrue(failure.exceptionOrNull() is OfflineException)
    }

    @Test
    fun `la ciudad que sabe la app llena la del perfil y queda guardada, y con una guardada ya no pregunta`() = runTest {
        val counting = object : UserRepository by server {
            var fills = 0

            override suspend fun fillTown(town: Municipality): OwnProfile {
                fills++
                return server.fillTown(town)
            }
        }
        val repository = repository(counting)
        server.updateProfile(ProfileUpdate("Ana Ríos", null, Residency.RESIDENT, PhotoChange.Keep, town = null, townChanged = true))
        connectivity.online = false
        assertTrue(runCatching { repository.fillTown(calarca) }.exceptionOrNull() is OfflineException)

        connectivity.online = true
        repository.ownProfile()
        repository.fillTown(calarca)
        assertEquals("Quitada a mano, el servidor no la vuelve a llenar", null, repository.ownProfile().town)

        server.deleteOwnAccount()
        repository.ownProfile()
        assertEquals(sampleTown, repository.fillTown(calarca).town)
        connectivity.online = false
        assertEquals("Con la guardada no hace falta red", sampleTown, repository.fillTown(calarca).town)
        assertEquals("Solo preguntó cuando lo guardado no tenía ciudad", 1, counting.fills)
    }

    @Test
    fun `una ciudad nueva queda guardada para ver el perfil sin conexion`() = runTest {
        val empty = object : UserRepository by server {
            private var town: Municipality? = null

            override suspend fun ownProfile() = server.ownProfile().copy(town = town)

            override suspend fun fillTown(town: Municipality): OwnProfile {
                this.town = this.town ?: town
                return ownProfile()
            }
        }
        val repository = repository(empty)
        repository.ownProfile()

        repository.fillTown(calarca)
        connectivity.online = false

        assertEquals(calarca, repository.ownProfile().town)
    }

    @Test
    fun `lo guardado por la 1_2, con city en vez de town, se sigue viendo sin ciudad`() = runTest {
        val json = """{"authorId":"ana-rios","name":"Ana Ríos","points":340,"residency":"RESIDENT","city":"Armenia","memberSince":"2026-03",""" +
            """"pending":0,"verified":1,"rejected":0,"finalized":0,"badges":[]}"""
        database.profileDao().save(SavedProfileEntity(json = json, savedAtMillis = 0))
        connectivity.online = false

        val saved = repository().ownProfile()

        assertEquals("Ana Ríos", saved.author.name)
        assertNull(saved.town)
    }

    private val calarca = Municipality("Calarcá", "Quindío")

    private class FlakyServer(private val delegate: UserRepository) : UserRepository by delegate {
        var failing = false

        override suspend fun ownProfile(): OwnProfile {
            if (failing) throw IOException("el servidor no responde")
            return delegate.ownProfile()
        }
    }
}
