package co.edu.uniquindio.exploracity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import co.edu.uniquindio.exploracity.ui.catalog.DesignCatalog
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExploraCityTheme {
                // El fondo del tema cubre toda la ventana, también detrás de las barras del sistema:
                // si el tema elegido en Ajustes difiere del del teléfono, el fondo de ventana del XML no coincide.
                Surface(Modifier.fillMaxSize()) {
                    // Temporal: muestrario del sistema de diseño hasta que exista el NavHost (navigation/).
                    DesignCatalog(Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
