package co.edu.uniquindio.exploracity.ui.screens.detail

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.sampleDetails
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.LevelChip
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.components.spokenRes
import co.edu.uniquindio.exploracity.ui.components.symbol
import co.edu.uniquindio.exploracity.ui.screens.map.PlacePin
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.HoursWords
import co.edu.uniquindio.exploracity.util.formatDistance
import co.edu.uniquindio.exploracity.util.formatOpeningHours
import co.edu.uniquindio.exploracity.util.hasMapsApiKey
import co.edu.uniquindio.exploracity.viewmodel.DetailContent
import co.edu.uniquindio.exploracity.viewmodel.DetailMessage
import co.edu.uniquindio.exploracity.viewmodel.PoiDetailUiState
import co.edu.uniquindio.exploracity.viewmodel.PoiDetailViewModel
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/** 13 · Detalle del POI, conectado a su ViewModel. */
@Composable
fun PoiDetailRoute(
    onBack: () -> Unit,
    onOpenComments: (String) -> Unit,
    onAddComment: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    viewModel: PoiDetailViewModel = viewModel(factory = PoiDetailViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    val context = LocalContext.current
    val shareChooser = stringResource(R.string.detail_share_chooser)
    val poiId = viewModel.poiId
    PoiDetailScreen(
        state = state,
        callbacks = DetailCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onShare = { details -> context.sharePlace(details, shareChooser) },
            onToggleVote = viewModel::onToggleVote,
            onOpenVisit = viewModel::onOpenVisit,
            onVisitDraftChange = viewModel::onVisitDraftChange,
            onConfirmVisit = viewModel::onConfirmVisit,
            onDismissVisit = viewModel::onDismissVisit,
            onOpenComments = { onOpenComments(poiId) },
            onAddComment = { onAddComment(poiId) },
            onOpenAuthor = onOpenAuthor,
            onOpenMap = { onOpenMap(poiId) },
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

class DetailCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShare: (PoiDetails) -> Unit = {},
    val onToggleVote: () -> Unit = {},
    val onOpenVisit: () -> Unit = {},
    val onVisitDraftChange: (VisitExperience) -> Unit = {},
    val onConfirmVisit: () -> Unit = {},
    val onDismissVisit: () -> Unit = {},
    val onOpenComments: () -> Unit = {},
    val onAddComment: () -> Unit = {},
    val onOpenAuthor: (String) -> Unit = {},
    val onOpenMap: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun PoiDetailScreen(state: PoiDetailUiState, callbacks: DetailCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    DetailMessageEffect(state.message, snackbarHostState, callbacks.onMessageShown)

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // Cuando la galería sale por arriba, la franja de «Volver» y «Compartir» toma fondo: si no, el texto pasa por
    // debajo de la barra de estado y de los botones (visto al 200 % en el S20+).
    val solidTop by remember(density, statusBarPx) {
        derivedStateOf { scrollState.value > with(density) { (GalleryHeight - TopBarHeight).toPx() } - statusBarPx }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when (val content = state.content) {
            DetailContent.Loading -> DetailSkeleton()
            is DetailContent.Loaded -> DetailLoaded(content.details, state.voting, callbacks, scrollState)
            DetailContent.Error -> Centered {
                EmptyState(
                    icon = R.drawable.ic_sync_problem,
                    title = stringResource(R.string.detail_error_title),
                    body = stringResource(R.string.detail_error_body),
                    tone = EmptyStateTone.WARNING,
                ) {
                    ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                }
            }
            DetailContent.NotFound -> Centered {
                EmptyState(
                    icon = R.drawable.ic_location_off,
                    title = stringResource(R.string.detail_not_found_title),
                    body = stringResource(R.string.detail_not_found_body),
                ) {
                    ExploraButton(stringResource(R.string.navigate_back), onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        // «Volver» y «Compartir» flotan sobre la galería y siguen a mano al desplazarse (13.a).
        TopButtons(
            onBack = callbacks.onBack,
            onShare = state.details?.let { details -> { callbacks.onShare(details) } },
            solid = solidTop,
        )
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 72.dp),
        )
    }

    state.details?.let { details ->
        VisitSheet(
            placeTitle = details.poi.title,
            sheet = state.visitSheet,
            onDraftChange = callbacks.onVisitDraftChange,
            onConfirm = callbacks.onConfirmVisit,
            onDismiss = callbacks.onDismissVisit,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 56.dp).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DetailMessageEffect(message: DetailMessage?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val voteFailed = stringResource(R.string.detail_vote_failed)
    val visitFailed = stringResource(R.string.visit_failed)
    val visitSaved = stringResource(R.string.visit_saved)
    val points = (message as? DetailMessage.VisitSaved)?.points ?: 0
    val visitSavedPoints = pluralStringResource(R.plurals.visit_saved_points, points, points)
    LaunchedEffect(message) {
        val text = when (message) {
            null -> return@LaunchedEffect
            DetailMessage.VoteFailed -> voteFailed
            DetailMessage.VisitFailed -> visitFailed
            is DetailMessage.VisitSaved -> if (message.points > 0) visitSavedPoints else visitSaved
        }
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
        currentOnShown()
    }
}

@Composable
private fun DetailLoaded(details: PoiDetails, voting: Boolean, callbacks: DetailCallbacks, scrollState: ScrollState) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scrollState)) {
            Gallery(details)
            DetailBody(details, voting, callbacks)
        }
        CommentsBar(details.poi.comments, callbacks.onOpenComments, callbacks.onAddComment)
    }
}

/** Galería (HorizontalPager): cada foto dice qué muestra y su posición («Patio interior del café. Foto 1 de 3»). */
@Composable
private fun Gallery(details: PoiDetails) {
    val photos = details.photos
    val pagerState = rememberPagerState { photos.size }
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
            val description = stringResource(R.string.detail_photo_description, photos[page].description, page + 1, photos.size)
            // Sin API todavía no hay imágenes: el contenedor neutro con la cámara ocupa su lugar.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .semantics {
                        contentDescription = description
                        role = Role.Image
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_photo_camera), null, tint = MaterialTheme.exploraColors.textPlaceholder, modifier = Modifier.size(40.dp))
            }
        }
        if (photos.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(photos.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.exploraColors.textSecondary.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopButtons(onBack: () -> Unit, onShare: (() -> Unit)?, solid: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(if (solid) scheme.surface else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack)
            if (onShare != null) RoundIconButton(R.drawable.ic_share, stringResource(R.string.detail_share), onShare)
        }
        if (solid) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
    }
}

/** Alto de la galería (13.a) y de la franja de botones sobre ella, sin contar la barra de estado. */
private val GalleryHeight = 200.dp
private val TopBarHeight = 64.dp

/** Botón redondo de 48 dp sobre surface al 92 %: legible encima de cualquier foto. */
@Composable
private fun RoundIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.exploraColors.textSecondary, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(details: PoiDetails, voting: Boolean, callbacks: DetailCallbacks) {
    val poi = details.poi
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                poi.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 30.sp),
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            StatusBadge(poi.status)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTag(poi.category, size = BadgeSize.REGULAR)
            val distance = formatDistance(poi.distanceMeters)
            Fact(R.drawable.ic_near_me, distance, stringResource(R.string.detail_distance_description, distance))
            poi.price?.let { price ->
                val label = stringResource(price.labelRes)
                Fact(R.drawable.ic_payments, price.symbol?.let { "$it · $label" } ?: label, stringResource(price.spokenRes))
            }
        }
        Text(details.description, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp), color = explora.textSecondary)
        InfoBox(details)
        MiniMap(details, callbacks.onOpenMap)
        AuthorCard(details, onClick = { callbacks.onOpenAuthor(details.author.id) })
        Actions(details, voting, callbacks)
    }
}

@Composable
private fun Fact(@DrawableRes icon: Int, text: String, spoken: String) {
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Horario y dirección sobre surfaceContainer. Sin horario exacto (paso 4) solo se dice si está abierto, si se sabe. */
@Composable
private fun InfoBox(details: PoiDetails) {
    val hoursText = details.hours?.let {
        formatOpeningHours(it, HoursWords(stringResource(R.string.hours_every_day), stringResource(R.string.hours_to), stringResource(R.string.list_and)))
    }
    val openText = when (details.poi.openNow) {
        true -> stringResource(R.string.detail_open_now)
        false -> stringResource(R.string.detail_closed_now)
        null -> null
    }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hoursText != null || openText != null) {
            InfoRow(R.drawable.ic_schedule) {
                Text(
                    buildAnnotatedString {
                        if (hoursText != null) append(hoursText)
                        if (hoursText != null && openText != null) append(" · ")
                        if (openText != null) withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append(openText) }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
            }
        }
        InfoRow(R.drawable.ic_location_on) {
            Text(details.address, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.exploraColors.textSecondary)
        }
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(18.dp.scaledWithFont()))
        content()
    }
}

/**
 * Mapa pequeño (modo lite) con el marcador de la categoría; tocarlo abre 8 centrado en el lugar. Una capa encima
 * recibe el toque: en modo lite, el mapa abriría la app de Google Maps.
 */
@Composable
private fun MiniMap(details: PoiDetails, onOpenMap: () -> Unit) {
    val context = LocalContext.current
    val poi = details.poi
    val label = stringResource(R.string.detail_view_on_map)
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (remember(context) { context.hasMapsApiKey() }) {
            val target = LatLng(poi.location.latitude, poi.location.longitude)
            val colors = poi.category.colors
            val border = MaterialTheme.exploraColors.mapClusterBorder
            val dark = LocalDarkTheme.current
            // El modo lite no aplica el esquema oscuro de Google: en oscuro se usa un estilo JSON propio con tonos
            // parecidos (res/raw/map_style_dark.json).
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                mergeDescendants = true,
                cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(target, 15f) },
                googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                properties = MapProperties(
                    mapStyleOptions = remember(context, dark) {
                        MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style)
                    },
                ),
                uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false, compassEnabled = false, myLocationButtonEnabled = false),
                mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
            ) {
                MarkerComposable(poi.id, state = rememberUpdatedMarkerState(position = target), anchor = Offset(0.5f, 1f)) {
                    PlacePin(poi.category, colors, selected = false, border = border)
                }
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = label, onClick = onOpenMap)
                .semantics { contentDescription = label },
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clearAndSetSemantics { },
        )
    }
}

/** «Publicado por Camilo R.» con su nivel; lleva al perfil público (31). */
@Composable
private fun AuthorCard(details: PoiDetails, onClick: () -> Unit) {
    val author = details.author
    val level = author.level
    val description = stringResource(R.string.detail_author_description, author.name, stringResource(level.labelRes))
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, MaterialTheme.exploraColors.shadow)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = level.colors
        Box(Modifier.size(40.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_person), null, tint = colors.content, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.detail_published_by, author.name),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            LevelChip(level)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** «Es importante» y «Visitado». Con fuente grande pasan a columna. */
@Composable
private fun Actions(details: PoiDetails, voting: Boolean, callbacks: DetailCallbacks) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteButton(details, voting, callbacks.onToggleVote, Modifier.fillMaxWidth())
            VisitedButton(details.visited, callbacks.onOpenVisit, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteButton(details, voting, callbacks.onToggleVote, Modifier.weight(1f))
            VisitedButton(details.visited, callbacks.onOpenVisit, Modifier.weight(1f))
        }
    }
}

/**
 * Voto «Es importante» (opción A de Daniel: un solo voto; el «Me interesa» del SAD es el mismo). Toggle: al
 * activarse se rellena, dice «Ya votaste» y el contador sube. Para el lector es una casilla con su estado.
 */
@Composable
private fun VoteButton(details: PoiDetails, voting: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val voted = details.voted
    val votes = details.poi.votes
    val container = if (voted) scheme.primary else scheme.primaryContainer
    val content = if (voted) scheme.onPrimary else MaterialTheme.exploraColors.onPrimaryContainerAccent
    val description = pluralStringResource(R.plurals.detail_vote_description, votes, votes)
    val state = stringResource(if (voted) R.string.detail_vote_state_on else R.string.detail_vote_state_off)
    ActionButton(
        icon = R.drawable.ic_priority_high,
        text = stringResource(if (voted) R.string.detail_voted else R.string.detail_vote, votes),
        container = container,
        content = content,
        // Todo en un solo nodo: con semantics suelto, la descripción quedaba en un hijo del nodo marcable y el
        // lector repetía «Es importante · 48» (visto en el volcado de accesibilidad del S20+).
        modifier = modifier
            .toggleable(value = voted, enabled = !voting, role = Role.Checkbox, onValueChange = { onToggle() })
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = state
                role = Role.Checkbox
                toggleableState = ToggleableState(voted)
                liveRegion = LiveRegionMode.Polite
                if (voting) disabled() else onClick { onToggle(); true }
            },
    )
}

/**
 * «Visitado»: sin marcar abre 14.b; ya marcado queda relleno (tertiaryContainer, el diseño no fija el color) y deja
 * de ser tocable: la visita no se deshace desde aquí.
 */
@Composable
private fun VisitedButton(visited: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.detail_visited)
    if (visited) {
        val state = stringResource(R.string.detail_visited_state)
        ActionButton(
            icon = R.drawable.ic_check,
            text = label,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            modifier = modifier.clearAndSetSemantics {
                contentDescription = label
                stateDescription = state
            },
        )
    } else {
        val action = stringResource(R.string.detail_mark_visited)
        ActionButton(
            icon = R.drawable.ic_flag,
            text = label,
            container = scheme.surface,
            content = MaterialTheme.exploraColors.textSecondary,
            outlined = true,
            modifier = modifier
                .clickable(role = Role.Button, onClick = onOpen)
                .clearAndSetSemantics {
                    contentDescription = action
                    role = Role.Button
                    onClick { onOpen(); true }
                },
        )
    }
}

@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier,
    outlined: Boolean = false,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .clip(shape)
            .then(modifier)
            .heightIn(min = 48.dp)
            .background(container, shape)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = content)
    }
}

/** Barra fija: «Ver 12 comentarios» y el botón de comentar; los dos llevan a 14, el segundo con el teclado listo. */
@Composable
private fun CommentsBar(comments: Int, onOpenComments: () -> Unit, onAddComment: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    Column(Modifier.fillMaxWidth().background(scheme.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(explora.divider))
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(scheme.surfaceContainer)
                    .clickable(role = Role.Button, onClick = onOpenComments)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_chat_bubble), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
                Text(
                    if (comments > 0) pluralStringResource(R.plurals.detail_comments, comments, comments) else stringResource(R.string.detail_no_comments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = explora.textPlaceholder,
                )
            }
            val add = stringResource(R.string.detail_add_comment)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable(role = Role.Button, onClick = onAddComment)
                    .semantics { contentDescription = add },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add_comment), null, tint = scheme.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Mientras carga: silueta de galería y datos con un solo anuncio (como 11). */
@Composable
private fun DetailSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.detail_loading)
    Column(
        Modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(200.dp), MaterialTheme.shapes.extraSmall)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(24.dp))
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.5f).height(16.dp))
            SkeletonLines(brush)
            SkeletonBlock(brush, Modifier.fillMaxWidth().height(72.dp), MaterialTheme.shapes.medium)
            SkeletonBlock(brush, Modifier.fillMaxWidth().height(104.dp), MaterialTheme.shapes.medium)
        }
    }
}

@Composable
private fun ColumnScope.SkeletonLines(brush: Brush) {
    SkeletonBlock(brush, Modifier.fillMaxWidth().height(14.dp))
    SkeletonBlock(brush, Modifier.fillMaxWidth(0.9f).height(14.dp))
    SkeletonBlock(brush, Modifier.fillMaxWidth(0.6f).height(14.dp))
}

/** Menú de compartir de Android con el nombre y la dirección (aún no hay enlaces profundos a la app). */
private fun Context.sharePlace(details: PoiDetails, chooserTitle: String) {
    val text = getString(R.string.detail_share_text, details.poi.title, details.address)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, chooserTitle))
}

private val previewDetails = sampleDetails(samplePois.first())

@Preview(name = "13.a · claro", widthDp = 360, heightDp = 1100)
@Composable
private fun DetailLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        PoiDetailScreen(PoiDetailUiState(content = DetailContent.Loaded(previewDetails)), DetailCallbacks())
    }
}

@Preview(name = "13.a · oscuro · votado y visitado", widthDp = 360, heightDp = 1100)
@Composable
private fun DetailDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        val details = previewDetails.copy(voted = true, visited = true, poi = previewDetails.poi.copy(votes = 49))
        PoiDetailScreen(PoiDetailUiState(content = DetailContent.Loaded(details)), DetailCallbacks())
    }
}

@Preview(name = "13 · cargando", widthDp = 360, heightDp = 800)
@Composable
private fun DetailLoadingPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { PoiDetailScreen(PoiDetailUiState(), DetailCallbacks()) }
}
