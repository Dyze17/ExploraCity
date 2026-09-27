package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.DocumentWriter
import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.DataExport
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.time.Duration.Companion.seconds

/** 29 y 29A · Tema, «Descargar mis datos» y cierre de sesión. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val preferences = MemoryPreferences()
    private val accounts = FakeAccounts()
    private val session = FakeSession()
    private val documents = MemoryDocuments()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        SettingsViewModel(preferences, accounts, session, documents, connectivity, savedState)

    @Test
    fun `muestra el tema guardado y el correo de la sesión`() = runTest(dispatcher) {
        preferences.theme.value = ThemeMode.DARK
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        assertEquals("ana.rios@correo.com", vm.state.value.email)
    }

    @Test
    fun `elegir un tema lo guarda`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onThemeChange(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, preferences.theme.value)
        assertEquals(ThemeMode.LIGHT, vm.state.value.themeMode)
    }

    @Test
    fun `descargar mis datos prepara el archivo, pide dónde guardarlo y lo escribe`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDownloadData()
        runCurrent()
        assertEquals(DataDownload.Preparing, vm.state.value.download)

        advanceUntilIdle()
        val ready = vm.state.value.download as DataDownload.Ready
        assertEquals("exploracity-mis-datos-2026-09-26.json", ready.export.fileName)
        vm.onSaveDialogShown()
        assertTrue(vm.state.value.download is DataDownload.ChoosingDestination)

        vm.onDestinationChosen("content://documentos/mis-datos.json")
        assertEquals(DataDownload.Saving, vm.state.value.download)
        advanceUntilIdle()

        assertEquals(mapOf("content://documentos/mis-datos.json" to "{\"correo\":\"ana.rios@correo.com\"}"), documents.written)
        assertEquals(SettingsNotice(SettingsNoticeKind.DOWNLOAD_SAVED, "exploracity-mis-datos-2026-09-26.json"), vm.state.value.notice)
        assertEquals(DataDownload.Idle, vm.state.value.download)
    }

    @Test
    fun `cerrar el diálogo de Android sin guardar no escribe nada ni avisa`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDownloadData()
        advanceUntilIdle()
        vm.onSaveDialogShown()

        vm.onDestinationChosen(null)

        assertEquals(DataDownload.Idle, vm.state.value.download)
        assertTrue(documents.written.isEmpty())
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `sin red no se pide el archivo`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel()

        vm.onDownloadData()
        advanceUntilIdle()

        assertEquals(SettingsNotice(SettingsNoticeKind.DOWNLOAD_OFFLINE), vm.state.value.notice)
        assertEquals(0, accounts.exports)
        assertEquals(DataDownload.Idle, vm.state.value.download)
    }

    @Test
    fun `si el servidor falla lo dice, y si se cayó la red también`() = runTest(dispatcher) {
        accounts.error = IOException("500")
        val vm = viewModel()
        vm.onDownloadData()
        advanceUntilIdle()
        assertEquals(SettingsNotice(SettingsNoticeKind.DOWNLOAD_FAILED), vm.state.value.notice)
        vm.onNoticeShown()

        accounts.error = OfflineException()
        vm.onDownloadData()
        advanceUntilIdle()

        assertEquals(SettingsNotice(SettingsNoticeKind.DOWNLOAD_OFFLINE), vm.state.value.notice)
        assertEquals(DataDownload.Idle, vm.state.value.download)
    }

    @Test
    fun `si no se pudo escribir el archivo lo dice`() = runTest(dispatcher) {
        documents.failing = true
        val vm = viewModel()
        vm.onDownloadData()
        advanceUntilIdle()
        vm.onSaveDialogShown()

        vm.onDestinationChosen("content://documentos/mis-datos.json")
        advanceUntilIdle()

        assertEquals(SettingsNotice(SettingsNoticeKind.SAVE_FAILED), vm.state.value.notice)
    }

    @Test
    fun `mientras se prepara, volver a tocar no pide otro archivo`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDownloadData()
        runCurrent()

        vm.onDownloadData()
        advanceUntilIdle()

        assertEquals(1, accounts.exports)
    }

    @Test
    fun `si Android cerró la app con el diálogo abierto, pide volver a descargar`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onDestinationChosen("content://documentos/mis-datos.json")

        assertEquals(SettingsNotice(SettingsNoticeKind.SAVE_FAILED), vm.state.value.notice)
        assertTrue(documents.written.isEmpty())
    }

    @Test
    fun `cerrar sesión pregunta antes y avisa de los envíos que se perderían (29A)`() = runTest(dispatcher) {
        session.pending = 2
        val vm = viewModel()

        vm.onLogoutClick()
        advanceUntilIdle()
        assertEquals(LogoutDialog(pendingSends = 2), vm.state.value.logout)

        vm.onDismissLogout()
        assertNull(vm.state.value.logout)
        assertFalse(session.signedOut)
    }

    @Test
    fun `confirmar cierra la sesión y lleva al inicio de sesión`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onLogoutClick()
        advanceUntilIdle()

        vm.onConfirmLogout()
        runCurrent()
        assertEquals(true, vm.state.value.logout?.signingOut)
        vm.onDismissLogout()
        assertTrue("Mientras cierra no se puede cancelar", vm.state.value.logout != null)
        advanceUntilIdle()

        assertTrue(session.signedOut)
        assertTrue(vm.state.value.signedOut)
        assertNull(vm.state.value.logout)
    }

    @Test
    fun `aunque falle borrar lo guardado, la sesión se cierra`() = runTest(dispatcher) {
        session.error = IOException("disco lleno")
        val vm = viewModel()
        vm.onLogoutClick()
        advanceUntilIdle()

        vm.onConfirmLogout()
        advanceUntilIdle()

        assertTrue(vm.state.value.signedOut)
    }

    @Test
    fun `el diálogo de cierre sigue abierto tras la rotación o si Android cierra la app`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState)
        vm.onLogoutClick()
        advanceUntilIdle()

        val recreated = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(LogoutDialog(), recreated.state.value.logout)
    }
}

private class MemoryPreferences : AppPreferences {
    val theme = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode = theme

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }
}

private class FakeAccounts : AccountRepository {
    var error: Exception? = null
    var exports = 0

    override fun account() = Account("ana.rios@correo.com")

    override suspend fun exportData(): DataExport {
        exports++
        delay(1.seconds)
        error?.let { throw it }
        return DataExport("exploracity-mis-datos-2026-09-26.json", "{\"correo\":\"ana.rios@correo.com\"}")
    }
}

private class FakeSession : SessionManager {
    var pending = 0
    var error: Exception? = null
    var signedOut = false

    override suspend fun pendingSends() = pending

    override suspend fun signOut() {
        delay(1.seconds)
        error?.let { throw it }
        signedOut = true
    }
}

private class MemoryDocuments : DocumentWriter {
    val written = mutableMapOf<String, String>()
    var failing = false

    override suspend fun write(uri: String, content: String) {
        if (failing) throw IOException("sin espacio")
        written[uri] = content
    }
}
