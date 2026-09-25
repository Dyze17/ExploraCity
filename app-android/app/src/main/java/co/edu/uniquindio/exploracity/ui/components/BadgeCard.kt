package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.ui.theme.ContainerColors
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.formatCount
import co.edu.uniquindio.exploracity.util.spanishColombia

/** Icono de la insignia desbloqueada según lo que cuenta; bloqueada lleva siempre el candado. */
@get:DrawableRes
val Badge.iconRes: Int
    get() = when (metric) {
        BadgeMetric.PUBLICATIONS -> R.drawable.ic_military_tech
        BadgeMetric.VERIFIED_PLACES -> R.drawable.ic_verified
        BadgeMetric.CATEGORY_PLACES -> category?.iconRes ?: R.drawable.ic_military_tech
        BadgeMetric.COMMENTS -> R.drawable.ic_chat_bubble
        BadgeMetric.VISITS -> R.drawable.ic_task_alt
        BadgeMetric.VOTES_RECEIVED -> R.drawable.ic_thumb_up
    }

/**
 * Círculo de la insignia desbloqueada: Primera publicación en primaryContainer y Amigo del verde con los colores de
 * Naturaleza, como en 27.a; las demás siguen la misma idea (su categoría, su estado o un contenedor del tema).
 */
val Badge.colors: ContainerColors
    @Composable
    @ReadOnlyComposable
    get() {
        val scheme = MaterialTheme.colorScheme
        val explora = MaterialTheme.exploraColors
        return when (metric) {
            BadgeMetric.PUBLICATIONS -> ContainerColors(scheme.primaryContainer, explora.onPrimaryContainerAccent)
            BadgeMetric.VERIFIED_PLACES -> explora.status.verified
            BadgeMetric.CATEGORY_PLACES -> category?.colors?.let { ContainerColors(it.container, it.content) }
                ?: ContainerColors(scheme.primaryContainer, explora.onPrimaryContainerAccent)
            BadgeMetric.COMMENTS -> ContainerColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
            BadgeMetric.VISITS -> explora.status.finalized
            BadgeMetric.VOTES_RECEIVED -> explora.warning
        }
    }

/** «3 de 10 lugares verificados»: la unidad depende de lo que cuenta la insignia. */
@Composable
fun badgeProgressText(badge: Badge): String {
    val target = badge.target
    val (shownProgress, shownTarget) = formatCount(badge.progress.coerceAtMost(target)) to formatCount(target)
    return when (badge.metric) {
        BadgeMetric.PUBLICATIONS -> pluralStringResource(R.plurals.badge_progress_publications, target, shownProgress, shownTarget)
        BadgeMetric.VERIFIED_PLACES -> pluralStringResource(R.plurals.badge_progress_verified, target, shownProgress, shownTarget)
        BadgeMetric.CATEGORY_PLACES -> {
            val category = badge.category?.let { stringResource(it.labelRes).lowercase(spanishColombia) }
            if (category != null) {
                pluralStringResource(R.plurals.badge_progress_category, target, shownProgress, shownTarget, category)
            } else {
                pluralStringResource(R.plurals.badge_progress_verified, target, shownProgress, shownTarget)
            }
        }
        BadgeMetric.COMMENTS -> pluralStringResource(R.plurals.badge_progress_comments, target, shownProgress, shownTarget)
        BadgeMetric.VISITS -> pluralStringResource(R.plurals.badge_progress_visits, target, shownProgress, shownTarget)
        BadgeMetric.VOTES_RECEIVED -> pluralStringResource(R.plurals.badge_progress_votes, target, shownProgress, shownTarget)
    }
}

/** «Bloqueada · 3 de 10». */
@Composable
fun badgeLockedProgress(badge: Badge): String =
    stringResource(R.string.badge_locked_progress, formatCount(badge.progress.coerceAtMost(badge.target)), formatCount(badge.target))

/** Lo que dice el lector de una insignia: «10 verificadas, bloqueada, 3 de 10 lugares verificados». */
@Composable
fun badgeSpokenDescription(badge: Badge): String = if (badge.unlocked) {
    stringResource(R.string.badge_unlocked_spoken, badge.name)
} else {
    stringResource(R.string.badge_locked_spoken, badge.name, badgeProgressText(badge))
}

/**
 * Tarjeta de insignia de 27.a (y, con [compact], la vista previa de 26.a). Desbloqueada: tarjeta con sombra, su icono
 * en color y «Desbloqueada» con check. Bloqueada: fondo apagado, borde punteado, candado, «Bloqueada · 3 de 10» y
 * barra; nunca solo color. Toda la tarjeta abre «Cómo se obtiene» (27A). Los textos van a 11 sp como mínimo: el
 * lienzo baja a 10 sp, pero aquí ya son lo más pequeño de la app. Con [horizontal] (fuente grande, una columna) el
 * icono va a la izquierda y el texto al lado: apilado, cada tarjeta ocupaba media pantalla.
 */
@Composable
fun BadgeCard(badge: Badge, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, horizontal: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(14.dp)
    val spoken = badgeSpokenDescription(badge)
    val openLabel = stringResource(R.string.badge_open)
    val unlocked = badge.unlocked
    val cardModifier = modifier
        .then(if (unlocked) Modifier.exploraShadow(ExploraElevation.Card, shape, explora.shadow) else Modifier)
        .clip(shape)
        .background(if (unlocked) scheme.surfaceContainerLowest else scheme.surfaceContainer)
        .then(if (unlocked) Modifier else Modifier.dashedBorder(1.dp, explora.outlineDisabled, 14.dp))
        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
        .clearAndSetSemantics { contentDescription = spoken }
        .padding(if (compact) 10.dp else 12.dp)

    if (horizontal) {
        Row(cardModifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BadgeCircle(badge, compact)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BadgeName(badge, TextAlign.Start)
                if (!compact) BadgeState(badge, TextAlign.Start)
            }
        }
    } else {
        Column(cardModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BadgeCircle(badge, compact)
            BadgeName(badge, TextAlign.Center)
            if (!compact) BadgeState(badge, TextAlign.Center)
        }
    }
}

@Composable
private fun BadgeCircle(badge: Badge, compact: Boolean) {
    val circle = if (badge.unlocked) {
        badge.colors
    } else {
        ContainerColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.exploraColors.textPlaceholder)
    }
    Box(Modifier.size(if (compact) 44.dp else 48.dp).background(circle.container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(if (badge.unlocked) badge.iconRes else R.drawable.ic_lock),
            contentDescription = null,
            tint = circle.content,
            modifier = Modifier.size(if (badge.unlocked) 24.dp else 22.dp),
        )
    }
}

@Composable
private fun BadgeName(badge: Badge, align: TextAlign) {
    Text(
        badge.name,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.W700),
        color = if (badge.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.exploraColors.iconSecondary,
        textAlign = align,
    )
}

/** «✓ Desbloqueada» o «Bloqueada · 3 de 10» con su barra. */
@Composable
private fun BadgeState(badge: Badge, align: TextAlign) {
    val explora = MaterialTheme.exploraColors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.W700)
    if (badge.unlocked) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = explora.status.verified.content,
                modifier = Modifier.size(13.dp.scaledWithFont()),
            )
            Text(stringResource(R.string.badge_unlocked), style = style, color = explora.status.verified.content)
        }
    } else {
        Text(badgeLockedProgress(badge), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
        ExploraProgressBar(badge.fraction, height = 6.dp, color = explora.textPlaceholder)
    }
}
