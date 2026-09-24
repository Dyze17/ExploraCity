package co.edu.uniquindio.exploracity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Temporal: ExploraCityTheme (ui/theme) y el NavHost (navigation/) reemplazan esto.
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("ExploraCity")
                    }
                }
            }
        }
    }
}
