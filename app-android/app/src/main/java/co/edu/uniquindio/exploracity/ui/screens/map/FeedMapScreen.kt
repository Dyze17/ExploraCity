package co.edu.uniquindio.exploracity.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.ui.components.ActiveFilterChip
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryChip
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSearchBar
import co.edu.uniquindio.exploracity.ui.components.FeedMode
import co.edu.uniquindio.exploracity.ui.components.FiltersBottomSheet
import co.edu.uniquindio.exploracity.ui.components.FiltersButton
import co.edu.uniquindio.exploracity.ui.components.ListMapToggle
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.components.spokenRes
import co.edu.uniquindio.exploracity.ui.components.symbol
import co.edu.uniquindio.exploracity.ui.screens.feed.FeedMessageEffect
import co.edu.uniquindio.exploracity.ui.screens.feed.FilterCallbacks
import co.edu.uniquindio.exploracity.ui.screens.feed.rememberFilterCallbacks
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.ExploraSpacing
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.LocationPurpose
import co.edu.uniquindio.exploracity.util.formatDistance
import co.edu.uniquindio.exploracity.util.hasMapsApiKey
import co.edu.uniquindio.exploracity.util.rememberLocationPermissionRequester
import co.edu.uniquindio.exploracity.viewmodel.FeedUiState
import co.edu.uniquindio.exploracity.viewmodel.FeedViewModel
import co.edu.uniquindio.exploracity.viewmodel.MapUiState
import co.edu.uniquindio.exploracity.viewmodel.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

/** Zoom al abrir el mapa y al centrarlo en la persona. */
private const val CITY_ZOOM = 13f
private const val STREET_ZOOM = 15f

/**
 * 8 · Feed en modo mapa. Comparte con la lista (7) la búsqueda, los filtros y la hoja 9 ([feedViewModel],
 * del grafo de Explorar); [mapViewModel] lleva el área visible, los marcadores y el lugar seleccionado.
 */
@Composable
fun FeedMapRoute(
    feedViewModel: FeedViewModel,
    onOpenList: () -> Unit,
    onOpenPoi: (String) -> Unit,
    mapViewModel: MapViewModel = viewModel(factory = MapViewModel.factory),
) {
    val feedState by feedViewModel.state.collectAsStateWithLifecycle()
    val mapState by mapViewModel.state.collectAsStateWithLifecycle()
    val filters = rememberFilterCallbacks(feedViewModel)
    val criteria = FeedQuery(feedState.filters, feedState.query.trim())
    LaunchedEffect(criteria) { mapViewModel.onCriteriaChange(criteria) }

    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapState.areaCenter.toLatLng(), CITY_ZOOM)
    }
    var locationDenied by rememberSaveable { mutableStateOf(false) }
    val location = rememberLocationPermissionRequester { purpose, granted ->
        if (purpose != LocationPurpose.CENTER_MAP) return@rememberLocationPermissionRequester
        if (granted) {
            scope.launch {
                val here = mapViewModel.locate()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(here.toLatLng(), STREET_ZOOM))
            }
        } else {
            locationDenied = true
        }
    }
    // Con el permiso ya concedido, el mapa abre donde está la persona (una vez; luego manda la cámara guardada),
    // salvo que venga del detalle (13): entonces se centra en ese lugar.
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!centeredOnUser && location.isGranted && !mapViewModel.hasFocus) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(mapViewModel.locate().toLatLng(), CITY_ZOOM)
        }
        centeredOnUser = true
    }
    LaunchedEffect(mapState.focusTarget) {
        val target = mapState.focusTarget ?: return@LaunchedEffect
        cameraPositionState.position = CameraPosition.fromLatLngZoom(target.toLatLng(), STREET_ZOOM)
        mapViewModel.onFocusShown()
    }

    FeedMapScreen(
        feedState = feedState,
        mapState = mapState,
        cameraPositionState = cameraPositionState,
        callbacks = MapCallbacks(
            filters = filters,
            onAreaChange = mapViewModel::onAreaChange,
            onSelect = mapViewModel::onSelect,
            onRetry = mapViewModel::onRetry,
            onMyLocation = { location.request(LocationPurpose.CENTER_MAP) },
            onAllowMyLocation = { location.allow(LocationPurpose.CENTER_MAP) },
            onOpenList = onOpenList,
            onOpenPoi = onOpenPoi,
        ),
        locationDenied = locationDenied,
        onLocationDeniedShown = { locationDenied = false },
    )
}

class MapCallbacks(
    val filters: FilterCallbacks = FilterCallbacks(),
    val onAreaChange: (GeoBounds) -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMyLocation: () -> Unit = {},
    val onAllowMyLocation: () -> Unit = {},
    val onOpenList: () -> Unit = {},
    val onOpenPoi: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedMapScreen(
    feedState: FeedUiState,
    mapState: MapUiState,
    cameraPositionState: CameraPositionState,
    callbacks: MapCallbacks,
    modifier: Modifier = Modifier,
    locationDenied: Boolean = false,
    onLocationDeniedShown: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val snackbarHostState = scaffoldState.snackbarHostState
    FeedMessageEffect(feedState.message, snackbarHostState, callbacks.filters)
    MapLocationDeniedEffect(locationDenied, snackbarHostState, callbacks.onAllowMyLocation, onLocationDeniedShown)

    // La hoja parcial muestra la tarjeta entera si cabe en el 45 % del alto; si no (fuente grande), muestra ese 45 %
    // y se arrastra hacia arriba hasta ver todo, como máximo el 90 % (README: «hojas crecen hasta el 90 %»).
    var cardHeightPx by remember { mutableIntStateOf(0) }
    var handleHeightPx by remember { mutableIntStateOf(0) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    val peekHeight = with(density) { minOf(handleHeightPx + cardHeightPx, (containerHeightPx * 0.45f).toInt()).toDp() }

    // Tocar un lugar vuelve a mostrar la tarjeta si se había deslizado fuera.
    val select: (String) -> Unit = { id ->
        callbacks.onSelect(id)
        if (sheetState.targetValue == SheetValue.Hidden) scope.launch { sheetState.partialExpand() }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        modifier = modifier,
        sheetPeekHeight = peekHeight,
        sheetShape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerZero, bottomEnd = CornerZero),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = ExploraElevation.SheetOrDialog,
        // El asa de M3 (con el color y la medida del lienzo) da al lector las acciones de expandir y contraer: con
        // fuente grande la hoja parcial no muestra todo. Aquí no hay foco inicial que proteger (no es modal).
        sheetDragHandle = {
            Box(Modifier.onSizeChanged { handleHeightPx = it.height }) {
                BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp, color = MaterialTheme.exploraColors.sheetHandle)
            }
        },
        snackbarHost = { SnackbarHost(it) },
        sheetContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = with(density) { (containerHeightPx * 0.9f).toDp() }.coerceAtLeast(160.dp))
                    .onSizeChanged { cardHeightPx = it.height },
            ) {
                PlaceSheet(feedState, mapState, callbacks)
            }
        },
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { containerHeightPx = it.height }) {
            val sheetVisible = sheetState.targetValue != SheetValue.Hidden
            // Con la tarjeta oculta, el logo de Google y «Mi ubicación» quedan por encima de la barra de gestos.
            val navigationBarPx = WindowInsets.navigationBars.getBottom(density)
            MapLayer(
                mapState = mapState,
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(
                    top = with(density) { headerHeightPx.toDp() },
                    bottom = if (sheetVisible) peekHeight else with(density) { navigationBarPx.toDp() },
                ),
                onAreaChange = callbacks.onAreaChange,
                onSelect = select,
                onGroupClick = { group ->
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(group.bounds.toLatLngBounds(), with(density) { 64.dp.roundToPx() }))
                    }
                },
            )
            MapHeader(
                feedState = feedState,
                mapState = mapState,
                callbacks = callbacks,
                modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
            )
            // «Mi ubicación» sigue a la hoja: siempre por encima de la tarjeta (README 8).
            MyLocationButton(
                onClick = callbacks.onMyLocation,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val sheetTop = runCatching { sheetState.requireOffset() }.getOrDefault(containerHeightPx.toFloat())
                            .coerceAtMost((containerHeightPx - navigationBarPx).toFloat())
                        val margin = ExploraSpacing.ScreenMargin.roundToPx()
                        IntOffset(-margin, (sheetTop - 48.dp.toPx() - margin).roundToInt())
                    },
            )
        }
    }

    FiltersBottomSheet(
        draft = feedState.filterSheet?.draft,
        count = feedState.filterSheet?.count,
        onDraftChange = callbacks.filters.onDraftChange,
        onClear = callbacks.filters.onClearDraft,
        onApply = callbacks.filters.onApplyFilters,
        onDismissRequest = callbacks.filters.onDismissFilters,
    )
}

private val CornerZero = CornerSize(0.dp)

@Composable
private fun MapLocationDeniedEffect(show: Boolean, hostState: SnackbarHostState, onAllow: () -> Unit, onShown: () -> Unit) {
    val currentOnAllow by rememberUpdatedState(onAllow)
    val currentOnShown by rememberUpdatedState(onShown)
    val text = stringResource(R.string.map_location_denied)
    val allow = stringResource(R.string.feed_location_allow)
    LaunchedEffect(show) {
        if (show) {
            val result = hostState.showSnackbar(text, actionLabel = allow, withDismissAction = true, duration = SnackbarDuration.Indefinite)
            currentOnShown()
            if (result == SnackbarResult.ActionPerformed) currentOnAllow()
        }
    }
}

/**
 * El mapa con sus marcadores. Al detenerse la cámara informa del área visible y reagrupa los marcadores por
 * cercanía en pantalla; mientras se mueve no recalcula nada.
 */
@Composable
private fun MapLayer(
    mapState: MapUiState,
    cameraPositionState: CameraPositionState,
    contentPadding: PaddingValues,
    onAreaChange: (GeoBounds) -> Unit,
    onSelect: (String) -> Unit,
    onGroupClick: (MapMarker.Group) -> Unit,
) {
    val context = LocalContext.current
    if (!remember(context) { context.hasMapsApiKey() }) {
        MissingMapsKey()
        return
    }
    val density = LocalDensity.current
    val dark = LocalDarkTheme.current
    var idleTick by remember { mutableIntStateOf(0) }
    val currentOnAreaChange by rememberUpdatedState(onAreaChange)
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.projection }
            .filter { (moving, projection) -> !moving && projection != null }
            .collect { (_, projection) ->
                currentOnAreaChange(projection!!.visibleRegion.latLngBounds.toGeoBounds())
                idleTick++
            }
    }
    var markers by remember { mutableStateOf<List<MapMarker>>(emptyList()) }
    LaunchedEffect(mapState.pois, mapState.selectedId, idleTick) {
        val projection = cameraPositionState.projection ?: return@LaunchedEffect
        val radius = with(density) { 48.dp.toPx() }
        markers = groupMarkers(
            pois = mapState.pois,
            screenPoint = { poi -> projection.toScreenLocation(poi.location.toLatLng()).let { ScreenPoint(it.x.toFloat(), it.y.toFloat()) } },
            radiusPx = radius,
            selectedId = mapState.selectedId,
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentDescription = stringResource(R.string.feed_mode_map),
        // Sin los iconos de negocios y lugares de Google: se confundían con los marcadores (el lienzo 8.a es limpio).
        properties = MapProperties(
            minZoomPreference = 10f,
            mapStyleOptions = remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style) },
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            indoorLevelPickerEnabled = false,
        ),
        contentPadding = contentPadding,
        mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
    ) {
        markers.forEach { marker ->
            when (marker) {
                // maps-compose solo fija la descripción al crear el marcador: al cambiar la selección se recrea
                // para que el lector anuncie «seleccionado» en el correcto.
                is MapMarker.Place -> {
                    val selected = marker.poi.id == mapState.selectedId
                    key(marker.poi.id, selected) { PlaceMarker(marker.poi, selected, dark, onSelect) }
                }
                is MapMarker.Group -> key(marker.pois.first().id, marker.pois.size) { GroupMarker(marker, dark, onGroupClick) }
            }
        }
        mapState.userLocation?.let { here ->
            val description = stringResource(R.string.map_you_are_here)
            MarkerComposable(
                dark,
                state = rememberUpdatedMarkerState(position = here.toLatLng()),
                contentDescription = description,
                anchor = Offset(0.5f, 0.5f),
                zIndex = 3f,
                onClick = { true },
            ) { UserPin() }
        }
    }
}

/** Solo en compilaciones de desarrollo sin MAPS_API_KEY: explica cómo configurarla en vez de dejar el mapa en blanco. */
@Composable
private fun MissingMapsKey() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.map_missing_key),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.exploraColors.textSecondary,
        )
    }
}

@Composable
private fun PlaceMarker(poi: Poi, selected: Boolean, dark: Boolean, onSelect: (String) -> Unit) {
    val colors = poi.category.colors
    val border = MaterialTheme.exploraColors.mapClusterBorder
    val description = markerDescription(poi).let { if (selected) stringResource(R.string.map_marker_selected, it) else it }
    MarkerComposable(
        poi.id,
        selected,
        dark,
        state = rememberUpdatedMarkerState(position = poi.location.toLatLng()),
        contentDescription = description,
        anchor = Offset(0.5f, 1f),
        zIndex = if (selected) 2f else 1f,
        onClick = {
            onSelect(poi.id)
            true
        },
    ) { PlacePin(poi.category, colors, selected, border) }
}

@Composable
private fun GroupMarker(group: MapMarker.Group, dark: Boolean, onClick: (MapMarker.Group) -> Unit) {
    val count = group.pois.size
    MarkerComposable(
        count,
        dark,
        state = rememberUpdatedMarkerState(position = group.center.toLatLng()),
        contentDescription = pluralStringResource(R.plurals.map_group_description, count, count),
        anchor = Offset(0.5f, 0.5f),
        onClick = {
            onClick(group)
            true
        },
    ) { GroupPin(count) }
}

@Composable
private fun markerDescription(poi: Poi): String = stringResource(
    R.string.map_marker_description,
    poi.title,
    stringResource(poi.category.labelRes),
    stringResource(poi.status.labelRes).lowercase(Locale.forLanguageTag("es")),
    formatDistance(poi.distanceMeters),
)

/** Cabecera flotante (8.a): buscador, «N lugares en el área», conmutador y chips de los filtros activos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapHeader(feedState: FeedUiState, mapState: MapUiState, callbacks: MapCallbacks, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shadow = MaterialTheme.exploraColors.shadow
    val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(modifier.fillMaxWidth()) {
        // Franja bajo la barra de estado para que la hora se lea sobre el mapa.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(scheme.surface.copy(alpha = 0.85f)))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExploraSearchBar(
                value = feedState.query,
                onValueChange = callbacks.filters.onQueryChange,
                placeholder = stringResource(R.string.map_search_placeholder),
                modifier = Modifier.exploraShadow(ExploraElevation.Fab, MaterialTheme.shapes.extraLarge, shadow),
                containerColor = scheme.surface,
                trailing = { FiltersButton(activeCount = feedState.filters.activeCount, onClick = callbacks.filters.onOpenFilters) },
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AreaPill(mapState)
                ListMapToggle(
                    selected = FeedMode.MAP,
                    onSelect = { if (it == FeedMode.LIST) callbacks.onOpenList() },
                    modifier = Modifier.exploraShadow(ExploraElevation.Card, CircleShape, shadow),
                    expanded = largeFont,
                    containerColor = scheme.surface,
                )
            }
            ActiveFilters(feedState, callbacks.filters)
            if (mapState.error) MapErrorBanner(callbacks.onRetry)
        }
    }
}

/** «186 lugares en el área» o, mientras busca, «Buscando lugares…» con un solo anuncio para el lector (8.b). */
@Composable
private fun AreaPill(mapState: MapUiState) {
    val searching = mapState.loading || mapState.totalInArea == null
    val searchingDescription = stringResource(R.string.map_searching_description)
    val text = if (searching) stringResource(R.string.map_searching) else {
        val total = mapState.totalInArea
        pluralStringResource(R.plurals.map_places_in_area, total, total)
    }
    Row(
        Modifier
            .exploraShadow(ExploraElevation.Card, CircleShape, MaterialTheme.exploraColors.shadow)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = if (searching) searchingDescription else text
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            CircularProgressIndicator(Modifier.size(14.dp.scaledWithFont()), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = MaterialTheme.exploraColors.textSecondary)
    }
}

/** En el mapa solo se muestran los filtros activos (README 9: «al aplicar → 8 con los chips activos visibles»). */
@Composable
private fun ActiveFilters(feedState: FeedUiState, filters: FilterCallbacks) {
    val active = feedState.filters
    if (active.isDefault) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenChips),
    ) {
        if (active.scope == LocationScope.NEARBY) {
            ActiveFilterChip(stringResource(R.string.feed_chip_nearby), R.drawable.ic_near_me, onRemove = filters.onSearchWholeCity)
        }
        if (active.verifiedOnly) {
            ActiveFilterChip(stringResource(R.string.feed_chip_verified), R.drawable.ic_verified, onRemove = filters.onRemoveVerifiedOnly)
        }
        feedState.categoryOrder.filter { it in active.categories }.forEach { category ->
            CategoryChip(category = category, selected = true, onSelectedChange = { filters.onToggleCategory(category) })
        }
    }
}

/** Falló la búsqueda del área: aviso recuperable; el mapa y los marcadores anteriores siguen usables. */
@Composable
private fun MapErrorBanner(onRetry: () -> Unit) {
    val warning = MaterialTheme.exploraColors.warning
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(warning.container)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_sync_problem), null, tint = warning.content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(stringResource(R.string.map_error), style = MaterialTheme.typography.bodySmall, color = warning.content, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.action_retry),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                color = MaterialTheme.exploraColors.warningAccent,
            )
        }
    }
}

@Composable
private fun MyLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val description = stringResource(R.string.map_my_location)
    Box(
        modifier
            .size(48.dp)
            .exploraShadow(ExploraElevation.Fab, shape, MaterialTheme.exploraColors.shadow)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_my_location), null, tint = MaterialTheme.exploraColors.textSecondary, modifier = Modifier.size(24.dp))
    }
}

/** Contenido de la hoja inferior: el lugar seleccionado, su silueta mientras busca o el aviso de área vacía. */
@Composable
private fun PlaceSheet(feedState: FeedUiState, mapState: MapUiState, callbacks: MapCallbacks) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val selected = mapState.selected
        when {
            selected != null -> PlaceSummary(selected, onOpen = { callbacks.onOpenPoi(selected.id) })
            mapState.loading -> PlaceSkeleton()
            !mapState.error -> EmptyArea(hasCriteria = !feedState.filters.isDefault || feedState.query.isNotBlank(), onClear = callbacks.filters.onClearFilters)
        }
    }
}

/** Tarjeta del lugar (8.a). Con fuente grande la foto pasa arriba y los datos se apilan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceSummary(poi: Poi, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val info = @Composable { infoModifier: Modifier ->
                Column(infoModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            poi.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 17.sp, lineHeight = 22.sp),
                            color = scheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        StatusBadge(poi.status, size = BadgeSize.SMALL)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryTag(poi.category)
                        PlaceFacts(poi)
                    }
                    poi.summary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = MaterialTheme.exploraColors.iconSecondary)
                    }
                }
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoPlaceholder(Modifier.fillMaxWidth().height(128.dp))
                    info(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoPlaceholder(Modifier.size(80.dp))
                    info(Modifier.weight(1f))
                }
            }
        }
        ExploraButton(stringResource(R.string.map_open_place), onClick = onOpen, modifier = Modifier.fillMaxWidth())
    }
}

/** «$ · a 1,2 km · abierto ahora»; el lector oye «hasta 25.000 pesos, a 1,2 km, abierto ahora». */
@Composable
private fun PlaceFacts(poi: Poi) {
    val price = poi.price
    val priceVisible = price?.let { it.symbol ?: stringResource(it.labelRes) }
    val priceSpoken = price?.let { stringResource(it.spokenRes) }
    val distance = stringResource(R.string.map_distance, formatDistance(poi.distanceMeters))
    val open = when (poi.openNow) {
        true -> stringResource(R.string.map_open_now)
        false -> stringResource(R.string.map_closed_now)
        null -> null
    }
    val visible = listOfNotNull(priceVisible, distance, open).joinToString(" · ")
    val spoken = listOfNotNull(priceSpoken, distance, open).joinToString(", ")
    Text(
        visible,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

/** Silueta de la tarjeta mientras busca (8.b). Sin semántica: el anuncio lo hace la píldora del área. */
@Composable
private fun PlaceSkeleton() {
    val brush = rememberShimmerBrush()
    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(brush, Modifier.size(80.dp), MaterialTheme.shapes.medium)
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.75f).height(16.dp))
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.5f).height(12.dp))
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.62f).height(12.dp))
        }
    }
}

/** Área sin lugares: no la define el diseño; se explica y, si hay criterios, se ofrece quitarlos (como 10.a). */
@Composable
private fun EmptyArea(hasCriteria: Boolean, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (hasCriteria) R.string.map_empty_area_criteria else R.string.map_empty_area),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (hasCriteria) {
            ExploraButton(stringResource(R.string.feed_clear_filters), onClick = onClear, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private fun LatLngBounds.toGeoBounds() = GeoBounds(
    southwest = GeoPoint(southwest.latitude, southwest.longitude),
    northeast = GeoPoint(northeast.latitude, northeast.longitude),
)

private fun GeoBounds.toLatLngBounds() = LatLngBounds(southwest.toLatLng(), northeast.toLatLng())

private val previewMap = MapUiState(
    areaCenter = samplePois.first().location,
    pois = samplePois,
    totalInArea = 186,
    loading = false,
    selectedId = samplePois.first().id,
)

@Preview(name = "8.a · tarjeta · claro", widthDp = 360)
@Composable
private fun PlaceSheetLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            PlaceSheet(FeedUiState(areaName = "Bogotá"), previewMap, MapCallbacks())
        }
    }
}

@Preview(name = "8.b · tarjeta cargando · oscuro", widthDp = 360)
@Composable
private fun PlaceSheetLoadingPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            PlaceSheet(FeedUiState(areaName = "Bogotá"), previewMap.copy(pois = emptyList(), selectedId = null, loading = true), MapCallbacks())
        }
    }
}

@Preview(name = "8.a · cabecera · claro", widthDp = 360, backgroundColor = 0xFFE6E0D4, showBackground = true)
@Composable
private fun MapHeaderPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        MapHeader(FeedUiState(areaName = "Bogotá"), previewMap, MapCallbacks())
    }
}

@Preview(name = "8 · cabecera con error · fuente 200 %", widthDp = 360, fontScale = 2f, backgroundColor = 0xFF22201C, showBackground = true)
@Composable
private fun MapHeaderLargeFontPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        MapHeader(FeedUiState(areaName = "Bogotá"), previewMap.copy(error = true), MapCallbacks())
    }
}
