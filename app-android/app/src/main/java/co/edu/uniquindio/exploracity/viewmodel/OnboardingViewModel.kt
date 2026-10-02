package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import kotlinx.coroutines.launch

/** 2 · Salir del onboarding por cualquier botón lo marca como visto: el arranque (1) ya no lo repite. */
class OnboardingViewModel(private val preferences: AppPreferences) : ViewModel() {

    fun onFinished() {
        viewModelScope.launch { preferences.setOnboardingSeen() }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OnboardingViewModel((this[APPLICATION_KEY] as ExploraApplication).container.preferences)
            }
        }
    }
}
