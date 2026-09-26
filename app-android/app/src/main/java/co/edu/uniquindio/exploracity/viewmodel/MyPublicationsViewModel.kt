package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.navigation.MyPublications
import co.edu.uniquindio.exploracity.navigation.toStatus
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

/** 22 · Estados de la lista; los de carga siguen a los de Avisos (25) y del perfil. */
sealed interface MyPublicationsContent {
    data object Loading : MyPublicationsContent

    data class Loaded(val items: List<OwnPublication>) : MyPublicationsContent {
        val counts: PublicationCounts get() = PublicationCounts.of(items.map { it.status })
    }

    data object Error : MyPublicationsContent

    /** Sin internet: la lista no se guarda para ver sin conexión (opción A de Daniel). */
    data object Offline : MyPublicationsContent
}

/** «¿Eliminar…?» abierto para [publicationId]. */
data class DeleteTarget(val publicationId: String, val dialog: DeleteDialogState = DeleteDialogState())

data class MyPublicationsUiState(
    val content: MyPublicationsContent = MyPublicationsContent.Loading,
    /** null: «Todas». */
    val filter: PublicationStatus? = null,
    val delete: DeleteTarget? = null,
    /** «Publicación eliminada» (snackbar, una vez): se borró aquí o en la publicación rechazada (24). */
    val deletedShown: Boolean = false,
) {
    val loaded: MyPublicationsContent.Loaded? get() = content as? MyPublicationsContent.Loaded

    /** Lo que se ve con el filtro elegido, en el orden del servidor (de la más reciente a la más antigua). */
    val visible: List<OwnPublication> get() = loaded?.items.orEmpty().filter { filter == null || it.status == filter }

    val deleting: OwnPublication? get() = delete?.let { target -> loaded?.items?.firstOrNull { it.id == target.publicationId } }
}

class MyPublicationsViewModel(
    private val publications: PublicationRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    initialFilter: PublicationStatus?,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MyPublicationsUiState(
            filter = savedStateHandle.get<String>(FILTER_KEY)?.let { saved -> PublicationStatus.entries.firstOrNull { it.name == saved } }
                ?: initialFilter.takeUnless { savedStateHandle.contains(FILTER_KEY) },
            delete = savedStateHandle.get<String>(DELETE_KEY)?.let { DeleteTarget(it) },
        ),
    )
    val state: StateFlow<MyPublicationsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se carga sola si no se pudo traer.
            connectivity.isOnline.drop(1).collect { online ->
                val content = _state.value.content
                if (online && (content is MyPublicationsContent.Offline || content is MyPublicationsContent.Error)) load()
            }
        }
    }

    fun onRetry() = load()

    /** Al volver de un detalle (13) o de la rechazada (24) se pone al día sin la silueta. */
    fun onResumed() = refresh()

    /** Se eliminó una publicación en la rechazada (24) y se volvió aquí: se dice y la lista se pone al día. */
    fun onDeletedElsewhere() {
        _state.update { it.copy(deletedShown = true) }
        refresh()
    }

    /** Un filtro de la fila; «Todas» es null. */
    fun onFilter(status: PublicationStatus?) {
        savedStateHandle[FILTER_KEY] = status?.name ?: ALL
        _state.update { it.copy(filter = status) }
    }

    /** «Eliminar» del menú de un ítem: abre la confirmación (23). */
    fun onOpenDelete(publication: OwnPublication) = updateDelete(DeleteTarget(publication.id))

    /** «Cancelar», atrás o tocar fuera; mientras borra no se puede cerrar. */
    fun onDismissDelete() {
        if (_state.value.delete?.dialog?.deleting == true) return
        updateDelete(null)
    }

    /** «Sí, eliminar»: se quita de la lista y se avisa aquí mismo. */
    fun onConfirmDelete() {
        val target = _state.value.delete ?: return
        if (target.dialog.deleting) return
        updateDelete(target.copy(dialog = DeleteDialogState(deleting = true)))
        viewModelScope.launch {
            val result = catchingNonCancellation { publications.delete(target.publicationId) }
            if (result.isSuccess) {
                updateDelete(null)
                _state.update { state ->
                    val loaded = state.loaded ?: return@update state.copy(deletedShown = true)
                    state.copy(content = loaded.copy(items = loaded.items.filterNot { it.id == target.publicationId }), deletedShown = true)
                }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) DeleteError.OFFLINE else DeleteError.FAILED
                updateDelete(target.copy(dialog = DeleteDialogState(error = error)))
            }
        }
    }

    fun onDeletedShown() = _state.update { it.copy(deletedShown = false) }

    private fun updateDelete(delete: DeleteTarget?) {
        _state.update { it.copy(delete = delete) }
        savedStateHandle[DELETE_KEY] = delete?.publicationId
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = MyPublicationsContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = fetch() ?: MyPublicationsContent.Error
            _state.update { it.copy(content = content) }
        }
    }

    /** Sin silueta: si falla, se queda lo que había (salvo que no hubiera nada que mostrar). */
    private fun refresh() {
        if (loadJob?.isActive == true) return
        if (_state.value.content !is MyPublicationsContent.Loaded) return load()
        loadJob = viewModelScope.launch {
            val content = fetch() as? MyPublicationsContent.Loaded ?: return@launch
            _state.update { it.copy(content = content) }
        }
    }

    /** null si falla o tarda más de 8 s. */
    private suspend fun fetch(): MyPublicationsContent? = try {
        withTimeoutOrNull(timeout) { publications.myPublications() }?.let { MyPublicationsContent.Loaded(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OfflineException) {
        MyPublicationsContent.Offline
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        private const val FILTER_KEY = "filtro_elegido"
        private const val DELETE_KEY = "eliminar"
        private const val ALL = "TODAS"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                val savedStateHandle = createSavedStateHandle()
                MyPublicationsViewModel(
                    container.publicationRepository,
                    container.connectivity,
                    savedStateHandle,
                    initialFilter = savedStateHandle.toRoute<MyPublications>().filter.toStatus(),
                )
            }
        }
    }
}
