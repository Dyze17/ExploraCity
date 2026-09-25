package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.sampleBadges
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraProgressBar
import co.edu.uniquindio.exploracity.ui.components.NoNavigationBarScrim
import co.edu.uniquindio.exploracity.ui.components.SheetHandle
import co.edu.uniquindio.exploracity.ui.components.badgeLockedProgress
import co.edu.uniquindio.exploracity.ui.components.badgeProgressText
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ContainerColors
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ScrimAlpha
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * 27A · Hoja «Cómo se obtiene» al tocar una insignia (en 27 y en la vista previa de 26). Está abierta mientras
 * [badge] no sea null; deslizar abajo o «Entendido» la cierran. Al cerrarse desde fuera se anima la salida con la
 * última insignia mostrada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeSheet(badge: Badge?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lastShown by remember { mutableStateOf<Badge?>(null) }
    LaunchedEffect(badge) {
        if (badge != null) lastShown = badge
    }
    LaunchedEffect(badge == null) {
        if (badge == null && lastShown != null) {
            sheetState.hide()
            lastShown = null
        }
    }
    val shown = badge ?: lastShown ?: return
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.9f }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha.Sheet),
        dragHandle = null,
    ) {
        BadgeSheetContent(shown, onDismiss, Modifier.heightIn(max = maxHeight))
    }
}

/**
 * Estado con icono + texto (Bloqueada / Desbloqueada), la barra con su frase para el lector y cómo se obtiene. Ya
 * desbloqueada no hay barra ni consejo: no queda nada por conseguir.
 */
@Composable
private fun BadgeSheetContent(badge: Badge, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    NoNavigationBarScrim()
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors

    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = if (badge.unlocked) badge.colors else ContainerColors(scheme.surfaceContainerHigh, explora.textPlaceholder)
                Box(Modifier.size(56.dp).background(icon.container, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (badge.unlocked) badge.iconRes else R.drawable.ic_lock),
                        contentDescription = null,
                        tint = icon.content,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        badge.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 28.sp),
                        modifier = Modifier.focusRequester(titleFocus).focusable().semantics { heading() },
                    )
                    StateChip(badge)
                }
            }
            if (!badge.unlocked) {
                val progress = badgeProgressText(badge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExploraProgressBar(badge.fraction, description = progress)
                    Text(
                        progress,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                        color = scheme.onSurfaceVariant,
                        // La barra ya lo dice.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.badge_how_to),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
                    color = explora.textSecondary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(badge.howTo, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp), color = explora.textSecondary)
            }
            val tip = badge.tip
            if (!badge.unlocked && tip != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.tertiaryContainer, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(20.dp.scaledWithFont()))
                    Text(tip, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = scheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        ExploraButton(stringResource(R.string.badge_got_it), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

/** «🔒 Bloqueada · 3 de 10» o «✓ Desbloqueada». */
@Composable
private fun StateChip(badge: Badge) {
    val explora = MaterialTheme.exploraColors
    val (colors, icon, text) = if (badge.unlocked) {
        Triple(explora.status.verified, R.drawable.ic_check_circle, stringResource(R.string.badge_unlocked))
    } else {
        Triple(
            ContainerColors(MaterialTheme.colorScheme.surfaceContainer, explora.iconSecondary),
            R.drawable.ic_lock,
            badgeLockedProgress(badge),
        )
    }
    // «Bloqueada · 3 de 10» se oiría con el punto medio; la barra dice el avance completo.
    val spoken = stringResource(if (badge.unlocked) R.string.badge_unlocked else R.string.badge_locked)
    Row(
        Modifier
            .background(colors.container, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W700), color = colors.content)
    }
}

@Preview(name = "27A · claro", widthDp = 360)
@Composable
private fun BadgeSheetLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) { BadgeSheetContent(sampleBadges.first { !it.unlocked }, {}) }
    }
}

@Preview(name = "27A · oscuro · desbloqueada", widthDp = 360)
@Composable
private fun BadgeSheetDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) { BadgeSheetContent(sampleBadges.first { it.unlocked }, {}) }
    }
}
