package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewUrgency
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.PhotoThumbnail
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import java.time.Duration
import java.time.Instant

/** Colores de la urgencia: el chip y la franja lateral. Nunca solo color: el chip lleva icono y texto. */
private class UrgencyColors(val container: Color, val content: Color, val stripe: Color)

@Composable
private fun ReviewUrgency.colors(): UrgencyColors {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    return when (this) {
        ReviewUrgency.HIGH -> UrgencyColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
        ReviewUrgency.MEDIUM -> UrgencyColors(explora.warning.container, explora.warning.content, explora.warningAccent)
        ReviewUrgency.NORMAL -> UrgencyColors(scheme.surfaceContainerHigh, explora.textSecondary, scheme.outlineVariant)
    }
}

/** «Lleva 3 días» desde un día; antes, «Hace 4 horas» o «Hace 20 minutos». */
@Composable
internal fun waitingText(item: ReviewItem, now: Instant): String {
    val waited = Duration.between(item.submittedAt, now)
    val days = waited.toDays().toInt()
    val hours = waited.toHours().toInt()
    val minutes = waited.toMinutes().toInt().coerceAtLeast(1)
    return when {
        days >= 1 -> pluralStringResource(R.plurals.moderation_waiting_days, days, days)
        hours >= 1 -> pluralStringResource(R.plurals.moderation_waiting_hours, hours, hours)
        else -> pluralStringResource(R.plurals.moderation_waiting_minutes, minutes, minutes)
    }
}

/** Lo que oye el lector: «lleva 3 días esperando», «llegó hace 4 horas». */
@Composable
internal fun waitingSpoken(item: ReviewItem, now: Instant): String {
    val waited = Duration.between(item.submittedAt, now)
    val days = waited.toDays().toInt()
    val hours = waited.toHours().toInt()
    val minutes = waited.toMinutes().toInt().coerceAtLeast(1)
    return when {
        days >= 1 -> pluralStringResource(R.plurals.moderation_waiting_days_spoken, days, days)
        hours >= 1 -> pluralStringResource(R.plurals.moderation_waiting_hours_spoken, hours, hours)
        else -> pluralStringResource(R.plurals.moderation_waiting_minutes_spoken, minutes, minutes)
    }
}

/** El encabezado de 33: «lleva 3 días», «llegó hace 4 horas». */
@Composable
internal fun reviewWaitingText(item: ReviewItem, now: Instant): String {
    val waited = Duration.between(item.submittedAt, now)
    val days = waited.toDays().toInt()
    val hours = waited.toHours().toInt()
    val minutes = waited.toMinutes().toInt().coerceAtLeast(1)
    return when {
        days >= 1 -> pluralStringResource(R.plurals.review_waiting_days, days, days)
        hours >= 1 -> pluralStringResource(R.plurals.review_waiting_hours, hours, hours)
        else -> pluralStringResource(R.plurals.review_waiting_minutes, minutes, minutes)
    }
}

/** Urgencia con icono y texto: rojo desde 3 días, ámbar desde 1 día (32). */
@Composable
internal fun UrgencyChip(item: ReviewItem, now: Instant, modifier: Modifier = Modifier) {
    val urgency = item.urgency(now)
    val colors = urgency.colors()
    Row(
        modifier.background(colors.container, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (urgency == ReviewUrgency.HIGH) R.drawable.ic_priority_high else R.drawable.ic_schedule),
            null,
            tint = colors.content,
            modifier = Modifier.size(14.dp.scaledWithFont()),
        )
        Text(waitingText(item, now), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.W600), color = colors.content)
    }
}

/** «Moderador» junto al título de 32 y 37. */
@Composable
internal fun ModeratorBadge(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.background(scheme.tertiaryContainer, MaterialTheme.shapes.extraLarge).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_shield_person), null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(stringResource(R.string.moderation_badge), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = scheme.onTertiaryContainer)
    }
}

/**
 * Una pendiente de la cola (32): foto, título, categoría (o «Posible duplicado») y urgencia, y quién la publicó. La
 * franja de la izquierda repite la urgencia; el lector oye todo junto, como dice el README.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewCard(item: ReviewItem, now: Instant, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = MaterialTheme.shapes.medium
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val photos = pluralStringResource(R.plurals.moderation_photos, item.photos.size, item.photos.size)
    val authorName = item.author.author.name
    val duplicate = item.duplicate
    val authorLine = when {
        duplicate == null -> stringResource(R.string.moderation_card_author, authorName, stringResource(item.author.author.level.labelRes), photos)
        duplicate.candidates.size == 1 -> stringResource(R.string.moderation_card_similar, authorName, duplicate.candidates.first().poi.title)
        else -> pluralStringResource(R.plurals.moderation_card_similar_many, duplicate.candidates.size, authorName, duplicate.candidates.size)
    }
    val category = stringResource(item.category.labelRes)
    val waiting = waitingSpoken(item, now)
    val spoken = buildList {
        add(stringResource(R.string.moderation_card_description, item.title, category, waiting, authorName, photos))
        if (duplicate != null) add(stringResource(R.string.moderation_card_duplicate_spoken))
    }.joinToString(", ")
    val stripe = item.urgency(now).colors().stripe
    Row(
        modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            // La franja de urgencia se dibuja detrás: la tarjeta crece con su contenido (fuente grande, chips en dos líneas).
            .drawBehind { drawRect(stripe, size = Size(STRIPE_WIDTH.toPx(), size.height)) }
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
    ) {
        Row(
            Modifier.weight(1f).padding(start = STRIPE_WIDTH + 10.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!stacked) PhotoThumbnail(item.photos.firstOrNull()?.url.orEmpty(), size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = scheme.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (duplicate != null) DuplicateFlag(size = BadgeSize.SMALL) else CategoryTag(item.category)
                    UrgencyChip(item, now)
                }
                Text(authorLine, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = explora.textSecondary)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
    }
}

private val STRIPE_WIDTH = 4.dp
