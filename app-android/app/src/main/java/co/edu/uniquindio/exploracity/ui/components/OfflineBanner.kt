package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * Banner sin conexión del sistema de diseño (12.a, 8.c): ámbar, con icono, qué pasa y qué se está viendo, y
 * «Reintentar». Es liveRegion polite: se anuncia al aparecer o cambiar sin robar el foco.
 *
 * @param underStatusBar va pegado arriba de la pantalla: el ámbar se extiende detrás de la barra de estado.
 */
@Composable
fun OfflineBanner(
    title: String,
    body: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    underStatusBar: Boolean = false,
) {
    val warning = MaterialTheme.exploraColors.warning
    Row(
        modifier
            .fillMaxWidth()
            .background(warning.container)
            .then(if (underStatusBar) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), null, tint = warning.content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = warning.content)
                Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = warning.content)
            }
            RetryAction(onRetry)
        }
    }
}

/** «Reintentar» del banner: texto de acento con 48 dp de alto táctil, alineado con el texto de arriba. */
@Composable
private fun RetryAction(onRetry: () -> Unit) {
    val accent = MaterialTheme.exploraColors.warningAccent
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onRetry),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_refresh), null, tint = accent, modifier = Modifier.size(18.dp.scaledWithFont()))
        Text(
            stringResource(R.string.action_retry),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = accent,
        )
    }
}
