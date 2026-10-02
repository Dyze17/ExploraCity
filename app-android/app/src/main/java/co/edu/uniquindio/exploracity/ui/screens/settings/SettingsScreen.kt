package co.edu.uniquindio.exploracity.ui.screens.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.BuildConfig
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.OptionButton
import co.edu.uniquindio.exploracity.ui.components.initialFocus
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.LocationAccess
import co.edu.uniquindio.exploracity.util.locationAccess
import co.edu.uniquindio.exploracity.util.openAppSettings
import co.edu.uniquindio.exploracity.viewmodel.DataDownload
import co.edu.uniquindio.exploracity.viewmodel.LogoutDialog
import co.edu.uniquindio.exploracity.viewmodel.SettingsNotice
import co.edu.uniquindio.exploracity.viewmodel.SettingsNoticeKind
import co.edu.uniquindio.exploracity.viewmodel.SettingsUiState
import co.edu.uniquindio.exploracity.viewmodel.SettingsViewModel

/**
 * 29 · Ajustes, conectada a su ViewModel. El permiso de ubicación se lee del teléfono cada vez que la pantalla vuelve
 * al frente: «Cambiar» abre la ficha de la app en los ajustes de Android y al regresar ya dice el estado nuevo.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenPolicy: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    onChangeEmail: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenDesignCatalog: () -> Unit,
    emailChanged: String? = null,
    onEmailChangedShown: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Al volver de confirmar el correo nuevo, Ajustes lo dice una vez.
    val currentOnEmailChangedShown by rememberUpdatedState(onEmailChangedShown)
    LaunchedEffect(emailChanged) {
        if (emailChanged == null) return@LaunchedEffect
        viewModel.onEmailChanged(emailChanged)
        currentOnEmailChangedShown()
    }
    val context = LocalContext.current
    var location by remember { mutableStateOf(context.locationAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { location = context.locationAccess() }

    // «Descargar mis datos»: Android pregunta dónde guardar el archivo; la app no necesita permisos.
    val saveDialog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        viewModel.onDestinationChosen(uri?.toString())
    }
    val ready = state.download as? DataDownload.Ready
    LaunchedEffect(ready) {
        if (ready == null) return@LaunchedEffect
        viewModel.onSaveDialogShown()
        try {
            saveDialog.launch(ready.export.fileName)
        } catch (e: ActivityNotFoundException) {
            viewModel.onSaveDialogUnavailable()
        }
    }
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(state.signedOut) {
        if (state.signedOut) currentOnSignedOut()
    }

    SettingsScreen(
        state = state,
        location = location,
        showDevelopment = BuildConfig.DEBUG,
        callbacks = SettingsCallbacks(
            onBack = onBack,
            onThemeChange = viewModel::onThemeChange,
            onChangeLocationPermission = { context.openAppSettings() },
            onOpenPolicy = onOpenPolicy,
            onOpenPrivacyNotice = onOpenPrivacyNotice,
            onDownloadData = viewModel::onDownloadData,
            onNoticeShown = viewModel::onNoticeShown,
            onChangeEmail = onChangeEmail,
            onLogout = viewModel::onLogoutClick,
            onDismissLogout = viewModel::onDismissLogout,
            onConfirmLogout = viewModel::onConfirmLogout,
            onDeleteAccount = onDeleteAccount,
            onOpenDesignCatalog = onOpenDesignCatalog,
        ),
    )
}

private const val JSON_MIME = "application/json"

class SettingsCallbacks(
    val onBack: () -> Unit = {},
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onChangeLocationPermission: () -> Unit = {},
    val onOpenPolicy: () -> Unit = {},
    val onOpenPrivacyNotice: () -> Unit = {},
    val onDownloadData: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val onChangeEmail: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onDismissLogout: () -> Unit = {},
    val onConfirmLogout: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
    val onOpenDesignCatalog: () -> Unit = {},
)

/**
 * 29.a · Apariencia, permisos, datos personales y cuenta, con ítems de 56 dp o más. Cada permiso dice su estado en
 * texto y qué se pierde sin él. «Eliminar mi cuenta» va al final, aparte y en tono destructivo. Con [showDevelopment]
 * (compilación de desarrollo) aparece además el muestrario del sistema de diseño.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    location: LocationAccess,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
    showDevelopment: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    NoticeEffect(state.notice, snackbarHostState, callbacks.onNoticeShown)

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            ExploraTopAppBar(title = stringResource(R.string.settings_title), onBack = callbacks.onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Section(stringResource(R.string.settings_section_appearance)) {
                    SettingsCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.settings_theme),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ThemePicker(state.themeMode, callbacks.onThemeChange)
                        }
                    }
                }
                Section(stringResource(R.string.settings_section_permissions)) {
                    SettingsCard {
                        LocationRow(location, callbacks.onChangeLocationPermission)
                        RowDivider()
                        PermissionRow(R.drawable.ic_photo_camera, stringResource(R.string.settings_permission_camera), stringResource(R.string.settings_camera_status))
                        RowDivider()
                        PermissionRow(R.drawable.ic_photo_library, stringResource(R.string.settings_permission_photos), stringResource(R.string.settings_photos_status))
                    }
                }
                Section(stringResource(R.string.settings_section_personal_data)) {
                    SettingsCard {
                        LinkRow(R.drawable.ic_policy, stringResource(R.string.settings_policy), callbacks.onOpenPolicy)
                        RowDivider()
                        LinkRow(R.drawable.ic_shield, stringResource(R.string.settings_privacy_notice), callbacks.onOpenPrivacyNotice)
                        RowDivider()
                        DownloadRow(state.download, callbacks.onDownloadData)
                    }
                }
                Section(stringResource(R.string.settings_section_account)) {
                    SettingsCard {
                        EmailRow(state.email, state.pendingEmail, callbacks.onChangeEmail)
                        RowDivider()
                        LinkRow(R.drawable.ic_logout, stringResource(R.string.settings_logout), callbacks.onLogout, chevron = false)
                    }
                    DeleteAccountRow(callbacks.onDeleteAccount)
                }
                if (showDevelopment) {
                    Section(stringResource(R.string.settings_section_development)) {
                        SettingsCard { LinkRow(R.drawable.ic_palette, stringResource(R.string.settings_design_catalog), callbacks.onOpenDesignCatalog) }
                    }
                }
            }
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    state.logout?.let { LogoutConfirmDialog(it, callbacks.onConfirmLogout, callbacks.onDismissLogout) }
}

/** «APARIENCIA», «PERMISOS»…: encabezado para el lector y, debajo, sus tarjetas. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W700, letterSpacing = 0.06.em),
            color = MaterialTheme.exploraColors.iconSecondary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.exploraColors.divider, shape),
        content = content,
    )
}

@Composable
private fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = MaterialTheme.exploraColors.divider)

/**
 * Claro, Oscuro y Sistema como un grupo de opciones: la elegida lleva check en lugar de su icono. Con fuente grande
 * van una debajo de otra.
 */
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val options = listOf(
        Triple(ThemeMode.LIGHT, R.drawable.ic_light_mode, R.string.settings_theme_light),
        Triple(ThemeMode.DARK, R.drawable.ic_dark_mode, R.string.settings_theme_dark),
        Triple(ThemeMode.SYSTEM, R.drawable.ic_smartphone, R.string.settings_theme_system),
    )
    val items: @Composable (Modifier) -> Unit = { itemModifier ->
        options.forEach { (mode, icon, label) ->
            OptionButton(stringResource(label), icon, mode == selected, { onSelect(mode) }, itemModifier, fontSize = 13.sp)
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(Modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(Modifier.weight(1f)) }
    }
}

/** Ubicación: su estado real y «Cambiar», que abre la ficha de la app en los ajustes del teléfono. */
@Composable
private fun LocationRow(access: LocationAccess, onChange: () -> Unit) {
    val explora = MaterialTheme.exploraColors
    val (status, color) = when (access) {
        LocationAccess.PRECISE -> stringResource(R.string.settings_location_precise) to explora.status.verified.content
        LocationAccess.APPROXIMATE -> stringResource(R.string.settings_location_approximate) to explora.warning.content
        LocationAccess.DENIED -> stringResource(R.string.settings_location_denied) to explora.warning.content
    }
    val changeAction = stringResource(R.string.settings_permission_change_action)
    PermissionRow(
        icon = R.drawable.ic_my_location,
        title = stringResource(R.string.settings_permission_location),
        status = status,
        statusColor = color,
        modifier = Modifier.clickable(onClickLabel = changeAction, role = Role.Button, onClick = onChange),
    ) {
        Text(
            stringResource(R.string.settings_permission_change),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * Cámara y fotos no piden permiso: la app usa la cámara del teléfono y el selector de Android. La fila lo dice, sin
 * «Cambiar», en lugar de mostrar un permiso que nunca se pide.
 */
@Composable
private fun PermissionRow(
    @DrawableRes icon: Int,
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    statusColor: Color = MaterialTheme.exploraColors.textSecondary,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Con fuente grande la acción va debajo del estado: a la derecha dejaría el texto en una columna muy estrecha.
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.onSurface)
            Text(status, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600, lineHeight = 17.sp), color = statusColor)
            if (stacked) trailing?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
        }
        if (!stacked) trailing?.invoke()
    }
}

/** Fila que abre algo: la política, el aviso, el correo o el cierre de sesión. */
@Composable
private fun LinkRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    chevron: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(22.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary) }
        }
        if (chevron) {
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
    }
}

/** «Correo electrónico»: el de la cuenta y, si hay un cambio sin confirmar, «Falta confirmar …» con su icono (B1). */
@Composable
private fun EmailRow(email: String, pendingEmail: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_mail), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(22.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_email), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary)
            if (pendingEmail != null) {
                val warning = MaterialTheme.exploraColors.warningAccent
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_schedule), null, tint = warning, modifier = Modifier.size(14.dp.scaledWithFont()))
                    Text(
                        stringResource(R.string.settings_email_pending, pendingEmail),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                        color = warning,
                    )
                }
            }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
    }
}

/** «Descargar mis datos»: mientras el servidor arma el archivo o se guarda, lo dice y no se puede volver a tocar. */
@Composable
private fun DownloadRow(download: DataDownload, onClick: () -> Unit) {
    val busy = download.busy
    val progress = when (download) {
        DataDownload.Saving -> stringResource(R.string.settings_download_saving)
        DataDownload.Idle -> null
        else -> stringResource(R.string.settings_download_preparing)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_download), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(22.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_download), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            progress?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.exploraColors.textSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp.scaledWithFont()), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
    }
}

/** Aparte y en tono destructivo, como pide el README; lleva a la 30, que explica y confirma. */
@Composable
private fun DeleteAccountRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.exploraColors.errorFieldContainer)
            .border(1.dp, scheme.error, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_delete_forever), null, tint = scheme.error, modifier = Modifier.size(22.dp.scaledWithFont()))
        Text(
            stringResource(R.string.settings_delete_account),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
            color = scheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 29A · Confirmación simple: aclara que los borradores se conservan y, si hay envíos hechos sin conexión, que se
 * pierden. El foco inicial va a «Cancelar».
 */
@Composable
private fun LogoutConfirmDialog(dialog: LogoutDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.logout_title)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = title },
        icon = {
            Box(Modifier.size(44.dp).background(scheme.surfaceContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_logout), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.logout_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.textSecondary)
                if (dialog.pendingSends > 0) {
                    Text(
                        pluralStringResource(R.plurals.logout_pending, dialog.pendingSends, dialog.pendingSends),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                        color = scheme.error,
                    )
                }
            }
        },
        confirmButton = {
            ExploraButton(stringResource(R.string.logout_confirm), onClick = onConfirm, loading = dialog.signingOut)
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.logout_cancel),
                onClick = onDismiss,
                modifier = Modifier.initialFocus(cancelFocus),
                style = ExploraButtonStyle.TEXT,
                enabled = !dialog.signingOut,
            )
        },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
    )
}

@Composable
private fun NoticeEffect(notice: SettingsNotice?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val offline = stringResource(R.string.settings_download_offline)
    val failed = stringResource(R.string.settings_download_failed)
    val saveFailed = stringResource(R.string.settings_save_failed)
    val saved = stringResource(R.string.settings_download_saved, notice?.detail.orEmpty())
    val emailChanged = stringResource(R.string.settings_email_changed, notice?.detail.orEmpty())
    LaunchedEffect(notice) {
        val text = when (notice?.kind) {
            null -> return@LaunchedEffect
            SettingsNoticeKind.DOWNLOAD_OFFLINE -> offline
            SettingsNoticeKind.DOWNLOAD_FAILED -> failed
            SettingsNoticeKind.DOWNLOAD_SAVED -> saved
            SettingsNoticeKind.SAVE_FAILED -> saveFailed
            SettingsNoticeKind.EMAIL_CHANGED -> emailChanged
        }
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
        currentOnShown()
    }
}

private val previewState = SettingsUiState(themeMode = ThemeMode.DARK, email = "ana.rios@correo.com")

@Preview(name = "29.a · claro", widthDp = 360, heightDp = 1100)
@Composable
private fun SettingsLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { SettingsScreen(previewState, LocationAccess.PRECISE, SettingsCallbacks(), showDevelopment = true) }
}

@Preview(name = "29.a · oscuro", widthDp = 360, heightDp = 1100)
@Composable
private fun SettingsDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { SettingsScreen(previewState.copy(download = DataDownload.Preparing), LocationAccess.DENIED, SettingsCallbacks()) }
}

@Preview(name = "29A · cerrar sesión", widthDp = 360, heightDp = 800)
@Composable
private fun LogoutPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { SettingsScreen(previewState.copy(logout = LogoutDialog(pendingSends = 2)), LocationAccess.APPROXIMATE, SettingsCallbacks()) }
}

@Preview(name = "29 · fuente 200 %", widthDp = 360, heightDp = 2000, fontScale = 2f)
@Composable
private fun SettingsLargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { SettingsScreen(previewState, LocationAccess.APPROXIMATE, SettingsCallbacks()) }
}
