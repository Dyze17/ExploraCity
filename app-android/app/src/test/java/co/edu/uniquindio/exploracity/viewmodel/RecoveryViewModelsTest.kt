package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.ResetLink
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
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 5, 6.a y 6.b · Pedir el enlace, esperar el reenvío y crear la contraseña nueva. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val connectivity = FakeConnectivity()
    private val auth = FakeAuth()

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val goodPassword = "a".repeat(7) + "1"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun recover(email: String = "") =
        RecoverPasswordViewModel(auth, connectivity, clock, SavedStateHandle(mapOf("email" to email)))

    private fun sent(
        sentAt: Long = 0L,
        mailbox: FakeMailbox? = FakeMailbox(),
        savedState: SavedStateHandle? = null,
        email: String = "ana@correo.com",
    ) = LinkSentViewModel(
        resend = auth::requestPasswordReset,
        connectivity = connectivity,
        clock = clock,
        savedStateHandle = savedState ?: SavedStateHandle(mapOf("email" to email, "sentAtMillis" to sentAt)),
        devLink = if (mailbox == null) null else { address, expired -> if (expired) mailbox.expiredResetLink(address) else mailbox.latestResetLink(address) },
    )

    private fun newPassword() = NewPasswordViewModel(auth, connectivity, clock, SavedStateHandle(mapOf("token" to "enlace")))

    private fun expiringIn(minutes: Int) = ResetLink("ana@correo.com", Instant.ofEpochMilli(minutes * 60_000L))

    // 5 · Recuperar contraseña

    @Test
    fun `abre con el correo que venía escrito`() = runTest(dispatcher) {
        assertEquals("ana@correo.com", recover("ana@correo.com").state.value.email)
    }

    @Test
    fun `valida el formato antes de enviar`() = runTest(dispatcher) {
        val vm = recover("ana@correo")

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.state.value.showEmailError)
        assertEquals(1, vm.state.value.focusRequest)
        assertTrue(auth.resetRequests.isEmpty())
    }

    @Test
    fun `al enviar sigue a 6a con el correo y la hora del envío`() = runTest(dispatcher) {
        val vm = recover(" ana@correo.com ")

        vm.onSubmit()
        runCurrent()
        assertTrue(vm.state.value.sending)
        advanceUntilIdle()

        assertEquals(listOf("ana@correo.com"), auth.resetRequests)
        assertEquals(RecoverySent("ana@correo.com", 1_000L), vm.state.value.sent)
        vm.onSentHandled()
        assertNull(vm.state.value.sent)
    }

    @Test
    fun `si el correo no sale se avisa sin perder lo escrito y se puede reintentar`() = runTest(dispatcher) {
        auth.resetRequestError = EmailDeliveryException()
        val vm = recover("ana@correo.com")

        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertEquals("ana@correo.com", vm.state.value.email)
        assertNull(vm.state.value.sent)

        auth.resetRequestError = null
        vm.onFailureShown()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(2, auth.resetRequests.size)
        assertEquals("ana@correo.com", vm.state.value.sent?.email)
    }

    @Test
    fun `sin red no se pide el enlace`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = recover("ana@correo.com")

        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.state.value.canSubmit)
        assertTrue(auth.resetRequests.isEmpty())
    }

    // 6.a · Revisa tu correo

    @Test
    fun `el reenvío espera 60 s contados desde el envío`() = runTest(dispatcher) {
        val vm = sent()
        runCurrent()
        assertEquals(60, vm.state.value.resendIn)

        advanceTimeBy(18.seconds)
        runCurrent()
        assertEquals(42, vm.state.value.resendIn)
        vm.onResend()
        advanceTimeBy(1.seconds)
        assertTrue(auth.resetRequests.isEmpty())

        advanceTimeBy(41.seconds)
        runCurrent()
        assertEquals(0, vm.state.value.resendIn)
        assertTrue(vm.state.value.canResend)
    }

    @Test
    fun `la cuenta sigue al recrear la pantalla`() = runTest(dispatcher) {
        advanceTimeBy(30.seconds)
        val vm = sent(sentAt = 0L)
        runCurrent()

        assertEquals(30, vm.state.value.resendIn)
    }

    @Test
    fun `reenviar pide otro enlace, lo dice y vuelve a esperar 60 s`() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("email" to "ana@correo.com", "sentAtMillis" to 0L))
        val vm = sent(savedState = savedState)
        advanceTimeBy(61.seconds)

        vm.onResend()
        runCurrent()
        assertTrue(vm.state.value.resending)
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(listOf("ana@correo.com"), auth.resetRequests)
        assertEquals(LinkSentMessage.RESENT, vm.state.value.message)
        assertEquals(60, vm.state.value.resendIn)
        assertEquals(clock.millis(), savedState.get<Long>("sentAtMillis"))
    }

    @Test
    fun `si el reenvío falla se avisa y se puede intentar otra vez`() = runTest(dispatcher) {
        auth.resetRequestError = IOException("500")
        val vm = sent()
        advanceTimeBy(61.seconds)

        vm.onResend()
        advanceUntilIdle()

        assertEquals(LinkSentMessage.SEND_FAILED, vm.state.value.message)
        assertTrue(vm.state.value.canResend)
    }

    @Test
    fun `sin red no se reenvía`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = sent()
        advanceTimeBy(61.seconds)

        vm.onResend()
        advanceUntilIdle()

        assertFalse(vm.state.value.canResend)
        assertTrue(auth.resetRequests.isEmpty())
    }

    @Test
    fun `en desarrollo el buzón de prueba abre el enlace vigente o uno vencido`() = runTest(dispatcher) {
        val vm = sent()
        assertTrue(vm.state.value.devLinks)

        vm.onDevLink(expired = false)
        runCurrent()
        assertEquals("enlace-vigente", vm.state.value.openLink)
        vm.onLinkOpened()
        vm.onDevLink(expired = true)
        runCurrent()
        assertEquals("enlace-vencido", vm.state.value.openLink)
    }

    @Test
    fun `a un correo sin cuenta no llega nada, y sin buzón de prueba no hay enlaces`() = runTest(dispatcher) {
        val other = sent(email = "nadie@correo.com")
        other.onDevLink(expired = false)
        runCurrent()
        assertTrue(other.state.value.devNoMail)
        assertNull(other.state.value.openLink)

        assertFalse(sent(mailbox = null).state.value.devLinks)
    }

    // 6.b · Nueva contraseña

    @Test
    fun `abre el enlace y dice para quién es y cuántos minutos le quedan`() = runTest(dispatcher) {
        auth.link = expiringIn(12)
        val vm = newPassword()
        assertEquals(NewPasswordContent.LOADING, vm.state.value.content)

        advanceTimeBy(1.seconds)

        assertEquals(NewPasswordContent.READY, vm.state.value.content)
        assertEquals("ana@correo.com", vm.state.value.email)
        assertEquals(12, vm.state.value.minutesLeft)
        advanceTimeBy(2.minutes)
        assertEquals(10, vm.state.value.minutesLeft)
    }

    @Test
    fun `un enlace vencido al abrirlo lleva a 6C con su correo`() = runTest(dispatcher) {
        auth.link = null
        val vm = newPassword()

        advanceUntilIdle()

        assertEquals(NewPasswordDone.Expired("ana@correo.com"), vm.state.value.done)
    }

    @Test
    fun `si vence con la pantalla abierta también lleva a 6C`() = runTest(dispatcher) {
        auth.link = expiringIn(5)
        val vm = newPassword()
        advanceTimeBy(1.seconds)
        assertNull(vm.state.value.done)

        advanceTimeBy(5.minutes)

        assertEquals(NewPasswordDone.Expired("ana@correo.com"), vm.state.value.done)
    }

    @Test
    fun `los requisitos se cumplen uno a uno y el aviso de coincidencia sale al dejar el campo`() = runTest(dispatcher) {
        auth.link = expiringIn(20)
        val vm = newPassword()
        advanceTimeBy(1.seconds)

        vm.onPasswordChange("a".repeat(8))
        assertTrue(vm.state.value.hasLength)
        assertFalse(vm.state.value.hasLetterAndDigit)
        vm.onPasswordChange(goodPassword)
        assertTrue(vm.state.value.passwordValid)

        vm.onConfirmChange("a".repeat(6))
        assertFalse(vm.state.value.showMismatch)
        vm.onConfirmBlur()
        assertTrue(vm.state.value.showMismatch)
        assertFalse(vm.state.value.canSubmit)

        vm.onConfirmChange(goodPassword)
        assertFalse(vm.state.value.showMismatch)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `guardar envía la contraseña nueva y vuelve al inicio de sesión`() = runTest(dispatcher) {
        auth.link = expiringIn(20)
        val vm = newPassword()
        advanceTimeBy(1.seconds)
        vm.onPasswordChange(goodPassword)
        vm.onConfirmChange(goodPassword)

        vm.onSubmit()
        vm.onSubmit()
        advanceTimeBy(2.seconds)

        assertEquals(listOf("enlace" to goodPassword), auth.resets)
        assertEquals(NewPasswordDone.Saved, vm.state.value.done)
    }

    @Test
    fun `si el enlace vence al guardar lleva a 6C`() = runTest(dispatcher) {
        auth.link = expiringIn(20)
        auth.resetError = ExpiredLinkException("ana@correo.com")
        val vm = newPassword()
        advanceTimeBy(1.seconds)
        vm.onPasswordChange(goodPassword)
        vm.onConfirmChange(goodPassword)

        vm.onSubmit()
        advanceTimeBy(2.seconds)

        assertEquals(NewPasswordDone.Expired("ana@correo.com"), vm.state.value.done)
    }

    @Test
    fun `otro fallo al guardar se avisa y deja lo escrito`() = runTest(dispatcher) {
        auth.link = expiringIn(20)
        auth.resetError = IOException("500")
        val vm = newPassword()
        advanceTimeBy(1.seconds)
        vm.onPasswordChange(goodPassword)
        vm.onConfirmChange(goodPassword)

        vm.onSubmit()
        advanceTimeBy(2.seconds)

        assertTrue(vm.state.value.saveFailed)
        assertNull(vm.state.value.done)
        assertEquals(goodPassword, vm.state.value.password)
    }

    @Test
    fun `un intento con errores lleva el foco al primer campo con error`() = runTest(dispatcher) {
        auth.link = expiringIn(20)
        val vm = newPassword()
        advanceTimeBy(1.seconds)
        vm.onPasswordChange(goodPassword)
        vm.onConfirmChange("a")

        vm.onSubmit()

        assertEquals(NewPasswordField.CONFIRM, vm.state.value.focusField)
        assertTrue(vm.state.value.showMismatch)
        assertTrue(auth.resets.isEmpty())
    }

    @Test
    fun `sin red al abrir el enlace se puede reintentar`() = runTest(dispatcher) {
        auth.link = expiringIn(20)
        auth.linkError = OfflineException()
        val vm = newPassword()
        advanceTimeBy(1.seconds)
        assertEquals(NewPasswordContent.OFFLINE, vm.state.value.content)

        auth.linkError = null
        vm.onRetry()
        advanceTimeBy(1.seconds)

        assertEquals(NewPasswordContent.READY, vm.state.value.content)
    }
}
