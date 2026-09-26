package co.edu.uniquindio.exploracity.ui.screens.publication

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Rejection
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.RequiredFix
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.formatDate
import co.edu.uniquindio.exploracity.util.formatDistance
import co.edu.uniquindio.exploracity.viewmodel.DeleteDialogState
import co.edu.uniquindio.exploracity.viewmodel.RejectedContent
import co.edu.uniquindio.exploracity.viewmodel.RejectedPublicationUiState
import co.edu.uniquindio.exploracity.viewmodel.RejectedPublicationViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 24 · Publicación rechazada, conectada a su ViewModel. Al eliminarla, [onDeleted] vuelve a Mis publicaciones (22). */
@Composable
fun RejectedPublicationRoute(
    onBack: () -> Unit,
    onResubmit: (publicationId: String, step: Int) -> Unit,
    onOpenExisting: (poiId: String) -> Unit,
    onOpenMine: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: RejectedPublicationViewModel = viewModel(factory = RejectedPublicationViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(state.deleted) {
        if (!state.deleted) return@LaunchedEffect
        viewModel.onDeletedHandled()
        currentOnDeleted()
    }
    RejectedPublicationScreen(
        state = state,
        callbacks = RejectedCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onResubmit = { publication -> onResubmit(publication.id, publication.rejection?.firstStepToFix ?: 1) },
            onOpenExisting = onOpenExisting,
            onOpenMine = onOpenMine,
            onOpenDelete = viewModel::onOpenDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
        ),
    )
}

class RejectedCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onResubmit: (OwnPublication) -> Unit = {},
    val onOpenExisting: (String) -> Unit = {},
    val onOpenMine: () -> Unit = {},
    val onOpenDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
)

/**
 * 24 · Dos variantes según el motivo. Por otro motivo (24.a): el mensaje del moderador en lenguaje claro, qué revisar,
 * la publicación y «Corregir y reenviar» / «Eliminar publicación». Por duplicado: «ya existía» (nunca «repetida» ni
 * «error»), el lugar original tocable e «Ir al lugar existente» / «Volver a mis publicaciones».
 */
@Composable
fun RejectedPublicationScreen(state: RejectedPublicationUiState, callbacks: RejectedCallbacks, modifier: Modifier = Modifier) {
    val publication = state.publication
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(title = publication?.title ?: stringResource(R.string.rejected_title), onBack = callbacks.onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                RejectedContent.Loading -> RejectedSkeleton()
                is RejectedContent.Loaded -> RejectedBody(content.publication, callbacks)
                RejectedContent.Error -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.rejected_error_title),
                        body = stringResource(R.string.profile_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { RetryButton(callbacks.onRetry) }
                }
                RejectedContent.Offline -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.rejected_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { RetryButton(callbacks.onRetry) }
                }
                RejectedContent.NotFound -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_delete,
                        title = stringResource(R.string.rejected_not_found_title),
                        body = stringResource(R.string.rejected_not_found_body),
                    ) {
                        ExploraButton(stringResource(R.string.rejected_back_to_mine), onClick = callbacks.onOpenMine, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    val delete = state.delete
    if (publication != null && delete != null) {
        DeletePublicationDialog(publication, delete, callbacks.onConfirmDelete, callbacks.onDismissDelete)
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

@Composable
private fun RejectedBody(publication: OwnPublication, callbacks: RejectedCallbacks) {
    val rejection = publication.rejection
    val duplicate = rejection?.reason == RejectionReason.DUPLICATE
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val rejectedAt = rejection?.rejectedAt
            StatusBadge(
                PublicationStatus.REJECTED,
                label = rejectedAt?.let { stringResource(R.string.rejected_status, dateText(it)) },
            )
            when {
                rejection == null -> Unit
                duplicate -> {
                    DuplicateReason(publication, rejection.duplicateOf)
                    rejection.duplicateOf?.let { original -> OriginalCard(publication, original) { callbacks.onOpenExisting(original.id) } }
                }
                else -> {
                    ModeratorReason(rejection)
                    if (rejection.fixes.isNotEmpty() && rejection.canResubmit) Fixes(rejection.fixes)
                    PublicationSummary(publication)
                }
            }
        }
        Actions(publication, duplicate, callbacks)
    }
}

/** Botones fijos abajo: la acción principal arriba y la secundaria como texto. */
@Composable
private fun Actions(publication: OwnPublication, duplicate: Boolean, callbacks: RejectedCallbacks) {
    val rejection = publication.rejection
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (duplicate) {
                val original = rejection?.duplicateOf
                if (original != null) {
                    ExploraButton(
                        stringResource(R.string.rejected_go_existing),
                        onClick = { callbacks.onOpenExisting(original.id) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_arrow_forward,
                    )
                }
                ExploraButton(
                    stringResource(R.string.rejected_back_to_mine),
                    onClick = callbacks.onOpenMine,
                    modifier = Modifier.fillMaxWidth(),
                    style = if (original != null) ExploraButtonStyle.TEXT else ExploraButtonStyle.PRIMARY,
                )
            } else {
                if (rejection?.canResubmit == true) {
                    ExploraButton(
                        stringResource(R.string.rejected_resubmit),
                        onClick = { callbacks.onResubmit(publication) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_edit,
                    )
                }
                ExploraButton(
                    stringResource(R.string.rejected_delete),
                    onClick = callbacks.onOpenDelete,
                    modifier = Modifier.fillMaxWidth(),
                    style = ExploraButtonStyle.DESTRUCTIVE_TEXT,
                )
            }
        }
    }
}

/** «Motivo del moderador»: el mensaje tal cual y quién la revisó, con el borde de error de 24.a. */
@Composable
private fun ModeratorReason(rejection: Rejection) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(explora.errorFieldContainer, shape)
            .border(1.dp, scheme.error, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.rejected_reason_title),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = scheme.error,
            modifier = Modifier.semantics { heading() },
        )
        Text(rejection.message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp), color = scheme.onSurface)
        Text(
            stringResource(R.string.rejected_reviewed_by, rejection.reviewerName),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = explora.iconSecondary,
        )
        if (!rejection.canResubmit) {
            Text(
                stringResource(R.string.rejected_no_resubmit),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
                color = scheme.onSurface,
            )
        }
    }
}

/** «Qué revisar antes de reenviar»: cada corrección con el icono de lo que toca. */
@Composable
private fun Fixes(fixes: List<RequiredFix>) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.surfaceContainerHighest, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.rejected_fixes_title),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = scheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        fixes.forEach { fix ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(fix.kind.iconRes), contentDescription = null, tint = explora.warningAccent, modifier = Modifier.size(18.dp.scaledWithFont()))
                Text(fix.text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = explora.textSecondary)
            }
        }
    }
}

@get:DrawableRes
private val FixKind.iconRes: Int
    get() = when (this) {
        FixKind.TITLE, FixKind.DESCRIPTION -> R.drawable.ic_edit
        FixKind.CATEGORY -> R.drawable.ic_tune
        FixKind.LOCATION -> R.drawable.ic_location_on
        FixKind.SCHEDULE -> R.drawable.ic_schedule
        FixKind.PRICE -> R.drawable.ic_payments
        FixKind.PHOTOS -> R.drawable.ic_photo_camera
    }

/** La publicación rechazada: foto, título, categoría y cuándo se envió. No abre nada: todo está en esta pantalla. */
@Composable
private fun PublicationSummary(publication: OwnPublication) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.surfaceContainerHighest, shape)
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoPlaceholder(80.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(publication.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.W700), color = scheme.onSurface)
            CategoryTag(publication.category)
            Text(
                stringResource(R.string.rejected_submitted, dateText(publication.submittedAt)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.exploraColors.iconSecondary,
            )
        }
    }
}

/** Por duplicado: «Este lugar ya existía en ExploraCity» con el nombre y la distancia del original. */
@Composable
private fun DuplicateReason(publication: OwnPublication, original: Poi?) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    val body = if (original != null) {
        stringResource(R.string.rejected_duplicate_body, original.title, formatDistance(publication.location.distanceTo(original.location)))
    } else {
        stringResource(R.string.rejected_duplicate_body_unknown)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLowest, shape)
            .border(1.dp, explora.divider, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.rejected_duplicate_label),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W700),
            color = explora.iconSecondary,
        )
        Text(
            stringResource(R.string.rejected_duplicate_title),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.W600),
            color = scheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp), color = explora.textSecondary)
    }
}

/** El lugar original: toda la tarjeta hace lo mismo que «Ir al lugar existente» (README 24). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginalCard(publication: OwnPublication, original: Poi, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    val distance = formatDistance(publication.location.distanceTo(original.location))
    val votes = pluralStringResource(R.plurals.poi_votes, original.votes, original.votes)
    val spoken = stringResource(
        R.string.rejected_original_spoken,
        original.title,
        stringResource(original.category.labelRes),
        stringResource(original.status.labelRes),
        distance,
        votes,
    )
    val openLabel = stringResource(R.string.rejected_go_existing)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, explora.divider, shape)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoPlaceholder(64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                original.title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.W600),
                color = scheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                CategoryTag(original.category)
                StatusBadge(original.status, size = BadgeSize.SMALL)
            }
            Text(
                stringResource(R.string.rejected_original_meta, distance, votes),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W700),
                color = explora.textSecondary,
            )
        }
    }
}

/** Contenedor neutro de la foto, como en el feed mientras no haya imágenes reales. */
@Composable
private fun PhotoPlaceholder(size: Dp) {
    Box(Modifier.size(size).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

/** «12 de septiembre»; de otro año, con el año. */
private fun dateText(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.atZone(zone).toLocalDate()
    return formatDate(date, withYear = date.year != Instant.now().atZone(zone).year)
}

/** Mientras carga: el estado y tres tarjetas, con un solo anuncio. */
@Composable
private fun RejectedSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.rejected_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBlock(brush, Modifier.fillMaxWidth(0.55f).height(28.dp), MaterialTheme.shapes.small)
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(140.dp), RoundedCornerShape(16.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(16.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(104.dp), RoundedCornerShape(16.dp))
    }
}

private val previewNow = Instant.now()

private val previewRejected = OwnPublication(
    id = "mirador-de-la-pena",
    title = "Mirador de La Peña",
    category = Category.NATURE,
    status = PublicationStatus.REJECTED,
    location = GeoPoint(4.5905, -74.0590),
    photos = 2,
    submittedAt = previewNow.minus(4, ChronoUnit.DAYS),
    rejection = Rejection(
        reason = RejectionReason.PHOTO,
        message = "La foto no permite reconocer el lugar y el pin quedó a unas tres cuadras de la entrada. Si subes una foto del mirador y ajustas la ubicación, con gusto la revisamos otra vez.",
        reviewerName = "Laura M.",
        rejectedAt = previewNow.minus(3, ChronoUnit.HOURS),
        canResubmit = true,
        fixes = listOf(
            RequiredFix(FixKind.PHOTOS, "Una foto donde se vea el mirador completo"),
            RequiredFix(FixKind.LOCATION, "El pin sobre la entrada del sendero"),
        ),
    ),
)

private val previewDuplicate = OwnPublication(
    id = "puerta-falsa-tamales",
    title = "Puerta Falsa, tamales",
    category = Category.GASTRONOMY,
    status = PublicationStatus.REJECTED,
    location = GeoPoint(4.59791, -74.0746),
    photos = 1,
    submittedAt = previewNow.minus(1, ChronoUnit.DAYS),
    rejection = Rejection(
        reason = RejectionReason.DUPLICATE,
        message = "Es La Puerta Falsa, que ya está publicada.",
        reviewerName = "Laura M.",
        rejectedAt = previewNow.minus(45, ChronoUnit.MINUTES),
        canResubmit = false,
        duplicateOf = samplePois.first { it.id == "la-puerta-falsa" },
    ),
)

@Preview(name = "24.a · claro", widthDp = 360, heightDp = 900)
@Composable
private fun RejectedLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { RejectedPublicationScreen(RejectedPublicationUiState(RejectedContent.Loaded(previewRejected)), RejectedCallbacks()) }
}

@Preview(name = "24 · duplicado · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun DuplicateDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { RejectedPublicationScreen(RejectedPublicationUiState(RejectedContent.Loaded(previewDuplicate)), RejectedCallbacks()) }
}

@Preview(name = "23 · eliminar · claro", widthDp = 360, heightDp = 800)
@Composable
private fun DeleteDialogPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        RejectedPublicationScreen(
            RejectedPublicationUiState(RejectedContent.Loaded(previewRejected), delete = DeleteDialogState()),
            RejectedCallbacks(),
        )
    }
}
