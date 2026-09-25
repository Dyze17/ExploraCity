package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.ExploraSizes
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

enum class ExploraButtonStyle {
    /** Acción principal: relleno primary. */
    PRIMARY,

    /** Acción secundaria: contorno primary con texto de acento. */
    SECONDARY,

    /** Acción de texto o enlace: tertiary. */
    TEXT,

    /** Acción destructiva: relleno error. Nunca es la acción predeterminada de un diálogo. */
    DESTRUCTIVE,

    /** Acción destructiva secundaria, como texto en color error («Eliminar publicación», 24.a). */
    DESTRUCTIVE_TEXT,
}

/**
 * Botón del sistema de diseño: alto mínimo 48 dp, radio 12.
 *
 * Deshabilitado conserva 4,5:1 y, si hay [disabledReason], lo anuncia al recibir foco
 * («no disponible, completa el título»). Con [loading] muestra el progreso sin parecer deshabilitado
 * e ignora toques repetidos; el texto que pase la pantalla (p. ej. «Entrando…») se anuncia solo.
 */
@Composable
fun ExploraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ExploraButtonStyle = ExploraButtonStyle.PRIMARY,
    enabled: Boolean = true,
    disabledReason: String? = null,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val (container, content) = when (style) {
        ExploraButtonStyle.PRIMARY -> scheme.primary to scheme.onPrimary
        ExploraButtonStyle.SECONDARY -> Color.Transparent to explora.onSurfaceAccent
        ExploraButtonStyle.TEXT -> Color.Transparent to scheme.tertiary
        ExploraButtonStyle.DESTRUCTIVE -> scheme.error to scheme.onError
        ExploraButtonStyle.DESTRUCTIVE_TEXT -> Color.Transparent to scheme.error
    }
    val filled = container != Color.Transparent
    val border = if (style == ExploraButtonStyle.SECONDARY) {
        BorderStroke(1.dp, if (enabled) scheme.primary else scheme.outlineVariant)
    } else {
        null
    }
    val unavailable = if (!enabled && disabledReason != null) stringResource(R.string.button_unavailable, disabledReason) else null

    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .heightIn(min = ExploraSizes.MinTouchTarget)
            .semantics {
                // Solo durante la carga: con validación en vivo, anunciar cada cambio de estado sería ruido.
                if (loading) liveRegion = LiveRegionMode.Polite
                if (unavailable != null) stateDescription = unavailable
            },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (filled) scheme.surfaceContainerHighest else Color.Transparent,
            disabledContentColor = explora.textPlaceholder,
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp.scaledWithFont()),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            icon != null -> Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
        if (loading || icon != null) Spacer(Modifier.width(8.dp))
        // Con fuente grande el texto puede partirse en dos líneas: centrado, como la línea única.
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
