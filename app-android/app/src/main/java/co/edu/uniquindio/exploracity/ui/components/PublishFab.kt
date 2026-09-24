package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow

/**
 * FAB «Publicar un lugar nuevo»: 56 dp, radio 28 (README), primary, sombra de nivel 3 en claro.
 * [expanded] muestra la etiqueta «Publicar».
 *
 * Con fontScale > 1,5 no se dibuja: a ese tamaño taparía el contenido, y «Publicar» sigue en la
 * barra inferior con su etiqueta.
 */
@Composable
fun PublishFab(onClick: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = false) {
    if (LocalDensity.current.fontScale > FontScaleThresholds.HideFab) return

    val description = stringResource(R.string.fab_publish_description)
    val shape = MaterialTheme.shapes.extraLarge
    val fabModifier = modifier
        .exploraShadow(ExploraElevation.Fab, shape, MaterialTheme.exploraColors.shadow)
        .semantics { contentDescription = description }
    // Sin elevación propia de M3: la sombra cálida la pone exploraShadow y en oscuro no hay sombra.
    val elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)

    if (expanded) {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.fab_publish), style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(painterResource(R.drawable.ic_add_location_alt), contentDescription = null, modifier = Modifier.size(24.dp)) },
            onClick = onClick,
            modifier = fabModifier,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = elevation,
        )
    } else {
        FloatingActionButton(
            onClick = onClick,
            modifier = fabModifier,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = elevation,
        ) {
            Icon(painterResource(R.drawable.ic_add_location_alt), contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
