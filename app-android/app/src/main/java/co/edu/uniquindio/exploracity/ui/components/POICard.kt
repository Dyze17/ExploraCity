package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import java.util.Locale

/**
 * Tarjeta de un punto de interés en el feed. Alto mínimo 96 dp y crece con el texto.
 * Con fontScale > 1,3 pasa a vertical: foto arriba y cada dato en su propia fila, con texto completo.
 * El lector de pantalla la lee como un solo elemento: «Café Las Acacias, Gastronomía, verificada, a 1,2 km,
 * 48 votos, 12 comentarios».
 *
 * @param distance distancia ya formateada, p. ej. «1,2 km».
 * @param saved 12.a: guardado para ver sin conexión. «Guardado» ocupa el lugar del estado junto al título, como en el
 *   lienzo, y el estado pasa a la fila de la categoría.
 * @param photo contenido de la foto (imagen remota); vacío muestra el contenedor neutro.
 */
@Composable
fun POICard(
    title: String,
    category: Category,
    status: PublicationStatus,
    distance: String,
    votes: Int,
    comments: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
    photo: @Composable BoxScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val votesText = pluralStringResource(R.plurals.poi_votes, votes, votes)
    val commentsText = pluralStringResource(R.plurals.poi_comments, comments, comments)
    val description = stringResource(
        R.string.poi_card_description,
        title,
        stringResource(category.labelRes),
        stringResource(status.labelRes).lowercase(Locale.forLanguageTag("es")),
        distance,
        votesText,
        commentsText,
    ).let { if (saved) stringResource(R.string.poi_card_saved_description, it) else it }
    val shape = MaterialTheme.shapes.large
    val cardModifier = modifier
        .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
        .clip(shape)
        .background(scheme.surfaceContainerLowest)
        .clickable(role = Role.Button, onClick = onClick)
        .clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick { onClick(); true }
        }
        .heightIn(min = 96.dp)
        .padding(12.dp)
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 22.sp)

    if (stacked) {
        Column(cardModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Photo(Modifier.fillMaxWidth().height(128.dp), photo)
            Text(title, style = titleStyle, color = scheme.onSurface)
            StatusBadge(status, size = BadgeSize.SMALL)
            if (saved) SavedBadge()
            CategoryTag(category)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Meta(R.drawable.ic_near_me, distance, explora.textSecondary)
                Meta(R.drawable.ic_priority_high, votesText, explora.textSecondary)
                Meta(R.drawable.ic_chat_bubble, commentsText, explora.textSecondary)
            }
        }
    } else {
        // El texto fija el alto (mínimo 96 dp) y la foto lo iguala. Sin IntrinsicSize.Min: TitleWithBadge y FlowRow
        // dan un alto intrínseco menor que el real y la tarjeta recortaba la fila de votos.
        Box(cardModifier) {
            Photo(Modifier.matchParentSize().wrapContentWidth(Alignment.Start).width(96.dp), photo)
            Column(
                Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(start = 96.dp + 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TitleWithBadge(
                    title = title,
                    titleStyle = titleStyle,
                    titleContent = { Text(title, style = titleStyle, color = scheme.onSurface) },
                    badge = { if (saved) SavedBadge() else StatusBadge(status, size = BadgeSize.SMALL) },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (saved) StatusBadge(status, size = BadgeSize.SMALL)
                    CategoryTag(category)
                    Meta(R.drawable.ic_near_me, distance, scheme.onSurfaceVariant, gap = 3.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Meta(R.drawable.ic_priority_high, votesText, scheme.onSurfaceVariant)
                    Meta(R.drawable.ic_chat_bubble, comments.toString(), scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Título con el badge a la derecha, como en el diseño, mientras la palabra más larga del título quepa al
 * lado; si no, el badge pasa debajo y el título usa todo el ancho. Así nunca se parte una palabra.
 */
@Composable
private fun TitleWithBadge(
    title: String,
    titleStyle: TextStyle,
    titleContent: @Composable () -> Unit,
    badge: @Composable () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val longestWordPx = remember(title, titleStyle, measurer) {
        title.split(' ').filter { it.isNotBlank() }.maxOfOrNull { measurer.measure(it, titleStyle).size.width } ?: 0
    }
    Layout(contents = listOf(titleContent, badge)) { (titleMeasurables, badgeMeasurables), constraints ->
        val gap = 8.dp.roundToPx()
        val badgePlaceable = badgeMeasurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val besideWidth = constraints.maxWidth - badgePlaceable.width - gap
        if (longestWordPx <= besideWidth) {
            val titlePlaceable = titleMeasurables.first().measure(Constraints(maxWidth = besideWidth))
            layout(constraints.maxWidth, maxOf(titlePlaceable.height, badgePlaceable.height)) {
                titlePlaceable.place(0, 0)
                badgePlaceable.place(constraints.maxWidth - badgePlaceable.width, 0)
            }
        } else {
            val titlePlaceable = titleMeasurables.first().measure(Constraints(maxWidth = constraints.maxWidth))
            val below = titlePlaceable.height + 6.dp.roundToPx()
            layout(constraints.maxWidth, below + badgePlaceable.height) {
                titlePlaceable.place(0, 0)
                badgePlaceable.place(0, below)
            }
        }
    }
}

@Composable
private fun Photo(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun Meta(@DrawableRes icon: Int, text: String, color: Color, gap: Dp = 4.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
