package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

enum class EmptyStateTone {
    /** Vacíos y sin resultados: contenedor neutro con icono de acento. */
    NEUTRAL,

    /** Errores recuperables: aviso ámbar. */
    WARNING,
}

/**
 * Estado vacío o de error: todo vacío explica qué pasa y propone la acción siguiente, sin códigos técnicos.
 * [actions] va a lo ancho, debajo del texto (botón principal y, si aplica, uno de texto).
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: EmptyStateTone = EmptyStateTone.NEUTRAL,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val explora = MaterialTheme.exploraColors
    val (container, content) = when (tone) {
        EmptyStateTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to explora.onSurfaceAccent
        EmptyStateTone.WARNING -> explora.warning.container to explora.warning.content
    }
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(96.dp).background(container, RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(44.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600, lineHeight = 30.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = explora.iconSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = actions,
        )
    }
}
