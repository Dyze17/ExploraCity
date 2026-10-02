package co.edu.uniquindio.exploracity.ui.screens.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.initialFocus
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.DeleteAccountError
import co.edu.uniquindio.exploracity.viewmodel.DeleteAccountUiState
import co.edu.uniquindio.exploracity.viewmodel.DeleteAccountViewModel
import co.edu.uniquindio.exploracity.viewmodel.DeleteConfirmation
import java.time.YearMonth

/** 30 · Eliminar cuenta, conectada a su ViewModel. Al terminar lleva al inicio de sesión (3), que lo dice. */
@Composable
fun DeleteAccountRoute(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeleteAccountViewModel = viewModel(factory = DeleteAccountViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(state.deleted) {
        if (state.deleted) currentOnDeleted()
    }
    DeleteAccountScreen(
        state = state,
        callbacks = DeleteAccountCallbacks(
            onBack = onBack,
            onContinue = viewModel::onContinue,
            onTypedChange = viewModel::onTypedChange,
            onDismiss = viewModel::onDismissConfirmation,
            onConfirm = viewModel::onConfirmDelete,
        ),
    )
}

class DeleteAccountCallbacks(
    val onBack: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onTypedChange: (String) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onConfirm: () -> Unit = {},
)

/**
 * 30.a · Primera confirmación: qué se borra, qué se conserva anónimo y el recordatorio de descargar los datos. «Continuar
 * con la eliminación» abre la segunda, el diálogo que exige escribir ELIMINAR. Sin red no se puede continuar y lo dice.
 */
@Composable
fun DeleteAccountScreen(state: DeleteAccountUiState, callbacks: DeleteAccountCallbacks, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(title = stringResource(R.string.delete_account_title), onBack = callbacks.onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WarningBanner()
            ConsequenceCard(
                title = stringResource(R.string.delete_account_removed_title),
                icon = R.drawable.ic_delete,
                iconTint = MaterialTheme.colorScheme.error,
                items = listOf(
                    stringResource(R.string.delete_account_removed_personal),
                    stringResource(R.string.delete_account_removed_photos),
                    reputationItem(state.profile),
                ),
            )
            ConsequenceCard(
                title = stringResource(R.string.delete_account_kept_title),
                icon = R.drawable.ic_visibility_off,
                iconTint = MaterialTheme.colorScheme.tertiary,
                items = listOf(
                    stringResource(R.string.delete_account_kept_places),
                    stringResource(R.string.delete_account_kept_comments),
                    stringResource(R.string.delete_account_kept_votes),
                ),
            )
            DownloadHint()
        }
        Actions(state.offline, callbacks)
    }
    state.confirmation?.let { ConfirmDialog(it, callbacks) }
}

@Composable
private fun WarningBanner() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_warning), null, tint = scheme.onErrorContainer, modifier = Modifier.size(22.dp.scaledWithFont()))
        Text(
            stringResource(R.string.delete_account_warning),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = scheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

/** «Tus 340 puntos, tu nivel (Aventurero) y tus 2 insignias»; sin el perfil cargado, sin cifras. */
@Composable
private fun reputationItem(profile: OwnProfile?): String {
    if (profile == null) return stringResource(R.string.delete_account_removed_reputation_generic)
    val points = profile.author.points
    val level = stringResource(profile.author.level.labelRes)
    val badges = profile.unlockedBadges
    return if (badges == 0) {
        stringResource(R.string.delete_account_removed_reputation_no_badges, points, level)
    } else {
        pluralStringResource(R.plurals.delete_account_removed_reputation, badges, points, level, badges)
    }
}

@Composable
private fun ConsequenceCard(title: String, @DrawableRes icon: Int, iconTint: Color, items: List<String>) {
    val shape = RoundedCornerShape(16.dp)
    val explora = MaterialTheme.exploraColors
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = explora.shadow, spotColor = explora.shadow)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = explora.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(18.dp.scaledWithFont()))
                Text(item, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = explora.textSecondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DownloadHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_download), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(
            stringResource(R.string.delete_account_download_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Botones fijos abajo: el destructivo arriba y «Mejor no, volver» como texto. Sin red, el aviso va encima. */
@Composable
private fun Actions(offline: Boolean, callbacks: DeleteAccountCallbacks) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.exploraColors.divider))
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (offline) {
                Text(
                    stringResource(R.string.delete_account_offline_notice),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.exploraColors.warningAccent,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ExploraButton(
                stringResource(R.string.delete_account_continue),
                onClick = callbacks.onContinue,
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.DESTRUCTIVE,
                enabled = !offline,
                disabledReason = stringResource(R.string.delete_account_offline),
                icon = R.drawable.ic_delete_forever,
            )
            ExploraButton(
                stringResource(R.string.delete_account_back),
                onClick = callbacks.onBack,
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.TEXT,
            )
        }
    }
}

/**
 * 30.a · Segunda confirmación: hay que escribir ELIMINAR. El foco inicial va a «Cancelar»; «Eliminar cuenta» nunca es
 * la acción por defecto ni responde a Enter, y se habilita solo con la palabra. Si falla, el aviso queda dentro.
 */
@Composable
private fun ConfirmDialog(confirmation: DeleteConfirmation, callbacks: DeleteAccountCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.delete_account_dialog_title)
    val body = AnnotatedString.fromHtml(stringResource(R.string.delete_account_dialog_body))
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    val typed = rememberTextFieldState(confirmation.typed)
    val currentOnTyped by rememberUpdatedState(callbacks.onTypedChange)
    LaunchedEffect(typed) { snapshotFlow { typed.text.toString() }.collect { currentOnTyped(it) } }
    AlertDialog(
        onDismissRequest = callbacks.onDismiss,
        modifier = Modifier.semantics { paneTitle = title },
        icon = {
            Box(Modifier.size(44.dp).background(scheme.errorContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_delete_forever), null, tint = scheme.onErrorContainer, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.textSecondary)
                ExploraTextField(
                    state = typed,
                    label = stringResource(R.string.delete_account_dialog_field),
                    placeholder = stringResource(R.string.delete_account_dialog_hint),
                    enabled = !confirmation.deleting,
                    // Enter solo cierra el teclado: eliminar la cuenta exige tocar el botón.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                confirmation.error?.let { error ->
                    Text(
                        stringResource(if (error == DeleteAccountError.OFFLINE) R.string.delete_account_offline_notice else R.string.delete_account_failed),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                        color = scheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            ExploraButton(
                stringResource(R.string.delete_account_confirm),
                onClick = callbacks.onConfirm,
                style = ExploraButtonStyle.DESTRUCTIVE,
                enabled = confirmation.matches || confirmation.deleting,
                disabledReason = stringResource(R.string.delete_account_confirm_unavailable),
                loading = confirmation.deleting,
            )
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.delete_account_cancel),
                onClick = callbacks.onDismiss,
                modifier = Modifier.initialFocus(cancelFocus),
                style = ExploraButtonStyle.TEXT,
                enabled = !confirmation.deleting,
            )
        },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
    )
}

private val previewProfile = OwnProfile(
    author = Author("ana-rios", "Ana Ríos", 340),
    residency = Residency.RESIDENT,
    city = "Bogotá",
    memberSince = YearMonth.of(2026, 3),
    publications = PublicationCounts(),
    badges = emptyList(),
)

@Preview(name = "30.a · claro", widthDp = 360, heightDp = 900)
@Composable
private fun DeleteAccountPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { DeleteAccountScreen(DeleteAccountUiState(profile = previewProfile), DeleteAccountCallbacks()) }
}

@Preview(name = "30.a · diálogo · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun DeleteAccountDialogPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        DeleteAccountScreen(DeleteAccountUiState(profile = previewProfile, confirmation = DeleteConfirmation("ELIMINAR")), DeleteAccountCallbacks())
    }
}

@Preview(name = "30 · sin red · fuente 200 %", widthDp = 360, heightDp = 1800, fontScale = 2f)
@Composable
private fun DeleteAccountOfflinePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { DeleteAccountScreen(DeleteAccountUiState(offline = true), DeleteAccountCallbacks()) }
}
