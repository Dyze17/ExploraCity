package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds

/**
 * El lugar de los avisos (snackbars) de cada pantalla. Con fuente grande (README · por encima de 1,3 las filas pasan a
 * columnas) la acción («Reintentar», «Permitir»…) va debajo del texto: al lado, el texto quedaba en una columna de una
 * o dos palabras por línea.
 */
@Composable
fun ExploraSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    SnackbarHost(hostState, modifier) { data -> Snackbar(data, actionOnNewLine = stacked) }
}
