package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.SentSummary
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow

/**
 * 20 · Confirmación. Tres variantes con el mismo diseño centrado de 20.a:
 * - Enviada: «Pendiente · Suele tardar menos de 48 horas» y, si fue la primera, la insignia con sus puntos.
 * - Marcada como posible duplicado (17B): la marca junto al estado y «El moderador revisará que este lugar no esté
 *   repetido». El lienzo de la marca no ofrece «Publicar otro lugar».
 * - Sin conexión: quedó en la cola, «Pendiente de envío».
 * El foco del lector va al titular al entrar.
 */
@Composable
fun PublishSentScreen(
    summary: SentSummary,
    onMyPublications: () -> Unit,
    onPublishAnother: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    val title = stringResource(if (summary.queued) R.string.publish_sent_queued_title else R.string.publish_sent_title)
    // 20 marcada: el anuncio al llegar suma la revisión de duplicado.
    val spokenTitle = if (summary.possibleDuplicate && !summary.queued) "$title. ${stringResource(R.string.publish_sent_duplicate_info)}" else title
    Column(modifier.fillMaxSize().background(scheme.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            val (container, content, icon) = when {
                summary.queued -> Triple(explora.warning.container, explora.warning.content, R.drawable.ic_cloud_off)
                summary.possibleDuplicate -> Triple(PublicationStatus.PENDING.colors.container, PublicationStatus.PENDING.colors.content, R.drawable.ic_hourglass_top)
                else -> Triple(PublicationStatus.VERIFIED.colors.container, PublicationStatus.VERIFIED.colors.content, R.drawable.ic_send)
            }
            Box(Modifier.size(104.dp).background(container, RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(50.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Outfit, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.W600),
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics {
                        heading()
                        contentDescription = spokenTitle
                    },
            )
            Text(body(summary), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp), color = explora.textSecondary, textAlign = TextAlign.Center)
            StatusCard(summary)
            summary.firstPublicationPoints?.takeUnless { summary.queued }?.let { points -> RewardCard(points) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExploraButton(stringResource(R.string.publish_sent_my_publications), onClick = onMyPublications, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
            if (!summary.possibleDuplicate) {
                ExploraButton(stringResource(R.string.publish_sent_another), onClick = onPublishAnother, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
            }
            ExploraButton(stringResource(R.string.publish_sent_explore), onClick = onExplore, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
        }
    }
}

@Composable
private fun body(summary: SentSummary) = when {
    summary.queued -> buildAnnotatedString { append(stringResource(R.string.publish_sent_queued_body)) }
    else -> {
        val template = stringResource(if (summary.possibleDuplicate) R.string.publish_sent_duplicate_body else R.string.publish_sent_body, TITLE_MARK)
        val (before, after) = template.split(TITLE_MARK, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append(summary.title) }
            append(after)
        }
    }
}

/** Marca temporal donde va el título en negrita dentro del texto traducible. */
private const val TITLE_MARK = "\u0000"

/** El estado de la publicación: «Pendiente» y cuánto suele tardar, con la marca de duplicado o sin conexión. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(summary: SentSummary) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .background(scheme.surfaceContainerLowest, shape)
            .semantics(mergeDescendants = true) { }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (summary.queued) {
                PendingSendBadge()
            } else {
                StatusBadge(PublicationStatus.PENDING, label = stringResource(if (summary.possibleDuplicate) R.string.publish_sent_pending_review else R.string.status_pending))
                if (summary.possibleDuplicate) DuplicateFlag()
            }
        }
        val note = when {
            summary.queued -> R.string.publish_sent_queued_note
            summary.possibleDuplicate -> R.string.publish_sent_duplicate_info
            else -> R.string.publish_sent_wait
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (summary.possibleDuplicate && !summary.queued) {
                Icon(painterResource(R.drawable.ic_info), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
            }
            Text(stringResource(note), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = explora.textSecondary)
        }
    }
}

/** «Pendiente de envío» (20 sin conexión): con los colores de aviso y la nube tachada, nunca solo color. */
@Composable
private fun PendingSendBadge() {
    val warning = MaterialTheme.exploraColors.warning
    Row(
        Modifier.background(warning.container, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), null, tint = warning.content, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(stringResource(R.string.publish_sent_pending_send), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = warning.content)
    }
}

/** «Ganaste la insignia «Primera publicación» · +20 puntos». */
@Composable
private fun RewardCard(points: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.tertiaryContainer, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_military_tech), null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(22.dp.scaledWithFont()))
        Text(
            stringResource(R.string.publish_sent_reward, points),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.W600),
            color = scheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "20.a · enviada · claro", widthDp = 360, heightDp = 800)
@Composable
private fun SentPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        PublishSentScreen(SentSummary("Café Las Acacias", possibleDuplicate = false, queued = false, firstPublicationPoints = 20), {}, {}, {})
    }
}

@Preview(name = "20 · marcada · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun SentMarkedPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        PublishSentScreen(SentSummary("Café La Fonda", possibleDuplicate = true, queued = false), {}, {}, {})
    }
}

@Preview(name = "20 · sin conexión · claro", widthDp = 360, heightDp = 800)
@Composable
private fun SentQueuedPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        PublishSentScreen(SentSummary("Café Las Acacias", possibleDuplicate = false, queued = true), {}, {}, {})
    }
}
