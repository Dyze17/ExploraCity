package co.edu.uniquindio.exploracity.ui.screens.feed

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.ui.components.ActiveFilterChip
import co.edu.uniquindio.exploracity.ui.components.CategoryChip
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSearchBar
import co.edu.uniquindio.exploracity.ui.components.FeedMode
import co.edu.uniquindio.exploracity.ui.components.FiltersBottomSheet
import co.edu.uniquindio.exploracity.ui.components.FiltersButton
import co.edu.uniquindio.exploracity.ui.components.ListMapToggle
import co.edu.uniquindio.exploracity.ui.components.POICard
import co.edu.uniquindio.exploracity.ui.components.POICardSkeleton
import co.edu.uniquindio.exploracity.ui.components.PublishFab
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraSpacing
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.LOCATION_PERMISSIONS
import co.edu.uniquindio.exploracity.util.formatDistance
import co.edu.uniquindio.exploracity.util.hasLocationPermission
import co.edu.uniquindio.exploracity.util.openAppSettings
import co.edu.uniquindio.exploracity.util.shouldShowLocationRationale
import co.edu.uniquindio.exploracity.viewmodel.FeedContent
import co.edu.uniquindio.exploracity.viewmodel.FeedMessage
import co.edu.uniquindio.exploracity.viewmodel.FeedUiState
import co.edu.uniquindio.exploracity.viewmodel.FeedViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** 7 · Feed en modo lista, conectado a su ViewModel. */
@Composable
fun FeedRoute(
    isModerator: Boolean,
    onOpenPoi: (String) -> Unit,
    onOpenMap: () -> Unit,
    onPublish: () -> Unit,
    onOpenModeration: () -> Unit,
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory(isModerator)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Quién pidió el permiso de ubicación: la hoja al aplicar «Cercanos» o el aviso «Permitir».
    var locationRequester by rememberSaveable { mutableStateOf<LocationRequester?>(null) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants.values.any { it }
        when (locationRequester) {
            LocationRequester.SHEET -> if (granted) viewModel.onApplyFilters() else viewModel.onLocationDenied()
            LocationRequester.SNACKBAR -> if (granted) viewModel.onLocationGranted()
            null -> Unit
        }
        locationRequester = null
    }
    FeedScreen(
        state = state,
        isModerator = isModerator,
        callbacks = FeedCallbacks(
            onQueryChange = viewModel::onQueryChange,
            onToggleCategory = viewModel::onToggleCategory,
            onSearchWholeCity = viewModel::onSearchWholeCity,
            onRemoveVerifiedOnly = viewModel::onRemoveVerifiedOnly,
            onClearFilters = viewModel::onClearFilters,
            onOpenFilters = viewModel::onOpenFilters,
            onDraftChange = viewModel::onDraftChange,
            onClearDraft = viewModel::onClearDraft,
            onApplyFilters = {
                // README 9: «Si no diste permiso, te lo pedimos al aplicar».
                if (state.filterSheet?.draft?.scope == LocationScope.NEARBY && !context.hasLocationPermission()) {
                    locationRequester = LocationRequester.SHEET
                    requestLocation.launch(LOCATION_PERMISSIONS)
                } else {
                    viewModel.onApplyFilters()
                }
            },
            onDismissFilters = viewModel::onDismissFilters,
            onAllowLocation = {
                when {
                    context.hasLocationPermission() -> viewModel.onLocationGranted()
                    activity?.shouldShowLocationRationale() == true -> {
                        locationRequester = LocationRequester.SNACKBAR
                        requestLocation.launch(LOCATION_PERMISSIONS)
                    }
                    // Negado para siempre: el sistema ya no muestra el diálogo, solo queda la ficha de la app.
                    else -> context.openAppSettings()
                }
            },
            onMessageShown = viewModel::onMessageShown,
            onRetry = viewModel::onRetry,
            onLoadMore = viewModel::onLoadMore,
            onOpenPoi = onOpenPoi,
            onOpenMap = onOpenMap,
            onPublish = onPublish,
            onOpenModeration = onOpenModeration,
        ),
    )
}

private enum class LocationRequester { SHEET, SNACKBAR }

class FeedCallbacks(
    val onQueryChange: (String) -> Unit = {},
    val onToggleCategory: (Category) -> Unit = {},
    val onSearchWholeCity: () -> Unit = {},
    val onRemoveVerifiedOnly: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onDraftChange: (FeedFilters) -> Unit = {},
    val onClearDraft: () -> Unit = {},
    val onApplyFilters: () -> Unit = {},
    val onDismissFilters: () -> Unit = {},
    val onAllowLocation: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpenPoi: (String) -> Unit = {},
    val onOpenMap: () -> Unit = {},
    val onPublish: () -> Unit = {},
    val onOpenModeration: () -> Unit = {},
)

@Composable
fun FeedScreen(state: FeedUiState, isModerator: Boolean, callbacks: FeedCallbacks, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val collapsing = remember { CollapsingHeaderState() }
        // Al cambiar los filtros los chips activos vuelven a la vista (README 9: «con los chips activos visibles»).
        LaunchedEffect(state.filters) { collapsing.expand() }
        Column(Modifier.fillMaxSize().nestedScroll(collapsing.connection)) {
            PinnedHeader(state.areaName, callbacks.onOpenMap)
            CollapsibleFilters(state, isModerator, callbacks, collapsing)
            when (val content = state.content) {
                FeedContent.Loading -> FeedSkeleton()
                is FeedContent.Loaded -> FeedList(content, state, isModerator, callbacks)
                is FeedContent.NoResults -> Scrollable {
                    EmptyState(
                        icon = R.drawable.ic_search_off,
                        title = stringResource(R.string.feed_no_results_title),
                        body = noResultsBody(content.query),
                    ) {
                        // Las dos salidas de 10.a son reversibles: amplían o quitan filtros, que se pueden volver a poner.
                        if (content.query.filters.scope == LocationScope.NEARBY) {
                            ExploraButton(
                                stringResource(R.string.feed_search_whole_city),
                                onClick = callbacks.onSearchWholeCity,
                                modifier = Modifier.fillMaxWidth(),
                                icon = R.drawable.ic_location_city,
                            )
                        }
                        ExploraButton(
                            stringResource(R.string.feed_clear_filters),
                            onClick = callbacks.onClearFilters,
                            modifier = Modifier.fillMaxWidth(),
                            style = ExploraButtonStyle.TEXT,
                        )
                    }
                }
                FeedContent.EmptyArea -> Scrollable {
                    EmptyState(
                        icon = R.drawable.ic_travel_explore,
                        title = stringResource(R.string.feed_empty_area_title, state.areaName),
                        body = stringResource(R.string.feed_empty_area_body),
                    ) {
                        ExploraButton(
                            stringResource(R.string.feed_publish_first),
                            onClick = callbacks.onPublish,
                            modifier = Modifier.fillMaxWidth(),
                            icon = R.drawable.ic_add_location_alt,
                        )
                    }
                }
                FeedContent.Error -> Scrollable {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.feed_error_title),
                        body = stringResource(R.string.feed_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) {
                        ExploraButton(
                            stringResource(R.string.action_retry),
                            onClick = callbacks.onRetry,
                            modifier = Modifier.fillMaxWidth(),
                            icon = R.drawable.ic_refresh,
                        )
                    }
                }
            }
        }
        // El aviso aparece debajo del FAB y lo empuja hacia arriba, como en un Scaffold.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (state.content is FeedContent.Loaded) {
                PublishFab(onClick = callbacks.onPublish, modifier = Modifier.padding(ExploraSpacing.ScreenMargin))
            }
            FeedSnackbar(state.message, callbacks)
        }
        FiltersBottomSheet(
            draft = state.filterSheet?.draft,
            count = state.filterSheet?.count,
            onDraftChange = callbacks.onDraftChange,
            onClear = callbacks.onClearDraft,
            onApply = callbacks.onApplyFilters,
            onDismissRequest = callbacks.onDismissFilters,
        )
    }
}

/** Avisos del feed. Sin permiso de ubicación: persiste hasta que la persona actúa («Permitir» o cerrar). */
@Composable
private fun FeedSnackbar(message: FeedMessage?, callbacks: FeedCallbacks) {
    val hostState = remember { SnackbarHostState() }
    val currentCallbacks by rememberUpdatedState(callbacks)
    val locationDenied = stringResource(R.string.feed_location_denied)
    val allow = stringResource(R.string.feed_location_allow)
    LaunchedEffect(message) {
        if (message == FeedMessage.LOCATION_DENIED) {
            val result = hostState.showSnackbar(locationDenied, actionLabel = allow, withDismissAction = true, duration = SnackbarDuration.Indefinite)
            currentCallbacks.onMessageShown()
            if (result == SnackbarResult.ActionPerformed) currentCallbacks.onAllowLocation()
        }
    }
    SnackbarHost(hostState)
}

@Composable
private fun Scrollable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}

/** Fija arriba: «Estás explorando / ciudad» y el conmutador Lista ⇄ Mapa, siempre visible (README 7). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinnedHeader(areaName: String, onOpenMap: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    FlowRow(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.feed_exploring),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                areaName,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        ListMapToggle(
            selected = FeedMode.LIST,
            onSelect = { if (it == FeedMode.MAP) onOpenMap() },
            expanded = largeFont,
        )
    }
}

/** Rol, buscador y chips: se esconden al bajar por la lista y vuelven al regresar arriba o al enfocar el buscador. */
@Composable
private fun CollapsibleFilters(state: FeedUiState, isModerator: Boolean, callbacks: FeedCallbacks, collapsing: CollapsingHeaderState) {
    Column(
        collapsing.modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isModerator) ModeratorChip()
        ExploraSearchBar(
            value = state.query,
            onValueChange = callbacks.onQueryChange,
            placeholder = stringResource(R.string.feed_search_placeholder, state.areaName),
            modifier = Modifier.onFocusChanged { if (it.isFocused) collapsing.expand() },
            trailing = { FiltersButton(activeCount = state.filters.activeCount, onClick = callbacks.onOpenFilters) },
        )
        // Primero los filtros que no son categorías y después las categorías, activas primero tras aplicar la hoja.
        val chipsScroll = rememberScrollState()
        LaunchedEffect(state.categoryOrder, state.filters.scope, state.filters.verifiedOnly) { chipsScroll.animateScrollTo(0) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(chipsScroll),
            horizontalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenChips),
        ) {
            if (state.filters.scope == LocationScope.NEARBY) {
                ActiveFilterChip(stringResource(R.string.feed_chip_nearby), R.drawable.ic_near_me, onRemove = callbacks.onSearchWholeCity)
            }
            if (state.filters.verifiedOnly) {
                ActiveFilterChip(stringResource(R.string.feed_chip_verified), R.drawable.ic_verified, onRemove = callbacks.onRemoveVerifiedOnly)
            }
            state.categoryOrder.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category in state.filters.categories,
                    onSelectedChange = { callbacks.onToggleCategory(category) },
                )
            }
        }
    }
}

/**
 * Cabecera que se pliega con el desplazamiento: al bajar se esconde antes de mover la lista; solo vuelve
 * cuando la lista regresa arriba. Se encoge (no se superpone), así nada queda tapado.
 */
@Stable
private class CollapsingHeaderState {
    private var offset by mutableFloatStateOf(0f)
    private var height = 0

    fun expand() {
        offset = 0f
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0) consume(available.y) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0) consume(available.y) else Offset.Zero
    }

    private fun consume(delta: Float): Offset {
        val next = (offset + delta).coerceIn(-height.toFloat(), 0f)
        val used = next - offset
        offset = next
        return Offset(0f, used)
    }

    val modifier: Modifier = Modifier
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            height = placeable.height
            val shift = offset.roundToInt()
            layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) { placeable.place(0, shift) }
        }
}

@Composable
private fun ModeratorChip() {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_shield_person),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(16.dp.scaledWithFont()),
        )
        Text(
            stringResource(R.string.feed_moderator),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun FeedList(content: FeedContent.Loaded, state: FeedUiState, isModerator: Boolean, callbacks: FeedCallbacks) {
    val moderation = state.moderation.takeIf { isModerator }
    val listState = rememberLazyListState()
    val loadMore by rememberUpdatedState(callbacks.onLoadMore)
    // Carga la página siguiente al acercarse al final, sin bloquear la lista.
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }.distinctUntilChanged().filter { it }.collect { loadMore() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // 96 dp abajo para que el FAB no tape la última tarjeta (README).
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (moderation != null && moderation.pending > 0) {
            item(key = "moderation") { ModerationQueueCard(moderation, callbacks.onOpenModeration) }
        }
        item(key = "header") { ListHeader(content.total, state.filters.scope, state.areaName) }
        items(content.items, key = { it.id }) { poi -> PoiItem(poi, callbacks.onOpenPoi) }
        if (content.loadingMore) {
            item(key = "loading-more") { LoadingMore() }
        }
    }
}

@Composable
private fun PoiItem(poi: Poi, onOpenPoi: (String) -> Unit) {
    POICard(
        title = poi.title,
        category = poi.category,
        status = poi.status,
        distance = formatDistance(poi.distanceMeters),
        votes = poi.votes,
        comments = poi.comments,
        onClick = { onOpenPoi(poi.id) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListHeader(total: Int, scope: LocationScope, areaName: String) {
    // «24 lugares cerca de ti» (7.a) solo con «Cercanos»; con toda la ciudad lo dice («26 lugares en Bogotá»).
    val title = when (scope) {
        LocationScope.NEARBY -> pluralStringResource(R.plurals.feed_places_nearby, total, total)
        LocationScope.CITY -> pluralStringResource(R.plurals.feed_places_in_area, total, total, areaName)
    }
    // Se desdobla en dos líneas si no cabe (fuente grande), sin partir «Más cercanos».
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.exploraColors.iconSecondary,
            modifier = Modifier.semantics { heading() },
        )
        // Orden actual de la lista (el diseño no define cambiarlo): se muestra como información, no como acción.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                painterResource(R.drawable.ic_swap_vert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp.scaledWithFont()),
            )
            Text(
                stringResource(R.string.feed_sort_nearest),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Acceso rápido a la cola (7.c): solo con rol Moderador. */
@Composable
private fun ModerationQueueCard(summary: ModerationSummary, onClick: () -> Unit) {
    val warning = MaterialTheme.exploraColors.warning
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(warning.container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_shield_person), null, tint = MaterialTheme.exploraColors.warningAccent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pluralStringResource(R.plurals.feed_moderation_pending, summary.pending, summary.pending),
                style = MaterialTheme.typography.titleSmall,
                color = warning.content,
            )
            Text(
                pluralStringResource(R.plurals.feed_moderation_oldest, summary.oldestWaitingDays, summary.oldestWaitingDays),
                style = MaterialTheme.typography.bodySmall,
                color = warning.content,
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = warning.content, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LoadingMore() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
        Text(
            stringResource(R.string.feed_loading_more),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 11: 4 skeletons con la silueta de POICard y un único anuncio. */
@Composable
private fun FeedSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.feed_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(brush, Modifier.padding(vertical = 15.dp).size(width = 150.dp, height = 14.dp), RoundedCornerShape(7.dp))
        POICardSkeleton(brush, titleWidth = 0.78f, lineWidths = 0.52f to 0.38f)
        POICardSkeleton(brush, titleWidth = 0.66f, lineWidths = 0.58f to 0.34f)
        POICardSkeleton(brush, titleWidth = 0.72f, lineWidths = 0.48f to 0.40f)
        POICardSkeleton(brush, titleWidth = 0.60f, lineWidths = 0.54f to 0.30f)
    }
}

/**
 * 10.a repite los criterios aplicados y propone la salida: «Buscaste «teatro al aire libre» en Naturaleza
 * y cerca de ti. Prueba quitando un filtro o ampliando a toda la ciudad.»
 */
@Composable
private fun noResultsBody(query: FeedQuery): String {
    val filters = query.filters
    // Las categorías se suman (cualquiera de ellas): «en Gastronomía o Cultura».
    val categories = filters.categories.sorted().map { stringResource(it.labelRes) }
    val criteria = buildList {
        if (categories.isNotEmpty()) add(stringResource(R.string.feed_criteria_categories, joinWords(categories, stringResource(R.string.list_or))))
        if (filters.scope == LocationScope.NEARBY) add(stringResource(R.string.feed_criteria_nearby))
        if (filters.verifiedOnly) add(stringResource(R.string.feed_criteria_verified))
    }
    val joinedCriteria = joinWords(criteria, stringResource(R.string.list_and))
    val searched = query.text.isNotBlank()
    val sentence = when {
        searched && criteria.isNotEmpty() -> stringResource(R.string.feed_no_results_query_criteria, query.text, joinedCriteria)
        searched -> stringResource(R.string.feed_no_results_query, query.text)
        else -> stringResource(R.string.feed_no_results_criteria, joinedCriteria)
    }
    val hint = when {
        filters.scope == LocationScope.NEARBY -> R.string.feed_no_results_hint_city
        searched && criteria.isNotEmpty() -> R.string.feed_no_results_hint_filters_words
        searched -> R.string.feed_no_results_hint_words
        else -> R.string.feed_no_results_hint_filters
    }
    return "$sentence ${stringResource(hint)}"
}

/** «a», «a y b», «a, b y c». */
private fun joinWords(words: List<String>, conjunction: String): String = when (words.size) {
    0 -> ""
    1 -> words.single()
    else -> words.dropLast(1).joinToString(", ") + " $conjunction " + words.last()
}

private val previewState = FeedUiState(
    areaName = "Bogotá",
    filters = FeedFilters(categories = setOf(Category.GASTRONOMY), scope = LocationScope.NEARBY),
    content = FeedContent.Loaded(samplePois.take(5), total = 24, canLoadMore = true, loadingMore = true),
    moderation = ModerationSummary(pending = 7, oldestWaitingDays = 3),
)

@Preview(name = "10.a · Sin resultados · claro", widthDp = 360, heightDp = 800)
@Composable
private fun FeedNoResultsPreview() {
    val query = FeedQuery(FeedFilters(categories = setOf(Category.NATURE), scope = LocationScope.NEARBY), "teatro al aire libre")
    ExploraCityTheme(ThemeMode.LIGHT) {
        FeedScreen(
            previewState.copy(query = query.text, filters = query.filters, content = FeedContent.NoResults(query)),
            isModerator = false,
            callbacks = FeedCallbacks(),
        )
    }
}

@Preview(name = "7 · Feed · claro", widthDp = 360, heightDp = 800)
@Composable
private fun FeedLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { FeedScreen(previewState, isModerator = false, callbacks = FeedCallbacks()) }
}

@Preview(name = "7.c · Moderador · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun FeedModeratorDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { FeedScreen(previewState, isModerator = true, callbacks = FeedCallbacks()) }
}

@Preview(name = "11 · Cargando", widthDp = 360, heightDp = 800)
@Composable
private fun FeedLoadingPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { FeedScreen(previewState.copy(content = FeedContent.Loading), isModerator = false, callbacks = FeedCallbacks()) }
}

@Preview(name = "12.b · Error · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun FeedErrorPreview() {
    ExploraCityTheme(ThemeMode.DARK) { FeedScreen(previewState.copy(content = FeedContent.Error), isModerator = false, callbacks = FeedCallbacks()) }
}
