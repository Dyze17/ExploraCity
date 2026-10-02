package co.edu.uniquindio.exploracity.ui.screens.access

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.LocalDarkTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.SplashDestination
import co.edu.uniquindio.exploracity.viewmodel.SplashState
import co.edu.uniquindio.exploracity.viewmodel.SplashViewModel

/** 1 · Arranque, conectado a su ViewModel: cuando decide, sigue a [onDestination]. */
@Composable
fun SplashRoute(onDestination: (SplashDestination) -> Unit, viewModel: SplashViewModel = viewModel(factory = SplashViewModel.factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDestination by rememberUpdatedState(onDestination)
    LaunchedEffect(state) {
        (state as? SplashState.Done)?.let { currentOnDestination(it.destination) }
    }
    SplashScreen(state, onRetry = viewModel::onRetry, onContinueOffline = viewModel::onContinueOffline)
}

/**
 * 1.a/1.b · Logo, nombre y lema mientras se prepara la entrada; en claro sobre el color de marca y en oscuro sobre la
 * superficie, como los lienzos. 1.c · Sin conexión con sesión abierta: reintentar o seguir con lo guardado.
 */
@Composable
fun SplashScreen(state: SplashState, onRetry: () -> Unit, onContinueOffline: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        SplashState.Offline -> OfflineContent(onRetry, onContinueOffline, modifier)
        else -> LoadingContent(modifier)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val background = if (dark) scheme.surface else scheme.primary
    val title = if (dark) scheme.onSurface else scheme.onPrimary
    val secondary = if (dark) MaterialTheme.exploraColors.iconSecondary else scheme.onPrimary
    LightIconsOnBrand(enabled = !dark)
    Column(
        modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Logo(size = 96, iconSize = 50)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 34.sp, lineHeight = 42.sp),
                color = title,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        Column(
            Modifier.padding(bottom = 32.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                Modifier.size(28.dp),
                color = if (dark) scheme.primary else scheme.onPrimary,
                trackColor = if (dark) MaterialTheme.exploraColors.divider else scheme.onPrimary.copy(alpha = 0.3f),
                strokeWidth = 3.dp,
            )
            Text(stringResource(R.string.splash_loading), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = secondary)
        }
    }
}

/** El logo: la lupa sobre el mundo en un cuadro redondeado del color de marca claro. */
@Composable
internal fun Logo(size: Int, iconSize: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.splash_logo)
    Box(
        modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape((size * 0.31f).dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_travel_explore), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(iconSize.dp))
    }
}

/** En claro el fondo es terracota: los iconos de la barra de estado van en blanco mientras se ve. */
@Composable
private fun LightIconsOnBrand(enabled: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        if (enabled) controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

@Composable
private fun OfflineContent(onRetry: () -> Unit, onContinueOffline: () -> Unit, modifier: Modifier) {
    val warning = MaterialTheme.exploraColors.warning
    val title = stringResource(R.string.splash_offline_title)
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .semantics { paneTitle = title },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(88.dp).background(warning.container, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_cloud_off), null, tint = MaterialTheme.exploraColors.warningAccent, modifier = Modifier.size(42.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 32.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.splash_offline_body),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.exploraColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
            ExploraButton(
                stringResource(R.string.splash_continue_offline),
                onClick = onContinueOffline,
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.TEXT,
            )
        }
    }
}

@Preview(name = "1.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun SplashLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { SplashScreen(SplashState.Loading, {}, {}) }
}

@Preview(name = "1.b · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun SplashDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { SplashScreen(SplashState.Loading, {}, {}) }
}

@Preview(name = "1.c · sin conexión", widthDp = 360, heightDp = 800)
@Composable
private fun SplashOfflinePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { SplashScreen(SplashState.Offline, {}, {}) }
}
