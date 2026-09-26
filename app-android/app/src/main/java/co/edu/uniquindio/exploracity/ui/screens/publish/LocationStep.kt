package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.location.ApproximateAddress
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSearchBar
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.screens.map.MissingMapsKey
import co.edu.uniquindio.exploracity.ui.screens.map.NewPlacePin
import co.edu.uniquindio.exploracity.ui.screens.map.PlacePin
import co.edu.uniquindio.exploracity.ui.screens.map.toGeoPoint
import co.edu.uniquindio.exploracity.ui.screens.map.toLatLng
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.hasMapsApiKey
import co.edu.uniquindio.exploracity.util.rememberTouchExplorationEnabled
import co.edu.uniquindio.exploracity.viewmodel.AddressSearch
import co.edu.uniquindio.exploracity.viewmodel.DraftField
import co.edu.uniquindio.exploracity.viewmodel.PinAddress
import co.edu.uniquindio.exploracity.viewmodel.PublishContent
import co.edu.uniquindio.exploracity.viewmodel.PublishUiState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

/** Con el pin puesto se ve la cuadra; sin él, el centro de la ciudad. */
private const val PIN_ZOOM = 17f
private const val CITY_ZOOM = 14f

/** Ajuste fino con el lector de pantalla (README 17: ±0,0001°, unos 11 m). */
private const val NUDGE_DEGREES = 0.0001

/** El mapa nunca queda más bajo que esto: con fuente grande se desplazan los textos de arriba y de abajo. */
private val MinMapHeight = 200.dp

/**
 * 17 · Paso 3, ubicación. Se arrastra el mapa bajo un pin fijo (17.a lo dibuja levantado, con su sombra): al soltarlo
 * queda puesto y se anuncia la dirección aproximada. «Usar mi ubicación» lo lleva donde está la persona; sin permiso
 * (17.b) aparecen el aviso y la búsqueda por dirección. Con TalkBack, cuatro botones lo mueven unos 11 m.
 */
@Composable
internal fun LocationStep(
    state: PublishUiState,
    callbacks: PublishCallbacks,
    cityCenter: GeoPoint,
    canAskLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val camera = rememberCameraPositionState {
        val start = draft.location
        position = CameraPosition.fromLatLngZoom((start ?: cityCenter).toLatLng(), if (start != null) PIN_ZOOM else CITY_ZOOM)
    }
    val currentOnPinMoved by rememberUpdatedState(callbacks.onPinMoved)
    val currentOnTargetShown by rememberUpdatedState(callbacks.onPinTargetShown)
    // Soltar el mapa tras arrastrarlo pone el pin en el centro. Los movimientos de la app (ir a tu ubicación, a una
    // dirección) no cuentan: el ViewModel ya sabe adónde va.
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(camera) {
        snapshotFlow { camera.isMoving }.collect { moving ->
            if (moving) {
                dragging = camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
            } else if (dragging) {
                dragging = false
                currentOnPinMoved(camera.position.target.toGeoPoint(), false)
            }
        }
    }
    LaunchedEffect(state.pinTarget) {
        val target = state.pinTarget ?: return@LaunchedEffect
        try {
            camera.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), max(camera.position.zoom, PIN_ZOOM)))
        } finally {
            currentOnTargetShown(target)
        }
    }
    // 21 · «Confirmar ubicación» sin pin lleva el foco al pin (solo a los pedidos de ahora, no a los de otros pasos).
    val pinFocus = remember { FocusRequester() }
    val initialFocusRequest = remember { state.errorFocusRequest }
    LaunchedEffect(state.errorFocusRequest) {
        if (state.errorFocusRequest != initialFocusRequest && state.stepErrors.firstOrNull() == DraftField.LOCATION) pinFocus.requestFocus()
    }
    val touchExploration = rememberTouchExplorationEnabled()
    // Con fuente grande lo que flota sobre el mapa lo taparía: la indicación sin pin sube a los textos de arriba, la
    // etiqueta del barrio se quita (la tarjeta de abajo la dice) y «Usar mi ubicación» queda solo con su icono.
    val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    // En 17.b el aviso y la búsqueda achican el mapa: ahí la indicación también va arriba, para no tapar el pin.
    val hintAbove = draft.location == null && (largeFont || state.locationDenied)

    MapStepLayout(
        modifier = modifier,
        top = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepHeading(stringResource(R.string.publish_location_heading), stringResource(R.string.publish_location_intro))
                if (state.showLocationError) ErrorSummary(listOf(DraftField.LOCATION))
                if (state.locationDenied) {
                    LocationDeniedBanner(canAskLocation, callbacks.onAllowLocation)
                    AddressSearchField(state, callbacks)
                }
                if (hintAbove) MapHint(inline = true)
            }
        },
        map = {
            PinMap(
                camera = camera,
                draft = draft,
                address = state.address,
                lifted = dragging,
                showUseMyLocation = !state.locationDenied,
                onUseMyLocation = callbacks.onUseMyLocation,
                pinFocus = pinFocus,
                largeFont = largeFont,
                hintAbove = hintAbove,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        bottom = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (touchExploration) {
                    NudgeButtons(stacked = largeFont) { dLat, dLng ->
                        val from = draft.location ?: camera.position.target.toGeoPoint()
                        callbacks.onPinMoved(GeoPoint(from.latitude + dLat, from.longitude + dLng), true)
                    }
                }
                draft.location?.let { AddressCard(it, state.address) }
            }
        },
    )
}

/**
 * Tres franjas: [top] y [bottom] toman lo que necesiten (como mucho, lo que deje el mapa con su alto mínimo, y se
 * desplazan por dentro) y el mapa, lo que sobra. Así el mapa nunca queda dentro de algo desplazable: arrastrarlo no
 * mueve la pantalla.
 */
@Composable
private fun MapStepLayout(
    top: @Composable () -> Unit,
    map: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
) {
    Layout(contents = listOf(top, map, bottom), modifier = modifier) { (topSlot, mapSlot, bottomSlot), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gapPx = gap.roundToPx()
        val room = (height - MinMapHeight.roundToPx() - gapPx * 2).coerceAtLeast(0)
        val bottomPlaceables = bottomSlot.map { it.measure(Constraints(maxWidth = width, maxHeight = room / 2)) }
        val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0
        val topPlaceables = topSlot.map { it.measure(Constraints(maxWidth = width, maxHeight = room - bottomHeight)) }
        val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomGap = if (bottomHeight > 0) gapPx else 0
        val mapHeight = (height - topHeight - gapPx - bottomHeight - bottomGap).coerceAtLeast(0)
        val mapPlaceables = mapSlot.map { it.measure(Constraints.fixed(width, mapHeight)) }
        layout(width, height) {
            topPlaceables.forEach { it.place(0, 0) }
            mapPlaceables.forEach { it.place(0, topHeight + gapPx) }
            bottomPlaceables.forEach { it.place(0, topHeight + gapPx + mapHeight + bottomGap) }
        }
    }
}

@Composable
private fun PinMap(
    camera: CameraPositionState,
    draft: PublicationDraft,
    address: PinAddress?,
    lifted: Boolean,
    showUseMyLocation: Boolean,
    onUseMyLocation: () -> Unit,
    pinFocus: FocusRequester,
    largeFont: Boolean,
    hintAbove: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh)) {
        if (remember(context) { context.hasMapsApiKey() }) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                contentDescription = stringResource(R.string.publish_location_map),
                // Sin iconos de negocios, como el mapa 8: el pin es lo único que importa aquí.
                properties = MapProperties(
                    minZoomPreference = 10f,
                    mapStyleOptions = remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style) },
                ),
                // Siempre con el norte arriba: los botones de ajuste fino dicen «al norte», «al este»…
                uiSettings = MapUiSettings(
                    compassEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                    indoorLevelPickerEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                ),
                mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
            )
        } else {
            MissingMapsKey()
        }
        CenterPin(draft, lifted, pinFocus)
        val placed = draft.location != null
        val area = (address as? PinAddress.Found)?.address?.area
        when {
            largeFont || hintAbove -> Unit
            !placed -> MapHint(Modifier.align(Alignment.TopCenter).padding(12.dp))
            area != null -> AreaLabel(area, Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        // Abajo a la derecha: a la izquierda va el logo de Google, que debe verse siempre.
        if (showUseMyLocation) UseMyLocationButton(onUseMyLocation, iconOnly = largeFont, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
    }
}

/**
 * El pin, con la punta en el centro del mapa: la gota con el icono y el color de la categoría (17.a). Mientras se
 * arrastra el mapa se levanta y deja ver el punto exacto. Para el lector es un elemento con foco propio.
 */
@Composable
private fun CenterPin(draft: PublicationDraft, lifted: Boolean, focusRequester: FocusRequester) {
    val category = draft.category
    val lift by animateDpAsState(if (lifted) 10.dp else 0.dp, label = "pin")
    val description = stringResource(if (draft.location != null) R.string.publish_pin_placed else R.string.publish_pin_unplaced)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), CircleShape))
        Box(
            Modifier
                .offset { IntOffset(0, -(PinHeight / 2 + lift).roundToPx()) }
                .focusRequester(focusRequester)
                .focusable()
                .semantics { contentDescription = description },
        ) {
            if (category != null) {
                PlacePin(category, category.colors, selected = true, border = MaterialTheme.exploraColors.mapClusterBorder)
            } else {
                NewPlacePin()
            }
        }
    }
}

/** Alto de la gota seleccionada de [PlacePin]: 52 dp de círculo más la punta. */
private val PinHeight = 52.dp * (0.5f + sqrt(2f) / 2f)

/** Sin pin puesto: cómo ponerlo (17.b). Sobre el mapa o, con fuente grande ([inline]), entre los textos de arriba. */
@Composable
private fun MapHint(modifier: Modifier = Modifier, inline: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        stringResource(R.string.publish_location_hint),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        color = MaterialTheme.exploraColors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .then(if (inline) Modifier else Modifier.exploraShadow(ExploraElevation.Card, shape, MaterialTheme.exploraColors.shadow))
            .background(if (inline) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** «Chapinero, Bogotá» sobre el mapa (17.a). La tarjeta de abajo ya lo dice al lector. */
@Composable
private fun AreaLabel(area: String, modifier: Modifier = Modifier) {
    Text(
        area,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
        color = MaterialTheme.exploraColors.textSecondary,
        modifier = modifier
            .exploraShadow(ExploraElevation.Card, RoundedCornerShape(8.dp), MaterialTheme.exploraColors.shadow)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clearAndSetSemantics { },
    )
}

/** 17.a · «Usar mi ubicación». Con [iconOnly] (fuente grande) es un botón de 48 dp, como «Mi ubicación» del mapa 8. */
@Composable
private fun UseMyLocationButton(onClick: () -> Unit, iconOnly: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val label = stringResource(R.string.publish_use_my_location)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .exploraShadow(ExploraElevation.Fab, shape, MaterialTheme.exploraColors.shadow)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (iconOnly) Modifier.size(48.dp).semantics { contentDescription = label } else Modifier.padding(horizontal = 14.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_my_location), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(if (iconOnly) 24.dp else 20.dp.scaledWithFont()))
        if (!iconOnly) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700), color = MaterialTheme.exploraColors.textSecondary)
        }
    }
}

/**
 * Con TalkBack: mueve el pin 0,0001° en cada dirección (README 17). La dirección nueva se anuncia sola. Con fuente
 * grande ([stacked]) el título va sobre los botones.
 */
@Composable
private fun NudgeButtons(stacked: Boolean, onNudge: (dLat: Double, dLng: Double) -> Unit) {
    val title = @Composable { titleModifier: Modifier ->
        Text(
            stringResource(R.string.publish_nudge_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = titleModifier,
        )
    }
    val buttons = @Composable {
        NudgeButton(R.string.publish_nudge_north, rotation = -90f) { onNudge(NUDGE_DEGREES, 0.0) }
        NudgeButton(R.string.publish_nudge_south, rotation = 90f) { onNudge(-NUDGE_DEGREES, 0.0) }
        NudgeButton(R.string.publish_nudge_west, rotation = 180f) { onNudge(0.0, -NUDGE_DEGREES) }
        NudgeButton(R.string.publish_nudge_east, rotation = 0f) { onNudge(0.0, NUDGE_DEGREES) }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth()) {
            title(Modifier)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { buttons() }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            title(Modifier.weight(1f))
            buttons()
        }
    }
}

@Composable
private fun NudgeButton(label: Int, rotation: Float, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.ic_arrow_forward),
            contentDescription = stringResource(label),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/**
 * «Dirección aproximada» con las coordenadas (17.a). La dirección se anuncia cada vez que cambia (al soltar el pin);
 * las coordenadas no, para no alargar el anuncio.
 */
@Composable
private fun AddressCard(location: GeoPoint, address: PinAddress?) {
    val explora = MaterialTheme.exploraColors
    val coordinates = remember(location) {
        String.format(Locale.forLanguageTag("es-CO"), "%.5f · %.5f", location.latitude, location.longitude)
    }
    val coordinatesSpoken = remember(location) {
        String.format(Locale.forLanguageTag("es-CO"), "%.5f, %.5f", location.latitude, location.longitude)
    }
    val coordinatesDescription = stringResource(R.string.publish_coordinates_spoken, coordinatesSpoken)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_location_on), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    stringResource(R.string.publish_address_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
                    color = explora.textSecondary,
                )
                AddressText(address)
            }
            Text(
                coordinates,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.W400),
                color = explora.iconSecondary,
                modifier = Modifier.semantics { contentDescription = coordinatesDescription },
            )
        }
    }
}

@Composable
private fun AddressText(address: PinAddress?) {
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp)
    val color = MaterialTheme.exploraColors.textSecondary
    when (address) {
        is PinAddress.Found -> Text(address.address.line, style = style, color = color)
        PinAddress.NotFound -> Text(stringResource(R.string.publish_address_none), style = style, color = color)
        PinAddress.Offline -> Text(stringResource(R.string.publish_address_offline), style = style, color = color)
        PinAddress.Unavailable -> Text(stringResource(R.string.publish_address_unavailable), style = style, color = color)
        PinAddress.Loading, null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp.scaledWithFont()), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Text(stringResource(R.string.publish_address_loading), style = style, color = color)
        }
    }
}

/**
 * 17.b · Sin permiso: el flujo sigue a mano. Si Android aún puede mostrar su diálogo, «Dar permiso» lo muestra; si ya
 * no (se negó para siempre), «Abrir Ajustes» lleva a la ficha de la app.
 */
@Composable
private fun LocationDeniedBanner(canAskAgain: Boolean, onAllow: () -> Unit) {
    val warning = MaterialTheme.exploraColors.warning
    Banner(
        container = warning.container,
        content = warning.content,
        leading = { BannerIcon(R.drawable.ic_location_off, warning.content) },
        title = stringResource(R.string.publish_location_denied_title),
        body = stringResource(if (canAskAgain) R.string.publish_location_denied_body_ask else R.string.publish_location_denied_body_settings),
    ) {
        ExploraButton(
            stringResource(if (canAskAgain) R.string.publish_location_allow else R.string.publish_location_open_settings),
            onClick = onAllow,
            style = ExploraButtonStyle.TEXT,
        )
    }
}

/** 17.b · «Buscar una dirección o barrio»: la tecla Buscar del teclado lleva el pin al resultado. */
@Composable
private fun AddressSearchField(state: PublishUiState, callbacks: PublishCallbacks) {
    val searching = state.addressSearch == AddressSearch.Searching
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExploraSearchBar(
            value = state.addressQuery,
            onValueChange = callbacks.onAddressQueryChange,
            placeholder = stringResource(R.string.publish_address_search),
            onSearch = callbacks.onSearchAddress,
            trailing = if (searching) {
                { CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        val message = when (val search = state.addressSearch) {
            is AddressSearch.NotFound -> stringResource(R.string.publish_address_search_not_found, search.query)
            AddressSearch.Offline -> stringResource(R.string.publish_address_search_offline)
            AddressSearch.Failed -> stringResource(R.string.publish_address_search_failed)
            AddressSearch.Idle, AddressSearch.Searching -> null
        }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = MaterialTheme.exploraColors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Preview(name = "17 · tarjeta de dirección · claro", widthDp = 360)
@Composable
private fun AddressCardPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AddressCard(GeoPoint(4.63412, -74.06558), PinAddress.Found(ApproximateAddress("Cl. 45 #19-32", "Chapinero", "Bogotá")))
            AddressCard(GeoPoint(4.63412, -74.06558), PinAddress.Offline)
        }
    }
}

@Preview(name = "17.b · aviso y búsqueda · oscuro", widthDp = 360)
@Composable
private fun DeniedPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LocationDeniedBanner(canAskAgain = false, onAllow = {})
            AddressSearchField(
                PublishUiState(
                    content = PublishContent.Editing,
                    draft = PublicationDraft(category = Category.GASTRONOMY, step = PublishStep.LOCATION),
                    locationDenied = true,
                    addressQuery = "Calle 85",
                    addressSearch = AddressSearch.NotFound("Calle 85"),
                ),
                PublishCallbacks(),
            )
        }
    }
}
