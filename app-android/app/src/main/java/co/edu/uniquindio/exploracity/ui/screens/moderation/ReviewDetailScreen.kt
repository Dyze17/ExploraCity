package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.InitialsAvatar
import co.edu.uniquindio.exploracity.ui.components.LevelChip
import co.edu.uniquindio.exploracity.ui.components.NoNavigationBarScrim
import co.edu.uniquindio.exploracity.ui.components.PhotoViewer
import co.edu.uniquindio.exploracity.ui.components.SheetHandle
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.dashedBorder
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.screens.access.AccessOfflineNotice
import co.edu.uniquindio.exploracity.ui.screens.map.PlacePin
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ScrimAlpha
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.HoursWords
import co.edu.uniquindio.exploracity.util.formatOpeningHours
import co.edu.uniquindio.exploracity.util.hasMapsApiKey
import co.edu.uniquindio.exploracity.viewmodel.ReviewContent
import co.edu.uniquindio.exploracity.viewmodel.ReviewDetailUiState
import co.edu.uniquindio.exploracity.viewmodel.ReviewDetailViewModel
import co.edu.uniquindio.exploracity.viewmodel.ReviewDone
import co.edu.uniquindio.exploracity.viewmodel.VerifySheet
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
import java.time.Instant
import java.util.Locale

/**
 * 33 y 34 · Revisión de una pendiente, conectada a su ViewModel. Al verificar abre la siguiente ([onNext], con cuántas
 * quedan) o vuelve a la cola vacía ([onQueueEmpty]). [verifiedNotice] es el aviso con que se llega desde la anterior.
 */
@Composable
fun ReviewDetailRoute(
    onBack: () -> Unit,
    onCompare: () -> Unit,
    onReject: () -> Unit,
    onNext: (id: String, remaining: Int) -> Unit,
    onQueueEmpty: () -> Unit,
    verifiedNotice: Int? = null,
    onNoticeShown: () -> Unit = {},
    viewModel: ReviewDetailViewModel = viewModel(factory = ReviewDetailViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnQueueEmpty by rememberUpdatedState(onQueueEmpty)
    LaunchedEffect(state.done) {
        when (val done = state.done) {
            is ReviewDone.Next -> currentOnNext(done.id, done.remaining)
            ReviewDone.QueueEmpty -> currentOnQueueEmpty()
            null -> return@LaunchedEffect
        }
        viewModel.onDoneHandled()
    }
    ReviewDetailScreen(
        state = state,
        callbacks = ReviewDetailCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onCompare = {
                viewModel.onCompared()
                onCompare()
            },
            onReject = onReject,
            onVerify = viewModel::onVerifyClick,
            onNoteChange = viewModel::onNoteChange,
            onConfirmVerify = viewModel::onConfirmVerify,
            onDismissVerify = viewModel::onDismissVerify,
            onRetryVerify = viewModel::onRetryVerify,
            onVerifyFailureShown = viewModel::onVerifyFailureShown,
            onNoticeShown = onNoticeShown,
        ),
        verifiedNotice = verifiedNotice,
    )
}

class ReviewDetailCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCompare: () -> Unit = {},
    val onReject: () -> Unit = {},
    val onVerify: () -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onConfirmVerify: () -> Unit = {},
    val onDismissVerify: () -> Unit = {},
    val onRetryVerify: () -> Unit = {},
    val onVerifyFailureShown: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
)

/**
 * 33.a/33.b y la variante con aviso de duplicado · Todo lo necesario para decidir en una pantalla: el aviso de
 * duplicado (primero para el lector), las fotos (a pantalla completa con zoom), el texto, horario, precio, dirección y
 * coordenadas, el mapa, el reporte si lo hay y el historial del autor. Abajo, fija: «Rechazar» a la izquierda y
 * «Verificar». Sin conexión se lee, pero no se decide.
 */
@Composable
fun ReviewDetailScreen(
    state: ReviewDetailUiState,
    callbacks: ReviewDetailCallbacks,
    modifier: Modifier = Modifier,
    verifiedNotice: Int? = null,
    now: Instant? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    NoticeEffect(verifiedNotice, snackbarHostState, callbacks.onNoticeShown)
    VerifyFailedEffect(state.verifyFailed, snackbarHostState, callbacks)
    val clock by rememberNow()
    val current = now ?: clock
    val item = state.item
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            val subtitle = if (item != null && state.position != null) {
                stringResource(R.string.review_position, state.position, state.total, reviewWaitingText(item, current))
            } else {
                null
            }
            ExploraTopAppBar(title = stringResource(R.string.review_title), subtitle = subtitle, onBack = callbacks.onBack)
            if (state.offline && item != null) AccessOfflineNotice(stringResource(R.string.review_offline))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.content) {
                    ReviewContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is ReviewContent.Loaded -> if (item != null) Review(item, current, callbacks)
                    ReviewContent.Gone -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_done_all,
                            title = stringResource(R.string.review_gone_title),
                            body = stringResource(R.string.review_gone_body),
                        ) {
                            ExploraButton(stringResource(R.string.review_back_to_queue), onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    ReviewContent.Offline -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.review_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                    ReviewContent.Error -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.review_error_title),
                            body = stringResource(R.string.reset_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                }
            }
            if (item != null) ActionBar(state, callbacks)
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp).windowInsetsPadding(WindowInsets.navigationBars))
    }
    if (item != null) VerifySheetHost(item, state, callbacks)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Review(item: ReviewItem, now: Instant, callbacks: ReviewDetailCallbacks) {
    var viewer by rememberSaveable { mutableStateOf<Int?>(null) }
    var bigMap by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // El aviso de duplicado va primero: el lector lo oye antes que el resto.
        item.duplicate?.let { DuplicateBanner(item, callbacks.onCompare) }
        Gallery(item, onOpen = { viewer = it })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryTag(item.category, size = BadgeSize.REGULAR)
                StatusBadge(PublicationStatus.PENDING)
                OriginTag(item.categoryOrigin)
            }
        }
        Text(
            item.description,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.exploraColors.textSecondary,
        )
        InfoCard(item)
        item.reportReason?.let { ReportCard(it) }
        ReviewMap(item, onOpen = { bigMap = true })
        AuthorCard(item)
    }
    viewer?.let { index -> PhotoViewer(item.photos, index, onDismiss = { viewer = null }) }
    if (bigMap) MapDialog(item, onDismiss = { bigMap = false })
}

/** «Posible duplicado · Se parece a «…», a 23 m. Quien la publicó dejó una nota.» y «Comparar lugares» (33). */
@Composable
private fun DuplicateBanner(item: ReviewItem, onCompare: () -> Unit) {
    val duplicate = item.duplicate ?: return
    val flag = MaterialTheme.exploraColors.duplicateFlag
    val first = duplicate.candidates.firstOrNull()
    val body = buildList {
        when {
            duplicate.candidates.size == 1 && first != null -> add(stringResource(R.string.review_duplicate_one, first.poi.title, first.distanceMeters))
            duplicate.candidates.size > 1 -> add(pluralStringResource(R.plurals.review_duplicate_many, duplicate.candidates.size, duplicate.candidates.size))
        }
        if (duplicate.authorNote != null) add(stringResource(R.string.review_duplicate_note))
    }.joinToString(" ")
    Column(
        Modifier
            .fillMaxWidth()
            .background(flag.container, MaterialTheme.shapes.medium)
            .dashedBorder(1.dp, flag.border, 12.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_join_inner), null, tint = flag.content, modifier = Modifier.size(20.dp.scaledWithFont()))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.review_duplicate_title), style = MaterialTheme.typography.titleSmall, color = flag.content)
                if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = flag.content)
            }
        }
        ExploraButton(
            stringResource(R.string.review_compare),
            onClick = onCompare,
            modifier = Modifier.fillMaxWidth(),
            style = ExploraButtonStyle.SECONDARY,
            icon = R.drawable.ic_compare,
        )
    }
}

/** La portada grande y las demás al lado (33.a); cada una abre las fotos a pantalla completa. */
@Composable
private fun Gallery(item: ReviewItem, onOpen: (Int) -> Unit) {
    val photos = item.photos
    if (photos.isEmpty()) return
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Row(Modifier.fillMaxWidth().height(if (stacked) 180.dp else 140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoTile(0, photos.size, Modifier.weight(2f).fillMaxSize(), onOpen)
        if (photos.size > 1) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.indices.drop(1).take(2).forEach { index -> PhotoTile(index, photos.size, Modifier.weight(1f).fillMaxWidth(), onOpen) }
            }
        }
    }
}

/** Una foto de la galería: sin API todavía no hay imagen; el lector oye cuál es y que se abre a pantalla completa. */
@Composable
private fun PhotoTile(index: Int, count: Int, modifier: Modifier, onOpen: (Int) -> Unit) {
    val name = if (index == 0) stringResource(R.string.review_photo_cover, index + 1, count) else stringResource(R.string.review_photo, index + 1, count)
    val action = stringResource(R.string.review_photo_open)
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = action) { onOpen(index) }
            .clearAndSetSemantics {
                contentDescription = name
                role = Role.Image
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_photo_camera), null, tint = MaterialTheme.exploraColors.textPlaceholder, modifier = Modifier.size(32.dp))
    }
}

/** «Categoría sugerida» (16) o «Elegida por el autor»: con icono y texto. */
@Composable
private fun OriginTag(origin: CategoryOrigin) {
    val suggested = origin == CategoryOrigin.SUGGESTED
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (suggested) R.drawable.ic_auto_awesome else R.drawable.ic_person),
            null,
            tint = MaterialTheme.exploraColors.iconSecondary,
            modifier = Modifier.size(14.dp.scaledWithFont()),
        )
        Text(
            stringResource(if (suggested) R.string.review_category_suggested else R.string.review_category_chosen),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
            color = MaterialTheme.exploraColors.textSecondary,
        )
    }
}

/** Horario, precio y dirección con coordenadas (para comparar con el mapa). */
@Composable
private fun InfoCard(item: ReviewItem) {
    val hours = item.hours?.let {
        formatOpeningHours(it, HoursWords(stringResource(R.string.hours_every_day), stringResource(R.string.hours_to), stringResource(R.string.list_and)))
    } ?: stringResource(R.string.review_no_hours)
    val price = item.price?.let { stringResource(it.labelRes) } ?: stringResource(R.string.review_no_price)
    val coordinates = item.location.let { "${it.latitude.format()} · ${it.longitude.format()}" }
    val place = item.address?.let { stringResource(R.string.review_place, it, coordinates) } ?: coordinates
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InfoRow(R.drawable.ic_schedule, hours)
        InfoRow(R.drawable.ic_payments, price)
        InfoRow(R.drawable.ic_location_on, place)
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(18.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val spanishColombia: Locale = Locale.forLanguageTag("es-CO")

/** «4,71203»: coma decimal, como se escribe en Colombia. */
private fun Double.format(): String = String.format(spanishColombia, "%.5f", this)

/** 33.b · Un usuario reportó la publicación: el motivo, en tono de error y con su icono. */
@Composable
private fun ReportCard(reason: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .border(1.dp, scheme.error, MaterialTheme.shapes.medium)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_flag), null, tint = scheme.error, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(stringResource(R.string.review_report, reason), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = scheme.onSurface)
    }
}

/** Mapa pequeño con el pin y «Abrir mapa grande». Sin la clave de Maps queda el contenedor con el botón. */
@Composable
private fun ReviewMap(item: ReviewItem, onOpen: () -> Unit) {
    val label = stringResource(R.string.review_open_map)
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        PlaceMap(item, lite = true, Modifier.fillMaxSize())
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = label, onClick = onOpen)
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

/** El mapa con el pin de la categoría; [lite] para el pequeño (en modo lite no se mueve). */
@Composable
private fun PlaceMap(item: ReviewItem, lite: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    if (!remember(context) { context.hasMapsApiKey() }) return
    val target = LatLng(item.location.latitude, item.location.longitude)
    val dark = LocalDarkTheme.current
    val border = MaterialTheme.exploraColors.mapClusterBorder
    GoogleMap(
        modifier = modifier,
        mergeDescendants = lite,
        cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(target, if (lite) 15f else 16f) },
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(lite) },
        properties = MapProperties(
            mapStyleOptions = remember(context, dark) { MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style) },
        ),
        uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = !lite, compassEnabled = false, myLocationButtonEnabled = false),
        mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
    ) {
        MarkerComposable(item.id, state = rememberUpdatedMarkerState(position = target), anchor = Offset(0.5f, 1f)) {
            PlacePin(item.category, item.category.colors, selected = !lite, border = border)
        }
    }
}

/** «Abrir mapa grande»: el mapa a pantalla completa, con zoom, para revisar dónde quedó el pin. */
@Composable
private fun MapDialog(item: ReviewItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NoNavigationBarScrim()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.map_dialog_close), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    stringResource(R.string.review_map_title, item.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                PlaceMap(item, lite = false, Modifier.fillMaxSize())
            }
        }
    }
}

/** Quién publicó y su historial: «18 verificadas · 0 rechazos previos», con su nivel. */
@Composable
private fun AuthorCard(item: ReviewItem) {
    val author = item.author
    val history = stringResource(
        R.string.review_author_history,
        pluralStringResource(R.plurals.review_verified_count, author.verified, author.verified),
        pluralStringResource(R.plurals.review_rejected_count, author.rejected, author.rejected),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.exploraColors.divider, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsAvatar(author.author, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(author.author.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(history, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary)
        }
        LevelChip(author.author.level)
    }
}

/** Barra fija abajo (52 dp): «Rechazar» a la izquierda, para no confundirla con la acción principal. */
@Composable
private fun ActionBar(state: ReviewDetailUiState, callbacks: ReviewDetailCallbacks) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val reason = if (state.offline) stringResource(R.string.review_offline_reason) else null
    val reject: @Composable (Modifier) -> Unit = { modifier ->
        ExploraButton(
            stringResource(R.string.review_reject),
            onClick = callbacks.onReject,
            modifier = modifier.heightIn(min = 52.dp),
            style = ExploraButtonStyle.SECONDARY,
            enabled = state.canDecide,
            disabledReason = reason,
            icon = R.drawable.ic_cancel,
        )
    }
    val verify: @Composable (Modifier) -> Unit = { modifier ->
        ExploraButton(
            stringResource(if (state.sending) R.string.verify_sending else R.string.review_verify),
            onClick = callbacks.onVerify,
            modifier = modifier.heightIn(min = 52.dp),
            enabled = state.canDecide || state.sending,
            disabledReason = reason,
            loading = state.sending,
            icon = R.drawable.ic_verified,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
        if (stacked) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                verify(Modifier.fillMaxWidth())
                reject(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                reject(Modifier.weight(1f))
                verify(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 34 · ¿Verificar? Enuncia las consecuencias (visibilidad, aviso al autor, puntos) y que es reversible. Si es posible
 * duplicado y no se comparó, lo advierte. El foco empieza en el titular.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerifySheetHost(item: ReviewItem, state: ReviewDetailUiState, callbacks: ReviewDetailCallbacks) {
    val sheet = state.verify ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.9f }
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismissVerify,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha.Sheet),
        dragHandle = null,
    ) {
        VerifySheetContent(item, sheet, warnDuplicate = item.duplicate != null && !state.compared, callbacks, Modifier.heightIn(max = maxHeight))
    }
}

@Composable
private fun VerifySheetContent(item: ReviewItem, sheet: VerifySheet, warnDuplicate: Boolean, callbacks: ReviewDetailCallbacks, modifier: Modifier) {
    NoNavigationBarScrim()
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val verified = MaterialTheme.exploraColors.status.verified
    val textState = rememberTextFieldState(sheet.note)
    val currentOnNoteChange by rememberUpdatedState(callbacks.onNoteChange)
    LaunchedEffect(textState) { snapshotFlow { textState.text.toString() }.collect { currentOnNoteChange(it) } }
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(verified.container, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_verified), null, tint = verified.content, modifier = Modifier.size(26.dp))
            }
            Text(
                stringResource(R.string.verify_title, item.title),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 28.sp),
                modifier = Modifier.focusRequester(titleFocus).focusable().semantics { heading() },
            )
            Text(
                stringResource(R.string.verify_body, item.author.author.name),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = MaterialTheme.exploraColors.textSecondary,
            )
            if (warnDuplicate) DuplicateWarning()
            ExploraTextField(
                state = textState,
                label = stringResource(R.string.verify_note),
                placeholder = stringResource(R.string.verify_note_placeholder),
                counter = stringResource(R.string.verify_note_counter, textState.text.length, ReviewDetailViewModel.NOTE_MAX),
                singleLine = false,
                minLines = 2,
                enabled = !sheet.sending,
                inputTransformation = InputTransformation.maxLength(ReviewDetailViewModel.NOTE_MAX),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        val cancel: @Composable (Modifier) -> Unit = { m ->
            ExploraButton(stringResource(R.string.verify_cancel), onClick = callbacks.onDismissVerify, modifier = m, style = ExploraButtonStyle.TEXT, enabled = !sheet.sending)
        }
        val confirm: @Composable (Modifier) -> Unit = { m ->
            ExploraButton(
                stringResource(if (sheet.sending) R.string.verify_sending else R.string.verify_confirm),
                onClick = callbacks.onConfirmVerify,
                modifier = m,
                loading = sheet.sending,
                icon = R.drawable.ic_verified,
            )
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                confirm(Modifier.fillMaxWidth())
                cancel(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                cancel(Modifier.weight(1f))
                confirm(Modifier.weight(2f))
            }
        }
    }
}

/** «Esta publicación se parece a un lugar existente. ¿Quieres verificarla de todos modos?» (README 33). */
@Composable
private fun DuplicateWarning() {
    val flag = MaterialTheme.exploraColors.duplicateFlag
    Row(
        Modifier
            .fillMaxWidth()
            .background(flag.container, MaterialTheme.shapes.medium)
            .dashedBorder(1.dp, flag.border, 12.dp)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_join_inner), null, tint = flag.content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(stringResource(R.string.verify_duplicate_warning), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = flag.content)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

/** «Verificada. Quedan 6 por revisar», al llegar desde la anterior (C1). */
@Composable
private fun NoticeEffect(remaining: Int?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = remaining?.let { pluralStringResource(R.plurals.review_verified_next, it, it) }
    LaunchedEffect(remaining) {
        if (text == null) return@LaunchedEffect
        hostState.showSnackbar(text, withDismissAction = true)
        currentOnShown()
    }
}

/** «No pudimos guardar la decisión. La publicación sigue pendiente» con «Reintentar» (README 34). */
@Composable
private fun VerifyFailedEffect(failed: Boolean, hostState: SnackbarHostState, callbacks: ReviewDetailCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val text = stringResource(R.string.review_verify_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        currentCallbacks.onVerifyFailureShown()
        val result = hostState.showSnackbar(text, actionLabel = retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) currentCallbacks.onRetryVerify()
    }
}

private val previewNow: Instant = Instant.parse("2026-10-02T15:00:00Z")

@Preview(name = "33.a · revisión · claro", widthDp = 360, heightDp = 1200)
@Composable
private fun ReviewPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        val item = previewReviews(previewNow).first()
        ReviewDetailScreen(ReviewDetailUiState(content = ReviewContent.Loaded(item), position = 1, total = 7), ReviewDetailCallbacks(), now = previewNow)
    }
}

@Preview(name = "33 · posible duplicado y reportada · oscuro", widthDp = 360, heightDp = 1200)
@Composable
private fun ReviewDuplicatePreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        val item = previewReviews(previewNow)[1]
        ReviewDetailScreen(ReviewDetailUiState(content = ReviewContent.Loaded(item), position = 2, total = 7, offline = true), ReviewDetailCallbacks(), now = previewNow)
    }
}
