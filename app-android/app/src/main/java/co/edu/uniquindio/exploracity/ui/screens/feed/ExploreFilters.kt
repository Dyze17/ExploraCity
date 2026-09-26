package co.edu.uniquindio.exploracity.ui.screens.feed

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.util.LocationPurpose
import co.edu.uniquindio.exploracity.util.rememberLocationPermissionRequester
import co.edu.uniquindio.exploracity.viewmodel.FeedMessage
import co.edu.uniquindio.exploracity.viewmodel.FeedViewModel

/** Búsqueda, chips y hoja de filtros (9): las mismas acciones en la lista (7) y en el mapa (8). */
class FilterCallbacks(
    val onQueryChange: (String) -> Unit = {},
    val onToggleCategory: (Category) -> Unit = {},
    val onSearchWholeCity: () -> Unit = {},
    val onRemoveVerifiedOnly: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onDraftChange: (FeedFilters) -> Unit = {},
    val onClearDraft: () -> Unit = {},
    val onApplyFilters: () -> Unit = {},
    val onDismissFilters: () -> Unit = {},
    val onAllowLocation: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Conecta [FilterCallbacks] con el [FeedViewModel] compartido y con el permiso de ubicación de «Cercanos». */
@Composable
fun rememberFilterCallbacks(viewModel: FeedViewModel): FilterCallbacks {
    val location = rememberLocationPermissionRequester { purpose, granted ->
        when (purpose) {
            LocationPurpose.APPLY_FILTERS -> if (granted) viewModel.onApplyFilters() else viewModel.onLocationDenied()
            LocationPurpose.ENABLE_NEARBY -> if (granted) viewModel.onLocationGranted()
            LocationPurpose.CENTER_MAP, LocationPurpose.PLACE_PIN -> Unit
        }
    }
    return remember(viewModel, location) {
        FilterCallbacks(
            onQueryChange = viewModel::onQueryChange,
            onToggleCategory = viewModel::onToggleCategory,
            onSearchWholeCity = viewModel::onSearchWholeCity,
            onRemoveVerifiedOnly = viewModel::onRemoveVerifiedOnly,
            onClearFilters = viewModel::onClearFilters,
            onOpenFilters = viewModel::onOpenFilters,
            onDraftChange = viewModel::onDraftChange,
            onClearDraft = viewModel::onClearDraft,
            onApplyFilters = {
                // README 9: «Si no diste permiso, te lo pedimos al aplicar».
                if (viewModel.state.value.filterSheet?.draft?.scope == LocationScope.NEARBY) {
                    location.request(LocationPurpose.APPLY_FILTERS)
                } else {
                    viewModel.onApplyFilters()
                }
            },
            onDismissFilters = viewModel::onDismissFilters,
            onAllowLocation = { location.allow(LocationPurpose.ENABLE_NEARBY) },
            onMessageShown = viewModel::onMessageShown,
        )
    }
}

/**
 * Muestra en [hostState] los avisos del feed. Sin permiso de ubicación: persiste hasta que la persona actúa
 * («Permitir» o cerrar).
 */
@Composable
fun FeedMessageEffect(message: FeedMessage?, hostState: SnackbarHostState, callbacks: FilterCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val locationDenied = stringResource(R.string.feed_location_denied)
    val allow = stringResource(R.string.feed_location_allow)
    LaunchedEffect(message) {
        if (message == FeedMessage.LOCATION_DENIED) {
            val result = hostState.showSnackbar(locationDenied, actionLabel = allow, withDismissAction = true, duration = SnackbarDuration.Indefinite)
            currentCallbacks.onMessageShown()
            if (result == SnackbarResult.ActionPerformed) currentCallbacks.onAllowLocation()
        }
    }
}
