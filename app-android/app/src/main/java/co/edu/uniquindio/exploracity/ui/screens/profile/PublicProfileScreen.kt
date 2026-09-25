package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.InitialsAvatar
import co.edu.uniquindio.exploracity.ui.components.LevelChip
import co.edu.uniquindio.exploracity.ui.components.POICard
import co.edu.uniquindio.exploracity.ui.components.POICardSkeleton
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.spokenDescription
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.formatDistance
import co.edu.uniquindio.exploracity.viewmodel.ProfileContent
import co.edu.uniquindio.exploracity.viewmodel.PublicProfileUiState
import co.edu.uniquindio.exploracity.viewmodel.PublicProfileViewModel
import co.edu.uniquindio.exploracity.viewmodel.ReportDialogState

/** 31 · Perfil público, conectado a su ViewModel. */
@Composable
fun PublicProfileRoute(
    onBack: () -> Unit,
    onOpenPoi: (String) -> Unit,
    viewModel: PublicProfileViewModel = viewModel(factory = PublicProfileViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PublicProfileScreen(
        state = state,
        callbacks = ProfileCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onOpenPoi = onOpenPoi,
            onOpenReport = viewModel::onOpenReport,
            onReasonChange = viewModel::onReasonChange,
            onDismissReport = viewModel::onDismissReport,
            onConfirmReport = viewModel::onConfirmReport,
            onReportSentShown = viewModel::onReportSentShown,
        ),
    )
}

class ProfileCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenPoi: (String) -> Unit = {},
    val onOpenReport: () -> Unit = {},
    val onReasonChange: (ReportReason) -> Unit = {},
    val onDismissReport: () -> Unit = {},
    val onConfirmReport: () -> Unit = {},
    val onReportSentShown: () -> Unit = {},
)

/**
 * 31 · Solo lo verificado y finalizado, sin correo ni datos de contacto (README). La bandera abre 31A; en el perfil
 * propio no aparece.
 */
@Composable
fun PublicProfileScreen(state: PublicProfileUiState, callbacks: ProfileCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    ReportSentEffect(state.reportSent, snackbarHostState, callbacks.onReportSentShown)
    val profile = state.profile

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            ExploraTopAppBar(
                title = profile?.author?.name ?: stringResource(R.string.profile_title),
                onBack = callbacks.onBack,
                actions = {
                    if (profile != null && !state.isOwn) {
                        IconButton(onClick = callbacks.onOpenReport) {
                            Icon(
                                painterResource(R.drawable.ic_flag),
                                contentDescription = stringResource(R.string.profile_report),
                                tint = MaterialTheme.exploraColors.textSecondary,
                            )
                        }
                    }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    ProfileContent.Loading -> ProfileSkeleton()
                    is ProfileContent.Loaded -> ProfileBody(content.profile, callbacks.onOpenPoi)
                    ProfileContent.Error -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.profile_error_title),
                            body = stringResource(R.string.profile_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) {
                            ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                        }
                    }
                    ProfileContent.Offline -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.profile_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) {
                            ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                        }
                    }
                    ProfileContent.NotFound -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_person,
                            title = stringResource(R.string.profile_not_found_title),
                            body = stringResource(R.string.profile_not_found_body),
                        ) {
                            ExploraButton(stringResource(R.string.navigate_back), onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    if (profile != null) {
        state.report?.let { report ->
            ReportProfileDialog(
                name = profile.author.name,
                state = report,
                onReasonChange = callbacks.onReasonChange,
                onConfirm = callbacks.onConfirmReport,
                onDismiss = callbacks.onDismissReport,
            )
        }
    }
}

@Composable
private fun ReportSentEffect(sent: Boolean, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = stringResource(R.string.report_sent)
    LaunchedEffect(sent) {
        if (!sent) return@LaunchedEffect
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
        currentOnShown()
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ProfileBody(profile: PublicProfile, onOpenPoi: (String) -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { ProfileHeader(profile) }
        item(key = "stats") { ProfileStats(profile) }
        item(key = "places-title") {
            Text(
                stringResource(R.string.profile_places),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = MaterialTheme.exploraColors.textSecondary,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        }
        if (profile.places.isEmpty()) {
            item(key = "no-places") {
                Text(stringResource(R.string.profile_no_places), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.iconSecondary)
            }
        }
        items(profile.places, key = { it.id }) { poi ->
            POICard(
                title = poi.title,
                category = poi.category,
                status = poi.status,
                distance = formatDistance(poi.distanceMeters),
                votes = poi.votes,
                comments = poi.comments,
                onClick = { onOpenPoi(poi.id) },
            )
        }
    }
}

/**
 * Avatar, nombre, nivel y «Residente · Bogotá · 320 puntos», más la biografía si la hay. El nombre ya es el título de
 * la barra: aquí no se repite como encabezado para el lector.
 */
@Composable
private fun ProfileHeader(profile: PublicProfile) {
    val author = profile.author
    val residency = stringResource(if (profile.residency == Residency.RESIDENT) R.string.residency_resident else R.string.residency_visitor)
    val points = pluralStringResource(R.plurals.profile_points, author.points, author.points)
    val levelDescription = author.level.spokenDescription()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(author, size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    author.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LevelChip(author.level, large = true, modifier = Modifier.clearAndSetSemantics { contentDescription = levelDescription })
                Text(
                    stringResource(R.string.profile_summary, residency, profile.city, points),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        profile.bio?.let { bio ->
            Text(bio, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp), color = MaterialTheme.exploraColors.textSecondary)
        }
    }
}

/** Tres cifras con el color de su estado (verificada, finalizada) y las insignias. Con fuente grande, en columna. */
@Composable
private fun ProfileStats(profile: PublicProfile) {
    val status = MaterialTheme.exploraColors.status
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> StatTile(profile.verifiedCount, R.plurals.profile_stat_verified, R.plurals.profile_stat_verified_spoken, status.verified.container, status.verified.content, status.verified.content, m) },
        { m -> StatTile(profile.finalizedCount, R.plurals.profile_stat_finalized, R.plurals.profile_stat_finalized_spoken, status.finalized.container, status.finalized.content, status.finalized.content, m) },
        { m ->
            StatTile(
                profile.badges,
                R.plurals.profile_stat_badges,
                R.plurals.profile_stat_badges_spoken,
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.exploraColors.textSecondary,
                MaterialTheme.exploraColors.iconSecondary,
                m,
            )
        },
    )
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun StatTile(
    count: Int,
    @PluralsRes label: Int,
    @PluralsRes spoken: Int,
    container: Color,
    numberColor: Color,
    labelColor: Color,
    modifier: Modifier,
) {
    val description = pluralStringResource(spoken, count, count)
    Column(
        modifier
            .background(container, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W700, fontSize = 22.sp), color = numberColor)
        Text(pluralStringResource(label, count), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp), color = labelColor)
    }
}

/** Mientras carga: silueta de la cabecera, las cifras y dos tarjetas con un solo anuncio (como 11). */
@Composable
private fun ProfileSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.profile_loading)
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
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(brush, Modifier.size(72.dp), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.55f).height(20.dp))
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.4f).height(18.dp), CircleShape)
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(12.dp))
            }
        }
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(14.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth(0.8f).height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBlock(brush, Modifier.weight(1f).height(64.dp), RoundedCornerShape(14.dp)) }
        }
        POICardSkeleton(brush)
        POICardSkeleton(brush, titleWidth = 0.6f)
    }
}

private val previewProfile = PublicProfile(
    author = Author("camilo-r", "Camilo R.", points = 320),
    residency = Residency.RESIDENT,
    city = "Bogotá",
    bio = "Guío caminatas por el centro los sábados. Me obsesionan las tiendas de barrio que llevan más de 40 años abiertas.",
    places = samplePois.take(3),
    badges = 9,
)

@Preview(name = "31.a · claro", widthDp = 360, heightDp = 900)
@Composable
private fun ProfileLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { PublicProfileScreen(PublicProfileUiState(ProfileContent.Loaded(previewProfile)), ProfileCallbacks()) }
}

@Preview(name = "31A · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun ReportDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        PublicProfileScreen(
            PublicProfileUiState(ProfileContent.Loaded(previewProfile), report = ReportDialogState(reason = ReportReason.SPAM)),
            ProfileCallbacks(),
        )
    }
}
