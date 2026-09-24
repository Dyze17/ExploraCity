package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.ui.theme.ExploraSizes
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import kotlin.math.min

/**
 * Pestaña de la barra inferior.
 * @param badgeDescription lectura completa cuando hay badge, p. ej. «Avisos, 3 sin leer».
 */
@Immutable
data class NavigationBarItem(
    @DrawableRes val icon: Int,
    val label: String,
    val badgeCount: Int? = null,
    val badgeDescription: String? = null,
)

/**
 * Barra inferior del diseño: 80 dp como mínimo, sobre surfaceContainer con borde superior.
 * No tiene alto fijo: con fuente grande crece (104 dp al 200 %) y nunca oculta las etiquetas.
 * Con 5 pestañas (rol Moderador) los ítems se compactan a 56 dp.
 */
@Composable
fun ExploraNavigationBar(
    items: List<NavigationBarItem>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = items.size > 4
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.exploraColors.divider)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = ExploraSizes.BottomBar - 1.dp)
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavigationItem(item, selected = index == selectedIndex, compact = compact, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun RowScope.NavigationItem(item: NavigationBarItem, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val content = if (selected) MaterialTheme.exploraColors.onPrimaryContainerAccent else scheme.onSurfaceVariant
    val description = item.badgeDescription?.takeIf { (item.badgeCount ?: 0) > 0 }
    // Topes del lienzo al 200 %: etiqueta 16, icono 30 e ítem 80. Sin ellos la barra taparía el contenido
    // y con 5 pestañas las etiquetas no cabrían.
    val fontScale = LocalDensity.current.fontScale
    val iconSize = min((if (compact) 22f else 24f) * fontScale, if (compact) 28f else 30f).dp
    val labelMaxSp = min((if (compact) 10f else 11f) * fontScale, 16f) / fontScale
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = min(ExploraSizes.NavigationItem.value * fontScale, 80f).dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        // El indicador ocupa su espacio también sin seleccionar, para que iconos y etiquetas no salten.
        Box(
            Modifier
                .background(if (selected) scheme.primaryContainer else Color.Transparent, CircleShape)
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = 4.dp),
        ) {
            Icon(
                painterResource(item.icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(iconSize),
            )
            val count = item.badgeCount ?: 0
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
                    color = scheme.onError,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-6).dp)
                        .background(scheme.error, CircleShape)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        // Una sola línea y nunca cortada: si no cabe al tope, se reduce lo justo en vez de partir la palabra.
        BasicText(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = content,
                fontWeight = if (selected) FontWeight.W700 else FontWeight.W600,
                textAlign = TextAlign.Center,
                lineHeight = 1.4.em,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = (8f / fontScale).sp,
                maxFontSize = labelMaxSp.sp,
                stepSize = 0.25.sp,
            ),
        )
    }
}
