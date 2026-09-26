package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.local.DraftKey
import co.edu.uniquindio.exploracity.data.local.DraftRepository
import co.edu.uniquindio.exploracity.data.repository.CategorySuggester
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import co.edu.uniquindio.exploracity.navigation.PublishForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 15–19 · Qué muestra el formulario. */
sealed interface PublishContent {
    /** Leyendo el borrador (o la publicación a corregir). */
    data object Loading : PublishContent

    data object Editing : PublishContent

    /** No se pudo traer la publicación rechazada que se iba a corregir (24). */
    data object Error : PublishContent
}

/** 16 · La sugerencia automática de categoría. */
sealed interface Suggestion {
    /** No aplica: aún no se pidió o la categoría la eligió la persona. */
    data object Idle : Suggestion

    /** 16.a · «Buscando una categoría para ti…». */
    data object Searching : Suggestion

    /** 16.b · Llegó y quedó preseleccionada. */
    data class Ready(val category: Category) : Suggestion

    /** 16.b · Se eligió otra: «Cambiaste la categoría sugerida». */
    data class Replaced(val suggested: Category) : Suggestion

    /** 16.c · Sin respuesta a los 5 s (o sin red): se sigue a mano, con reintento opcional. */
    data object NoAnswer : Suggestion

    /** Respondió, pero ninguna categoría encaja con claridad. */
    data object NotFound : Suggestion
}

/** Un campo que impide continuar (21). */
enum class DraftField { TITLE, DESCRIPTION, CATEGORY }

/** Cómo sale del formulario. */
enum class PublishExit {
    /** Cerrar o «Guardar»: el borrador queda guardado (o no había nada que guardar). */
    CLOSED,

    /** Paso 5 provisional: sigue a la confirmación (20) mientras no exista el envío (parte 3). */
    SENT,
}

data class PublishUiState(
    val content: PublishContent = PublishContent.Loading,
    val draft: PublicationDraft = PublicationDraft(),
    /** Corrige una rechazada (24): no toca el borrador de la publicación nueva. */
    val resubmit: Boolean = false,
    /** El error de un campo se ve al salir de él (15.b) o al tocar «Continuar» con algo pendiente (21). */
    val titleTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
    /** 21 · Se tocó «Continuar» con errores en este paso: resumen arriba y error junto a cada campo. */
    val showErrors: Boolean = false,
    /** Cambia cada vez que hay que llevar el foco al primer campo con error (21). */
    val errorFocusRequest: Int = 0,
    val suggestion: Suggestion = Suggestion.Idle,
    /** 15A · «¿Guardar el borrador?» abierto. */
    val closeDialog: Boolean = false,
    val exit: PublishExit? = null,
) {
    val step: PublishStep get() = draft.step

    /** Lo que falta en el paso actual, en el orden de la pantalla. */
    val stepErrors: List<DraftField>
        get() = when (step) {
            PublishStep.BASICS -> listOfNotNull(
                DraftField.TITLE.takeIf { draft.titleMissing > 0 },
                DraftField.DESCRIPTION.takeIf { draft.descriptionMissing > 0 },
            )
            PublishStep.CATEGORY -> listOfNotNull(DraftField.CATEGORY.takeIf { draft.category == null })
            // Los pasos 3 a 5 llegan en las partes 2 y 3.
            PublishStep.LOCATION, PublishStep.SCHEDULE, PublishStep.PHOTOS -> emptyList()
        }

    val showTitleError: Boolean get() = (titleTouched || showErrors) && draft.titleMissing > 0

    val showDescriptionError: Boolean get() = (descriptionTouched || showErrors) && draft.descriptionMissing > 0

    val showCategoryError: Boolean get() = showErrors && step == PublishStep.CATEGORY && draft.category == null
}

class PublishViewModel(
    private val drafts: DraftRepository,
    private val suggester: CategorySuggester,
    private val publications: PublicationRepository,
    resubmitId: String?,
    private val initialStep: PublishStep,
    private val suggestionTimeout: Duration = SUGGESTION_TIMEOUT,
    private val saveDelay: Duration = SAVE_DELAY,
) : ViewModel() {

    private val key: DraftKey = resubmitId?.let { DraftKey.Resubmit(it) } ?: DraftKey.New
    private val resubmitId: String? = resubmitId

    private val _state = MutableStateFlow(PublishUiState(resubmit = resubmitId != null))
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    private var saveJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    fun onTitleChange(title: String) = updateDraft { it.copy(title = title) }

    fun onDescriptionChange(description: String) = updateDraft { it.copy(description = description) }

    fun onTitleBlur() = _state.update { it.copy(titleTouched = true) }

    fun onDescriptionBlur() = _state.update { it.copy(descriptionTouched = true) }

    /**
     * Elegir una categoría a mano cancela la sugerencia en curso; elegir otra distinta de la sugerida la reemplaza
     * («Cambiaste la categoría sugerida») y volver a la sugerida la recupera.
     */
    fun onCategoryChange(category: Category) {
        suggestionJob?.cancel()
        val suggestion = when (val current = _state.value.suggestion) {
            is Suggestion.Ready -> if (category == current.category) current else Suggestion.Replaced(current.category)
            is Suggestion.Replaced -> if (category == current.suggested) Suggestion.Ready(category) else current
            Suggestion.Searching -> Suggestion.Idle
            Suggestion.Idle, Suggestion.NoAnswer, Suggestion.NotFound -> current
        }
        val origin = if (suggestion is Suggestion.Ready) CategoryOrigin.SUGGESTED else CategoryOrigin.CHOSEN
        _state.update { it.copy(suggestion = suggestion) }
        updateDraft { it.copy(category = category, categoryOrigin = origin) }
    }

    /** 16.c · «Intentar la sugerencia otra vez»: la persona la pidió, así que si llega, se aplica. */
    fun onRetrySuggestion() = startSuggestion(overrideChoice = true)

    /** «Continuar»: con algo pendiente muestra el resumen (21) y lleva el foco al primer error; si no, avanza. */
    fun onContinue() {
        val state = _state.value
        if (state.content != PublishContent.Editing) return
        if (state.stepErrors.isNotEmpty()) {
            _state.update {
                it.copy(showErrors = true, titleTouched = true, descriptionTouched = true, errorFocusRequest = it.errorFocusRequest + 1)
            }
            return
        }
        val next = state.step.next
        if (next == null) {
            // Parte 3: aquí se enviará a verificación. Mientras tanto se sigue a la confirmación provisional.
            _state.update { it.copy(exit = PublishExit.SENT) }
            return
        }
        moveTo(next)
    }

    /** «Atrás»: al paso anterior; en el primero equivale a cerrar. */
    fun onBack() {
        val previous = _state.value.step.previous ?: return onClose()
        moveTo(previous)
    }

    /** Cerrar (X): con algo escrito pregunta (15A); si no, sale. */
    fun onClose() {
        if (_state.value.draft.hasContent && _state.value.content == PublishContent.Editing) {
            _state.update { it.copy(closeDialog = true) }
        } else {
            _state.update { it.copy(exit = PublishExit.CLOSED) }
        }
    }

    /** «Guardar» (barra o 15A): el borrador queda para seguir después desde Publicar. */
    fun onSaveAndClose() {
        viewModelScope.launch {
            saveJob?.cancel()
            val draft = _state.value.draft
            if (draft.hasContent) drafts.save(key, draft) else drafts.clear(key)
            _state.update { it.copy(closeDialog = false, exit = PublishExit.CLOSED) }
        }
    }

    /** 15A · «Descartar»: se borra lo escrito. */
    fun onDiscard() {
        viewModelScope.launch {
            saveJob?.cancel()
            suggestionJob?.cancel()
            drafts.clear(key)
            _state.update { it.copy(closeDialog = false, exit = PublishExit.CLOSED) }
        }
    }

    /** 15A · «Seguir editando». */
    fun onKeepEditing() = _state.update { it.copy(closeDialog = false) }

    fun onExitHandled() = _state.update { it.copy(exit = null) }

    private fun moveTo(step: PublishStep) {
        _state.update { it.copy(showErrors = false) }
        updateDraft { it.copy(step = step) }
        if (step == PublishStep.CATEGORY && _state.value.draft.category == null) startSuggestion(overrideChoice = false)
    }

    /** Cada cambio se guarda solo, un momento después de escribir («Guardamos tu borrador automáticamente»). */
    private fun updateDraft(change: (PublicationDraft) -> PublicationDraft) {
        if (_state.value.content != PublishContent.Editing) return
        val draft = change(_state.value.draft)
        _state.update { it.copy(draft = draft) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(saveDelay)
            drafts.save(key, draft)
        }
    }

    /**
     * 16 · Pide la sugerencia con tiempo límite de 5 s. La lista está activa desde el primer instante: si la persona
     * elige antes, la sugerencia se cancela ([onCategoryChange]); con [overrideChoice] (reintento) se aplica igual.
     */
    private fun startSuggestion(overrideChoice: Boolean) {
        suggestionJob?.cancel()
        _state.update { it.copy(suggestion = Suggestion.Searching) }
        val draft = _state.value.draft
        suggestionJob = viewModelScope.launch {
            val result = try {
                withTimeoutOrNull(suggestionTimeout) { Found(suggester.suggest(draft.title.trim(), draft.description.trim())) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val category = result?.category
            when {
                result == null -> _state.update { it.copy(suggestion = Suggestion.NoAnswer) }
                category == null -> _state.update { it.copy(suggestion = Suggestion.NotFound) }
                !overrideChoice && _state.value.draft.categoryOrigin == CategoryOrigin.CHOSEN ->
                    _state.update { it.copy(suggestion = Suggestion.Idle) }
                else -> {
                    _state.update { it.copy(suggestion = Suggestion.Ready(category)) }
                    updateDraft { it.copy(category = category, categoryOrigin = CategoryOrigin.SUGGESTED) }
                }
            }
        }
    }

    private class Found(val category: Category?)

    private fun load() {
        _state.update { it.copy(content = PublishContent.Loading) }
        viewModelScope.launch {
            val saved = drafts.load(key)
            val draft = when {
                saved != null -> saved
                resubmitId == null -> PublicationDraft()
                else -> {
                    val publication = try {
                        publications.publication(resubmitId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (publication == null) {
                        _state.update { it.copy(content = PublishContent.Error) }
                        return@launch
                    }
                    PublicationDraft.from(publication, initialStep)
                }
            }
            // Al retomar, una categoría sugerida conserva su aviso (16.b) aunque el estado de la sugerencia no se guarde.
            val category = draft.category
            val suggestion = if (category != null && draft.categoryOrigin == CategoryOrigin.SUGGESTED) Suggestion.Ready(category) else Suggestion.Idle
            _state.update { it.copy(content = PublishContent.Editing, draft = draft, suggestion = suggestion) }
            if (draft.step == PublishStep.CATEGORY && category == null) startSuggestion(overrideChoice = false)
        }
    }

    companion object {
        /** README · «tiempo límite 5 s»: después se sigue con la elección manual (16.c). */
        val SUGGESTION_TIMEOUT = 5.seconds

        /** Espera tras el último cambio antes de guardar el borrador: no escribe en disco con cada tecla. */
        val SAVE_DELAY = 400.milliseconds

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                val route = createSavedStateHandle().toRoute<PublishForm>()
                PublishViewModel(
                    drafts = container.draftRepository,
                    suggester = container.categorySuggester,
                    publications = container.publicationRepository,
                    resubmitId = route.resubmitId,
                    initialStep = PublishStep.of(route.step),
                )
            }
        }
    }
}
