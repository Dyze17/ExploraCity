package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

// Piezas de las pantallas donde se decide con un motivo: rechazar (35) y cambiar estado (36).

/** Los motivos de 35, en su orden (A1): el duplicado primero. */
internal val RejectionReason.labelRes: Int
    get() = when (this) {
        RejectionReason.DUPLICATE -> R.string.reject_reason_duplicate
        RejectionReason.PHOTO -> R.string.reject_reason_photo
        RejectionReason.LOCATION -> R.string.reject_reason_location
        RejectionReason.INAPPROPRIATE -> R.string.reject_reason_inappropriate
        RejectionReason.OTHER -> R.string.reject_reason_other
    }

/** El motivo dicho en «Resueltas»: «Otro motivo», sin el «(lo escribo)» de la opción. */
internal val RejectionReason.shortLabelRes: Int
    get() = if (this == RejectionReason.OTHER) R.string.reject_reason_other_short else labelRes

/** Los motivos de finalizar de 36.a. */
internal val FinalizeReason.labelRes: Int
    get() = when (this) {
        FinalizeReason.CLOSED -> R.string.finalize_reason_closed
        FinalizeReason.EVENT_ENDED -> R.string.finalize_reason_event
        FinalizeReason.MERGED -> R.string.finalize_reason_merged
    }

/** Fondo, contenido y borde de una opción elegida. */
internal class ChoiceColors(val container: Color, val content: Color, val border: Color)

/** La opción elegida de 35.a y 36.a: primaryContainer con borde primary. */
@Composable
internal fun primaryChoiceColors(): ChoiceColors =
    ChoiceColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.exploraColors.onPrimaryContainerAccent, MaterialTheme.colorScheme.primary)

/**
 * Una opción de un grupo de una sola elección (35 y 36): fila de 48 dp con borde. La elegida lleva fondo, borde de 2 dp
 * y el radio marcado (o un check, si la opción tiene su propio [icon]): el estado no depende solo del color. [extra] va
 * debajo de la etiqueta mientras está elegida, fuera de la parte que se elige (el enlace al original en 35).
 */
@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    colors: ChoiceColors = primaryChoiceColors(),
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(12.dp)
    val content = if (selected) colors.content else explora.textSecondary
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.container else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.border else scheme.outline, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leading = icon ?: if (selected) R.drawable.ic_radio_button_checked else R.drawable.ic_radio_button_unchecked
            val leadingTint = when {
                selected -> colors.content
                icon != null -> iconTint ?: explora.iconSecondary
                else -> scheme.onSurfaceVariant
            }
            Icon(painterResource(leading), null, tint = leadingTint, modifier = Modifier.size(20.dp.scaledWithFont()))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
                color = content,
            )
            if (icon != null && selected) Icon(painterResource(R.drawable.ic_check), null, tint = content, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
        if (selected && extra != null) {
            Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { extra() }
        }
    }
}

/**
 * Un grupo de opciones con su título («Motivo (obligatorio)») y, si falta elegir, el borde de error alrededor y el
 * mensaje junto a él (35.b). El lector oye el título al entrar al grupo.
 */
@Composable
internal fun ChoiceGroup(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val requiredMark = stringResource(R.string.decision_required)
    val title = buildAnnotatedString {
        append(label)
        if (required) {
            append(" ")
            withStyle(SpanStyle(color = scheme.error)) { append(requiredMark) }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = if (error != null) scheme.error else MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
        val spoken = if (required) "$label $requiredMark" else label
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (error != null) Modifier.border(2.dp, scheme.error, RoundedCornerShape(14.dp)).padding(8.dp) else Modifier)
                .selectableGroup()
                .semantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
        if (error != null) InlineError(error)
    }
}

/** «Elige un motivo para continuar.»: icono y texto en color error, anunciado al aparecer. */
@Composable
internal fun InlineError(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.error)
    }
}

/** Una nota con icono sobre un fondo suave: lo que pasará al confirmar (36), o la nota de quien publicó (33A). */
@Composable
internal fun HintCard(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_info,
    title: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.exploraColors.textSecondary,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.W700), color = content)
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = content)
        }
    }
}

/**
 * Un botón que se ve deshabilitado mientras falte algo, pero que al tocarlo dice qué falta ([onExplain]): «permanece
 * deshabilitado con explicación, sin diálogos de bloqueo» (35.b). El lector oye un botón no disponible y por qué.
 */
@Composable
internal fun ExplainedButton(
    text: String,
    onClick: () -> Unit,
    onExplain: () -> Unit,
    enabled: Boolean,
    disabledReason: String?,
    modifier: Modifier = Modifier,
    style: ExploraButtonStyle = ExploraButtonStyle.PRIMARY,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    Box(modifier) {
        ExploraButton(
            text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            enabled = enabled || loading,
            disabledReason = disabledReason,
            loading = loading,
            icon = icon,
        )
        if (!enabled && !loading) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = null, indication = null, onClick = onExplain)
                    .clearAndSetSemantics { },
            )
        }
    }
}

/**
 * La barra fija de abajo de 35 y 36: «Cancelar» (1/3) y la acción (2/3), con la línea divisoria arriba. Con fuente
 * grande se apilan, la acción primero, y se ocultan mientras el teclado está abierto: apiladas, encima del teclado, no
 * dejaban ver lo que se escribe (S20+ al 200 %). Vuelven al cerrarlo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DecisionBottomBar(onCancel: () -> Unit, cancelEnabled: Boolean, confirm: @Composable (Modifier) -> Unit) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    if (stacked && WindowInsets.isImeVisible) return
    val cancel: @Composable (Modifier) -> Unit = { m ->
        ExploraButton(stringResource(R.string.verify_cancel), onClick = onCancel, modifier = m, style = ExploraButtonStyle.TEXT, enabled = cancelEnabled)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
        if (stacked) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                confirm(Modifier.fillMaxWidth())
                cancel(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                cancel(Modifier.weight(1f))
                confirm(Modifier.weight(2f))
            }
        }
    }
}

/** Un estado de pantalla completa (cargando, sin conexión, error) centrado y desplazable con fuente grande. */
@Composable
internal fun ModerationCentered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun ModerationRetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}
