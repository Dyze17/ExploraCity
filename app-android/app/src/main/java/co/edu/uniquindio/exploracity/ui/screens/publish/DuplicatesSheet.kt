package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.DuplicateRules
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.DuplicateCandidateCard
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.NoNavigationBarScrim
import co.edu.uniquindio.exploracity.ui.components.SheetHandle
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.screens.map.MissingMapsKey
import co.edu.uniquindio.exploracity.ui.screens.map.NewPlacePin
import co.edu.uniquindio.exploracity.ui.screens.map.NumberPin
import co.edu.uniquindio.exploracity.ui.screens.map.toLatLng
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ScrimAlpha
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.hasMapsApiKey
import co.edu.uniquindio.exploracity.viewmodel.DuplicateReview
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
import kotlinx.coroutines.launch
import kotlin.math.cos

/**
 * 17A · «¿Ya existe este lugar?» y 17B · «Es un lugar distinto», dos estados de la misma hoja (el conteo de 5 pasos no
 * cambia). No bloquea: deslizarla hacia abajo vuelve al paso 3 con el pin donde estaba. Abierta mientras [review] no
 * sea null; al cerrarse desde fuera se anima la salida con el último estado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicatesSheet(review: DuplicateReview?, location: GeoPoint?, callbacks: PublishCallbacks) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lastShown by remember { mutableStateOf<DuplicateReview?>(null) }
    LaunchedEffect(review) {
        if (review != null) lastShown = review
    }
    LaunchedEffect(review == null) {
        if (review == null && lastShown != null) {
            sheetState.hide()
            lastShown = null
        }
    }
    val shown = review ?: lastShown ?: return
    val here = location ?: return
    val scope = rememberCoroutineScope()
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.9f }
    ModalBottomSheet(
        onDismissRequest = callbacks.onDuplicatesDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha.Sheet),
        dragHandle = null,
    ) {
        NoNavigationBarScrim()
        // En 17B, volver regresa a 17A; en 17A cierra la hoja (lo hace la propia hoja).
        BackHandler(enabled = shown.different, onBack = callbacks.onBackToSimilar)
        val contentModifier = Modifier.heightIn(max = maxHeight)
        if (shown.different) {
            DifferentPlaceContent(shown, callbacks, contentModifier)
        } else {
            SimilarPlacesContent(
                review = shown,
                here = here,
                onNotSamePlace = callbacks.onNotSamePlace,
                // La hoja baja antes de abrir el lugar: no queda encima del detalle mientras cambia la pantalla.
                onOpen = { id -> scope.launch { sheetState.hide() }.invokeOnCompletion { callbacks.onOpenSimilar(id) } },
                modifier = contentModifier,
            )
        }
    }
}

/** 17A · Minimapa, de 1 a 3 parecidos del más cercano al más lejano y «Es otro lugar, continuar». */
@Composable
private fun SimilarPlacesContent(
    review: DuplicateReview,
    here: GeoPoint,
    onNotSamePlace: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.duplicate_sheet_title)
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SheetTitle(title, Modifier.focusRequester(titleFocus).focusable())
                Text(
                    stringResource(R.string.duplicate_sheet_body),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
            }
            // Con fuente grande la leyenda taparía el minimapa: va debajo.
            val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
            SimilarPlacesMap(here, review.places, legendInside = !largeFont)
            if (largeFont) MapLegend(inline = true)
            review.places.forEachIndexed { index, place ->
                DuplicateCandidateCard(index + 1, place, onOpen = { onOpen(place.id) })
            }
        }
        Spacer(Modifier.height(12.dp))
        ExploraButton(stringResource(R.string.duplicate_not_same), onClick = onNotSamePlace, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_cloud_done), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(16.dp.scaledWithFont()))
            Text(
                stringResource(R.string.duplicate_draft_saved),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.exploraColors.textSecondary,
            )
        }
    }
}

/**
 * 17B · La nota es opcional y sin validación que bloquee: «Continuar» siempre sigue al paso 4 con la marca y la nota
 * en el borrador.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifferentPlaceContent(review: DuplicateReview, callbacks: PublishCallbacks, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.duplicate_different_title)
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    val noteState = rememberTextFieldState(review.note)
    val currentOnNote by rememberUpdatedState(callbacks.onDuplicateNoteChange)
    LaunchedEffect(noteState) { snapshotFlow { noteState.text.toString() }.collect { currentOnNote(it) } }
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = callbacks.onBackToSimilar) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.duplicate_back_to_similar))
                }
                SheetTitle(title, Modifier.weight(1f).focusRequester(titleFocus).focusable())
            }
            Text(
                stringResource(R.string.duplicate_different_body),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = MaterialTheme.exploraColors.textSecondary,
            )
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.duplicate_flag_intro),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
                DuplicateFlag(size = BadgeSize.SMALL)
            }
            ExploraTextField(
                state = noteState,
                label = stringResource(R.string.duplicate_note_label),
                supportingText = stringResource(R.string.duplicate_note_hint, noteState.text.length, DuplicateRules.NOTE_MAX),
                singleLine = false,
                minLines = 3,
                inputTransformation = InputTransformation.maxLength(DuplicateRules.NOTE_MAX),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        ExploraButton(stringResource(R.string.publish_continue), onClick = callbacks.onConfirmDifferent, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
        ExploraButton(
            stringResource(R.string.duplicate_back_to_similar),
            onClick = callbacks.onBackToSimilar,
            modifier = Modifier.fillMaxWidth(),
            style = ExploraButtonStyle.TEXT,
        )
    }
}

@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Minimapa de 17A: tu lugar (gota con «+»), los existentes (cuadrados numerados como sus tarjetas) y el radio de
 * búsqueda de 50 m. Forma + número + leyenda, nunca solo color. No se mueve: es para ubicarse de un vistazo.
 */
@Composable
private fun SimilarPlacesMap(here: GeoPoint, places: List<SimilarPlace>, legendInside: Boolean) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh)) {
        if (remember(context) { context.hasMapsApiKey() }) {
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(here.toLatLng(), 17f) }
            val padding = with(density) { 8.dp.roundToPx() }
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                contentDescription = pluralStringResource(R.plurals.duplicate_map_description, places.size, places.size),
                properties = MapProperties(mapStyleOptions = remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style) }),
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
                // Encuadra el círculo de 50 m con un margen.
                onMapLoaded = { camera.move(CameraUpdateFactory.newLatLngBounds(here.boundsAround(DuplicateRules.RADIUS_METERS + 15), padding)) },
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
                places.forEachIndexed { index, place ->
                    MarkerComposable(
                        index,
                        dark,
                        state = rememberUpdatedMarkerState(position = place.location.toLatLng()),
                        contentDescription = stringResource(R.string.duplicate_marker_existing, index + 1),
                        anchor = Offset(0.5f, 0.5f),
                        // Por encima de tu gota: un parecido a pocos metros al norte quedaría escondido detrás de ella.
                        zIndex = 2f,
                        onClick = { true },
                    ) { NumberPin(index + 1) }
                }
                MarkerComposable(
                    dark,
                    state = rememberUpdatedMarkerState(position = here.toLatLng()),
                    contentDescription = stringResource(R.string.duplicate_marker_new),
                    anchor = Offset(0.5f, 1f),
                    zIndex = 1f,
                    onClick = { true },
                ) { NewPlacePin() }
            }
        } else {
            MissingMapsKey()
        }
        // Arriba: abajo a la izquierda va el logo de Google, que debe verse siempre.
        if (legendInside) MapLegend(Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/**
 * «+ Tu nuevo lugar · ▢ Lugar existente · ◌ 50 m». Sobre el minimapa o, con fuente grande ([inline]), debajo en una
 * fila que se parte. El lector ya lo oye en la descripción del mapa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLegend(modifier: Modifier = Modifier, inline: Boolean = false) {
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.W700)
    val items = @Composable {
        Text(stringResource(R.string.duplicate_legend_new), style = style, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.duplicate_legend_existing), style = style, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.duplicate_legend_radius, DuplicateRules.RADIUS_METERS), style = style, color = MaterialTheme.colorScheme.onSurface)
    }
    if (inline) {
        FlowRow(
            modifier.clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { items() }
    } else {
        Column(
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) { items() }
    }
}

/** Un cuadrado de [meters] a cada lado del punto: basta para encuadrar el radio de búsqueda. */
private fun GeoPoint.boundsAround(meters: Int): LatLngBounds {
    val dLat = meters / METERS_PER_DEGREE
    val dLng = meters / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
    return LatLngBounds(GeoPoint(latitude - dLat, longitude - dLng).toLatLng(), GeoPoint(latitude + dLat, longitude + dLng).toLatLng())
}

private const val METERS_PER_DEGREE = 111_320.0

private val previewPlaces = listOf(
    SimilarPlace("la-puerta-falsa", "La Puerta Falsa", Category.GASTRONOMY, PublicationStatus.VERIFIED, GeoPoint(4.5977, -74.0746), 23),
    SimilarPlace("tamales-puerta", "Tamales de la Puerta", Category.GASTRONOMY, PublicationStatus.PENDING, GeoPoint(4.5979, -74.0749), 41),
)

@Preview(name = "17A · con coincidencias · claro", widthDp = 360, heightDp = 800)
@Composable
private fun SimilarPlacesPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            SimilarPlacesContent(DuplicateReview(previewPlaces), GeoPoint(4.5975, -74.0745), onNotSamePlace = {}, onOpen = {})
        }
    }
}

@Preview(name = "17B · nota opcional · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun DifferentPlacePreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            DifferentPlaceContent(
                DuplicateReview(previewPlaces, different = true, note = "Es el local del segundo piso, con entrada por la calle 10."),
                PublishCallbacks(),
            )
        }
    }
}
