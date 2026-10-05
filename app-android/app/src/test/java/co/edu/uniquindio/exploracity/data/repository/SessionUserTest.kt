package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.FakeProfileDao
import co.edu.uniquindio.exploracity.data.local.SessionAccount
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** La persona de la sesión: el id de la sesión con la API, y el nombre y los puntos del perfil propio guardado. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionUserTest {

    private val stores = TestSession()
    private val profiles = FakeProfileDao()

    private fun profile(author: Author) =
        OwnProfile(author, Residency.RESIDENT, "Armenia", YearMonth.of(2026, 3), PublicationCounts(), emptyList()).toEntity(savedAtMillis = 0)

    @Test
    fun `el id llega con la sesión, y el nombre y los puntos con el perfil guardado`() = runTest {
        val user = SessionUser(stores.session, profiles, backgroundScope)
        runCurrent()
        assertEquals(Author("", "", 0), user.author.value)

        stores.accounts.save(SessionAccount("ana-rios", Account("ana@correo.com")))
        runCurrent()
        assertEquals(Author("ana-rios", "", 0), user.author.value)

        profiles.save(profile(Author("ana-rios", "Ana Ríos", 340)))
        runCurrent()
        assertEquals(Author("ana-rios", "Ana Ríos", 340), user.author.value)
    }

    @Test
    fun `el perfil guardado de otra cuenta no cuenta`() = runTest {
        profiles.save(profile(Author("ana-rios", "Ana Ríos", 340)))
        stores.accounts.save(SessionAccount("pedro", Account("pedro@correo.com")))

        val user = SessionUser(stores.session, profiles, backgroundScope)
        runCurrent()

        assertEquals(Author("pedro", "", 0), user.author.value)
    }
}
