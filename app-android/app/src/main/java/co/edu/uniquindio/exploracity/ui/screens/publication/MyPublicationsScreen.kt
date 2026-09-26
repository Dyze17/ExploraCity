package co.edu.uniquindio.exploracity.ui.screens.publication

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.SubmittedDay
import co.edu.uniquindio.exploracity.util.formatDate
import co.edu.uniquindio.exploracity.util.formatTime
import co.edu.uniquindio.exploracity.util.submittedDay
import co.edu.uniquindio.exploracity.viewmodel.MyPublicationsContent
import co.edu.uniquindio.exploracity.viewmodel.MyPublicationsUiState
import co.edu.uniquindio.exploracity.viewmodel.MyPublicationsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 22 · Mis publicaciones, conectada a su ViewModel. Cada destino depende del estado de la publicación. */
@Composable
fun MyPublicationsRoute(
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenRejected: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onResubmit: (publicationId: String, step: Int) -> Unit,
    onPublish: () -> Unit,
    deletedElsewhere: Boolean = false,
    onDeletedElsewhereHandled: () -> Unit = {},
    viewModel: MyPublicationsViewModel = viewModel(factory = MyPublicationsViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    val currentOnHandled by rememberUpdatedState(onDeletedElsewhereHandled)
    LaunchedEffect(deletedElsewhere) {
        if (!deletedElsewhere) return@LaunchedEffect
        viewModel.onDeletedElsewhere()
        currentOnHandled()
    }
    MyPublicationsScreen(
        state = state,
        callbacks = MyPublicationsCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onFilter = viewModel::onFilter,
            onOpen = { publication ->
                when (publication.status) {
                    PublicationStatus.VERIFIED, PublicationStatus.FINALIZED -> onOpenPlace(publication.id)
                    PublicationStatus.REJECTED -> onOpenRejected(publication.id)
                    // Aún no es pública: su «detalle propio» es la edición (23).
                    PublicationStatus.PENDING -> onEdit(publication.id)
                }
            },
            onEdit = { onEdit(it.id) },
            onOpenComments = { onOpenComments(it.id) },
            onResubmit = { onResubmit(it.id, it.rejection?.firstStepToFix ?: 1) },
            onOpenDelete = viewModel::onOpenDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDeletedShown = viewModel::onDeletedShown,
            onPublish = onPublish,
        ),
    )
}

class MyPublicationsCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFilter: (PublicationStatus?) -> Unit = {},
    val onOpen: (OwnPublication) -> Unit = {},
    val onEdit: (OwnPublication) -> Unit = {},
    val onOpenComments: (OwnPublication) -> Unit = {},
    val onResubmit: (OwnPublication) -> Unit = {},
    val onOpenDelete: (OwnPublication) -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDeletedShown: () -> Unit = {},
    val onPublish: () -> Unit = {},
)

/**
 * 22 · Filtros por estado con su conteo y las publicaciones, de la más reciente a la más antigua. La marca de posible
 * duplicado va solo en las pendientes marcadas, siempre después del estado. Cada ítem tiene su menú (Editar · Ver
 * comentarios · Eliminar, este al final y en rojo) con lo que aplica a su estado.
 */
@Composable
fun MyPublicationsScreen(state: MyPublicationsUiState, callbacks: MyPublicationsCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    DeletedEffect(state.deletedShown, snackbarHostState, callbacks.onDeletedShown)
    val loaded = state.loaded

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            ExploraTopAppBar(title = stringResource(R.string.my_publications_title), onBack = callbacks.onBack)
            if (loaded != null) FilterRow(loaded.counts, state.filter, callbacks.onFilter)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    MyPublicationsContent.Loading -> MyPublicationsSkeleton()
                    is MyPublicationsContent.Loaded -> when {
                        content.items.isEmpty() -> Centered {
                            EmptyState(
                                icon = R.drawable.ic_add_location_alt,
                                title = stringResource(R.string.my_publications_empty_title),
                                body = stringResource(R.string.my_publications_empty_body),
                            ) {
                                ExploraButton(
                                    stringResource(R.string.my_publications_publish),
                                    onClick = callbacks.onPublish,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = R.drawable.ic_add_location_alt,
                                )
                            }
                        }
                        state.visible.isEmpty() -> Centered { FilterEmpty(state.filter) { callbacks.onFilter(null) } }
                        else -> PublicationList(state.visible, callbacks)
                    }
                    MyPublicationsContent.Error -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.my_publications_error_title),
                            body = stringResource(R.string.profile_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                    MyPublicationsContent.Offline -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.my_publications_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    val deleting = state.deleting
    val dialog = state.delete?.dialog
    if (deleting != null && dialog != null) {
        DeletePublicationDialog(deleting, dialog, callbacks.onConfirmDelete, callbacks.onDismissDelete)
    }
}

@Composable
private fun DeletedEffect(shown: Boolean, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = stringResource(R.string.publication_deleted)
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Short)
        currentOnShown()
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Un filtro por estado, en el orden del lienzo 22.a. */
private class StatusFilter(val status: PublicationStatus?, @param:StringRes val label: Int, @param:DrawableRes val icon: Int?)

private val statusFilters = listOf(
    StatusFilter(null, R.string.my_publications_filter_all, null),
    StatusFilter(PublicationStatus.PENDING, R.string.my_publications_filter_pending, R.drawable.ic_schedule),
    StatusFilter(PublicationStatus.VERIFIED, R.string.my_publications_filter_verified, R.drawable.ic_verified),
    StatusFilter(PublicationStatus.REJECTED, R.string.my_publications_filter_rejected, R.drawable.ic_cancel),
    StatusFilter(PublicationStatus.FINALIZED, R.string.my_publications_filter_finalized, R.drawable.ic_task_alt),
)

/** Fila deslizable de filtros; se elige uno a la vez (radios para el lector). */
@Composable
private fun FilterRow(counts: PublicationCounts, selected: PublicationStatus?, onFilter: (PublicationStatus?) -> Unit) {
    val group = stringResource(R.string.my_publications_filters)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .semantics { contentDescription = group }
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statusFilters.forEach { filter ->
            val count = filter.status?.let { counts[it] } ?: counts.total
            StatusFilterChip(stringResource(filter.label), count, filter.icon, selected == filter.status) { onFilter(filter.status) }
        }
    }
}

/** Elegido: relleno primary y check en lugar del icono, no solo color (como los chips de categoría y 32). */
@Composable
private fun StatusFilterChip(label: String, count: Int, @DrawableRes icon: Int?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val content = if (selected) scheme.onPrimary else MaterialTheme.exploraColors.textSecondary
    val spoken = pluralStringResource(R.plurals.my_publications_filter_spoken, count, label, count)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .heightIn(min = 40.dp)
            .background(if (selected) scheme.primary else Color.Transparent, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outline, shape))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shownIcon = if (selected) R.drawable.ic_check else icon
        if (shownIcon != null) Icon(painterResource(shownIcon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(
            stringResource(R.string.my_publications_filter_count, label, count),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
            color = content,
        )
    }
}

/** Un filtro sin publicaciones: se dice cuál y se ofrece volver a «Todas». */
@Composable
private fun FilterEmpty(filter: PublicationStatus?, onSeeAll: () -> Unit) {
    val (title, icon) = when (filter) {
        PublicationStatus.PENDING -> R.string.my_publications_empty_pending to R.drawable.ic_schedule
        PublicationStatus.VERIFIED -> R.string.my_publications_empty_verified to R.drawable.ic_verified
        PublicationStatus.REJECTED -> R.string.my_publications_empty_rejected to R.drawable.ic_cancel
        PublicationStatus.FINALIZED, null -> R.string.my_publications_empty_finalized to R.drawable.ic_task_alt
    }
    EmptyState(icon = icon, title = stringResource(title), body = stringResource(R.string.my_publications_filter_empty_body)) {
        ExploraButton(stringResource(R.string.my_publications_see_all), onClick = onSeeAll, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.SECONDARY)
    }
}

@Composable
private fun PublicationList(items: List<OwnPublication>, callbacks: MyPublicationsCallbacks) {
    val now by rememberNow()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { publication -> PublicationCard(publication, now, callbacks) }
    }
}

/**
 * Tarjeta de 22.a: foto, título, estado (+ posible duplicado) y una línea que depende del estado. Toda la tarjeta abre
 * su destino; el menú ⋮ es un botón aparte de 48 dp. El lector la dice en una frase: «Café La Fonda, pendiente de
 * verificación, posible duplicado en revisión, Enviada hoy».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicationCard(publication: OwnPublication, now: Instant, callbacks: MyPublicationsCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    val meta = metaText(publication, now)
    // «Posible duplicado en revisión» ya dice lo de «· en revisión»: el lector no lo repite.
    val spokenMeta = if (publication.possibleDuplicate) submittedText(submittedDay(publication.submittedAt, now, ZoneId.systemDefault()), withTime = false) else meta
    val spoken = listOfNotNull(
        publication.title,
        stringResource(publication.status.spokenRes),
        stringResource(R.string.my_publications_duplicate_spoken).takeIf { publication.possibleDuplicate },
        spokenMeta.replace(" · ", ", "),
    ).joinToString(", ")

    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val cardModifier = Modifier
        .fillMaxWidth()
        .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
        .clip(shape)
        .background(scheme.surfaceContainerLowest)
        .clickable(role = Role.Button) { callbacks.onOpen(publication) }
        .semantics { contentDescription = spoken }
    val photo = @Composable { photoModifier: Modifier -> Box(photoModifier.clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHigh)) }
    // El texto lo dice la tarjeta en una frase; el menú queda como botón aparte.
    val texts = @Composable { textModifier: Modifier ->
        Column(textModifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                publication.title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.W600),
                color = scheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(publication.status, size = BadgeSize.SMALL, label = stringResource(publication.status.shortLabelRes))
                if (publication.possibleDuplicate) DuplicateFlag(size = BadgeSize.SMALL)
            }
            Text(meta, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600), color = scheme.onSurfaceVariant)
        }
    }

    if (stacked) {
        // Con fuente grande, como POICard (7): la foto arriba a todo lo ancho y el texto con el ancho completo.
        Column(cardModifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            photo(Modifier.fillMaxWidth().height(128.dp))
            Row {
                texts(Modifier.weight(1f))
                PublicationMenu(publication, callbacks)
            }
        }
    } else {
        Row(cardModifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            photo(Modifier.size(80.dp))
            texts(Modifier.weight(1f))
            PublicationMenu(publication, callbacks)
        }
    }
}

@get:StringRes
private val PublicationStatus.shortLabelRes: Int
    get() = when (this) {
        PublicationStatus.PENDING -> R.string.my_publications_pending
        PublicationStatus.VERIFIED -> R.string.status_verified
        PublicationStatus.REJECTED -> R.string.status_rejected_action
        PublicationStatus.FINALIZED -> R.string.status_finalized
    }

@get:StringRes
private val PublicationStatus.spokenRes: Int
    get() = when (this) {
        PublicationStatus.PENDING -> R.string.my_publications_status_pending_spoken
        PublicationStatus.VERIFIED -> R.string.my_publications_status_verified_spoken
        PublicationStatus.REJECTED -> R.string.my_publications_status_rejected_spoken
        PublicationStatus.FINALIZED -> R.string.my_publications_status_finalized_spoken
    }

/** «132 votos · 31 comentarios», «Cerrada por el moderador», «Enviada hoy · 9:12» o «Enviada hoy · en revisión». */
@Composable
private fun metaText(publication: OwnPublication, now: Instant): String = when (publication.status) {
    PublicationStatus.VERIFIED -> stringResource(
        R.string.my_publications_stats,
        pluralStringResource(R.plurals.poi_votes, publication.votes, publication.votes),
        pluralStringResource(R.plurals.poi_comments, publication.comments, publication.comments),
    )
    PublicationStatus.FINALIZED -> stringResource(R.string.my_publications_closed)
    PublicationStatus.PENDING, PublicationStatus.REJECTED -> {
        val day = submittedDay(publication.submittedAt, now, ZoneId.systemDefault())
        if (publication.possibleDuplicate) {
            stringResource(R.string.my_publications_in_review, submittedText(day, withTime = false))
        } else {
            submittedText(day, withTime = true)
        }
    }
}

@Composable
private fun submittedText(day: SubmittedDay, withTime: Boolean): String = when (day) {
    is SubmittedDay.Today ->
        if (withTime) stringResource(R.string.my_publications_submitted_today, formatTime(day.time)) else stringResource(R.string.my_publications_submitted_today_short)
    SubmittedDay.Yesterday -> stringResource(R.string.my_publications_submitted_yesterday)
    is SubmittedDay.DaysAgo -> pluralStringResource(R.plurals.my_publications_submitted_days, day.days, day.days)
    is SubmittedDay.On -> stringResource(R.string.rejected_submitted, formatDate(day.date, withYear = !day.sameYear))
}

/**
 * Menú ⋮ con lo que aplica al estado: una pendiente aún no tiene comentarios; una finalizada la cerró el moderador y
 * editarla la devolvería a pendiente; una rechazada se corrige y reenvía (si el moderador lo permitió). «Eliminar» va
 * siempre al final, en rojo y separado.
 */
@Composable
private fun PublicationMenu(publication: OwnPublication, callbacks: MyPublicationsCallbacks) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.my_publications_more, publication.title),
                tint = scheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = scheme.surfaceContainerLowest) {
            fun item(onClick: (OwnPublication) -> Unit): () -> Unit = {
                expanded = false
                onClick(publication)
            }
            val status = publication.status
            if (status == PublicationStatus.PENDING || status == PublicationStatus.VERIFIED) {
                MenuItem(R.string.my_publications_edit, R.drawable.ic_edit, scheme.onSurface, item(callbacks.onEdit))
            }
            if (status == PublicationStatus.REJECTED && publication.rejection?.canResubmit == true) {
                MenuItem(R.string.rejected_resubmit, R.drawable.ic_edit, scheme.onSurface, item(callbacks.onResubmit))
            }
            if (publication.isPublic) {
                MenuItem(R.string.my_publications_comments, R.drawable.ic_chat_bubble, scheme.onSurface, item(callbacks.onOpenComments))
            }
            HorizontalDivider(color = MaterialTheme.exploraColors.divider)
            MenuItem(R.string.my_publications_delete, R.drawable.ic_delete, scheme.error, item(callbacks.onOpenDelete))
        }
    }
}

@Composable
private fun MenuItem(@StringRes label: Int, @DrawableRes icon: Int, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = color) },
        onClick = onClick,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp.scaledWithFont())) },
    )
}

/** Mientras carga: filtros y tres tarjetas, con un solo anuncio. */
@Composable
private fun MyPublicationsSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.my_publications_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(76, 112, 116).forEach { width -> SkeletonBlock(brush, Modifier.width(width.dp).height(40.dp), MaterialTheme.shapes.small) }
        }
        repeat(3) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(brush, Modifier.size(80.dp), MaterialTheme.shapes.medium)
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(16.dp))
                    SkeletonBlock(brush, Modifier.fillMaxWidth(0.5f).height(22.dp), MaterialTheme.shapes.small)
                    SkeletonBlock(brush, Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
        }
    }
}

private val previewNow = Instant.now()

private fun previewPublication(id: String, title: String, category: Category, status: PublicationStatus, daysAgo: Long, duplicate: Boolean = false) =
    OwnPublication(
        id = id,
        title = title,
        category = category,
        status = status,
        location = GeoPoint(4.6, -74.07),
        photos = 3,
        submittedAt = previewNow.minus(daysAgo, ChronoUnit.DAYS),
        votes = if (status == PublicationStatus.VERIFIED) 132 else 0,
        comments = if (status == PublicationStatus.VERIFIED) 31 else 0,
        possibleDuplicate = duplicate,
    )

private val previewItems = listOf(
    previewPublication("fonda", "Café La Fonda", Category.GASTRONOMY, PublicationStatus.PENDING, 0, duplicate = true),
    previewPublication("mirador", "Mirador del Alto", Category.NATURE, PublicationStatus.REJECTED, 4),
    previewPublication("sendero", "Sendero La Vieja", Category.NATURE, PublicationStatus.VERIFIED, 12),
    previewPublication("casa", "Casa de la Independencia", Category.HISTORY, PublicationStatus.FINALIZED, 40),
)

@Preview(name = "22.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun MyPublicationsLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { MyPublicationsScreen(MyPublicationsUiState(MyPublicationsContent.Loaded(previewItems)), MyPublicationsCallbacks()) }
}

@Preview(name = "22 · vacía · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun MyPublicationsEmptyPreview() {
    ExploraCityTheme(ThemeMode.DARK) { MyPublicationsScreen(MyPublicationsUiState(MyPublicationsContent.Loaded(emptyList())), MyPublicationsCallbacks()) }
}
