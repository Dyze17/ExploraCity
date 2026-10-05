package co.edu.uniquindio.exploracity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.uniquindio.exploracity.ui.ExploraApp
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    /** El enlace del correo con que se abrió la app, o que llegó con ella abierta, hasta que la navegación lo abre. */
    private val emailLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Al rotar o al volver de un cierre de Android, el enlace con que se abrió ya se usó.
        if (savedInstanceState == null) emailLink.value = intent?.dataString
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
            val link by emailLink.collectAsStateWithLifecycle()
            ExploraCityTheme(themeMode) {
                ExploraApp(emailLink = link, onEmailLinkHandled = { emailLink.value = null })
            }
        }
    }

    /** La app ya estaba abierta (una sola instancia, singleTask) y llegó un enlace del correo. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        emailLink.value = intent.dataString
    }
}
