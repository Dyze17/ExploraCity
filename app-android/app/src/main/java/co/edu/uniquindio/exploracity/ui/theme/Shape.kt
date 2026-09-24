package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Los valores por defecto de M3 no coinciden con el diseño en Button (píldora → 12),
// Card (12 → 16) ni OutlinedTextField (4 → 12): los componentes del sistema pasan la forma explícita.
val ExploraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp), // chips y badges
    medium = RoundedCornerShape(12.dp), // campos y botones
    large = RoundedCornerShape(16.dp), // tarjetas
    extraLarge = RoundedCornerShape(28.dp), // hojas inferiores y diálogos
)
