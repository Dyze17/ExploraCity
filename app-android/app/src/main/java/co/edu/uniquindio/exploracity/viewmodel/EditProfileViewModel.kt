package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.ProfileForm
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 28 · Los estados de carga no los dibuja el diseño; siguen a los de 23. */
enum class EditProfileContent { LOADING, LOADED, OFFLINE, ERROR }

enum class ProfileSaveError { OFFLINE, FAILED }

enum class EditProfileDone { SAVED, LEFT }

data class EditProfileUiState(
    val content: EditProfileContent = EditProfileContent.LOADING,
    /** Para el color y las iniciales del avatar. */
    val author: Author? = null,
    val original: ProfileForm? = null,
    val form: ProfileForm? = null,
    val email: String = "",
    /** El nombre ya perdió el foco una vez: desde ahí se avisa si es muy corto. */
    val nameTouched: Boolean = false,
    val preparingPhoto: Boolean = false,
    val photoUnreadable: Boolean = false,
    val saving: Boolean = false,
    val saveError: ProfileSaveError? = null,
    val discardDialog: Boolean = false,
    val done: EditProfileDone? = null,
) {
    val changed: Boolean get() = form != null && original != null && form.trimmed() != original.trimmed()

    val canSave: Boolean get() = changed && form?.isValid == true && !saving && !preparingPhoto

    /** Pasarse se avisa al momento (28.a); que falte, al dejar el campo. */
    val showNameError: Boolean get() = form != null && (form.nameExcess > 0 || (nameTouched && form.nameMissing > 0))

    val showBioError: Boolean get() = (form?.bioExcess ?: 0) > 0
}

class EditProfileViewModel(
    private val users: UserRepository,
    accounts: AccountRepository,
    private val photos: PhotoStore,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditProfileUiState(email = accounts.account().email, nameTouched = savedStateHandle[NAME_TOUCHED_KEY] ?: false),
    )
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    fun onNameChange(name: String) = updateForm { it.copy(name = name) }

    fun onNameBlur() {
        savedStateHandle[NAME_TOUCHED_KEY] = true
        _state.update { it.copy(nameTouched = true) }
    }

    fun onBioChange(bio: String) = updateForm { it.copy(bio = bio) }

    fun onResidencyChange(residency: Residency) = updateForm { it.copy(residency = residency) }

    /** La foto elegida en la galería se comprime en el teléfono; se sube al guardar. */
    fun onPhotoPicked(uri: String?) {
        if (uri == null || _state.value.form == null) return
        _state.update { it.copy(preparingPhoto = true) }
        viewModelScope.launch {
            val photo = runCatchingNonCancellation { photos.import(uri) }
            if (photo == null) {
                _state.update { it.copy(preparingPhoto = false, photoUnreadable = true) }
                return@launch
            }
            deleteLocal(newPhoto())
            updateForm { it.copy(photo = photo.path) }
            _state.update { it.copy(preparingPhoto = false) }
        }
    }

    fun onRemovePhoto() {
        deleteLocal(newPhoto())
        updateForm { it.copy(photo = null) }
    }

    fun onPhotoProblemShown() = _state.update { it.copy(photoUnreadable = false) }

    fun onSave() {
        val state = _state.value
        val form = state.form?.trimmed() ?: return
        val original = state.original ?: return
        if (!state.canSave) return
        if (!connectivity.isOnline.value) return _state.update { it.copy(saveError = ProfileSaveError.OFFLINE) }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val photo = when (form.photo) {
                original.photo -> PhotoChange.Keep
                null -> PhotoChange.Remove
                else -> PhotoChange.Replace(form.photo)
            }
            val update = ProfileUpdate(form.name, form.bio.ifEmpty { null }, form.residency, photo)
            val result = catchingNonCancellation { users.updateProfile(update) }
            if (result.isSuccess) {
                // El servidor ya tiene su copia: la del teléfono sobra.
                deleteLocal((photo as? PhotoChange.Replace)?.path)
                _state.update { it.copy(saving = false, done = EditProfileDone.SAVED) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) ProfileSaveError.OFFLINE else ProfileSaveError.FAILED
                _state.update { it.copy(saving = false, saveError = error) }
            }
        }
    }

    fun onSaveErrorShown() = _state.update { it.copy(saveError = null) }

    /** La X o el gesto de volver: con cambios pregunta antes de perderlos. */
    fun onBack() {
        val state = _state.value
        if (state.saving) return
        if (state.changed) _state.update { it.copy(discardDialog = true) } else _state.update { it.copy(done = EditProfileDone.LEFT) }
    }

    fun onDiscard() {
        deleteLocal(newPhoto())
        _state.update { it.copy(discardDialog = false, done = EditProfileDone.LEFT) }
    }

    fun onKeepEditing() = _state.update { it.copy(discardDialog = false) }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = EditProfileContent.LOADING) }
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { withTimeoutOrNull(timeout) { users.ownProfile() } }
            val profile = result.getOrNull()
            if (profile == null) {
                val content = if (result.exceptionOrNull() is OfflineException) EditProfileContent.OFFLINE else EditProfileContent.ERROR
                _state.update { it.copy(content = content) }
                return@launch
            }
            val original = ProfileForm.of(profile)
            _state.update {
                it.copy(content = EditProfileContent.LOADED, author = profile.author, original = original, form = restoredForm() ?: original)
            }
        }
    }

    private fun updateForm(transform: (ProfileForm) -> ProfileForm) {
        val form = _state.value.form ?: return
        val updated = transform(form)
        savedStateHandle[NAME_KEY] = updated.name
        savedStateHandle[BIO_KEY] = updated.bio
        savedStateHandle[RESIDENCY_KEY] = updated.residency.name
        savedStateHandle[PHOTO_KEY] = updated.photo
        savedStateHandle[PHOTO_SAVED_KEY] = true
        _state.update { it.copy(form = updated) }
    }

    /** Lo que se estaba editando si Android cerró la app; null si no había cambios. */
    private fun restoredForm(): ProfileForm? {
        val name = savedStateHandle.get<String>(NAME_KEY) ?: return null
        val residency = Residency.entries.firstOrNull { it.name == savedStateHandle.get<String>(RESIDENCY_KEY) } ?: return null
        val photo = if (savedStateHandle.get<Boolean>(PHOTO_SAVED_KEY) == true) savedStateHandle.get<String>(PHOTO_KEY) else null
        return ProfileForm(name, savedStateHandle[BIO_KEY] ?: "", residency, photo)
    }

    /** La foto nueva del teléfono, si la hay: la de antes vive en el servidor y no se toca. */
    private fun newPhoto(): String? = _state.value.form?.photo?.takeIf { it != _state.value.original?.photo }

    private fun deleteLocal(path: String?) {
        if (path == null) return
        viewModelScope.launch { runCatchingNonCancellation { photos.delete(DraftPhoto(id = path, path = path, name = "")) } }
    }

    companion object {
        /** Como el resto de pantallas: más de 8 s cargando es un error que se puede reintentar. */
        val LOAD_TIMEOUT = 8.seconds

        private const val NAME_KEY = "nombre"
        private const val BIO_KEY = "sobre_mi"
        private const val RESIDENCY_KEY = "como_se_presenta"
        private const val PHOTO_KEY = "foto"
        private const val PHOTO_SAVED_KEY = "foto_guardada"
        private const val NAME_TOUCHED_KEY = "nombre_tocado"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                EditProfileViewModel(
                    users = container.userRepository,
                    accounts = container.accountRepository,
                    photos = container.photoStore,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
