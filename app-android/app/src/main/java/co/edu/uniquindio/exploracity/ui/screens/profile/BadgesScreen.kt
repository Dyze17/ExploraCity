package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.UserLevel
import co.edu.uniquindio.exploracity.domain.model.inDisplayOrder
import co.edu.uniquindio.exploracity.ui.components.BadgeCard
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.dashedBorder
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ContainerColors
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileContent
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileUiState
import co.edu.uniquindio.exploracity.viewmodel.OwnProfileViewModel

/** 27 · Niveles e insignias, conectado a su ViewModel. */
@Composable
fun BadgesRoute(onBack: () -> Unit, viewModel: OwnProfileViewModel = viewModel(factory = OwnProfileViewModel.factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    BadgesScreen(
        state = state,
        callbacks = BadgesCallbacks(
            onBack = onBack,
            onOpenBadge = viewModel::onOpenBadge,
            onDismissBadge = viewModel::onDismissBadge,
            onRetry = viewModel::onRetry,
        ),
    )
}

class BadgesCallbacks(
    val onBack: () -> Unit = {},
    val onOpenBadge: (String) -> Unit = {},
    val onDismissBadge: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * 27 · «Tu camino»: cada nivel con su rango de puntos; el actual con borde y «Estás aquí», no solo con color. Debajo,
 * las insignias en dos columnas (una con fuente grande), primero las desbloqueadas. Tocar una abre 27A.
 */
@Composable
fun BadgesScreen(state: OwnProfileUiState, callbacks: BadgesCallbacks, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(title = stringResource(R.string.badges_title), onBack = callbacks.onBack)
        OwnProfileContentFrame(state, callbacks.onRetry, skeleton = { BadgesSkeleton() }, Modifier.weight(1f)) { content ->
            BadgesBody(content.profile, callbacks.onOpenBadge)
        }
    }
    BadgeSheet(state.openBadge, callbacks.onDismissBadge)
}

@Composable
private fun BadgesBody(profile: OwnProfile, onOpenBadge: (String) -> Unit) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val columns = if (stacked) 1 else 2
    val rows = profile.badges.inDisplayOrder().chunked(columns)
    val points = profile.author.points
    val current = profile.author.level
    // Sin barra inferior en 27: la lista termina por encima de la barra de gestos, como el perfil público (31).
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "path-title") { SectionTitle(stringResource(R.string.badges_path)) }
        items(UserLevel.entries, key = { "level-${it.name}" }) { level -> LevelRow(level, current, points, stacked) }
        item(key = "badges-title") { SectionTitle(stringResource(R.string.badges_section), Modifier.padding(top = 8.dp)) }
        items(rows, key = { row -> row.joinToString { it.id } }) { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { badge ->
                    BadgeCard(badge, onClick = { onOpenBadge(badge.id) }, Modifier.weight(1f).fillMaxHeight(), horizontal = stacked)
                }
                // La última fila impar deja su hueco: la tarjeta no se estira a todo el ancho.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private enum class LevelState { ACHIEVED, CURRENT, LOCKED }

/**
 * Logrado: tarjeta con su icono y «✓ Logrado». Actual: primaryContainer con borde de 2 dp y «Estás aquí». Por venir:
 * fondo apagado, borde punteado, candado y «Faltan 160». El lector lo dice en una frase: «Embajador Local, desde 500
 * puntos, faltan 160 puntos». Con fuente grande ([stacked]) el estado va bajo el rango y el nombre usa todo el ancho.
 */
@Composable
private fun LevelRow(level: UserLevel, current: UserLevel, points: Int, stacked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val state = when {
        level == current -> LevelState.CURRENT
        level.ordinal < current.ordinal -> LevelState.ACHIEVED
        else -> LevelState.LOCKED
    }
    val label = stringResource(level.labelRes)
    val max = level.maxPoints
    val range = if (max != null) {
        pluralStringResource(R.plurals.level_range, max, level.minPoints, max)
    } else {
        pluralStringResource(R.plurals.level_range_from, level.minPoints, level.minPoints)
    }
    val remaining = level.minPoints - points
    val (status, spokenStatus) = when (state) {
        LevelState.ACHIEVED -> stringResource(R.string.level_achieved).let { it to it }
        LevelState.CURRENT -> stringResource(R.string.level_current).let { it to it }
        LevelState.LOCKED ->
            pluralStringResource(R.plurals.level_remaining, remaining, remaining) to
                pluralStringResource(R.plurals.level_remaining_spoken, remaining, remaining)
    }
    val spoken = stringResource(R.string.level_row_spoken, label, range, spokenStatus)
    val shape = RoundedCornerShape(12.dp)

    val (container, nameColor, rangeColor) = when (state) {
        LevelState.ACHIEVED -> Triple(scheme.surfaceContainerLowest, scheme.onSurface, scheme.onSurfaceVariant)
        LevelState.CURRENT -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, explora.onPrimaryContainerAccent)
        LevelState.LOCKED -> Triple(scheme.surfaceContainer, explora.iconSecondary, scheme.onSurfaceVariant)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                when (state) {
                    LevelState.ACHIEVED -> Modifier.exploraShadow(ExploraElevation.Card, shape, explora.shadow).background(container, shape)
                    LevelState.CURRENT -> Modifier.background(container, shape).border(2.dp, scheme.primary, shape)
                    LevelState.LOCKED -> Modifier.background(container, shape).dashedBorder(1.dp, explora.outlineDisabled, 12.dp)
                },
            )
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            LevelState.ACHIEVED -> IconCircle(level.iconRes, level.achievedColors())
            LevelState.CURRENT -> IconCircle(level.iconRes, ContainerColors(scheme.surfaceContainerLowest, explora.onPrimaryContainerAccent))
            LevelState.LOCKED -> IconCircle(R.drawable.ic_lock, ContainerColors(scheme.surfaceContainerHighest, explora.textPlaceholder))
        }
        val statusColor = when (state) {
            LevelState.ACHIEVED -> explora.status.verified.content
            LevelState.CURRENT -> explora.onPrimaryContainerAccent
            LevelState.LOCKED -> scheme.onSurfaceVariant
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700), color = nameColor)
            Text(range, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W400), color = rangeColor)
            if (stacked) LevelStatus(status, statusColor, achieved = state == LevelState.ACHIEVED)
        }
        if (!stacked) LevelStatus(status, statusColor, achieved = state == LevelState.ACHIEVED)
    }
}

/** «✓ Logrado», «Estás aquí» o «Faltan 160». */
@Composable
private fun LevelStatus(status: String, color: Color, achieved: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (achieved) {
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = color, modifier = Modifier.size(14.dp.scaledWithFont()))
        }
        Text(status, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700), color = color)
    }
}

/**
 * Círculo de un nivel ya logrado: el color de su LevelChip, salvo Embajador Local, cuyo primary lleno no se ve como
 * círculo sobre la tarjeta blanca (solo se llega a verlo logrado si en el futuro hay un nivel más).
 */
@Composable
private fun UserLevel.achievedColors(): ContainerColors =
    if (this == UserLevel.LOCAL_AMBASSADOR) ContainerColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.exploraColors.onPrimaryContainerAccent) else colors

@Composable
private fun IconCircle(@DrawableRes icon: Int, colors: ContainerColors) {
    Box(Modifier.size(36.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
    }
}

/** Mientras carga: cuatro niveles y dos filas de insignias, con un solo anuncio. */
@Composable
private fun BadgesSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.badges_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBlock(brush, Modifier.fillMaxWidth(0.3f).height(14.dp))
        repeat(4) { SkeletonBlock(brush, Modifier.fillMaxWidth().height(58.dp), RoundedCornerShape(12.dp)) }
        SkeletonBlock(brush, Modifier.padding(top = 8.dp).fillMaxWidth(0.25f).height(14.dp))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { SkeletonBlock(brush, Modifier.weight(1f).height(130.dp), RoundedCornerShape(14.dp)) }
            }
        }
    }
}

@Preview(name = "27.a · claro", widthDp = 360, heightDp = 1200)
@Composable
private fun BadgesLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { BadgesScreen(OwnProfileUiState(OwnProfileContent.Loaded(previewOwnProfile)), BadgesCallbacks()) }
}

@Preview(name = "27.a · oscuro", widthDp = 360, heightDp = 1200)
@Composable
private fun BadgesDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { BadgesScreen(OwnProfileUiState(OwnProfileContent.Loaded(previewOwnProfile)), BadgesCallbacks()) }
}
