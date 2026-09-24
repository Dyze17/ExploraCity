package co.edu.uniquindio.exploracity.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ScrimAlpha
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * 9 · Hoja de filtros (FiltersBottomSheet). Modal: el fondo queda inerte y el foco inicial va al título.
 * Todo cambia un borrador; [onApply] lo aplica y el botón anuncia en vivo cuántos lugares se verán.
 * «Limpiar» vuelve al valor inicial sin cerrar. Crece hasta el 90 % del alto: las secciones se desplazan
 * y el pie con los botones queda fijo.
 *
 * Está abierta mientras [draft] no sea null. Si el dueño la cierra (aplicar o la x), se anima la salida
 * con el último borrador. [count] null = todavía sin cifra: el botón dice «Ver lugares».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersBottomSheet(
    draft: FeedFilters?,
    count: Int?,
    onDraftChange: (FeedFilters) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lastShown by remember { mutableStateOf<Pair<FeedFilters, Int?>?>(null) }
    LaunchedEffect(draft, count) {
        if (draft != null) lastShown = draft to count
    }
    LaunchedEffect(draft == null) {
        if (draft == null && lastShown != null) {
            sheetState.hide()
            lastShown = null
        }
    }
    val (shownDraft, shownCount) = if (draft != null) draft to count else lastShown ?: return

    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.9f }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha.Sheet),
        // Asa propia y decorativa: la de M3 es un botón y se llevaría el foco inicial, que va al título.
        dragHandle = null,
    ) {
        FiltersSheetContent(
            draft = shownDraft,
            count = shownCount,
            onDraftChange = onDraftChange,
            onClear = onClear,
            onApply = onApply,
            onClose = onDismissRequest,
            modifier = Modifier.heightIn(max = maxHeight),
        )
    }
}

@Composable
private fun FiltersSheetContent(
    draft: FeedFilters,
    count: Int?,
    onDraftChange: (FeedFilters) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    // La hoja vive en su propia ventana: sin esto el sistema pinta un velo oscuro bajo la barra de gestos,
    // como hacía en la actividad (ver MainActivity).
    val view = LocalView.current
    SideEffect {
        val window = (view as? DialogWindowProvider ?: view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }
    val stackRows = LocalDensity.current.fontScale > FontScaleThresholds.StackRows

    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.exploraColors.sheetHandle, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 22 sp como en el lienzo (el mismo estilo que el nombre de la ciudad del feed), no titleLarge de 20.
            Text(
                stringResource(R.string.filters_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 28.sp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.filters_close),
                    tint = MaterialTheme.exploraColors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CategorySection(draft, onDraftChange, stackRows)
            LocationSection(draft, onDraftChange, stackRows)
            VerifiedSection(draft, onDraftChange)
        }
        Spacer(Modifier.height(20.dp))
        SheetActions(count, onClear, onApply, stackRows)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
        color = MaterialTheme.exploraColors.textSecondary,
        modifier = Modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(draft: FeedFilters, onDraftChange: (FeedFilters) -> Unit, stackRows: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.filters_category))
        // Cada chip mide 40 dp a la vista (lienzo 9.a) dentro de su área táctil de 48 dp, que ya deja 8 dp
        // entre filas. Con fuente grande el chip supera los 48 dp y ese margen desaparece: se añade aparte.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(if (stackRows) 8.dp else 0.dp),
        ) {
            Category.entries.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category in draft.categories,
                    onSelectedChange = { selected ->
                        val categories = if (selected) draft.categories + category else draft.categories - category
                        onDraftChange(draft.copy(categories = categories))
                    },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationSection(draft: FeedFilters, onDraftChange: (FeedFilters) -> Unit, stackRows: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.filters_location))
        val options = @Composable { itemModifier: Modifier ->
            LocationOption(
                label = stringResource(R.string.filters_nearby),
                icon = R.drawable.ic_near_me,
                selected = draft.scope == LocationScope.NEARBY,
                onSelect = { onDraftChange(draft.copy(scope = LocationScope.NEARBY)) },
                modifier = itemModifier,
            )
            LocationOption(
                label = stringResource(R.string.filters_city),
                icon = R.drawable.ic_location_city,
                selected = draft.scope == LocationScope.CITY,
                onSelect = { onDraftChange(draft.copy(scope = LocationScope.CITY)) },
                modifier = itemModifier,
            )
        }
        // Con fuente grande las opciones pasan a columna para no partir sus nombres.
        if (stackRows) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.weight(1f)) }
        }
        Text(
            stringResource(R.string.filters_nearby_hint, FeedFilters.NEARBY_RADIUS_METERS / 1_000),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opción de radio de 48 dp. Elegida = tertiaryContainer + borde + check que sustituye al icono. */
@Composable
private fun LocationOption(label: String, @DrawableRes icon: Int, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val content = if (selected) scheme.onTertiaryContainer else MaterialTheme.exploraColors.textSecondary
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .background(if (selected) scheme.tertiaryContainer else scheme.surface, shape)
            .border(1.dp, if (selected) scheme.tertiary else scheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (selected) R.drawable.ic_check else icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp.scaledWithFont()),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
            color = content,
        )
    }
}

@Composable
private fun VerifiedSection(draft: FeedFilters, onDraftChange: (FeedFilters) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.filters_show_only))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .toggleable(
                    value = draft.verifiedOnly,
                    role = Role.Checkbox,
                    onValueChange = { onDraftChange(draft.copy(verifiedOnly = it)) },
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(checked = draft.verifiedOnly)
            Text(
                stringResource(R.string.filters_verified),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Casilla de 24 dp y radio 6 del lienzo (la de M3 mide 18 dp). Acompaña a un texto, así que escala con la
 * fuente como los iconos de chips y badges. El estado lo anuncia la fila.
 */
@Composable
private fun CheckBox(checked: Boolean) {
    val shape = RoundedCornerShape(6.dp.scaledWithFont())
    Box(
        Modifier
            .size(24.dp.scaledWithFont())
            .then(
                if (checked) {
                    Modifier.background(MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.exploraColors.textPlaceholder, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp.scaledWithFont()),
            )
        }
    }
}

/**
 * «Limpiar» (1/3) y «Ver N lugares» (2/3). El segundo es liveRegion: cada conteo nuevo se anuncia.
 * Con fuente grande pasan a columna, con la acción principal arriba.
 */
@Composable
private fun SheetActions(count: Int?, onClear: () -> Unit, onApply: () -> Unit, stackRows: Boolean) {
    val applyLabel = if (count != null) pluralStringResource(R.plurals.filters_apply, count, count) else stringResource(R.string.filters_apply_unknown)
    val clearLabel = stringResource(R.string.filters_clear)
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (stackRows) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExploraButton(applyLabel, onClick = onApply, modifier = live.fillMaxWidth())
            ExploraButton(clearLabel, onClick = onClear, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExploraButton(clearLabel, onClick = onClear, modifier = Modifier.weight(1f), style = ExploraButtonStyle.TEXT)
            ExploraButton(applyLabel, onClick = onApply, modifier = live.weight(2f))
        }
    }
}

/**
 * Botón de filtros del buscador (7 y 8): círculo de 36 dp sobre primaryContainer dentro de 48 dp táctiles.
 * Anuncia cuántos filtros hay activos («Filtros, 2 activos»).
 */
@Composable
fun FiltersButton(activeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (activeCount == 0) {
        stringResource(R.string.feed_filters_button)
    } else {
        pluralStringResource(R.plurals.feed_filters_button_active, activeCount, activeCount)
    }
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_tune),
                contentDescription = null,
                tint = MaterialTheme.exploraColors.onPrimaryContainerAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val previewDraft = FeedFilters(
    categories = setOf(Category.GASTRONOMY, Category.NATURE),
    scope = LocationScope.NEARBY,
    verifiedOnly = true,
)

@Preview(name = "9.a · claro", widthDp = 360)
@Composable
private fun FiltersSheetLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { FiltersSheetContent(previewDraft, 24, {}, {}, {}, {}) }
    }
}

@Preview(name = "9.a · oscuro", widthDp = 360)
@Composable
private fun FiltersSheetDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { FiltersSheetContent(previewDraft, 24, {}, {}, {}, {}) }
    }
}

@Preview(name = "9.a · fuente 200 %", widthDp = 360, fontScale = 2f)
@Composable
private fun FiltersSheetLargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { FiltersSheetContent(FeedFilters.DEFAULT, null, {}, {}, {}, {}) }
    }
}
