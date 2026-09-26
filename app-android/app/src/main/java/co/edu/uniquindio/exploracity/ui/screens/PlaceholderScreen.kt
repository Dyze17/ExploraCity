package co.edu.uniquindio.exploracity.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.theme.ExploraSpacing

/** Enlace de una pantalla provisional hacia otra ruta. */
data class PlaceholderLink(val label: String, val onClick: () -> Unit)

/**
 * Pantalla provisional del esqueleto de navegación: título, número del README y los enlaces que sale
 * de ella. Cada una se reemplaza por la pantalla real cuando se implementa.
 */
@Composable
fun PlaceholderScreen(
    number: String,
    title: String,
    links: List<PlaceholderLink>,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    fab: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        ExploraTopAppBar(title = title, onBack = onBack)
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(ExploraSpacing.ScreenMargin),
                verticalArrangement = Arrangement.spacedBy(ExploraSpacing.CardPadding),
            ) {
                Text(
                    "Pantalla $number · en construcción",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                links.forEach { link ->
                    ExploraButton(
                        text = link.label,
                        onClick = link.onClick,
                        modifier = Modifier.fillMaxWidth(),
                        style = ExploraButtonStyle.SECONDARY,
                    )
                }
            }
            if (fab != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(ExploraSpacing.ScreenMargin)) { fab() }
            }
        }
    }
}
