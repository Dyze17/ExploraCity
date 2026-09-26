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
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 23 · Estados de carga; siguen a los de la publicación rechazada (24). */
sealed interface EditContent {
    data object Loading : EditContent

    data class Loaded(val original: OwnPublication) : EditContent

    /** Ya no existe (se eliminó desde otro dispositivo, por ejemplo). */
    data object NotFound : EditContent

    /** Rechazada o finalizada: se corrige desde 24 o ya la cerró el moderador. */
    data object NotEditable : EditContent

    data object Error : EditContent

    /** Sin internet: la publicación no se guarda para ver sin conexión, como en 22 y 24. */
    data object Offline : EditContent
}

enum class SaveError {
    FAILED,

    /** Sin red no se encola: una edición en cola podría chocar con la revisión del moderador. */
    OFFLINE,
}

data class EditPublicationUiState(
    val content: EditContent = EditContent.Loading,
    /** Lo que se está editando; null hasta que carga. */
    val form: PublicationChanges? = null,
    /** El error de un campo se muestra al salir de él, no mientras se escribe (15.b). */
    val titleTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
    val saving: Boolean = false,
    /** Snackbar, una vez; los cambios siguen en los campos. */
    val saveError: SaveError? = null,
    /** «¿Descartar los cambios?» abierto. */
    val discardDialog: Boolean = false,
    val delete: DeleteDialogState? = null,
    /** Terminó: se guardó o se eliminó (la pantalla vuelve a 22 con el aviso) o se salió sin cambios (null). */
    val done: Done? = null,
) {
    val original: OwnPublication? get() = (content as? EditContent.Loaded)?.original

    /** Hay algo distinto de lo publicado (sin contar espacios de los extremos). */
    val changed: Boolean
        get() {
            val form = form ?: return false
            val original = original ?: return false
            return form.trimmed() != PublicationChanges.of(original).trimmed()
        }

    val canSave: Boolean get() = changed && form?.isValid == true && !saving

    val showTitleError: Boolean get() = titleTouched && (form?.titleMissing ?: 0) > 0

    val showDescriptionError: Boolean get() = descriptionTouched && (form?.descriptionMissing ?: 0) > 0
}

/** Cómo termina la edición. */
sealed interface Done {
    data class WithMessage(val message: PublicationMessage) : Done

    data object Left : Done
}

class EditPublicationViewModel(
    private val publications: PublicationRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumento de la ruta EditPublication(publicationId). */
    val publicationId: String = checkNotNull(savedStateHandle[PUBLICATION_ID_KEY]) { "Falta el id de la publicación" }

    private val _state = MutableStateFlow(
        EditPublicationUiState(
            titleTouched = savedStateHandle[TITLE_TOUCHED_KEY] ?: false,
            descriptionTouched = savedStateHandle[DESCRIPTION_TOUCHED_KEY] ?: false,
            discardDialog = savedStateHandle[DISCARD_KEY] ?: false,
            delete = if (savedStateHandle.get<Boolean>(DELETE_KEY) == true) DeleteDialogState() else null,
        ),
    )
    val state: StateFlow<EditPublicationUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se carga sola si no se pudo traer.
            connectivity.isOnline.drop(1).collect { online ->
                val content = _state.value.content
                if (online && (content is EditContent.Offline || content is EditContent.Error)) load()
            }
        }
    }

    fun onRetry() = load()

    fun onTitleChange(title: String) = updateForm { it.copy(title = title) }

    fun onDescriptionChange(description: String) = updateForm { it.copy(description = description) }

    fun onCategoryChange(category: Category) = updateForm { it.copy(category = category) }

    /** Salió del campo: desde ahora su error se ve (y se actualiza mientras escribe). */
    fun onTitleBlur() {
        savedStateHandle[TITLE_TOUCHED_KEY] = true
        _state.update { it.copy(titleTouched = true) }
    }

    fun onDescriptionBlur() {
        savedStateHandle[DESCRIPTION_TOUCHED_KEY] = true
        _state.update { it.copy(descriptionTouched = true) }
    }

    /** «Guardar»: solo con cambios válidos. La publicación vuelve a verificación. */
    fun onSave() {
        val state = _state.value
        val form = state.form ?: return
        if (!state.canSave) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val result = catchingNonCancellation { publications.update(publicationId, form.trimmed()) }
            if (result.isSuccess) {
                _state.update { it.copy(saving = false, done = Done.WithMessage(PublicationMessage.SAVED)) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) SaveError.OFFLINE else SaveError.FAILED
                _state.update { it.copy(saving = false, saveError = error) }
            }
        }
    }

    fun onSaveErrorShown() = _state.update { it.copy(saveError = null) }

    /** Volver (flecha o gesto): con cambios pregunta; sin ellos, sale. */
    fun onBack() {
        if (_state.value.saving) return
        if (_state.value.changed) setDiscardDialog(true) else _state.update { it.copy(done = Done.Left) }
    }

    fun onDiscard() {
        setDiscardDialog(false)
        _state.update { it.copy(done = Done.Left) }
    }

    fun onKeepEditing() = setDiscardDialog(false)

    fun onOpenDelete() {
        if (_state.value.original == null) return
        updateDelete(DeleteDialogState())
    }

    fun onDismissDelete() {
        if (_state.value.delete?.deleting == true) return
        updateDelete(null)
    }

    fun onConfirmDelete() {
        val dialog = _state.value.delete ?: return
        if (dialog.deleting) return
        updateDelete(dialog.copy(deleting = true, error = null))
        viewModelScope.launch {
            val result = catchingNonCancellation { publications.delete(publicationId) }
            if (result.isSuccess) {
                updateDelete(null)
                _state.update { it.copy(done = Done.WithMessage(PublicationMessage.DELETED)) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) DeleteError.OFFLINE else DeleteError.FAILED
                updateDelete(DeleteDialogState(error = error))
            }
        }
    }

    fun onDoneHandled() = _state.update { it.copy(done = null) }

    private fun updateForm(change: (PublicationChanges) -> PublicationChanges) {
        val form = _state.value.form ?: return
        val updated = change(form)
        saveForm(updated)
        _state.update { it.copy(form = updated) }
    }

    /** Lo escrito sobrevive si Android cierra la app. */
    private fun saveForm(form: PublicationChanges) {
        savedStateHandle[TITLE_KEY] = form.title
        savedStateHandle[CATEGORY_KEY] = form.category.name
        savedStateHandle[DESCRIPTION_KEY] = form.description
    }

    private fun restoredForm(original: OwnPublication): PublicationChanges {
        val title = savedStateHandle.get<String>(TITLE_KEY) ?: return PublicationChanges.of(original)
        val category = savedStateHandle.get<String>(CATEGORY_KEY)?.let { saved -> Category.entries.firstOrNull { it.name == saved } } ?: original.category
        return PublicationChanges(title, category, savedStateHandle.get<String>(DESCRIPTION_KEY) ?: original.description)
    }

    private fun setDiscardDialog(open: Boolean) {
        savedStateHandle[DISCARD_KEY] = open
        _state.update { it.copy(discardDialog = open) }
    }

    private fun updateDelete(delete: DeleteDialogState?) {
        savedStateHandle[DELETE_KEY] = delete != null
        _state.update { it.copy(delete = delete) }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = EditContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                // Envuelto para distinguir el tiempo agotado (null) de una publicación que ya no existe (Found(null)).
                val found = withTimeoutOrNull(timeout) { Found(publications.publication(publicationId)) }
                val publication = found?.publication
                when {
                    found == null -> EditContent.Error
                    publication == null -> EditContent.NotFound
                    publication.status !in editable -> EditContent.NotEditable
                    else -> EditContent.Loaded(publication)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                EditContent.Offline
            } catch (e: Exception) {
                EditContent.Error
            }
            _state.update { state ->
                val original = (content as? EditContent.Loaded)?.original
                // Lo que ya se estaba escribiendo no se pisa al reintentar ni al volver la red.
                val form = state.form ?: original?.let(::restoredForm)
                state.copy(content = content, form = form)
            }
        }
    }

    private class Found(val publication: OwnPublication?)

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        const val PUBLICATION_ID_KEY = "publicationId"
        private const val TITLE_KEY = "titulo"
        private const val CATEGORY_KEY = "categoria"
        private const val DESCRIPTION_KEY = "descripcion"
        private const val TITLE_TOUCHED_KEY = "titulo_tocado"
        private const val DESCRIPTION_TOUCHED_KEY = "descripcion_tocada"
        private const val DISCARD_KEY = "descartar"
        private const val DELETE_KEY = "eliminar"

        /** Rechazadas se corrigen desde 24 (formulario) y las finalizadas las cerró el moderador. */
        private val editable = setOf(PublicationStatus.PENDING, PublicationStatus.VERIFIED)

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                EditPublicationViewModel(container.publicationRepository, container.connectivity, createSavedStateHandle())
            }
        }
    }
}
