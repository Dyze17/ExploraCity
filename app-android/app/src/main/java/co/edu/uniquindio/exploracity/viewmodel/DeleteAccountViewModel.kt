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
import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeleteAccountError { OFFLINE, FAILED }

/** 30 · El diálogo que exige escribir ELIMINAR. */
data class DeleteConfirmation(val typed: String = "", val deleting: Boolean = false, val error: DeleteAccountError? = null) {
    /** Sin distinguir mayúsculas ni espacios alrededor: el teclado o el dictado pueden cambiarlos. */
    val matches: Boolean get() = typed.trim().equals(DeleteAccountViewModel.CONFIRMATION_WORD, ignoreCase = true)
}

data class DeleteAccountUiState(
    /** Para nombrar lo que se pierde («tus 340 puntos… y tus 2 insignias»); null si no se pudo cargar. */
    val profile: OwnProfile? = null,
    val offline: Boolean = false,
    val confirmation: DeleteConfirmation? = null,
    /** La cuenta ya no existe: la pantalla lleva al inicio de sesión (3). */
    val deleted: Boolean = false,
)

/**
 * 30 · Doble confirmación: la pantalla explica qué se borra y qué queda anónimo, y el diálogo exige escribir ELIMINAR.
 * Sin estados a medias: si el servidor falla, la cuenta y todo lo del teléfono siguen como estaban.
 */
class DeleteAccountViewModel(
    private val users: UserRepository,
    private val accounts: AccountRepository,
    private val session: SessionManager,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DeleteAccountUiState(
            offline = !connectivity.isOnline.value,
            // Tras la rotación o si Android cerró la app, el diálogo sigue abierto con lo que se había escrito.
            confirmation = savedStateHandle.get<String>(TYPED_KEY)?.let { DeleteConfirmation(it) },
        ),
    )
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = runCatchingNonCancellation { users.ownProfile() }
            _state.update { it.copy(profile = profile) }
        }
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
    }

    /** «Continuar con la eliminación» abre el diálogo; sin red el botón está deshabilitado y dice por qué. */
    fun onContinue() {
        if (_state.value.offline) return
        savedStateHandle[TYPED_KEY] = ""
        _state.update { it.copy(confirmation = DeleteConfirmation()) }
    }

    fun onTypedChange(typed: String) {
        if (_state.value.confirmation?.deleting != false) return
        savedStateHandle[TYPED_KEY] = typed
        _state.update { state -> state.copy(confirmation = state.confirmation?.copy(typed = typed, error = null)) }
    }

    fun onDismissConfirmation() {
        if (_state.value.confirmation?.deleting == true) return
        savedStateHandle.remove<String>(TYPED_KEY)
        _state.update { it.copy(confirmation = null) }
    }

    fun onConfirmDelete() {
        val confirmation = _state.value.confirmation ?: return
        if (!confirmation.matches || confirmation.deleting) return
        if (!connectivity.isOnline.value) return setConfirmation(confirmation.copy(error = DeleteAccountError.OFFLINE))
        setConfirmation(confirmation.copy(deleting = true, error = null))
        viewModelScope.launch {
            val result = catchingNonCancellation { accounts.deleteAccount() }
            if (result.isSuccess) {
                // La cuenta ya no existe en el servidor: lo del teléfono se borra aunque algo falle aquí.
                runCatchingNonCancellation { session.deleteAccountData() }
                savedStateHandle.remove<String>(TYPED_KEY)
                _state.update { it.copy(confirmation = null, deleted = true) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) DeleteAccountError.OFFLINE else DeleteAccountError.FAILED
                setConfirmation(confirmation.copy(deleting = false, error = error))
            }
        }
    }

    private fun setConfirmation(confirmation: DeleteConfirmation) = _state.update { it.copy(confirmation = confirmation) }

    companion object {
        /** La palabra que pide el diálogo (README 30). */
        const val CONFIRMATION_WORD = "ELIMINAR"

        private const val TYPED_KEY = "confirmacion_escrita"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                DeleteAccountViewModel(
                    users = container.userRepository,
                    accounts = container.accountRepository,
                    session = container.sessionManager,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
