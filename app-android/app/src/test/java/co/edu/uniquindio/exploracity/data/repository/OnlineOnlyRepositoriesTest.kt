package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

/**
 * Lo que solo funciona con red (acceso, cuenta, publicaciones, sugerencia y parecidos): sin ella se avisa sin intentar y
 * nada queda a medias. Con red, cada envoltorio llega al servidor.
 */
class OnlineOnlyRepositoriesTest {

    private val offline = FakeConnectivity(online = false)
    private val pois = FakePoiRepository(latency = Duration.ZERO)
    private val publications = FakePublicationRepository(pois)
    private val accounts = FakeAccountRepository(pois, publications, FakeUserRepository(pois, publications), FakeAuthRepository())

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val goodPassword = "a".repeat(7) + "1"

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test
    fun `sin red el acceso no se intenta`() = runTest {
        val auth = OnlineOnlyAuthRepository(FakeAuthRepository(), offline)

        assertTrue(failure { auth.signIn("ana@correo.com", goodPassword) } is OfflineException)
        assertTrue(failure { auth.resumeSession() } is OfflineException)
        assertTrue(failure { auth.register(NewAccount("Pedro", "pedro@correo.com", goodPassword, Residency.VISITOR)) } is OfflineException)
        assertTrue(failure { auth.requestPasswordReset("ana@correo.com") } is OfflineException)
        assertTrue(failure { auth.openResetLink("enlace") } is OfflineException)
        assertTrue(failure { auth.resetPassword("enlace", goodPassword) } is OfflineException)
    }

    @Test
    fun `sin red no se entra, no se registra ni se vincula con Google`() = runTest {
        var calls = 0
        val remote = object : GoogleAuthRepository {
            override suspend fun signInWithGoogle(idToken: String): GoogleSignInOutcome {
                calls++
                return GoogleSignInOutcome.SignedIn(UserRole.USER)
            }

            override suspend fun registerWithGoogle(idToken: String, name: String, residency: Residency): Registration {
                calls++
                return Registration(UserRole.USER, welcomeEmailSent = true)
            }

            override suspend fun linkGoogle(idToken: String, password: String): UserRole {
                calls++
                return UserRole.USER
            }
        }
        val google = OnlineOnlyGoogleAuthRepository(remote, offline)
        val idToken = randomSecret()

        assertTrue(failure { google.signInWithGoogle(idToken) } is OfflineException)
        assertTrue(failure { google.registerWithGoogle(idToken, "Pedro", Residency.VISITOR) } is OfflineException)
        assertTrue(failure { google.linkGoogle(idToken, goodPassword) } is OfflineException)
        assertEquals(0, calls)

        assertEquals(GoogleSignInOutcome.SignedIn(UserRole.USER), OnlineOnlyGoogleAuthRepository(remote, FakeConnectivity()).signInWithGoogle(idToken))
        assertEquals(1, calls)
    }

    @Test
    fun `sin red no se descarga ni se borra la cuenta, pero el correo sí se conoce`() = runTest {
        val account = OnlineOnlyAccountRepository(accounts, offline)

        assertEquals("ana.rios@correo.com", account.account.value.email)
        assertTrue(failure { account.exportData() } is OfflineException)
        assertTrue(failure { account.deleteAccount() } is OfflineException)
        assertTrue(!accounts.deleted)
    }

    @Test
    fun `sin red no se pide, no se reenvía ni se confirma el cambio de correo`() = runTest {
        val account = OnlineOnlyAccountRepository(accounts, offline)

        assertTrue(failure { account.requestEmailChange("ana.nueva@correo.com", goodPassword) } is OfflineException)
        assertTrue(failure { account.resendEmailChange() } is OfflineException)
        assertTrue(failure { account.confirmEmailChange("enlace") } is OfflineException)
    }

    @Test
    fun `con red, borrar la cuenta llega al servidor`() = runTest {
        OnlineOnlyAccountRepository(accounts, FakeConnectivity()).deleteAccount()

        assertTrue(accounts.deleted)
    }

    @Test
    fun `sin red una publicación ni se ve ni se elimina, y no queda nada en cola`() = runTest {
        val repository = OnlineOnlyPublicationRepository(publications, offline)

        assertTrue(failure { repository.publication("mirador-de-la-pena") } is OfflineException)
        assertTrue(failure { repository.delete("mirador-de-la-pena") } is OfflineException)
        assertEquals(PublicationStatus.REJECTED, publications.publication("mirador-de-la-pena")?.status)
    }

    @Test
    fun `sin red no se pide la sugerencia ni se buscan parecidos`() = runTest {
        val suggester = OnlineOnlyCategorySuggester(FakeCategorySuggester(latency = Duration.ZERO), offline)
        val finder = OnlineOnlyDuplicateFinder(FakeDuplicateFinder(pois, publications, latency = Duration.ZERO), offline)

        assertTrue(failure { suggester.suggest("Café Las Acacias", "Café de barrio") } is OfflineException)
        assertTrue(failure { finder.similarPlaces("La Puerta Falsa", GeoPoint(4.59752, -74.0746)) } is OfflineException)
    }
}
