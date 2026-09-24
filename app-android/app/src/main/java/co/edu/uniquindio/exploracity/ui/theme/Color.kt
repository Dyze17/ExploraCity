package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Roles Material 3 del sistema de diseño (README del handoff, «Design Tokens»).
// «derivado» = la especificación no da el valor; sale de los lienzos .dc.html o de la convención de tonos de M3.
// Sin color dinámico: la paleta cálida propia es requisito de identidad.

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFFA9442A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3D1004),
    inversePrimary = Color(0xFFFFB59D), // derivado: primary del tema oscuro (acción del snackbar)

    // Contraste con blanco 4,23:1: solo detrás de iconos (marcador de Entretenimiento), nunca con texto.
    secondary = Color(0xFFB4690E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3BE),
    onSecondaryContainer = Color(0xFF4A2C00),

    tertiary = Color(0xFF0F6B63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEDE6),
    onTertiaryContainer = Color(0xFF00201D),

    background = Color(0xFFFDF8F5),
    onBackground = Color(0xFF231613),
    surface = Color(0xFFFDF8F5),
    onSurface = Color(0xFF231613),
    surfaceVariant = Color(0xFFEADCD4),
    onSurfaceVariant = Color(0xFF5E4740),
    // La jerarquía se da con los contenedores, sin tinte tonal: tinte = surface lo neutraliza.
    surfaceTint = Color(0xFFFDF8F5),
    inverseSurface = Color(0xFF33251F),
    inverseOnSurface = Color(0xFFF6E9E3),

    error = Color(0xFF8C1109),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF6E0C06),

    outline = Color(0xFF7A6259),
    outlineVariant = Color(0xFFD8C6BC),
    scrim = Color(0xFF231613),

    surfaceBright = Color(0xFFFDF8F5),
    surfaceDim = Color(0xFFE2D4CC), // derivado
    surfaceContainerLowest = Color(0xFFFFFFFF), // tarjeta
    surfaceContainerLow = Color(0xFFFAF3EE), // derivado: entre surface y surfaceContainer
    surfaceContainer = Color(0xFFF6EDE7),
    // En los lienzos #EFE4DE ↔ #2E2420 y #EADCD4 ↔ #392D28: son los dos niveles por encima de surfaceContainer.
    surfaceContainerHigh = Color(0xFFEFE4DE),
    surfaceContainerHighest = Color(0xFFEADCD4),

    // derivados: roles fixed (iguales en ambos temas) a partir de los tonos de la paleta.
    primaryFixed = Color(0xFFFFDBD0),
    primaryFixedDim = Color(0xFFFFB59D),
    onPrimaryFixed = Color(0xFF3D1004),
    onPrimaryFixedVariant = Color(0xFF7C2E16),
    secondaryFixed = Color(0xFFFFE3BE),
    secondaryFixedDim = Color(0xFFF2C078),
    onSecondaryFixed = Color(0xFF4A2C00),
    onSecondaryFixedVariant = Color(0xFF6D4A0A),
    tertiaryFixed = Color(0xFFBEEDE6),
    tertiaryFixedDim = Color(0xFF7FD6CB),
    onTertiaryFixed = Color(0xFF00201D),
    onTertiaryFixedVariant = Color(0xFF1F4D48),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5C1B08),
    primaryContainer = Color(0xFF7C2E16),
    onPrimaryContainer = Color(0xFFFFDBD0),
    inversePrimary = Color(0xFFA9442A), // derivado

    secondary = Color(0xFFF2C078),
    onSecondary = Color(0xFF3D2600),
    secondaryContainer = Color(0xFF6D4A0A),
    onSecondaryContainer = Color(0xFFFFE3BE),

    tertiary = Color(0xFF7FD6CB),
    onTertiary = Color(0xFF00332E),
    tertiaryContainer = Color(0xFF1F4D48), // derivado: valor de los lienzos oscuros
    onTertiaryContainer = Color(0xFFBEEDE6),

    background = Color(0xFF16110F),
    onBackground = Color(0xFFF2E4DE),
    surface = Color(0xFF16110F),
    onSurface = Color(0xFFF2E4DE),
    surfaceVariant = Color(0xFF392D28),
    onSurfaceVariant = Color(0xFFD6C1B8),
    surfaceTint = Color(0xFF16110F),
    inverseSurface = Color(0xFFF2E4DE), // derivado
    inverseOnSurface = Color(0xFF33251F), // derivado

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F1410),
    errorContainer = Color(0xFF73201A),
    onErrorContainer = Color(0xFFFFB4AB),

    outline = Color(0xFF9C867C),
    outlineVariant = Color(0xFF4A3A34), // derivado: par de #D8C6BC en los lienzos
    scrim = Color(0xFF231613),

    surfaceBright = Color(0xFF40342F), // derivado
    surfaceDim = Color(0xFF16110F),
    // El diseño llama «surface-container-lowest» a la tarjeta; en oscuro es #2A201D aunque rompa el orden tonal de M3.
    surfaceContainerLowest = Color(0xFF2A201D),
    surfaceContainerLow = Color(0xFF1D1714), // derivado: entre surface y surfaceContainer
    surfaceContainer = Color(0xFF241C19),
    surfaceContainerHigh = Color(0xFF2E2420),
    surfaceContainerHighest = Color(0xFF392D28),

    primaryFixed = Color(0xFFFFDBD0),
    primaryFixedDim = Color(0xFFFFB59D),
    onPrimaryFixed = Color(0xFF3D1004),
    onPrimaryFixedVariant = Color(0xFF7C2E16),
    secondaryFixed = Color(0xFFFFE3BE),
    secondaryFixedDim = Color(0xFFF2C078),
    onSecondaryFixed = Color(0xFF4A2C00),
    onSecondaryFixedVariant = Color(0xFF6D4A0A),
    tertiaryFixed = Color(0xFFBEEDE6),
    tertiaryFixedDim = Color(0xFF7FD6CB),
    onTertiaryFixed = Color(0xFF00201D),
    onTertiaryFixedVariant = Color(0xFF1F4D48),
)
