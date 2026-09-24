package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Par fondo / contenido (texto e icono) con contraste ≥ 4,5:1 en ambos temas. */
@Immutable
data class ContainerColors(val container: Color, val content: Color)

/** Estados de publicación: siempre color + icono + texto, nunca solo color. */
@Immutable
data class StatusColors(
    val pending: ContainerColors,
    val verified: ContainerColors,
    val rejected: ContainerColors,
    val finalized: ContainerColors,
)

/** Marca «Posible duplicado»: borde de 1 dp discontinuo. */
@Immutable
data class DuplicateFlagColors(val container: Color, val content: Color, val border: Color)

/** Color estable de una categoría en chip, lista, mapa, formulario y filtros. */
@Immutable
data class CategoryColors(
    val container: Color,
    val content: Color,
    val marker: Color,
    val onMarker: Color,
)

@Immutable
data class CategoryPalette(
    val gastronomy: CategoryColors,
    val culture: CategoryColors,
    val nature: CategoryColors,
    val entertainment: CategoryColors,
    val history: CategoryColors,
)

/** Colores del sistema de diseño que no tienen rol en el ColorScheme de Material 3. */
@Immutable
data class ExploraColors(
    /** Texto de cuerpo en descripciones y subtítulos de sección. */
    val textSecondary: Color,
    /** Iconos de apoyo y textos de ayuda. */
    val iconSecondary: Color,
    /**
     * Placeholders y controles sin marcar. También el contenido de botones deshabilitados:
     * los lienzos usan outline (4,25:1), pero el diseño exige 4,5:1 también deshabilitado.
     */
    val textPlaceholder: Color,
    /** Texto de acento sobre superficies: botón contorneado. */
    val onSurfaceAccent: Color,
    /** Contenido de acento sobre primaryContainer y su entorno: pestaña seleccionada de la barra inferior. */
    val onPrimaryContainerAccent: Color,
    /** Divisores y borde superior de la barra inferior. */
    val divider: Color,
    /** Fondo del campo con error. */
    val errorFieldContainer: Color,
    /** Borde discontinuo del campo deshabilitado. */
    val outlineDisabled: Color,
    /**
     * Banner sin conexión y urgencia ámbar (≥ 1 día). La urgencia roja (≥ 3 días) usa
     * `colorScheme.errorContainer` / `onErrorContainer`.
     */
    val warning: ContainerColors,
    val status: StatusColors,
    val duplicateFlag: DuplicateFlagColors,
    val category: CategoryPalette,
    val mapCluster: ContainerColors,
    val mapClusterBorder: Color,
    /** Sombra cálida en claro; transparente en oscuro, donde la jerarquía la dan los contenedores. */
    val shadow: Color,
)

private val White = Color(0xFFFFFFFF)

internal val LightExploraColors = ExploraColors(
    textSecondary = Color(0xFF3A2A25),
    iconSecondary = Color(0xFF4E3A33),
    textPlaceholder = Color(0xFF6B564E),
    onSurfaceAccent = Color(0xFF7A2A12),
    onPrimaryContainerAccent = Color(0xFF7A2A12),
    divider = Color(0xFFE5D5CC),
    errorFieldContainer = Color(0xFFFFF7F6),
    outlineDisabled = Color(0xFFA99086),
    warning = ContainerColors(Color(0xFFFFE3BE), Color(0xFF4A2C00)),
    status = StatusColors(
        pending = ContainerColors(Color(0xFFFFE3BE), Color(0xFF4A2C00)),
        verified = ContainerColors(Color(0xFFC8EDD5), Color(0xFF0B3A22)),
        rejected = ContainerColors(Color(0xFFFFDAD6), Color(0xFF6E0C06)),
        finalized = ContainerColors(Color(0xFFD9E3F8), Color(0xFF1B2F52)),
    ),
    duplicateFlag = DuplicateFlagColors(Color(0xFFFFF4E5), Color(0xFF5C3A00), Color(0xFF8A5A12)),
    category = CategoryPalette(
        gastronomy = CategoryColors(Color(0xFFFFDBD0), Color(0xFF7A2A12), Color(0xFFA9442A), White),
        culture = CategoryColors(Color(0xFFF8DCE7), Color(0xFF7A2246), Color(0xFF93315B), White),
        nature = CategoryColors(Color(0xFFCDEBD4), Color(0xFF14512A), Color(0xFF276B39), White),
        entertainment = CategoryColors(Color(0xFFFFE3BE), Color(0xFF6B3F00), Color(0xFFB4690E), White),
        history = CategoryColors(Color(0xFFD9E3F8), Color(0xFF23375C), Color(0xFF3F5B8C), White),
    ),
    mapCluster = ContainerColors(Color(0xFF33251F), White),
    mapClusterBorder = White,
    shadow = Color(0xFF3C2014),
)

// Valores oscuros de categorías, texto y marcadores: de los lienzos oscuros (el README solo da los claros).
internal val DarkExploraColors = ExploraColors(
    textSecondary = Color(0xFFE8D6CE),
    iconSecondary = Color(0xFFD6C1B8),
    textPlaceholder = Color(0xFFBCA69C),
    onSurfaceAccent = Color(0xFFFFB59D),
    onPrimaryContainerAccent = Color(0xFFFFDBD0),
    divider = Color(0xFF3A2C27),
    errorFieldContainer = Color(0xFF2A1917),
    outlineDisabled = Color(0xFF6B564E),
    warning = ContainerColors(Color(0xFF503500), Color(0xFFFFD9A0)),
    status = StatusColors(
        pending = ContainerColors(Color(0xFF503500), Color(0xFFFFD9A0)),
        verified = ContainerColors(Color(0xFF14472C), Color(0xFF9BE3B8)),
        rejected = ContainerColors(Color(0xFF73201A), Color(0xFFFFB4AB)),
        finalized = ContainerColors(Color(0xFF2A3F63), Color(0xFFC2D4F5)),
    ),
    duplicateFlag = DuplicateFlagColors(Color(0xFF3D2A0A), Color(0xFFFFD9A0), Color(0xFFF2C078)),
    category = CategoryPalette(
        gastronomy = CategoryColors(Color(0xFF7C2E16), Color(0xFFFFDBD0), Color(0xFFFFB59D), Color(0xFF5C1B08)),
        culture = CategoryColors(Color(0xFF5E2040), Color(0xFFF8DCE7), Color(0xFF93315B), White),
        nature = CategoryColors(Color(0xFF1E4A2C), Color(0xFFCDEBD4), Color(0xFF276B39), White),
        entertainment = CategoryColors(Color(0xFF503500), Color(0xFFFFD9A0), Color(0xFFB4690E), White),
        history = CategoryColors(Color(0xFF2A3F63), Color(0xFFC2D4F5), Color(0xFF3F5B8C), White),
    ),
    mapCluster = ContainerColors(Color(0xFF33251F), White),
    mapClusterBorder = White,
    shadow = Color.Transparent,
)

internal val LocalExploraColors = staticCompositionLocalOf { LightExploraColors }

/** Colores propios de ExploraCity, junto a `MaterialTheme.colorScheme`. */
val MaterialTheme.exploraColors: ExploraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExploraColors.current
