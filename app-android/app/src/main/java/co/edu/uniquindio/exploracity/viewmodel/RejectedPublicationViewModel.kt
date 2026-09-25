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
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
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

/** 24 · Los estados de carga no los dibuja el diseño; siguen a los del perfil público (31). */
sealed interface RejectedContent {
    data object Loading : RejectedContent

    data class Loaded(val publication: OwnPublication) : RejectedContent

    /** Ya se eliminó (desde otro dispositivo, por ejemplo). */
    data object NotFound : RejectedContent

    data object Error : RejectedContent

    /** Sin internet: la publicación no se guarda para ver sin conexión. */
    data object Offline : RejectedContent
}

enum class DeleteError {
    FAILED,

    /** Sin red no se encola: borrar es destructivo y deliberado. */
    OFFLINE,
}

/** Diálogo «¿Eliminar…?» abierto. Si falla, sigue abierto con el aviso. */
data class DeleteDialogState(val deleting: Boolean = false, val error: DeleteError? = null)

data class RejectedPublicationUiState(
    val content: RejectedContent = RejectedContent.Loading,
    val delete: DeleteDialogState? = null,
    /** Se eliminó: la pantalla vuelve a Mis publicaciones (22) con «Publicación eliminada». Se consume una vez. */
    val deleted: Boolean = false,
) {
    val publication: OwnPublication? get() = (content as? RejectedContent.Loaded)?.publication
}

class RejectedPublicationViewModel(
    private val publications: PublicationRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumento de la ruta RejectedPublication(publicationId). */
    val publicationId: String = checkNotNull(savedStateHandle[PUBLICATION_ID_KEY]) { "Falta el id de la publicación" }

    private val _state = MutableStateFlow(
        RejectedPublicationUiState(delete = if (savedStateHandle.get<Boolean>(DELETE_KEY) == true) DeleteDialogState() else null),
    )
    val state: StateFlow<RejectedPublicationUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se carga sola si no se pudo traer.
            connectivity.isOnline.drop(1).collect { online ->
                val content = _state.value.content
                if (online && (content is RejectedContent.Offline || content is RejectedContent.Error)) load()
            }
        }
    }

    fun onRetry() = load()

    /** «Eliminar publicación»: abre la confirmación (23). */
    fun onOpenDelete() {
        if (_state.value.publication == null) return
        updateDelete(DeleteDialogState())
    }

    /** «Cancelar», atrás o tocar fuera; mientras borra no se puede cerrar. */
    fun onDismissDelete() {
        if (_state.value.delete?.deleting == true) return
        updateDelete(null)
    }

    /** «Sí, eliminar». */
    fun onConfirmDelete() {
        val dialog = _state.value.delete ?: return
        if (dialog.deleting) return
        updateDelete(dialog.copy(deleting = true, error = null))
        viewModelScope.launch {
            val result = catchingNonCancellation { publications.delete(publicationId) }
            if (result.isSuccess) {
                updateDelete(null)
                _state.update { it.copy(deleted = true) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) DeleteError.OFFLINE else DeleteError.FAILED
                updateDelete(DeleteDialogState(error = error))
            }
        }
    }

    fun onDeletedHandled() = _state.update { it.copy(deleted = false) }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = RejectedContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                // Envuelto para distinguir el tiempo agotado (null) de una publicación que ya no existe (Found(null)).
                val found = withTimeoutOrNull(timeout) { Found(publications.publication(publicationId)) }
                when {
                    found == null -> RejectedContent.Error
                    found.publication == null -> RejectedContent.NotFound
                    else -> RejectedContent.Loaded(found.publication)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                RejectedContent.Offline
            } catch (e: Exception) {
                RejectedContent.Error
            }
            _state.update { it.copy(content = content) }
        }
    }

    private class Found(val publication: OwnPublication?)

    /** El diálogo abierto sobrevive si Android cierra la app. */
    private fun updateDelete(delete: DeleteDialogState?) {
        _state.update { it.copy(delete = delete) }
        savedStateHandle[DELETE_KEY] = delete != null
    }

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        const val PUBLICATION_ID_KEY = "publicationId"
        private const val DELETE_KEY = "eliminar"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                RejectedPublicationViewModel(container.publicationRepository, container.connectivity, createSavedStateHandle())
            }
        }
    }
}
