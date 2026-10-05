package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** «Cambiar correo»: el formulario, «Confirma tu correo nuevo» y la confirmación del enlace. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeEmailViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val connectivity = FakeConnectivity()
    private val accounts = FakeAccounts()

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val goodPassword = "a".repeat(7) + "1"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun form(newEmail: String = "") = ChangeEmailViewModel(accounts, connectivity, clock, SavedStateHandle(mapOf("newEmail" to newEmail)))

    private fun filled() = form().apply {
        onEmailChange(" Ana.Nueva@correo.com ")
        onPasswordChange(goodPassword)
    }

    private fun confirm() = ConfirmEmailViewModel(accounts, SavedStateHandle(mapOf("token" to "enlace")))

    // Formulario

    @Test
    fun `muestra el correo de ahora y, si lo hay, el que falta confirmar`() = runTest(dispatcher) {
        accounts.current.value = Account("ana.rios@correo.com", pendingEmail = "ana.otra@correo.com")

        val state = form().state.value

        assertEquals("ana.rios@correo.com", state.currentEmail)
        assertEquals("ana.otra@correo.com", state.pendingEmail)
    }

    @Test
    fun `abre con el correo nuevo que venía escrito`() = runTest(dispatcher) {
        assertEquals("ana.nueva@correo.com", form("ana.nueva@correo.com").state.value.newEmail)
    }

    @Test
    fun `pide un correo completo, distinto al de ahora, y la contraseña`() = runTest(dispatcher) {
        val vm = form()
        vm.onEmailChange("ANA.RIOS@correo.com")
        vm.onPasswordChange(goodPassword)
        assertTrue(vm.state.value.sameAsCurrent)
        assertFalse(vm.state.value.canSubmit)

        vm.onEmailChange("ana.nueva@correo")
        assertFalse(vm.state.value.emailValid)

        vm.onEmailChange("ana.nueva@correo.com")
        vm.onPasswordChange("a".repeat(7))
        assertFalse(vm.state.value.canSubmit)

        vm.onPasswordChange(goodPassword)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `un intento con errores lleva el foco al primer campo con error`() = runTest(dispatcher) {
        val vm = form()
        vm.onEmailChange("ana.nueva@correo.com")

        vm.onSubmit()

        assertEquals(ChangeEmailField.PASSWORD, vm.state.value.focusField)
        assertTrue(vm.state.value.passwordTouched)
        assertTrue(accounts.emailRequests.isEmpty())
    }

    @Test
    fun `al enviar sigue a confirmar con los dos correos, y la contraseña se borra`() = runTest(dispatcher) {
        val vm = filled()

        vm.onSubmit()
        vm.onSubmit()
        runCurrent()
        assertTrue(vm.state.value.sending)
        advanceUntilIdle()

        assertEquals(listOf("ana.nueva@correo.com" to goodPassword), accounts.emailRequests)
        assertEquals(EmailChangeRequested("ana.nueva@correo.com", "ana.rios@correo.com", 1_000L), vm.state.value.sent)
        assertEquals("", vm.state.value.password)
        assertEquals("ana.nueva@correo.com", vm.state.value.pendingEmail)
        vm.onSentHandled()
        assertNull(vm.state.value.sent)
    }

    @Test
    fun `una contraseña que no coincide se dice junto al campo hasta cambiarla`() = runTest(dispatcher) {
        accounts.emailError = InvalidCredentialsException()
        val vm = filled()

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.wrongPassword)
        assertEquals(ChangeEmailField.PASSWORD, vm.state.value.focusField)
        assertFalse(vm.state.value.canSubmit)
        vm.onPasswordChange(goodPassword + "2")
        assertFalse(vm.state.value.wrongPassword)
    }

    @Test
    fun `un correo que ya tiene cuenta se dice junto al campo hasta cambiarlo`() = runTest(dispatcher) {
        accounts.emailError = EmailTakenException()
        val vm = filled()

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.emailTaken)
        assertEquals(ChangeEmailField.EMAIL, vm.state.value.focusField)
        vm.onEmailChange("ana.otra@correo.com")
        assertFalse(vm.state.value.emailTaken)
    }

    @Test
    fun `si el correo no sale se avisa y se puede reintentar sin perder lo escrito`() = runTest(dispatcher) {
        accounts.emailError = EmailDeliveryException()
        val vm = filled()

        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(ChangeEmailError.MAIL_FAILED, vm.state.value.error)
        assertEquals(goodPassword, vm.state.value.password)

        accounts.emailError = null
        vm.onErrorShown()
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("ana.nueva@correo.com", vm.state.value.sent?.email)
    }

    @Test
    fun `otro fallo se avisa y sin red no se intenta`() = runTest(dispatcher) {
        accounts.emailError = IOException("500")
        val vm = filled()
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(ChangeEmailError.FAILED, vm.state.value.error)

        accounts.emailError = OfflineException()
        vm.onErrorShown()
        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun `al recrear la pantalla conserva el correo nuevo pero no la contraseña`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        ChangeEmailViewModel(accounts, connectivity, clock, savedState).apply {
            onEmailChange("ana.nueva@correo.com")
            onPasswordChange(goodPassword)
        }

        val recreated = ChangeEmailViewModel(accounts, connectivity, clock, savedState).state.value

        assertEquals("ana.nueva@correo.com", recreated.newEmail)
        assertEquals("", recreated.password)
    }

    // Confirma tu correo nuevo

    @Test
    fun `reenviar pide otro enlace del cambio pendiente`() = runTest(dispatcher) {
        val vm = LinkSentViewModel(
            resend = { accounts.resendEmailChange() },
            connectivity = connectivity,
            clock = clock,
            savedStateHandle = SavedStateHandle(mapOf("email" to "ana.nueva@correo.com", "sentAtMillis" to 0L)),
        )
        advanceTimeBy(61.seconds)

        vm.onResend()
        advanceTimeBy(2.seconds)

        assertEquals(1, accounts.resends)
        assertEquals(LinkSentMessage.RESENT, vm.state.value.message)
        assertFalse(vm.state.value.devLinks)
    }

    // Abrir el enlace

    @Test
    fun `al abrir el enlace el correo queda cambiado`() = runTest(dispatcher) {
        accounts.current.value = Account("ana.rios@correo.com", pendingEmail = "ana.nueva@correo.com")
        val vm = confirm()
        assertEquals(ConfirmEmailContent.LOADING, vm.state.value.content)

        advanceUntilIdle()

        assertEquals(listOf("enlace"), accounts.confirmed)
        assertEquals(ConfirmEmailDone.Confirmed("ana.nueva@correo.com"), vm.state.value.done)
        assertEquals(Account("ana.nueva@correo.com"), accounts.account.value)
    }

    @Test
    fun `un enlace vencido lleva a pedir otro con el correo nuevo`() = runTest(dispatcher) {
        accounts.confirmError = ExpiredLinkException("ana.nueva@correo.com")
        val vm = confirm()

        advanceUntilIdle()

        assertEquals(ConfirmEmailDone.Expired("ana.nueva@correo.com"), vm.state.value.done)
    }

    @Test
    fun `sin red o con otro fallo se puede reintentar`() = runTest(dispatcher) {
        accounts.confirmError = OfflineException()
        val vm = confirm()
        advanceUntilIdle()
        assertEquals(ConfirmEmailContent.OFFLINE, vm.state.value.content)

        accounts.confirmError = IOException("500")
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(ConfirmEmailContent.ERROR, vm.state.value.content)

        accounts.confirmError = null
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(ConfirmEmailDone.Confirmed("ana.nueva@correo.com"), vm.state.value.done)
    }
}
