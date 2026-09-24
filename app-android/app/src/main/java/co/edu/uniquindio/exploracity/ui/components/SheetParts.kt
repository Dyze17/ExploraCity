package co.edu.uniquindio.exploracity.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

// Piezas comunes de las hojas modales (9 · Filtros, 14.b · Visitado).

/**
 * Asa decorativa de 32 × 4 dp. Sin semántica: la de M3 es un botón y se llevaría el foco inicial, que en las
 * hojas modales va al título.
 */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.exploraColors.sheetHandle, RoundedCornerShape(2.dp)),
    )
}

/**
 * Las hojas modales viven en su propia ventana: sin esto el sistema pinta un velo oscuro bajo la barra de gestos,
 * como hacía en la actividad (ver MainActivity). Se llama desde el contenido de la hoja.
 */
@Composable
fun NoNavigationBarScrim() {
    val view = LocalView.current
    SideEffect {
        val window = (view as? DialogWindowProvider ?: view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }
}

/**
 * Casilla de 24 dp y radio 6 del lienzo (la de M3 mide 18 dp). Acompaña a un texto, así que escala con la
 * fuente como los iconos de chips y badges. Solo dibuja: el estado lo anuncia la fila `toggleable`.
 */
@Composable
fun ExploraCheckbox(checked: Boolean) {
    val shape = RoundedCornerShape(6.dp.scaledWithFont())
    Box(
        Modifier
            .size(24.dp.scaledWithFont())
            .then(
                if (checked) {
                    Modifier.background(MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.exploraColors.textPlaceholder, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp.scaledWithFont()),
            )
        }
    }
}
