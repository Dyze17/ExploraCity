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
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

/** 14.b abierta: [draft] es lo que la persona lleva escrito; [sending] mientras se guarda. */
data class VisitSheetState(val draft: VisitExperience = VisitExperience(), val sending: Boolean = false)

/** Avisos de una sola vez (snackbar). */
sealed interface DetailMessage {
    data object VoteFailed : DetailMessage

    /** Opción A de Daniel: los puntos los decide el servidor; con 0 el aviso no los menciona. */
    data class VisitSaved(val points: Int) : DetailMessage

    data object VisitFailed : DetailMessage
}

data class PoiDetailUiState(
    val content: DetailContent = DetailContent.Loading,
    /** Voto enviándose: se ignoran toques repetidos hasta que responda el servidor. */
    val voting: Boolean = false,
    val visitSheet: VisitSheetState? = null,
    val message: DetailMessage? = null,
) {
    val details: PoiDetails? get() = (content as? DetailContent.Loaded)?.details
}

/** Borrador de 14.b que sobrevive si Android cierra la app (el texto no se pierde). */
@Serializable
private data class SavedVisitDraft(val recommends: Boolean?, val text: String, val showName: Boolean)

class PoiDetailViewModel(
    private val poiRepository: PoiRepository,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumento de la ruta PoiDetail(poiId). */
    val poiId: String = checkNotNull(savedStateHandle[POI_ID_KEY]) { "Falta el id del lugar" }

    private val _state = MutableStateFlow(PoiDetailUiState(visitSheet = restoredDraft()?.let { VisitSheetState(it) }))
    val state: StateFlow<PoiDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    /** «Es importante»: cambia al instante (relleno + «Ya votaste») y se revierte si el servidor falla. */
    fun onToggleVote() {
        val state = _state.value
        val details = state.details ?: return
        if (state.voting) return
        val voted = !details.voted
        val optimistic = details.copy(voted = voted, poi = details.poi.copy(votes = details.poi.votes + if (voted) 1 else -1))
        _state.update { it.copy(content = DetailContent.Loaded(optimistic), voting = true) }
        viewModelScope.launch {
            val total = runCatchingNonCancellation { poiRepository.setVote(poiId, voted) }
            _state.update { current ->
                val latest = current.details ?: return@update current.copy(voting = false)
                if (total == null) {
                    // Solo se deshace el voto: el resto del detalle pudo cambiar mientras tanto (p. ej. «Visitado»).
                    val reverted = latest.copy(voted = details.voted, poi = latest.poi.copy(votes = details.poi.votes))
                    current.copy(content = DetailContent.Loaded(reverted), voting = false, message = DetailMessage.VoteFailed)
                } else {
                    current.copy(content = DetailContent.Loaded(latest.copy(poi = latest.poi.copy(votes = total))), voting = false)
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
            val result = runCatchingNonCancellation { poiRepository.markVisited(poiId, experience) }
            if (result == null) {
                _state.update { it.copy(visitSheet = it.visitSheet?.copy(sending = false), message = DetailMessage.VisitFailed) }
                return@launch
            }
            saveDraft(null)
            _state.update { current ->
                val details = current.details
                current.copy(
                    content = details?.let { DetailContent.Loaded(it.copy(visited = true)) } ?: current.content,
                    visitSheet = null,
                    message = DetailMessage.VisitSaved(result.pointsAwarded),
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
                PoiDetailViewModel(container.poiRepository, createSavedStateHandle())
            }
        }
    }
}
