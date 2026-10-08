package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Residency
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

/** 4 · Registro con autorización de datos. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val preferences = MemoryAccessPreferences()
    private val sessions = MemorySessions()
    private val auth = FakeAuth()
    private val google = FakeGoogleAuth()

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val goodPassword = "a".repeat(7) + "1"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun register(savedState: SavedStateHandle = SavedStateHandle()) =
        RegisterViewModel(auth, sessions, preferences, connectivity, savedState, google)

    /** Nombre, correo y contraseña válidos; la autorización sin marcar. */
    private fun filled(savedState: SavedStateHandle = SavedStateHandle()) = register(savedState).apply {
        onNameChange("  Pedro Gómez ")
        onEmailChange(" pedro@correo.com ")
        onPasswordChange(goodPassword)
    }

    @Test
    fun `la autorización nunca viene marcada y sin ella no se puede crear la cuenta`() = runTest(dispatcher) {
        val vm = filled()
        assertFalse(vm.state.value.consent)
        assertTrue(vm.state.value.fieldsValid)
        assertFalse(vm.state.value.canSubmit)

        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(auth.registered.isEmpty())

        vm.onConsentChange(true)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `la contraseña necesita 8 caracteres con una letra y un número, y el nombre de 2 a 40`() = runTest(dispatcher) {
        val vm = filled()

        vm.onPasswordChange("a".repeat(8))
        assertFalse(vm.state.value.passwordValid)
        vm.onPasswordChange("1".repeat(8))
        assertFalse(vm.state.value.passwordValid)
        vm.onPasswordChange("a".repeat(6) + "1")
        assertFalse(vm.state.value.passwordValid)
        vm.onPasswordChange(goodPassword)
        assertTrue(vm.state.value.passwordValid)

        vm.onNameChange(" P ")
        assertEquals(1, vm.state.value.nameMissing)
        vm.onNameChange("P".repeat(43))
        assertEquals(3, vm.state.value.nameExcess)
        assertFalse(vm.state.value.fieldsValid)
    }

    @Test
    fun `los avisos de cada campo salen al dejarlo, no antes`() = runTest(dispatcher) {
        val vm = register()
        vm.onEmailChange("pedro@")
        assertFalse(vm.state.value.showEmailError)

        vm.onEmailBlur()

        assertTrue(vm.state.value.showEmailError)
    }

    @Test
    fun `crear la cuenta abre la sesión como usuario y marca el onboarding visto`() = runTest(dispatcher) {
        val vm = filled()
        vm.onResidencyChange(Residency.RESIDENT)
        vm.onConsentChange(true)

        vm.onSubmit()
        runCurrent()
        assertTrue(vm.state.value.submitting)
        advanceUntilIdle()

        val sent = auth.registered.single()
        assertEquals(NewAccount("Pedro Gómez", "pedro@correo.com", goodPassword, Residency.RESIDENT, sent.clientId), sent)
        assertEquals(UserRole.USER, sessions.current.value)
        assertTrue(preferences.seen.value)
        assertEquals(true, vm.state.value.registered?.welcomeEmailSent)
    }

    @Test
    fun `si falla el correo de bienvenida la cuenta queda creada igual`() = runTest(dispatcher) {
        auth.welcomeEmailSent = false
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(UserRole.USER, sessions.current.value)
        assertEquals(false, vm.state.value.registered?.welcomeEmailSent)
    }

    @Test
    fun `un correo con cuenta se avisa junto al campo, con el foco ahí, hasta cambiarlo`() = runTest(dispatcher) {
        auth.registerError = EmailTakenException()
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.emailTaken)
        assertFalse(vm.state.value.canSubmit)
        assertEquals(RegisterField.EMAIL, vm.state.value.focusField)
        assertNull(sessions.current.value)

        vm.onEmailChange("pedro.gomez@correo.com")
        assertFalse(vm.state.value.emailTaken)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `un intento con errores lleva el foco al primer campo con error`() = runTest(dispatcher) {
        val vm = register()
        vm.onEmailChange("pedro@correo.com")

        vm.onSubmit()

        assertEquals(RegisterField.NAME, vm.state.value.focusField)
        assertTrue(vm.state.value.showNameError)
        assertTrue(vm.state.value.showPasswordError)
        assertTrue(auth.registered.isEmpty())
    }

    @Test
    fun `sin red no se intenta y el botón queda deshabilitado`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.offline)
        assertFalse(vm.state.value.canSubmit)
        assertTrue(auth.registered.isEmpty())
    }

    @Test
    fun `si la red se cae al enviar lo dice el aviso sin conexión`() = runTest(dispatcher) {
        auth.registerError = OfflineException()
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.offline)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `otro fallo deja todo escrito y se avisa hasta cerrarlo`() = runTest(dispatcher) {
        auth.registerError = IOException("500")
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(RegisterFailure.NOT_CREATED, vm.state.value.failure)
        assertEquals(" pedro@correo.com ", vm.state.value.email)
        assertEquals(goodPassword, vm.state.value.password)
        assertNull(sessions.current.value)
        vm.onFailureDismissed()
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `si el servidor no respondió no dice que la cuenta no se creó, y se puede intentar de nuevo`() = runTest(dispatcher) {
        auth.registerError = UnconfirmedRegistrationException(IOException("timeout"))
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(RegisterFailure.UNCONFIRMED, vm.state.value.failure)
        assertNull(sessions.current.value)
        assertTrue(vm.state.value.canSubmit)

        // La primera petición sí la creó: el reintento ofrece iniciar sesión con ese correo.
        auth.registerError = EmailTakenException()
        vm.onSubmit()
        advanceUntilIdle()

        assertNull(vm.state.value.failure)
        assertTrue(vm.state.value.emailTaken)
    }

    @Test
    fun `cada intento manda el mismo clientId, también al recrear la pantalla`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        auth.registerError = UnconfirmedRegistrationException(IOException("timeout"))
        val vm = filled(savedState)
        vm.onConsentChange(true)

        vm.onSubmit()
        advanceUntilIdle()
        vm.onSubmit()
        advanceUntilIdle()
        // Android cerró la app: la contraseña no se guarda y se escribe de nuevo.
        register(savedState).apply {
            onPasswordChange(goodPassword)
            onSubmit()
        }
        advanceUntilIdle()

        assertEquals(3, auth.registered.size)
        assertEquals(1, auth.registered.map { it.clientId }.distinct().size)
        // Otro formulario es otro registro.
        filled().apply {
            onConsentChange(true)
            onSubmit()
        }
        advanceUntilIdle()
        assertEquals(2, auth.registered.map { it.clientId }.distinct().size)
    }

    @Test
    fun `no hay doble envío ni cambios mientras se crea`() = runTest(dispatcher) {
        val vm = filled()
        vm.onConsentChange(true)

        vm.onSubmit()
        vm.onSubmit()
        runCurrent()
        vm.onNameChange("Otro")
        vm.onConsentChange(false)
        advanceUntilIdle()

        assertEquals(1, auth.registered.size)
        assertEquals("  Pedro Gómez ", vm.state.value.name)
        assertTrue(vm.state.value.consent)
    }

    @Test
    fun `al recrear la pantalla conserva lo escrito menos la contraseña`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        filled(savedState).apply {
            onResidencyChange(Residency.RESIDENT)
            onConsentChange(true)
        }

        val recreated = register(savedState).state.value

        assertEquals("  Pedro Gómez ", recreated.name)
        assertEquals(" pedro@correo.com ", recreated.email)
        assertEquals(Residency.RESIDENT, recreated.residency)
        assertTrue(recreated.consent)
        assertEquals("", recreated.password)
    }
}
