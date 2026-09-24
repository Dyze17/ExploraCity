package co.edu.uniquindio.exploracity

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.edu.uniquindio.exploracity.ui.ExploraApp
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Sin velo del sistema detrás de la navegación: la barra inferior del diseño llega hasta el borde
        // y cada pantalla ya respeta los márgenes de sistema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            ExploraCityTheme {
                ExploraApp()
            }
        }
    }
}
