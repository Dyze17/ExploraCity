package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.sampleBadges
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.domain.model.LevelProgress
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserLevel
import co.edu.uniquindio.exploracity.domain.model.inDisplayOrder
import co.edu.uniquindio.exploracity.ui.components.BadgeCard
import co.edu.uniquindio.exploracity.ui.components.ExploraProgressBar
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.InitialsAvatar
import co.edu.uniquindio.exploracity.ui.components.LevelChip
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.components.spokenDescription
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.formatMonth
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileContent
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileUiState
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileViewModel
import java.time.YearMonth

/** 26 · Perfil propio, conectado a su ViewModel. */
@Composable
fun OwnProfileRoute(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenPublications: (PublicationStatus?) -> Unit,
    viewModel: OwnProfileViewModel = viewModel(factory = OwnProfileViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    OwnProfileScreen(
        state = state,
        callbacks = OwnProfileCallbacks(
            onOpenSettings = onOpenSettings,
            onEditProfile = onEditProfile,
            onOpenBadges = onOpenBadges,
            onOpenPublications = onOpenPublications,
            onOpenBadge = viewModel::onOpenBadge,
            onDismissBadge = viewModel::onDismissBadge,
            onRetry = viewModel::onRetry,
        ),
    )
}

class OwnProfileCallbacks(
    val onOpenSettings: () -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onOpenBadges: () -> Unit = {},
    /** null: todas («Ver mis 7 publicaciones»). */
    val onOpenPublications: (PublicationStatus?) -> Unit = {},
    val onOpenBadge: (String) -> Unit = {},
    val onDismissBadge: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * 26 · Cabecera, puntos con el camino al siguiente nivel, las cifras de Mis publicaciones (cada una filtra 22 por su
 * estado) y la vista previa de las insignias. La tarjeta de puntos abre 27, como en el prototipo; tocar una insignia
 * abre su hoja (27A) aquí mismo.
 */
@Composable
fun OwnProfileScreen(state: OwnProfileUiState, callbacks: OwnProfileCallbacks, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(
            title = stringResource(R.string.own_profile_title),
            actions = {
                IconButton(onClick = callbacks.onOpenSettings) {
                    Icon(
                        painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.own_profile_settings),
                        tint = MaterialTheme.exploraColors.textSecondary,
                    )
                }
            },
        )
        OwnProfileContentFrame(state, callbacks.onRetry, skeleton = { OwnProfileSkeleton() }, Modifier.weight(1f)) { content ->
            OwnProfileBody(content.profile, callbacks)
        }
    }
    BadgeSheet(state.openBadge, callbacks.onDismissBadge)
}

@Composable
private fun OwnProfileBody(profile: OwnProfile, callbacks: OwnProfileCallbacks) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(profile, callbacks.onEditProfile)
        PointsCard(profile.author.points, callbacks.onOpenBadges)
        Publications(profile.publications, callbacks.onOpenPublications)
        BadgesPreview(profile, callbacks)
    }
}

/** Avatar, nombre, nivel y «Residente · Bogotá · desde marzo»; el lápiz abre Editar perfil (28). */
@Composable
private fun Header(profile: OwnProfile, onEdit: () -> Unit) {
    val author = profile.author
    val residency = stringResource(if (profile.residency == Residency.RESIDENT) R.string.residency_resident else R.string.residency_visitor)
    val since = stringResource(
        R.string.own_profile_since,
        formatMonth(profile.memberSince, withYear = profile.memberSince.year != YearMonth.now().year),
    )
    val levelDescription = author.level.spokenDescription()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(author, size = 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                author.name,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 20.sp, lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            LevelChip(author.level, large = true, modifier = Modifier.clearAndSetSemantics { contentDescription = levelDescription })
            Text(
                stringResource(R.string.profile_summary, residency, profile.city, since),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.own_profile_edit), tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/**
 * «340 puntos · 160 para Embajador Local», la barra y los dos niveles en sus extremos. Toda la tarjeta abre 27 y el
 * lector la dice como pide el README: «340 de 500 puntos, 68 por ciento hacia Embajador Local».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointsCard(points: Int, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val progress = LevelProgress(points)
    val next = progress.next
    val nextLabel = next?.let { stringResource(it.labelRes) }
    val spoken = if (next != null && nextLabel != null) {
        pluralStringResource(R.plurals.own_profile_progress_spoken, next.minPoints, points, next.minPoints, progress.percent, nextLabel)
    } else {
        pluralStringResource(R.plurals.own_profile_progress_max_spoken, points, points)
    }
    val openLabel = stringResource(R.string.own_profile_open_levels)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.profile_points, points, points),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
                color = explora.textSecondary,
            )
            Text(
                if (nextLabel != null) stringResource(R.string.own_profile_points_to_next, progress.remaining, nextLabel) else stringResource(R.string.own_profile_max_level),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                color = scheme.onSurfaceVariant,
            )
        }
        ExploraProgressBar(progress.fraction, height = 10.dp)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LevelEnd(progress.level)
            if (next != null) LevelEnd(next)
        }
    }
}

@Composable
private fun LevelEnd(level: UserLevel) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(level.iconRes), contentDescription = null, tint = color, modifier = Modifier.size(14.dp.scaledWithFont()))
        Text(stringResource(level.labelRes), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W600), color = color)
    }
}

/** Tres cifras con icono + etiqueta (no solo color), tocables: cada una abre 22 filtrada por su estado. */
@Composable
private fun Publications(counts: PublicationCounts, onOpen: (PublicationStatus?) -> Unit) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.own_profile_publications))
        val tiles: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> StatCard(PublicationStatus.VERIFIED, counts, R.plurals.own_stat_active, R.plurals.own_stat_active_spoken, onOpen, stacked, m) },
            { m -> StatCard(PublicationStatus.FINALIZED, counts, R.plurals.own_stat_finalized, R.plurals.own_stat_finalized_spoken, onOpen, stacked, m) },
            { m -> StatCard(PublicationStatus.PENDING, counts, R.plurals.own_stat_pending, R.plurals.own_stat_pending_spoken, onOpen, stacked, m) },
        )
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        SeeAllPublications(counts.total) { onOpen(null) }
    }
}

@Composable
private fun StatCard(
    status: PublicationStatus,
    counts: PublicationCounts,
    @PluralsRes label: Int,
    @PluralsRes spoken: Int,
    onOpen: (PublicationStatus?) -> Unit,
    stacked: Boolean,
    modifier: Modifier,
) {
    val count = counts[status]
    val colors = status.colors
    val description = pluralStringResource(spoken, count, count)
    val filterLabel = stringResource(R.string.own_stat_filter)
    val shape = RoundedCornerShape(14.dp)
    val cardModifier = modifier
        .clip(shape)
        .background(colors.container)
        .clickable(role = Role.Button, onClickLabel = filterLabel) { onOpen(status) }
        .clearAndSetSemantics { contentDescription = description }
        .padding(12.dp)
    val icon = @Composable {
        Icon(painterResource(status.iconRes), contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp.scaledWithFont()))
    }
    val number = @Composable {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 24.sp), color = colors.content)
    }
    val text = pluralStringResource(label, count)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.W600)
    // Con fuente grande van en columna a lo ancho: en fila (icono, cifra, etiqueta) cada una ocupa una línea y no media pantalla.
    if (stacked) {
        Row(cardModifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            number()
            Text(text, style = labelStyle, color = colors.content, modifier = Modifier.weight(1f))
        }
    } else {
        Column(cardModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            number()
            Text(text, style = labelStyle, color = colors.content)
        }
    }
}

/** «Ver mis 7 publicaciones ›»: todas, también las rechazadas. */
@Composable
private fun SeeAllPublications(total: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val text = if (total == 0) stringResource(R.string.own_profile_see_publications_none) else pluralStringResource(R.plurals.own_profile_see_publications, total, total)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.W600), color = explora.textSecondary, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp.scaledWithFont()))
    }
}

/** «Insignias · 2 de 9» con «Ver todas» (27) y las tres primeras en el orden de 27. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesPreview(profile: OwnProfile, callbacks: OwnProfileCallbacks) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val unlocked = profile.unlockedBadges
    val total = profile.badges.size
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                stringResource(R.string.own_profile_badges, unlocked, total),
                spoken = pluralStringResource(R.plurals.own_profile_badges_spoken, total, unlocked, total),
            )
            SeeAllLink(callbacks.onOpenBadges)
        }
        val preview = profile.badges.inDisplayOrder().take(PREVIEW_BADGES)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview.forEach { badge ->
                    BadgeCard(badge, onClick = { callbacks.onOpenBadge(badge.id) }, Modifier.fillMaxWidth(), compact = true, horizontal = true)
                }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                preview.forEach { badge ->
                    BadgeCard(badge, onClick = { callbacks.onOpenBadge(badge.id) }, Modifier.weight(1f).fillMaxHeight(), compact = true)
                }
            }
        }
    }
}

private const val PREVIEW_BADGES = 3

@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    val spoken = stringResource(R.string.own_profile_see_all_spoken)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.own_profile_see_all),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, spoken: String = text) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
        color = MaterialTheme.exploraColors.textSecondary,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spoken
            heading()
        },
    )
}

/** Mientras carga: silueta de la cabecera, la tarjeta de puntos, las cifras y las insignias, con un solo anuncio. */
@Composable
private fun OwnProfileSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.own_profile_loading)
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(brush, Modifier.size(72.dp), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.5f).height(20.dp))
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.35f).height(22.dp), CircleShape)
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(12.dp))
            }
        }
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(16.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth(0.4f).height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBlock(brush, Modifier.weight(1f).height(92.dp), RoundedCornerShape(14.dp)) }
        }
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(48.dp), MaterialTheme.shapes.medium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { SkeletonBlock(brush, Modifier.weight(1f).height(96.dp), RoundedCornerShape(14.dp)) }
        }
    }
}

internal val previewOwnProfile = OwnProfile(
    author = sampleCurrentUser,
    residency = Residency.RESIDENT,
    city = "Bogotá",
    memberSince = YearMonth.now().withMonth(3),
    publications = PublicationCounts(pending = 2, verified = 2, rejected = 2, finalized = 1),
    badges = sampleBadges,
)

@Preview(name = "26.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun OwnProfileLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { OwnProfileScreen(OwnProfileUiState(OwnProfileContent.Loaded(previewOwnProfile)), OwnProfileCallbacks()) }
}

@Preview(name = "26.b · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun OwnProfileDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { OwnProfileScreen(OwnProfileUiState(OwnProfileContent.Loaded(previewOwnProfile)), OwnProfileCallbacks()) }
}

@Preview(name = "26 · fuente 200 %", widthDp = 360, heightDp = 1400, fontScale = 2f)
@Composable
private fun OwnProfileLargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { OwnProfileScreen(OwnProfileUiState(OwnProfileContent.Loaded(previewOwnProfile)), OwnProfileCallbacks()) }
}
