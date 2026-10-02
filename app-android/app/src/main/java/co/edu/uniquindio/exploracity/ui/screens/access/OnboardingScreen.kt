package co.edu.uniquindio.exploracity.ui.screens.access

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

/** 2 · Onboarding, conectado a su ViewModel: salir por cualquier botón lo marca como visto. */
@Composable
fun OnboardingRoute(
    onSkip: () -> Unit,
    onCreateAccount: () -> Unit,
    onHaveAccount: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory),
) {
    OnboardingScreen(
        onSkip = {
            viewModel.onFinished()
            onSkip()
        },
        onCreateAccount = {
            viewModel.onFinished()
            onCreateAccount()
        },
        onHaveAccount = {
            viewModel.onFinished()
            onHaveAccount()
        },
    )
}

private class OnboardingPage(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

private val pages = listOf(
    OnboardingPage(R.drawable.ic_explore, R.string.onboarding_discover_title, R.string.onboarding_discover_body),
    OnboardingPage(R.drawable.ic_verified, R.string.onboarding_publish_title, R.string.onboarding_publish_body),
    OnboardingPage(R.drawable.ic_military_tech, R.string.onboarding_levels_title, R.string.onboarding_levels_body),
)

/**
 * 2.a–2.c · Tres paneles que se deslizan o avanzan con «Siguiente»; «Saltar» siempre a mano. El último cambia los
 * botones: «Crear mi cuenta» y «Ya tengo cuenta». El indicador dice «Panel 1 de 3».
 */
@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onCreateAccount: () -> Unit,
    onHaveAccount: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            ExploraButton(stringResource(R.string.onboarding_skip), onClick = onSkip, style = ExploraButtonStyle.TEXT)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index -> Page(pages[index], index) }
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Indicator(pager.currentPage, pages.size)
            if (last) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExploraButton(stringResource(R.string.onboarding_create_account), onClick = onCreateAccount, modifier = Modifier.fillMaxWidth())
                    ExploraButton(stringResource(R.string.onboarding_have_account), onClick = onHaveAccount, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
                }
            } else {
                ExploraButton(
                    stringResource(R.string.onboarding_next),
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Ilustración (aún un marcador con rayas y el icono del tema, como en el lienzo), titular y texto. */
@Composable
private fun Page(page: OnboardingPage, index: Int) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    // Rayas suaves sobre el contenedor, como el marcador del lienzo: la ilustración no debe competir con el texto.
    val (stripe, tint) = when (index) {
        0 -> scheme.primaryContainer.copy(alpha = 0.45f) to scheme.primary
        1 -> scheme.tertiaryContainer.copy(alpha = 0.45f) to scheme.tertiary
        else -> scheme.surfaceContainerHighest to explora.warningAccent
    }
    val base = scheme.surfaceContainer
    val large = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (large) 180.dp else 300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(base)
                .drawBehind { drawStripes(stripe) }
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(page.icon), null, tint = tint, modifier = Modifier.size(52.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(page.title),
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 36.sp),
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = explora.textSecondary)
        }
    }
}

/** Rayas a 135°, como el marcador de ilustración de los lienzos. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStripes(color: Color) {
    val step = 16.dp.toPx()
    val width = 8.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width)
        x += step
    }
}

/** Puntos con el actual alargado; el lector oye «Panel 2 de 3». */
@Composable
private fun Indicator(current: Int, count: Int) {
    val description = stringResource(R.string.onboarding_page, current + 1, count)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (active) 24.dp else 8.dp)
                    .heightIn(min = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Preview(name = "2.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingFirstPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { OnboardingScreen({}, {}, {}) }
}

@Preview(name = "2.c · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingLastPreview() {
    ExploraCityTheme(ThemeMode.DARK) { OnboardingScreen({}, {}, {}, initialPage = 2) }
}
