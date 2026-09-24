package co.edu.uniquindio.exploracity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Si el tema aplicado es oscuro, según la preferencia de Ajustes (29), no solo el del sistema. Lo usan las
 * superficies que no pinta Material, como el mapa (8).
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Preferencia de Ajustes (29): Claro / Oscuro / Sistema. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun ExploraCityTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Los iconos de las barras del sistema siguen al tema de la app, no solo al del teléfono.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalExploraColors provides if (darkTheme) DarkExploraColors else LightExploraColors,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = ExploraTypography,
            shapes = ExploraShapes,
            content = content,
        )
    }
}
