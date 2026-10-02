package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.domain.model.DemoAccount
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.seconds

/** 1, 2 y 3 · Por dónde entra la persona, el onboarding visto y el inicio de sesión. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val preferences = MemoryAccessPreferences()
    private val sessions = MemorySessions()
    private val auth = FakeAuth()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun splash() = SplashViewModel(preferences, sessions, auth, connectivity)

    private fun login(savedState: SavedStateHandle = SavedStateHandle()) =
        LoginViewModel(auth, sessions, preferences, connectivity, savedState, listOf(DemoAccount("Ana", "ana@correo.com")))

    // 1 · Splash

    @Test
    fun `la primera vez va al onboarding y luego al inicio de sesión`() = runTest(dispatcher) {
        val first = splash()
        advanceUntilIdle()
        assertEquals(SplashState.Done(SplashDestination.ONBOARDING), first.state.value)

        preferences.seen.value = true
        val later = splash()
        advanceUntilIdle()
        assertEquals(SplashState.Done(SplashDestination.LOGIN), later.state.value)
    }

    @Test
    fun `con sesión y red va al feed cuando el servidor la confirma`() = runTest(dispatcher) {
        sessions.current.value = UserRole.USER
        val vm = splash()
        runCurrent()
        assertEquals(SplashState.Loading, vm.state.value)

        advanceUntilIdle()

        assertEquals(SplashState.Done(SplashDestination.FEED), vm.state.value)
        assertEquals(1, auth.resumes)
    }

    @Test
    fun `si el servidor tarda más de 2 s sigue igual al feed`() = runTest(dispatcher) {
        sessions.current.value = UserRole.USER
        auth.resumeDelay = 10.seconds
        val vm = splash()

        advanceTimeBy(2.1.seconds)

        assertEquals(SplashState.Done(SplashDestination.FEED), vm.state.value)
    }

    @Test
    fun `con sesión y sin red ofrece reintentar o seguir con lo guardado (1c)`() = runTest(dispatcher) {
        sessions.current.value = UserRole.USER
        connectivity.online = false
        val vm = splash()
        advanceUntilIdle()
        assertEquals(SplashState.Offline, vm.state.value)

        connectivity.online = true
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(SplashState.Done(SplashDestination.FEED), vm.state.value)
    }

    @Test
    fun `continuar sin conexión abre el feed con lo guardado`() = runTest(dispatcher) {
        sessions.current.value = UserRole.USER
        connectivity.online = false
        val vm = splash()
        advanceUntilIdle()

        vm.onContinueOffline()

        assertEquals(SplashState.Done(SplashDestination.FEED), vm.state.value)
    }

    @Test
    fun `si el servidor no responde lo trata como sin conexión`() = runTest(dispatcher) {
        sessions.current.value = UserRole.USER
        auth.resumeError = IOException("500")
        val vm = splash()
        advanceUntilIdle()

        assertEquals(SplashState.Offline, vm.state.value)
    }

    // 2 · Onboarding

    @Test
    fun `salir del onboarding lo marca como visto`() = runTest(dispatcher) {
        OnboardingViewModel(preferences).onFinished()
        advanceUntilIdle()

        assertTrue(preferences.seen.value)
    }

    // 3 · Inicio de sesión

    @Test
    fun `el botón se habilita con un correo completo y 8 caracteres`() = runTest(dispatcher) {
        val vm = login()
        vm.onEmailChange("ana.rios@correo")
        vm.onPasswordChange("1234567")
        assertFalse(vm.state.value.canSubmit)

        vm.onEmailChange("ana.rios@correo.com")
        vm.onPasswordChange("12345678")

        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `los avisos de cada campo salen al dejarlo, y un intento con errores lleva al primero`() = runTest(dispatcher) {
        val vm = login()
        vm.onEmailChange("ana")
        assertFalse(vm.state.value.showEmailError)
        vm.onEmailBlur()
        assertTrue(vm.state.value.showEmailError)

        vm.onSubmit()

        assertTrue(vm.state.value.showPasswordError)
        assertEquals(LoginField.EMAIL, vm.state.value.focusField)
        assertEquals(0, auth.signIns)
    }

    @Test
    fun `entrar abre la sesión con su rol y marca el onboarding como visto`() = runTest(dispatcher) {
        val vm = login()
        vm.onEmailChange(" moderador@correo.com ")
        vm.onPasswordChange("clave-segura")

        vm.onSubmit()
        runCurrent()
        assertTrue(vm.state.value.submitting)
        vm.onEmailChange("otra@correo.com")
        vm.onSubmit()
        assertEquals("Mientras entra, ni se edita ni se envía dos veces", " moderador@correo.com ", vm.state.value.email)
        advanceUntilIdle()

        assertTrue(vm.state.value.signedIn)
        assertEquals(UserRole.MODERATOR, sessions.current.value)
        assertTrue(preferences.seen.value)
        assertEquals(1, auth.signIns)
        assertEquals("moderador@correo.com", auth.lastEmail)
    }

    @Test
    fun `si no coinciden lo dice hasta que se cierra, sin abrir sesión`() = runTest(dispatcher) {
        val vm = login()
        vm.onEmailChange("ana@correo.com")
        vm.onPasswordChange("otra-clave")

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(LoginError.CREDENTIALS, vm.state.value.error)
        assertFalse(vm.state.value.submitting)
        assertNull(sessions.current.value)
        vm.onErrorDismissed()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `sin red no se intenta y si se cae al enviar lo muestra`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = login()
        advanceUntilIdle()
        vm.onEmailChange("ana@correo.com")
        vm.onPasswordChange("clave-segura")
        assertFalse(vm.state.value.canSubmit)
        vm.onSubmit()
        assertEquals(0, auth.signIns)

        connectivity.online = true
        advanceUntilIdle()
        auth.signInError = OfflineException()
        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.offline)
        assertNull(sessions.current.value)
    }

    @Test
    fun `otro fallo del servidor tiene su propio aviso`() = runTest(dispatcher) {
        auth.signInError = IOException("500")
        val vm = login()
        vm.onEmailChange("ana@correo.com")
        vm.onPasswordChange("clave-segura")

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(LoginError.FAILED, vm.state.value.error)
    }

    @Test
    fun `una cuenta de prueba rellena el correo y una contraseña válida inventada al momento`() = runTest(dispatcher) {
        val vm = login()

        vm.onDemoAccount(vm.state.value.demoAccounts.single())

        assertEquals("ana@correo.com", vm.state.value.email)
        assertEquals(12, vm.state.value.password.length)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `el correo sobrevive si Android cierra la app, la contraseña no se guarda`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = login(savedState)
        vm.onEmailChange("ana@correo.com")
        vm.onPasswordChange("clave-segura")

        val recreated = login(savedState)

        assertEquals("ana@correo.com", recreated.state.value.email)
        assertEquals("", recreated.state.value.password)
    }
}

private class MemoryAccessPreferences : AppPreferences {
    val seen = MutableStateFlow(false)
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val onboardingSeen = seen

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setOnboardingSeen() {
        seen.value = true
    }
}

private class MemorySessions : SessionStore {
    val current = MutableStateFlow<UserRole?>(null)
    override val role = current

    override suspend fun open(role: UserRole) {
        current.value = role
    }

    override suspend fun close() {
        current.value = null
    }
}

/** Dos cuentas: «ana@correo.com» y «moderador@correo.com», ambas con «clave-segura». */
private class FakeAuth : AuthRepository {
    var signIns = 0
    var resumes = 0
    var lastEmail: String? = null
    var signInError: Exception? = null
    var resumeError: Exception? = null
    var resumeDelay = 0.5.seconds

    override suspend fun signIn(email: String, password: String): UserRole {
        signIns++
        lastEmail = email
        delay(1.seconds)
        signInError?.let { throw it }
        if (password != "clave-segura") throw InvalidCredentialsException()
        return when (email) {
            "ana@correo.com" -> UserRole.USER
            "moderador@correo.com" -> UserRole.MODERATOR
            else -> throw InvalidCredentialsException()
        }
    }

    override suspend fun resumeSession() {
        resumes++
        delay(resumeDelay)
        resumeError?.let { throw it }
    }
}
