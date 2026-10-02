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
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConfirmEmailContent { LOADING, OFFLINE, ERROR }

/** Cómo termina: el correo quedó cambiado (vuelve a Ajustes, que lo dice) o el enlace venció (para pedir otro). */
sealed interface ConfirmEmailDone {
    data class Confirmed(val email: String) : ConfirmEmailDone

    data class Expired(val email: String) : ConfirmEmailDone
}

data class ConfirmEmailUiState(val content: ConfirmEmailContent = ConfirmEmailContent.LOADING, val done: ConfirmEmailDone? = null)

/** «Cambiar correo» · Abre el enlace que llegó al correo nuevo: «Confirmando tu correo…» y el resultado. */
class ConfirmEmailViewModel(
    private val accounts: AccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val token: String = savedStateHandle[TOKEN_KEY] ?: ""

    private val _state = MutableStateFlow(ConfirmEmailUiState())
    val state: StateFlow<ConfirmEmailUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        confirm()
    }

    private fun confirm() {
        job?.cancel()
        _state.update { it.copy(content = ConfirmEmailContent.LOADING) }
        job = viewModelScope.launch {
            val result = catchingNonCancellation { accounts.confirmEmailChange(token) }
            val email = result.getOrNull()
            if (email != null) {
                _state.update { it.copy(done = ConfirmEmailDone.Confirmed(email)) }
                return@launch
            }
            when (val error = result.exceptionOrNull()) {
                is ExpiredLinkException -> _state.update { it.copy(done = ConfirmEmailDone.Expired(error.email)) }
                is OfflineException -> _state.update { it.copy(content = ConfirmEmailContent.OFFLINE) }
                else -> _state.update { it.copy(content = ConfirmEmailContent.ERROR) }
            }
        }
    }

    fun onRetry() = confirm()

    companion object {
        /** El nombre del argumento de la ruta (el enlace del correo). */
        private const val TOKEN_KEY = "token"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ConfirmEmailViewModel(accounts = container.accountRepository, savedStateHandle = createSavedStateHandle())
            }
        }
    }
}
