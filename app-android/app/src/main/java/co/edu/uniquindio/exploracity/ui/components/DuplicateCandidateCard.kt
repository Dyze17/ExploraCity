package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.ui.screens.map.NumberPin
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.formatDistance
import java.util.Locale

/**
 * 17A · Un lugar parecido: el número lo enlaza con su marcador del minimapa. El lector oye «Lugar parecido 1: La Fonda
 * Café, Gastronomía, verificada, a 23 m». Una pendiente no tiene página pública: en lugar de «Ver este lugar» dice que
 * está en revisión. Con fuente grande la foto va arriba.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicateCandidateCard(number: Int, place: SimilarPlace, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    val distance = stringResource(R.string.map_distance, formatDistance(place.distanceMeters))
    val spoken = stringResource(
        R.string.duplicate_candidate_description,
        number,
        place.title,
        stringResource(place.category.labelRes),
        stringResource(place.status.labelRes).lowercase(Locale.forLanguageTag("es")),
        distance,
    )
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(
        modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLowest, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val photo = @Composable {
            // El número sobresale de la esquina de la foto, como en el lienzo.
            Box(Modifier.padding(top = 6.dp, start = 6.dp)) {
                Box(Modifier.size(56.dp).background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp)))
                Box(Modifier.offset(x = (-6).dp, y = (-6).dp)) { NumberPin(number, size = 22.dp) }
            }
        }
        val info = @Composable { infoModifier: Modifier ->
            Column(infoModifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    place.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600),
                    color = scheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryTag(place.category)
                    StatusBadge(place.status, size = BadgeSize.SMALL)
                    Text(distance, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700), color = explora.textSecondary)
                }
            }
        }
        if (stacked) {
            Column(Modifier.clearAndSetSemantics { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photo()
                info(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                photo()
                info(Modifier.weight(1f))
            }
        }
        if (place.isPublic) {
            ExploraButton(
                stringResource(R.string.duplicate_open_place),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.SECONDARY,
                icon = R.drawable.ic_open_in_new,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = explora.iconSecondary, modifier = Modifier.size(16.dp.scaledWithFont()))
                Text(
                    stringResource(R.string.duplicate_not_public),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = explora.textSecondary,
                )
            }
        }
    }
}

private val previewPlace = SimilarPlace(
    id = "la-puerta-falsa",
    title = "La Puerta Falsa",
    category = Category.GASTRONOMY,
    status = PublicationStatus.VERIFIED,
    location = GeoPoint(4.5977, -74.0746),
    distanceMeters = 23,
)

@Preview(name = "DuplicateCandidateCard · claro", widthDp = 360)
@Composable
private fun CandidateLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DuplicateCandidateCard(1, previewPlace, onOpen = {})
            DuplicateCandidateCard(2, previewPlace.copy(title = "Café Fonda Vieja", status = PublicationStatus.PENDING, distanceMeters = 41), onOpen = {})
        }
    }
}

@Preview(name = "DuplicateCandidateCard · oscuro · 200 %", widthDp = 360, fontScale = 2f)
@Composable
private fun CandidateDarkLargePreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            DuplicateCandidateCard(1, previewPlace, onOpen = {})
        }
    }
}
