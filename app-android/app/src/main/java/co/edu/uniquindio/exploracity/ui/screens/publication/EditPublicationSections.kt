package co.edu.uniquindio.exploracity.ui.screens.publication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.screens.publish.AddressCard
import co.edu.uniquindio.exploracity.ui.screens.publish.DuplicateCallbacks
import co.edu.uniquindio.exploracity.ui.screens.publish.DuplicatesSheet
import co.edu.uniquindio.exploracity.ui.screens.publish.LocationStep
import co.edu.uniquindio.exploracity.ui.screens.publish.PinCallbacks
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.EditPublicationUiState
import co.edu.uniquindio.exploracity.viewmodel.LocationEditor
import co.edu.uniquindio.exploracity.viewmodel.PinAddress

// 23 · Lo que la edición agrega a 23.a: ubicación, horario y precio y fotos, con las piezas del formulario (17–19).

/** Título de una sección de 23 («Ubicación», «Horario y precio», «Fotos»). */
@Composable
internal fun EditSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.W700),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 10.dp).semantics { heading() },
    )
}

/**
 * La ubicación que se va a guardar: dirección aproximada y coordenadas, como en el paso 3, y «Cambiar ubicación». Si
 * el lugar nuevo quedó marcado como posible duplicado (17B), lo dice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditLocationSummary(form: PublicationChanges, address: PinAddress?, onChange: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AddressCard(form.location, address)
        if (form.duplicateCheck?.possibleDuplicate == true) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                DuplicateFlag(size = BadgeSize.SMALL)
                Text(
                    stringResource(R.string.publish_sent_duplicate_info),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
            }
        }
        ExploraButton(
            stringResource(R.string.edit_change_location),
            onClick = onChange,
            modifier = Modifier.fillMaxWidth(),
            style = ExploraButtonStyle.SECONDARY,
            icon = R.drawable.ic_location_on,
        )
    }
}

/**
 * «Cambiar ubicación»: el mapa del paso 3 a pantalla completa, con el pin donde está la publicación. «Usar esta
 * ubicación» busca parecidos si el pin se movió (17A/17B, en la misma hoja que al publicar); «Atrás» no cambia nada.
 */
@Composable
internal fun EditLocationScreen(
    editor: LocationEditor,
    state: EditPublicationUiState,
    form: PublicationChanges,
    pinCallbacks: PinCallbacks,
    duplicateCallbacks: DuplicateCallbacks,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    cityCenter: GeoPoint,
    canAskLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    val searching = state.pin.searchingNearby
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        ExploraTopAppBar(title = stringResource(R.string.edit_change_location), onBack = onClose)
        LocationStep(
            location = editor.point,
            category = form.category,
            pin = state.pin,
            callbacks = pinCallbacks,
            cityCenter = cityCenter,
            canAskLocation = canAskLocation,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp),
        )
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            ExploraButton(
                stringResource(if (searching) R.string.publish_searching_nearby else R.string.edit_use_location),
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                loading = searching,
            )
        }
    }
    // Al editar no hay borrador: la hoja no dice que esté guardado.
    DuplicatesSheet(state.pin.duplicates?.takeUnless { state.pin.awayForPlace }, editor.point, duplicateCallbacks, draftSaved = false)
}
