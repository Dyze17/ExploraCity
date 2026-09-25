package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.ExploraSizes
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * Barra superior del esqueleto común: mínimo 56 dp, flecha de 48 × 48 y título en Outfit 20/600 (titleLarge).
 * Crece con la fuente en vez de recortar (el TopAppBar de M3 tiene alto fijo). [subtitle] va debajo del título
 * («Café Las Acacias · 12» en 14); [subtitleDescription] es lo que oye el lector si el texto visible no se lee
 * bien en voz alta. [divider] dibuja la línea inferior de los lienzos con lista debajo.
 */
@Composable
fun ExploraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleDescription: String? = subtitle,
    onBack: (() -> Unit)? = null,
    divider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = ExploraSizes.TopAppBar).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.navigate_back), tint = scheme.onSurface)
                }
            }
            Column(Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 0.dp, top = 6.dp, bottom = 6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.exploraColors.iconSecondary,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = subtitleDescription.orEmpty() },
                    )
                }
            }
            CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) { actions() }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
    }
}
