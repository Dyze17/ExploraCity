package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakeUserRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.ProfileForm
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 28 · Validación, foto, guardado, descartar y restauración. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakeUserRepository(FakePoiRepository(), latency = Duration.ZERO, actionLatency = Duration.ZERO)
    private val photos = FakePhotos()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(users: UserRepository = server, savedState: SavedStateHandle = SavedStateHandle(), photoStore: PhotoStore = photos) =
        EditProfileViewModel(users, FakeAccounts(), photoStore, connectivity, savedState)

    private val EditProfileViewModel.form: ProfileForm
        get() = requireNotNull(state.value.form) { "El formulario debería estar cargado: ${state.value.content}" }

    @Test
    fun `carga el perfil y el correo de la sesión`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(EditProfileContent.LOADING, vm.state.value.content)
        advanceUntilIdle()

        assertEquals(EditProfileContent.LOADED, vm.state.value.content)
        assertEquals(ProfileForm("Ana Ríos", "", Residency.RESIDENT), vm.form)
        assertEquals("ana.rios@correo.com", vm.state.value.email)
        assertFalse(vm.state.value.changed)
        assertFalse("Sin cambios no se guarda", vm.state.value.canSave)
    }

    @Test
    fun `pasarse del límite se avisa al momento y que falte, al dejar el campo`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBioChange("a".repeat(162))
        assertTrue(vm.state.value.showBioError)
        assertEquals(12, vm.form.bioExcess)
        assertFalse(vm.state.value.canSave)

        vm.onBioChange("Busco cafés con patio.")
        vm.onNameChange("A")
        assertFalse("Aún escribiendo", vm.state.value.showNameError)
        vm.onNameBlur()
        assertTrue(vm.state.value.showNameError)
        assertEquals(1, vm.form.nameMissing)

        vm.onNameChange("A".repeat(41))
        assertEquals(1, vm.form.nameExcess)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `guarda nombre, sobre mí y cómo se presenta, sin espacios de sobra`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNameChange("  Ana María Ríos ")
        vm.onBioChange(" Busco cafés con patio y miradores. ")
        vm.onResidencyChange(Residency.VISITOR)
        assertTrue(vm.state.value.canSave)
        vm.onSave()
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()

        assertEquals(EditProfileDone.SAVED, vm.state.value.done)
        val profile = server.ownProfile()
        assertEquals("Ana María Ríos", profile.author.name)
        assertEquals("Busco cafés con patio y miradores.", profile.bio)
        assertEquals(Residency.VISITOR, profile.residency)
        assertEquals("También en el perfil público (31)", "Busco cafés con patio y miradores.", server.publicProfile("ana-rios")?.bio)
    }

    @Test
    fun `una foto nueva se sube al guardar y la copia del teléfono se borra`() = runTest(dispatcher) {
        // Comprimirla toma un momento: mientras tanto no se puede guardar.
        val slow = object : PhotoStore by photos {
            override suspend fun import(uri: String, fallbackName: String?): DraftPhoto? {
                delay(1.seconds)
                return photos.import(uri, fallbackName)
            }
        }
        val vm = viewModel(photoStore = slow)
        advanceUntilIdle()
        vm.onBioChange("Busco cafés con patio.")

        vm.onPhotoPicked("content://galeria/yo.jpg")
        runCurrent()
        assertTrue(vm.state.value.preparingPhoto)
        assertFalse("Espera a la foto", vm.state.value.canSave)
        advanceUntilIdle()
        assertEquals("/fotos/foto-1.jpg", vm.form.photo)

        vm.onSave()
        advanceUntilIdle()

        assertEquals("fake://perfil/foto-1.jpg", server.ownProfile().photo)
        assertEquals(listOf("/fotos/foto-1.jpg"), photos.deleted)
    }

    @Test
    fun `cambiar de nuevo la foto borra la que no se usó, y quitarla vuelve a las iniciales`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoPicked("content://galeria/primera.jpg")
        advanceUntilIdle()

        vm.onPhotoPicked("content://galeria/segunda.jpg")
        advanceUntilIdle()
        assertEquals("/fotos/foto-2.jpg", vm.form.photo)
        assertEquals(listOf("/fotos/foto-1.jpg"), photos.deleted)

        vm.onRemovePhoto()
        advanceUntilIdle()
        assertNull(vm.form.photo)
        assertEquals(listOf("/fotos/foto-1.jpg", "/fotos/foto-2.jpg"), photos.deleted)
        assertFalse("Sin foto, como antes: no hay cambios", vm.state.value.changed)
    }

    @Test
    fun `quitar la foto que ya tenía la borra del servidor al guardar`() = runTest(dispatcher) {
        server.updateProfile(ProfileUpdate("Ana Ríos", null, Residency.RESIDENT, PhotoChange.Replace("/fotos/antes.jpg")))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("fake://perfil/antes.jpg", vm.form.photo)

        vm.onRemovePhoto()
        vm.onSave()
        advanceUntilIdle()

        assertNull(server.ownProfile().photo)
        assertTrue("La del servidor no es un archivo del teléfono", photos.deleted.isEmpty())
    }

    @Test
    fun `una foto que no se puede leer lo dice y no cambia nada`() = runTest(dispatcher) {
        photos.unreadable = setOf("content://galeria/rota.heic")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoPicked("content://galeria/rota.heic")
        advanceUntilIdle()

        assertTrue(vm.state.value.photoUnreadable)
        assertNull(vm.form.photo)
        vm.onPhotoProblemShown()
        assertFalse(vm.state.value.photoUnreadable)
    }

    @Test
    fun `sin red no se intenta guardar y los cambios siguen`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBioChange("Busco cafés con patio.")
        connectivity.online = false

        vm.onSave()
        advanceUntilIdle()

        assertEquals(ProfileSaveError.OFFLINE, vm.state.value.saveError)
        assertEquals("Busco cafés con patio.", vm.form.bio)
        assertNull(server.ownProfile().bio)
    }

    @Test
    fun `si el servidor falla lo dice y los cambios siguen`() = runTest(dispatcher) {
        val failing = object : UserRepository by server {
            override suspend fun updateProfile(update: ProfileUpdate) = throw IOException("500")
        }
        val vm = viewModel(users = failing)
        advanceUntilIdle()
        vm.onBioChange("Busco cafés con patio.")

        vm.onSave()
        advanceUntilIdle()

        assertEquals(ProfileSaveError.FAILED, vm.state.value.saveError)
        assertFalse(vm.state.value.saving)
        assertNull(vm.state.value.done)
    }

    @Test
    fun `volver con cambios pregunta y descartar borra la foto nueva`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoPicked("content://galeria/yo.jpg")
        advanceUntilIdle()

        vm.onBack()
        assertTrue(vm.state.value.discardDialog)
        vm.onKeepEditing()
        assertFalse(vm.state.value.discardDialog)
        vm.onBack()
        vm.onDiscard()
        advanceUntilIdle()

        assertEquals(EditProfileDone.LEFT, vm.state.value.done)
        assertEquals(listOf("/fotos/foto-1.jpg"), photos.deleted)
    }

    @Test
    fun `volver sin cambios sale sin preguntar`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBack()

        assertEquals(EditProfileDone.LEFT, vm.state.value.done)
        assertFalse(vm.state.value.discardDialog)
    }

    @Test
    fun `lo que se estaba editando sobrevive si Android cierra la app`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()
        vm.onNameChange("Ana María")
        vm.onResidencyChange(Residency.VISITOR)
        vm.onPhotoPicked("content://galeria/yo.jpg")
        advanceUntilIdle()
        vm.onNameBlur()

        val recreated = viewModel(savedState = savedState)
        advanceUntilIdle()

        assertEquals(ProfileForm("Ana María", "", Residency.VISITOR, "/fotos/foto-1.jpg"), recreated.form)
        assertTrue(recreated.state.value.nameTouched)
        assertTrue(recreated.state.value.changed)
    }

    @Test
    fun `sin red y sin nada guardado lo dice`() = runTest(dispatcher) {
        val offline = object : UserRepository by server {
            override suspend fun ownProfile() = throw OfflineException()
        }
        val vm = viewModel(users = offline)
        advanceUntilIdle()

        assertEquals(EditProfileContent.OFFLINE, vm.state.value.content)
    }
}
