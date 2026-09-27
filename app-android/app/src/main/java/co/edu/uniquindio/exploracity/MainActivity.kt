package co.edu.uniquindio.exploracity

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.uniquindio.exploracity.ui.ExploraApp
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Sin velo del sistema detrás de la navegación: la barra inferior del diseño llega hasta el borde
        // y cada pantalla ya respeta los márgenes de sistema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // El tema elegido en Ajustes (29) se lee antes del primer cuadro: así no parpadea el del sistema al abrir.
        val preferences = (application as ExploraApplication).container.preferences
        val initialTheme = runBlocking { preferences.themeMode.first() }
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialTheme)
            ExploraCityTheme(themeMode) {
                ExploraApp()
            }
        }
    }
}
