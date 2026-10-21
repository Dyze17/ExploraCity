package co.edu.uniquindio.exploracity.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.core.ui.theme.Outfit
import co.edu.uniquindio.exploracity.core.ui.theme.ScrimAlpha
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.domain.model.TownOption

/**
 * v1.3.0 · «Elegir ciudad» (7 y 28): «Usar mi ubicación», una búsqueda de cualquier ciudad o pueblo y, sin texto, las
 * ciudades que ya tienen lugares. Abierta mientras [state] no sea null; el foco inicial va al título. [followsLocation]
 * marca «Usar mi ubicación»; sin [onUseLocation] (Editar perfil) esa fila no aparece.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TownPickerSheet(
    state: TownPickerState?,
    followsLocation: Boolean,
    onChoose: (TownOption) -> Unit,
    onQueryChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onUseLocation: (() -> Unit)? = null,
) {
    val shown = state ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.9f }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha.Sheet),
        // Asa propia y decorativa, como en la hoja de filtros: el foco inicial va al título.
        dragHandle = null,
    ) {
        TownPickerContent(shown, followsLocation, onChoose, onQueryChange, onDismissRequest, onUseLocation, Modifier.heightIn(max = maxHeight))
    }
}

@Composable
private fun TownPickerContent(
    state: TownPickerState,
    followsLocation: Boolean,
    onChoose: (TownOption) -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onUseLocation: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    NoNavigationBarScrim()
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.town_picker_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 28.sp),
                modifier = Modifier.weight(1f).focusRequester(titleFocus).focusable().semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.town_picker_close), tint = MaterialTheme.exploraColors.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (onUseLocation != null) {
            TownRow(
                icon = R.drawable.ic_my_location,
                title = stringResource(R.string.town_use_location),
                subtitle = stringResource(R.string.town_use_location_body),
                selected = followsLocation,
                onClick = onUseLocation,
            )
            Spacer(Modifier.height(12.dp))
        }
        ExploraSearchBar(value = state.query, onValueChange = onQueryChange, placeholder = stringResource(R.string.town_search))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.query.isBlank()) WithPlaces(state, onChoose) else SearchResults(state.search, onChoose)
        }
    }
}

@Composable
private fun WithPlaces(state: TownPickerState, onChoose: (TownOption) -> Unit) {
    Text(
        stringResource(R.string.town_with_places),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
        color = MaterialTheme.exploraColors.textSecondary,
        modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
    )
    val towns = state.withPlaces
    when {
        towns == null -> Note(stringResource(R.string.town_loading))
        state.withPlacesFailed -> Note(stringResource(R.string.town_list_failed))
        towns.isEmpty() -> Note(stringResource(R.string.town_list_empty))
        else -> towns.forEach { option -> TownOptionRow(option, onChoose) }
    }
}

@Composable
private fun SearchResults(search: TownSearch, onChoose: (TownOption) -> Unit) {
    when (search) {
        TownSearch.Idle -> Unit
        TownSearch.Searching -> Note(stringResource(R.string.town_searching))
        is TownSearch.Found -> search.options.forEach { option -> TownOptionRow(option, onChoose) }
        is TownSearch.NotFound -> Note(stringResource(R.string.town_not_found, search.query))
        TownSearch.Offline -> Note(stringResource(R.string.town_search_offline))
        TownSearch.Failed -> Note(stringResource(R.string.town_search_failed))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.exploraColors.textSecondary,
        modifier = Modifier.padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun TownOptionRow(option: TownOption, onChoose: (TownOption) -> Unit) {
    val town = option.municipality
    TownRow(
        icon = R.drawable.ic_location_city,
        title = town.name,
        subtitle = listOfNotNull(town.region, option.country).distinct().joinToString(" · ").ifEmpty { null },
        trailing = option.places?.let { pluralStringResource(R.plurals.town_places, it, it) },
        onClick = { onChoose(option) },
    )
}

/** Una fila tocable de 56 dp: icono, ciudad, departamento o país y, si hay, cuántos lugares tiene o el check. */
@Composable
private fun TownRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: String? = null,
    selected: Boolean = false,
) {
    val selectedText = stringResource(R.string.state_selected)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (selected) stateDescription = selectedText }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary) }
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.exploraColors.textSecondary) }
        if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Preview(name = "Elegir ciudad · claro", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun TownPickerPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        TownPickerContent(
            state = TownPickerState(
                withPlaces = listOf(
                    TownOption(Municipality("Armenia", "Quindío"), GeoPoint(4.53, -75.68), places = 10),
                    TownOption(Municipality("Calarcá", "Quindío"), GeoPoint(4.52, -75.64), places = 3),
                ),
            ),
            followsLocation = true,
            onChoose = {},
            onQueryChange = {},
            onClose = {},
            onUseLocation = {},
        )
    }
}
