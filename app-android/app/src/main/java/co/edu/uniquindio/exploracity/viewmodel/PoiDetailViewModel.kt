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
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 13 · Detalle: los estados de carga no los dibuja el diseño; siguen a 11 y 12.b. */
sealed interface DetailContent {
    data object Loading : DetailContent

    data class Loaded(val details: PoiDetails) : DetailContent

    /** El lugar se eliminó o dejó de ser público. */
    data object NotFound : DetailContent

    data object Error : DetailContent

    /** Sin internet y el lugar no está entre los guardados para ver sin conexión. */
    data object Offline : DetailContent
}

/** 14.b abierta: [draft] es lo que la persona lleva escrito; [sending] mientras se guarda. */
data class VisitSheetState(val draft: VisitExperience = VisitExperience(), val sending: Boolean = false)

/** Avisos de una sola vez (snackbar). */
sealed interface DetailMessage {
    data object VoteFailed : DetailMessage

    /** Sin red y sin el lugar guardado: no hay dónde dejar el voto para enviarlo después. */
    data object VoteOffline : DetailMessage

    /** Sin red: el voto quedó en la cola de envío. */
    data object VoteQueued : DetailMessage

    /** Opción A de Daniel: los puntos los decide el servidor; con 0 el aviso no los menciona. */
    data class VisitSaved(val points: Int) : DetailMessage

    data object VisitFailed : DetailMessage

    data object VisitOffline : DetailMessage

    /** Sin red: la visita quedó en la cola de envío (14.b: «se guarda y se envía luego»). */
    data object VisitQueued : DetailMessage
}

data class PoiDetailUiState(
    val content: DetailContent = DetailContent.Loading,
    /** Voto enviándose: se ignoran toques repetidos hasta que responda el servidor. */
    val voting: Boolean = false,
    val visitSheet: VisitSheetState? = null,
    val message: DetailMessage? = null,
    /** Sin internet: el detalle, si se ve, es el guardado (12.a). */
    val offline: Boolean = false,
) {
    val details: PoiDetails? get() = (content as? DetailContent.Loaded)?.details
}

/** Borrador de 14.b que sobrevive si Android cierra la app (el texto no se pierde). */
@Serializable
private data class SavedVisitDraft(val recommends: Boolean?, val text: String, val showName: Boolean)

class PoiDetailViewModel(
    private val poiRepository: PoiRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumento de la ruta PoiDetail(poiId). */
    val poiId: String = checkNotNull(savedStateHandle[POI_ID_KEY]) { "Falta el id del lugar" }

    private val _state = MutableStateFlow(
        PoiDetailUiState(visitSheet = restoredDraft()?.let { VisitSheetState(it) }, offline = !connectivity.isOnline.value),
    )
    val state: StateFlow<PoiDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red: lo que no se pudo abrir se carga, y lo guardado se pone al día sin la silueta.
            connectivity.isOnline.drop(1).collect { online ->
                _state.update { it.copy(offline = !online) }
                if (!online) return@collect
                when {
                    _state.value.content is DetailContent.Offline -> load()
                    _state.value.details?.savedAt != null -> onResumed()
                }
            }
        }
    }

    fun onRetry() = load()

    /**
     * Al volver al detalle (de 14, del mapa…) se recarga sin la silueta, para que el número de comentarios quede al
     * día. No pisa un voto ni una visita en curso; si falla, se queda lo que había.
     */
    fun onResumed() {
        if (_state.value.content !is DetailContent.Loaded || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val fresh = runCatchingNonCancellation { poiRepository.poiDetails(poiId) } ?: return@launch
            _state.update { current ->
                val shown = current.details
                if (shown == null || current.voting || current.visitSheet?.sending == true) return@update current
                current.copy(content = DetailContent.Loaded(fresh.copy(visited = fresh.visited || shown.visited)))
            }
        }
    }

    /**
     * «Es importante»: cambia al instante (relleno + «Ya votaste») y se revierte si el servidor falla. Sin red queda en
     * la cola de envío y se avisa.
     */
    fun onToggleVote() {
        val state = _state.value
        val details = state.details ?: return
        if (state.voting) return
        val voted = !details.voted
        val optimistic = details.copy(voted = voted, poi = details.poi.copy(votes = details.poi.votes + if (voted) 1 else -1))
        _state.update { it.copy(content = DetailContent.Loaded(optimistic), voting = true) }
        viewModelScope.launch {
            val result = catchingNonCancellation { poiRepository.setVote(poiId, voted) }
            val vote = result.getOrNull()
            _state.update { current ->
                val latest = current.details ?: return@update current.copy(voting = false)
                if (vote == null) {
                    // Solo se deshace el voto: el resto del detalle pudo cambiar mientras tanto (p. ej. «Visitado»).
                    val reverted = latest.copy(voted = details.voted, poi = latest.poi.copy(votes = details.poi.votes))
                    val message = if (result.exceptionOrNull() is OfflineException) DetailMessage.VoteOffline else DetailMessage.VoteFailed
                    current.copy(content = DetailContent.Loaded(reverted), voting = false, message = message)
                } else {
                    current.copy(
                        content = DetailContent.Loaded(latest.copy(poi = latest.poi.copy(votes = vote.votes))),
                        voting = false,
                        message = if (vote.queued) DetailMessage.VoteQueued else current.message,
                    )
                }
            }
        }
    }

    /** «Visitado»: abre 14.b con el borrador que hubiera. */
    fun onOpenVisit() {
        val details = _state.value.details ?: return
        if (details.visited) return
        _state.update { it.copy(visitSheet = it.visitSheet ?: VisitSheetState()) }
    }

    fun onVisitDraftChange(draft: VisitExperience) {
        val sheet = _state.value.visitSheet ?: return
        val limited = draft.copy(text = draft.text.take(VisitExperience.MAX_LENGTH))
        _state.update { it.copy(visitSheet = sheet.copy(draft = limited)) }
        saveDraft(limited)
    }

    /** «Cancelar», deslizar o atrás: se descarta el borrador. */
    fun onDismissVisit() {
        _state.update { it.copy(visitSheet = null) }
        saveDraft(null)
    }

    /** «Marcar visitado». Si falla, la hoja sigue abierta con lo escrito. */
    fun onConfirmVisit() {
        val sheet = _state.value.visitSheet ?: return
        if (sheet.sending) return
        _state.update { it.copy(visitSheet = sheet.copy(sending = true)) }
        viewModelScope.launch {
            val experience = sheet.draft.copy(text = sheet.draft.text.trim())
            val attempt = catchingNonCancellation { poiRepository.markVisited(poiId, experience) }
            val result = attempt.getOrNull()
            if (result == null) {
                val message = if (attempt.exceptionOrNull() is OfflineException) DetailMessage.VisitOffline else DetailMessage.VisitFailed
                _state.update { it.copy(visitSheet = it.visitSheet?.copy(sending = false), message = message) }
                return@launch
            }
            saveDraft(null)
            _state.update { current ->
                val details = current.details
                current.copy(
                    content = details?.let { DetailContent.Loaded(it.copy(visited = true)) } ?: current.content,
                    visitSheet = null,
                    message = if (result.queued) DetailMessage.VisitQueued else DetailMessage.VisitSaved(result.pointsAwarded),
                )
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = DetailContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                // Envuelto para distinguir el tiempo agotado (null) de un lugar que ya no existe (Found(null)).
                val found = withTimeoutOrNull(timeout) { Found(poiRepository.poiDetails(poiId)) }
                when {
                    found == null -> DetailContent.Error
                    found.details == null -> DetailContent.NotFound
                    else -> DetailContent.Loaded(found.details)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                DetailContent.Offline
            } catch (e: Exception) {
                DetailContent.Error
            }
            _state.update { it.copy(content = content) }
        }
    }

    private class Found(val details: PoiDetails?)

    private fun restoredDraft(): VisitExperience? = savedStateHandle.get<String>(DRAFT_KEY)
        ?.let { runCatching { Json.decodeFromString<SavedVisitDraft>(it) }.getOrNull() }
        ?.let { VisitExperience(it.recommends, it.text, it.showName) }

    private fun saveDraft(draft: VisitExperience?) {
        savedStateHandle[DRAFT_KEY] = draft?.let { Json.encodeToString(SavedVisitDraft(it.recommends, it.text, it.showName)) }
    }

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        const val POI_ID_KEY = "poiId"
        private const val DRAFT_KEY = "visitDraft"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                PoiDetailViewModel(container.poiRepository, container.connectivity, createSavedStateHandle())
            }
        }
    }
}
