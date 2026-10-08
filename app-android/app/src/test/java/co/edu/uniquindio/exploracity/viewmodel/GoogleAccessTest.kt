package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.google.GoogleCredentialResult
import co.edu.uniquindio.exploracity.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInUnavailableException
import co.edu.uniquindio.exploracity.domain.model.GoogleTokenRejectedException
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.TooManyAttemptsException
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

/** ADR-15 · «Continuar con Google» en el inicio de sesión (3) y el registro en modo Google (4, C1), y vincular (B1). */
@OptIn(ExperimentalCoroutinesApi::class)
class GoogleAccessTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val preferences = MemoryAccessPreferences()
    private val sessions = MemorySessions()
    private val auth = FakeAuth()
    private val google = FakeGoogleAuth()

    /** El ID token de la cuenta elegida, creado al ejecutar (GitGuardian). */
    private val idToken = randomSecret()

    private var prepared = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun login() = LoginViewModel(auth, sessions, preferences, connectivity, SavedStateHandle(), google) { prepared++ }

    private fun register(savedState: SavedStateHandle = SavedStateHandle()) =
        RegisterViewModel(auth, sessions, preferences, connectivity, savedState, google) { prepared++ }

    /** Como llega desde el inicio de sesión: los argumentos de la ruta Register. */
    private fun fromLogin(name: String? = "Pedro Gómez") =
        SavedStateHandle(mapOf("googleToken" to idToken, "googleEmail" to "pedro@gmail.com", "googleName" to name))

    // 3 · Inicio de sesión

    @Test
    fun `con la cuenta de Google vinculada entra como con la contraseña`() = runTest(dispatcher) {
        google.outcome = GoogleSignInOutcome.SignedIn(UserRole.MODERATOR)
        val vm = login()

        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        runCurrent()
        assertTrue(vm.google.state.value.busy)
        advanceUntilIdle()

        assertTrue(vm.state.value.signedIn)
        assertFalse(vm.google.state.value.busy)
        assertEquals(UserRole.MODERATOR, sessions.current.value)
        assertTrue(preferences.seen.value)
        assertEquals(1, prepared)
        assertEquals(listOf(idToken), google.signIns)
    }

    @Test
    fun `mientras espera la respuesta, otra cuenta elegida no se envía`() = runTest(dispatcher) {
        val vm = login()

        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        runCurrent()
        vm.google.onCredential(GoogleCredentialResult.Token(randomSecret()))
        advanceUntilIdle()

        assertEquals(listOf(idToken), google.signIns)
    }

    @Test
    fun `una cuenta de Google nueva abre el registro en modo Google con su correo y su nombre`() = runTest(dispatcher) {
        google.outcome = GoogleSignInOutcome.RegistrationRequired("pedro@gmail.com", "Pedro Gómez")
        val vm = login()

        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()

        assertEquals(GoogleRegistrationStart(idToken, "pedro@gmail.com", "Pedro Gómez"), vm.state.value.googleRegistration)
        assertFalse(vm.state.value.signedIn)
        assertNull(sessions.current.value)
        assertEquals(0, prepared)

        vm.onGoogleRegistrationShown()
        assertNull(vm.state.value.googleRegistration)
    }

    @Test
    fun `si el correo ya tiene contraseña, la pide una vez para vincular y entra`() = runTest(dispatcher) {
        google.outcome = GoogleSignInOutcome.LinkRequired("ana@correo.com")
        val vm = login()
        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()
        assertEquals(LinkPrompt("ana@correo.com"), vm.google.state.value.link)

        // Sin contraseña no se envía.
        vm.google.onLinkSubmit()
        advanceUntilIdle()
        assertTrue(google.links.isEmpty())

        val wrong = randomSecret()
        vm.google.onLinkPasswordChange(wrong)
        vm.google.onLinkSubmit()
        runCurrent()
        assertTrue(vm.google.state.value.link!!.submitting)
        advanceUntilIdle()
        // La contraseña queda escrita con el aviso; el campo que se vuelve a pintar con ella no lo quita.
        assertEquals(LinkPrompt("ana@correo.com", password = wrong, error = LinkError.CREDENTIALS), vm.google.state.value.link)
        vm.google.onLinkPasswordChange(wrong)
        assertEquals(LinkError.CREDENTIALS, vm.google.state.value.link!!.error)
        assertFalse(vm.state.value.signedIn)

        vm.google.onLinkPasswordChange(google.linkPassword)
        assertNull(vm.google.state.value.link!!.error)
        vm.google.onLinkSubmit()
        advanceUntilIdle()

        assertNull(vm.google.state.value.link)
        assertTrue(vm.state.value.signedIn)
        assertEquals(UserRole.USER, sessions.current.value)
        assertEquals(listOf(idToken to wrong, idToken to google.linkPassword), google.links)
    }

    @Test
    fun `tras 5 intentos el diálogo dice que espere, y si el token vence se cierra para elegir la cuenta otra vez`() = runTest(dispatcher) {
        google.outcome = GoogleSignInOutcome.LinkRequired("ana@correo.com")
        val vm = login()
        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()

        google.linkError = TooManyAttemptsException()
        vm.google.onLinkPasswordChange(randomSecret())
        vm.google.onLinkSubmit()
        advanceUntilIdle()
        assertEquals(LinkError.TOO_MANY_ATTEMPTS, vm.google.state.value.link?.error)

        google.linkError = GoogleTokenRejectedException()
        vm.google.onLinkPasswordChange(randomSecret())
        vm.google.onLinkSubmit()
        advanceUntilIdle()
        assertNull(vm.google.state.value.link)
        assertEquals(GoogleError.REJECTED, vm.google.state.value.error)
    }

    @Test
    fun `cerrar el diálogo de vincular olvida la cuenta elegida`() = runTest(dispatcher) {
        google.outcome = GoogleSignInOutcome.LinkRequired("ana@correo.com")
        val vm = login()
        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()
        vm.google.onLinkPasswordChange(google.linkPassword)

        vm.google.onLinkDismissed()
        vm.google.onLinkSubmit()
        advanceUntilIdle()

        assertNull(vm.google.state.value.link)
        assertTrue(google.links.isEmpty())
        assertFalse(vm.state.value.signedIn)
    }

    @Test
    fun `cerrar la hoja de Google no dice nada, sin cuentas o si falla, el aviso queda hasta cerrarlo`() = runTest(dispatcher) {
        val vm = login()

        vm.google.onCredential(GoogleCredentialResult.Cancelled)
        assertNull(vm.google.state.value.error)

        vm.google.onCredential(GoogleCredentialResult.NoAccount)
        assertEquals(GoogleError.NO_ACCOUNT, vm.google.state.value.error)
        vm.google.onErrorDismissed()
        assertNull(vm.google.state.value.error)

        vm.google.onCredential(GoogleCredentialResult.Failed)
        assertEquals(GoogleError.FAILED, vm.google.state.value.error)
        advanceUntilIdle()
        assertTrue(google.signIns.isEmpty())
    }

    @Test
    fun `lo que responde la API sin entrar se explica, y sin red lo dice el aviso de arriba`() = runTest(dispatcher) {
        val cases = mapOf(
            GoogleTokenRejectedException() to GoogleError.REJECTED,
            GoogleSignInUnavailableException() to GoogleError.UNAVAILABLE,
            EmailTakenException() to GoogleError.EMAIL_TAKEN,
            IOException("sin respuesta") to GoogleError.FAILED,
        )
        for ((error, shown) in cases) {
            google.signInError = error
            val vm = login()
            vm.google.onCredential(GoogleCredentialResult.Token(idToken))
            advanceUntilIdle()
            assertEquals(shown, vm.google.state.value.error)
            assertFalse(vm.state.value.signedIn)
        }

        google.signInError = OfflineException()
        val vm = login()
        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()
        assertNull(vm.google.state.value.error)
        assertTrue(vm.state.value.offline)
        assertNull(sessions.current.value)
    }

    // 4 · Registro en modo Google

    @Test
    fun `desde el inicio de sesión abre en modo Google, solo el nombre, ya escrito, y la autorización sin marcar`() = runTest(dispatcher) {
        val vm = register(fromLogin())
        advanceUntilIdle()

        assertEquals(GoogleMode("pedro@gmail.com"), vm.state.value.google)
        assertEquals("Pedro Gómez", vm.state.value.name)
        assertFalse(vm.state.value.consent)
        assertTrue(vm.state.value.fieldsValid)
        assertFalse(vm.state.value.canSubmit)

        vm.onResidencyChange(Residency.RESIDENT)
        vm.onConsentChange(true)
        vm.onSubmit()
        runCurrent()
        assertTrue(vm.state.value.submitting)
        advanceUntilIdle()

        assertEquals(listOf(Triple(idToken, "Pedro Gómez", Residency.RESIDENT)), google.registrations)
        assertTrue(auth.registered.isEmpty())
        assertEquals(Registration(UserRole.USER, welcomeEmailSent = true), vm.state.value.registered)
        assertEquals(UserRole.USER, sessions.current.value)
        assertTrue(preferences.seen.value)
        assertEquals(1, prepared)
    }

    @Test
    fun `el nombre de Google se puede cambiar y, sin uno válido, no se crea la cuenta`() = runTest(dispatcher) {
        val vm = register(fromLogin(name = null))
        advanceUntilIdle()
        assertEquals("", vm.state.value.name)
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(RegisterField.NAME, vm.state.value.focusField)
        assertTrue(google.registrations.isEmpty())

        vm.onNameChange(" Pedro ")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("Pedro", google.registrations.single().second)
    }

    @Test
    fun `si Android cierra la app, vuelve en modo Google con lo escrito`() = runTest(dispatcher) {
        val savedState = fromLogin()
        register(savedState).onNameChange("Pedro G.")

        val again = register(savedState)
        advanceUntilIdle()

        assertEquals(GoogleMode("pedro@gmail.com"), again.state.value.google)
        assertEquals("Pedro G.", again.state.value.name)
    }

    @Test
    fun `«Usar otro correo» vuelve al registro de siempre con el nombre escrito`() = runTest(dispatcher) {
        val savedState = fromLogin()
        val vm = register(savedState)
        advanceUntilIdle()

        vm.onUseEmailInstead()

        assertNull(vm.state.value.google)
        assertEquals("Pedro Gómez", vm.state.value.name)
        // Ahora faltan el correo y la contraseña.
        assertFalse(vm.state.value.fieldsValid)
        assertNull(register(savedState).state.value.google)
    }

    @Test
    fun `«Continuar con Google» en el registro, una cuenta nueva pasa al modo Google y una vinculada entra`() = runTest(dispatcher) {
        google.outcome = GoogleSignInOutcome.RegistrationRequired("pedro@gmail.com", "Pedro de Google")
        val vm = register()
        vm.onNameChange("Pedro")
        vm.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()

        assertEquals(GoogleMode("pedro@gmail.com"), vm.state.value.google)
        // Lo que ya estaba escrito no se pisa.
        assertEquals("Pedro", vm.state.value.name)

        google.outcome = GoogleSignInOutcome.SignedIn(UserRole.USER)
        val linked = register()
        linked.google.onCredential(GoogleCredentialResult.Token(idToken))
        advanceUntilIdle()

        assertTrue(linked.state.value.signedIn)
        assertNull(linked.state.value.registered)
        assertEquals(UserRole.USER, sessions.current.value)
    }

    @Test
    fun `si el token venció, vuelve al registro de siempre y pide elegir la cuenta otra vez`() = runTest(dispatcher) {
        google.registerError = GoogleTokenRejectedException()
        val vm = register(fromLogin())
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertNull(vm.state.value.google)
        assertFalse(vm.state.value.submitting)
        assertNull(vm.state.value.failure)
        assertEquals(GoogleError.REJECTED, vm.google.state.value.error)
        assertTrue(vm.state.value.consent)
        assertNull(sessions.current.value)
    }

    @Test
    fun `si el correo tomó una cuenta mientras tanto, el registro de siempre lo dice junto al correo`() = runTest(dispatcher) {
        google.registerError = EmailTakenException()
        val vm = register(fromLogin())
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertNull(vm.state.value.google)
        assertEquals("pedro@gmail.com", vm.state.value.email)
        assertTrue(vm.state.value.emailTaken)
    }

    @Test
    fun `sin respuesta no dice que la cuenta no se creó, y el reintento usa la misma cuenta de Google`() = runTest(dispatcher) {
        google.registerError = UnconfirmedRegistrationException(IOException("sin respuesta"))
        val vm = register(fromLogin())
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(RegisterFailure.UNCONFIRMED, vm.state.value.failure)
        assertEquals(GoogleMode("pedro@gmail.com"), vm.state.value.google)

        google.registerError = null
        vm.onFailureDismissed()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf(idToken, idToken), google.registrations.map { it.first })
        assertEquals(UserRole.USER, sessions.current.value)
    }

    @Test
    fun `sin red no se intenta crear la cuenta con Google`() = runTest(dispatcher) {
        val vm = register(fromLogin())
        vm.onConsentChange(true)
        connectivity.online = false
        advanceUntilIdle()

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(google.registrations.isEmpty())
        assertFalse(vm.state.value.canSubmit)
    }
}
