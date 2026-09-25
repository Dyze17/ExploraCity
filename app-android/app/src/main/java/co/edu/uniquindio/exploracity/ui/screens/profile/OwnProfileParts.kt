package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.OfflineBanner
import co.edu.uniquindio.exploracity.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileContent
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileUiState

// Piezas comunes del perfil propio (26) y de niveles e insignias (27): los dos leen el mismo perfil y tienen los mismos
// estados de carga, error y sin conexión, como Avisos (25).

/**
 * Banner con la antigüedad de lo guardado (sin red o sin respuesta del servidor), el cuerpo según [content] y, en
 * lugar de la silueta, [skeleton].
 */
@Composable
internal fun OwnProfileContentFrame(
    state: OwnProfileUiState,
    onRetry: () -> Unit,
    skeleton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    loaded: @Composable (OwnProfileContent.Loaded) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        state.profile?.savedAt?.let { savedAt ->
            val now by rememberNow()
            OfflineBanner(
                title = stringResource(if (state.offline) R.string.offline_title else R.string.own_profile_not_updated_title),
                body = stringResource(R.string.own_profile_saved_body, relativeTimeText(savedAt, now)),
                onRetry = onRetry,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                OwnProfileContent.Loading -> skeleton()
                is OwnProfileContent.Loaded -> loaded(content)
                OwnProfileContent.Error -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.own_profile_error_title),
                        body = stringResource(R.string.profile_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { RetryButton(onRetry) }
                }
                OwnProfileContent.Offline -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.own_profile_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { RetryButton(onRetry) }
                }
            }
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}
