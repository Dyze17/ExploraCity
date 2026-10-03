package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateRules
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.screens.access.AccessOfflineNotice
import co.edu.uniquindio.exploracity.ui.screens.map.MissingMapsKey
import co.edu.uniquindio.exploracity.ui.screens.map.NewPlacePin
import co.edu.uniquindio.exploracity.ui.screens.map.NumberPin
import co.edu.uniquindio.exploracity.ui.screens.map.toLatLng
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.hasMapsApiKey
import co.edu.uniquindio.exploracity.viewmodel.CompareDuplicatesUiState
import co.edu.uniquindio.exploracity.viewmodel.CompareDuplicatesViewModel
import co.edu.uniquindio.exploracity.viewmodel.ReviewContent
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.time.Instant
import kotlin.math.cos
import kotlin.math.max

/**
 * 33A · Comparar lugares, conectada a su ViewModel. «Verificar como lugar distinto» vuelve a 33 con la hoja de 34
 * abierta ([onVerifyDistinct]); «Rechazar por duplicado» abre 35 con ese lugar como original ([onRejectDuplicate]).
 */
@Composable
fun CompareDuplicatesRoute(
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onVerifyDistinct: () -> Unit,
    onRejectDuplicate: (originalId: String) -> Unit,
    viewModel: CompareDuplicatesViewModel = viewModel(factory = CompareDuplicatesViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompareDuplicatesScreen(
        state = state,
        callbacks = CompareCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onSelect = viewModel::onSelect,
            onOpenPlace = onOpenPlace,
            onVerifyDistinct = onVerifyDistinct,
            onRejectDuplicate = { state.candidate?.let { onRejectDuplicate(it.poi.id) } },
        ),
    )
}

class CompareCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onOpenPlace: (String) -> Unit = {},
    val onVerifyDistinct: () -> Unit = {},
    val onRejectDuplicate: () -> Unit = {},
)

/**
 * 33A · La publicación nueva y el lugar existente en columnas iguales, con los mismos campos en el mismo orden, para
 * comparar a simple vista. Cada una se rotula con texto, icono y borde (nueva = terracota, existente = oscuro). Con 2 o
 * 3 parecidos, «Comparar con» elige cuál. Tocar la columna existente abre su detalle (13). A 200 % las columnas se
 * apilan.
 */
@Composable
fun CompareDuplicatesScreen(state: CompareDuplicatesUiState, callbacks: CompareCallbacks, modifier: Modifier = Modifier, now: Instant? = null) {
    val clock by rememberNow()
    val current = now ?: clock
    val item = state.item
    val candidate = state.candidate
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(
            title = stringResource(R.string.compare_title),
            onBack = callbacks.onBack,
            actions = { if (item != null) DuplicateFlag(Modifier.padding(end = 12.dp), size = BadgeSize.SMALL) },
        )
        val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
        if (state.offline && item != null && !largeFont) AccessOfflineNotice(stringResource(R.string.review_offline))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.content) {
                ReviewContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is ReviewContent.Loaded -> if (item != null && candidate != null) {
                    Compare(item, candidate, state, callbacks, current, offlineNotice = state.offline && largeFont)
                }
                ReviewContent.Gone -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_done_all,
                        title = stringResource(R.string.review_gone_title),
                        body = stringResource(R.string.review_gone_body),
                    ) {
                        ExploraButton(stringResource(R.string.review_back_to_queue), onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth())
                    }
                }
                ReviewContent.Offline -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.review_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
                ReviewContent.Error -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.review_error_title),
                        body = stringResource(R.string.reset_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
            }
        }
        if (candidate != null) ActionBar(state, callbacks)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Compare(
    item: ReviewItem,
    candidate: DuplicateCandidate,
    state: CompareDuplicatesUiState,
    callbacks: CompareCallbacks,
    now: Instant,
    offlineNotice: Boolean,
) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (offlineNotice) Box(Modifier.clip(MaterialTheme.shapes.medium)) { AccessOfflineNotice(stringResource(R.string.review_offline)) }
        if (state.candidates.size > 1) CompareWith(state, callbacks.onSelect)
        CompareMap(item, candidate, number = state.candidates.indexOf(candidate) + 1)
        val new: @Composable (Modifier) -> Unit = { m -> NewColumn(item, now, m) }
        val existing: @Composable (Modifier) -> Unit = { m -> ExistingColumn(candidate, now, onOpen = { callbacks.onOpenPlace(candidate.poi.id) }, m) }
        if (stacked) {
            new(Modifier.fillMaxWidth())
            existing(Modifier.fillMaxWidth())
        } else {
            // Las dos del mismo alto: los campos quedan a la misma altura para compararlos de un vistazo.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                new(Modifier.weight(1f).fillMaxHeight())
                existing(Modifier.weight(1f).fillMaxHeight())
            }
        }
        item.duplicate?.authorNote?.let { note ->
            HintCard(
                text = stringResource(R.string.compare_note, note),
                title = stringResource(R.string.compare_note_title),
                icon = R.drawable.ic_sticky_note_2,
            )
        }
    }
}

/** «Comparar con: 1 · Museo Botero · 2 · Biblioteca…»: uno a la vez, con el número de su pin en el mapa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareWith(state: CompareDuplicatesUiState, onSelect: (Int) -> Unit) {
    val label = stringResource(R.string.compare_with)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700), color = MaterialTheme.exploraColors.textSecondary)
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.candidates.forEachIndexed { index, candidate ->
                CompareChip(stringResource(R.string.compare_with_option, index + 1, candidate.poi.title), selected = index == state.selected) { onSelect(index) }
            }
        }
    }
}

/** Elegido = relleno primary y check, no solo color (como los filtros de 32). */
@Composable
private fun CompareChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outline, shape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp.scaledWithFont()))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = if (selected) scheme.onPrimary else scheme.onSurface)
    }
}

/**
 * La nueva (gota con «+»), el lugar existente (cuadrado con su número) y el radio de 50 m, como en 17A, con «a 23 m
 * entre sí». No se mueve: es para ubicarse de un vistazo; el lector oye la distancia.
 */
@Composable
private fun CompareMap(item: ReviewItem, candidate: DuplicateCandidate, number: Int) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val distance = stringResource(R.string.compare_distance, candidate.distanceMeters)
    val description = stringResource(R.string.compare_map_description, candidate.poi.title, candidate.distanceMeters)
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .semantics { contentDescription = description },
    ) {
        if (remember(context) { context.hasMapsApiKey() }) {
            val here = item.location
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(here.toLatLng(), 17f) }
            val padding = with(density) { 8.dp.roundToPx() }
            val radius = max(DuplicateRules.RADIUS_METERS, candidate.distanceMeters) + 15
            GoogleMap(
                modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
                cameraPositionState = camera,
                properties = MapProperties(
                    mapStyleOptions = remember(context, dark) { MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style) },
                ),
                uiSettings = MapUiSettings(
                    compassEnabled = false,
                    indoorLevelPickerEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    rotationGesturesEnabled = false,
                    scrollGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                    zoomControlsEnabled = false,
                    zoomGesturesEnabled = false,
                ),
                mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
                onMapLoaded = { camera.move(CameraUpdateFactory.newLatLngBounds(here.boundsAround(radius), padding)) },
                onMapClick = {},
            ) {
                Circle(
                    center = here.toLatLng(),
                    radius = DuplicateRules.RADIUS_METERS.toDouble(),
                    fillColor = scheme.tertiary.copy(alpha = 0.12f),
                    strokeColor = scheme.tertiary,
                    strokeWidth = with(density) { 2.dp.toPx() },
                    strokePattern = with(density) { listOf(Dash(6.dp.toPx()), Gap(4.dp.toPx())) },
                )
                MarkerComposable(
                    candidate.poi.id,
                    number,
                    dark,
                    state = rememberUpdatedMarkerState(position = candidate.poi.location.toLatLng()),
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 2f,
                    onClick = { true },
                ) { NumberPin(number) }
                MarkerComposable(
                    dark,
                    state = rememberUpdatedMarkerState(position = here.toLatLng()),
                    anchor = Offset(0.5f, 1f),
                    zIndex = 1f,
                    onClick = { true },
                ) { NewPlacePin() }
            }
        } else {
            MissingMapsKey()
        }
        Text(
            distance,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W700),
            color = scheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(scheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clearAndSetSemantics { },
        )
    }
}

/** Un cuadrado de [meters] a cada lado del punto: encuadra el radio y el lugar existente. */
private fun GeoPoint.boundsAround(meters: Int): LatLngBounds {
    val dLat = meters / METERS_PER_DEGREE
    val dLng = meters / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
    return LatLngBounds(GeoPoint(latitude - dLat, longitude - dLng).toLatLng(), GeoPoint(latitude + dLat, longitude + dLng).toLatLng())
}

private const val METERS_PER_DEGREE = 111_320.0

/** «Publicación nueva»: borde terracota de 2 dp y la gota con «+» del mapa. */
@Composable
private fun NewColumn(item: ReviewItem, now: Instant, modifier: Modifier) {
    PlaceColumn(
        heading = stringResource(R.string.compare_new),
        headingIcon = R.drawable.ic_add_location,
        accent = MaterialTheme.exploraColors.onSurfaceAccent,
        border = MaterialTheme.colorScheme.primary,
        photoCount = item.photos.size,
        title = item.title,
        category = item.category,
        author = item.author.author,
        date = relativeTimeText(item.submittedAt, now),
        status = PublicationStatus.PENDING,
        modifier = modifier,
    )
}

/** «Lugar existente»: borde oscuro; se toca para ver su detalle (13). */
@Composable
private fun ExistingColumn(candidate: DuplicateCandidate, now: Instant, onOpen: () -> Unit, modifier: Modifier) {
    val action = stringResource(R.string.compare_open_existing)
    PlaceColumn(
        heading = stringResource(R.string.compare_existing),
        headingIcon = R.drawable.ic_location_on,
        accent = MaterialTheme.exploraColors.textSecondary,
        border = MaterialTheme.exploraColors.textSecondary,
        photoCount = candidate.photoCount,
        title = candidate.poi.title,
        category = candidate.poi.category,
        author = candidate.author,
        date = candidate.publishedAt?.let { relativeTimeText(it, now) },
        status = candidate.poi.status,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = action, onClick = onOpen),
    )
}

/** Los mismos campos en el mismo orden: rótulo, fotos, nombre, categoría, autor, fecha y estado. */
@Composable
private fun PlaceColumn(
    heading: String,
    headingIcon: Int,
    accent: Color,
    border: Color,
    photoCount: Int,
    title: String,
    category: Category,
    author: Author?,
    date: String?,
    status: PublicationStatus,
    modifier: Modifier,
) {
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(2.dp, border, shape)
            .padding(10.dp)
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(headingIcon), null, tint = accent, modifier = Modifier.size(14.dp.scaledWithFont()))
            Text(heading, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700), color = accent, modifier = Modifier.semantics { heading() })
        }
        Box(
            Modifier.fillMaxWidth().height(84.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_photo_camera), null, tint = explora.textPlaceholder, modifier = Modifier.size(16.dp.scaledWithFont()))
                Text(pluralStringResource(R.plurals.moderation_photos, photoCount, photoCount), style = MaterialTheme.typography.labelSmall, color = explora.textSecondary)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.onSurface)
        CategoryTag(category)
        if (author != null) {
            Text(
                stringResource(R.string.compare_author, author.name, stringResource(author.level.labelRes)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600),
                color = explora.textSecondary,
            )
        }
        if (date != null) Text(date, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp), color = explora.textSecondary)
        StatusBadge(status, size = BadgeSize.SMALL)
    }
}

/** «Verificar como lugar distinto» (34, sin la marca) y «Rechazar por duplicado» (35 con el original enlazado). */
@Composable
private fun ActionBar(state: CompareDuplicatesUiState, callbacks: CompareCallbacks) {
    val reason = if (state.offline) stringResource(R.string.review_offline_reason) else null
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExploraButton(
                stringResource(R.string.compare_verify_distinct),
                onClick = callbacks.onVerifyDistinct,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = state.canDecide,
                disabledReason = reason,
                icon = R.drawable.ic_verified,
            )
            ExploraButton(
                stringResource(R.string.compare_reject_duplicate),
                onClick = callbacks.onRejectDuplicate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                style = ExploraButtonStyle.DESTRUCTIVE_OUTLINED,
                enabled = state.canDecide,
                disabledReason = reason,
                icon = R.drawable.ic_join_inner,
            )
        }
    }
}

private val previewNow: Instant = Instant.parse("2026-10-02T15:00:00Z")

@Preview(name = "33A · lado a lado · claro", widthDp = 360, heightDp = 800)
@Composable
private fun ComparePreview() {
    val item = previewReviews(previewNow)[1]
    ExploraCityTheme(ThemeMode.LIGHT) {
        CompareDuplicatesScreen(CompareDuplicatesUiState(content = ReviewContent.Loaded(item)), CompareCallbacks(), now = previewNow)
    }
}

@Preview(name = "33A · lado a lado · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun CompareDarkPreview() {
    val item = previewReviews(previewNow)[1]
    ExploraCityTheme(ThemeMode.DARK) {
        CompareDuplicatesScreen(CompareDuplicatesUiState(content = ReviewContent.Loaded(item)), CompareCallbacks(), now = previewNow)
    }
}
