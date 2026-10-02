package co.edu.uniquindio.exploracity.ui.screens.access

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme

/**
 * 6C · Enlace vencido. Explica qué pasó sin códigos; «Pedir otro enlace» vuelve a 5 con el correo escrito y «Volver a
 * iniciar sesión» a 3. El icono es decorativo y no hay barra superior: el lector empieza por el titular.
 */
@Composable
fun ExpiredLinkScreen(onRequestNew: () -> Unit, onBackToLogin: () -> Unit, modifier: Modifier = Modifier) {
    val minutes = AuthRules.RESET_LINK_DURATION.inWholeMinutes.toInt()
    AccessMessage(
        icon = R.drawable.ic_link_off,
        iconContainer = MaterialTheme.colorScheme.errorContainer,
        iconContent = MaterialTheme.colorScheme.onErrorContainer,
        title = stringResource(R.string.expired_title),
        body = AnnotatedString(pluralStringResource(R.plurals.expired_body, minutes, minutes)),
        modifier = modifier,
    ) {
        ExploraButton(stringResource(R.string.expired_request_new), onClick = onRequestNew, modifier = Modifier.fillMaxWidth())
        ExploraButton(stringResource(R.string.back_to_login), onClick = onBackToLogin, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
    }
}

@Preview(name = "6C · claro", widthDp = 360, heightDp = 800)
@Composable
private fun ExpiredLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { ExpiredLinkScreen({}, {}) }
}

@Preview(name = "6C · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun ExpiredDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { ExpiredLinkScreen({}, {}) }
}
