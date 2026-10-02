package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// Temporal hasta que exista la API: las pendientes de otras personas en la cola de moderación (32). Con las dos de
// Ana (sampleHiddenPublications) son las 7 del lienzo, 2 de ellas posibles duplicados. Los nombres del lienzo ya están
// en el feed como lugares verificados; aquí van otros para que, al verificarlos (34), no aparezcan repetidos.

/** Una pendiente de otra persona. [submittedAgo] cuenta desde que abre la app; [similarIds] y [note], como en 17B. */
internal class ReviewSeed(
    val id: String,
    val title: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val description: String,
    val photoCount: Int,
    val hours: OpeningHours?,
    val price: PriceRange?,
    val address: String,
    val location: GeoPoint,
    val submittedAgo: Duration,
    val authorId: String,
    val reportReason: String? = null,
    val similarIds: List<String> = emptyList(),
    val note: String? = null,
)

private val everyDay = DayOfWeek.entries.toSet()

internal val sampleReviewSeeds = listOf(
    // 33.a: la más antigua, con la urgencia en rojo.
    ReviewSeed(
        id = "mirador-cruz-de-piedra",
        title = "Mirador de la Cruz de Piedra",
        category = Category.NATURE,
        categoryOrigin = CategoryOrigin.SUGGESTED,
        description = "Subida de 40 minutos desde el paradero. Al llegar se ve todo el valle y hay una cruz de piedra de 1890. " +
            "Mejor ir temprano porque se nubla.",
        photoCount = 3,
        hours = OpeningHours(everyDay, LocalTime.of(6, 0), LocalTime.of(17, 0)),
        price = PriceRange.FREE,
        address = "Vereda El Alto",
        location = GeoPoint(4.71203, -74.03118),
        submittedAgo = 3.days + 2.hours,
        authorId = "camilo-r",
    ),
    // Parecida a dos lugares a menos de 50 m (el lienzo de duplicados: «parecido a 2 lugares»).
    ReviewSeed(
        id = "sala-botero-biblioteca",
        title = "Sala Botero de la Biblioteca",
        category = Category.CULTURE,
        categoryOrigin = CategoryOrigin.CHOSEN,
        description = "Sala nueva con obras de la donación Botero dentro de la manzana del Banco de la República. Se entra por la calle 11.",
        photoCount = 2,
        hours = OpeningHours(everyDay - DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)),
        price = PriceRange.FREE,
        address = "Calle 11 # 4-41",
        location = GeoPoint(4.59685, -74.07285),
        submittedAgo = 1.days + 5.hours,
        authorId = "juan-david",
        similarIds = listOf("museo-botero", "biblioteca-luis-angel"),
        note = "Es la sala del segundo piso, no el museo de la casa colonial.",
    ),
    // 33.b: reportada por la comunidad.
    ReviewSeed(
        id = "cafe-el-patio",
        title = "Café El Patio Interior",
        category = Category.GASTRONOMY,
        categoryOrigin = CategoryOrigin.CHOSEN,
        description = "Café de barrio con tostión propia y un patio interior lleno de matas. Buen wifi en la mañana.",
        photoCount = 1,
        hours = OpeningHours(everyDay - DayOfWeek.SUNDAY, LocalTime.of(7, 0), LocalTime.of(19, 0)),
        price = PriceRange.LOW,
        address = "Calle 45 # 19-32",
        location = GeoPoint(4.63412, -74.06558),
        submittedAgo = 1.days + 1.hours,
        authorId = "laura-g",
        reportReason = "información desactualizada",
    ),
    ReviewSeed(
        id = "taller-titeres-macarena",
        title = "Taller de títeres La Macarena",
        category = Category.CULTURE,
        categoryOrigin = CategoryOrigin.SUGGESTED,
        description = "Taller abierto los sábados para hacer títeres de trapo con los vecinos; los materiales los ponen ellos.",
        photoCount = 1,
        hours = OpeningHours(setOf(DayOfWeek.SATURDAY), LocalTime.of(10, 0), LocalTime.of(14, 0)),
        price = PriceRange.MEDIUM,
        address = "Carrera 4A # 26-14",
        location = GeoPoint(4.6131, -74.0662),
        submittedAgo = 4.hours,
        authorId = "juan-david",
    ),
    ReviewSeed(
        id = "iglesia-la-candelaria",
        title = "Iglesia de La Candelaria",
        category = Category.HISTORY,
        categoryOrigin = CategoryOrigin.SUGGESTED,
        description = "Iglesia de 1703 con retablos dorados. Se puede entrar sin pagar entre las misas.",
        photoCount = 2,
        hours = OpeningHours(everyDay, LocalTime.of(7, 0), LocalTime.of(18, 0)),
        price = PriceRange.FREE,
        address = "Calle 11 # 3-89",
        location = GeoPoint(4.5974, -74.0702),
        submittedAgo = 1.hours,
        authorId = "maria-paula",
    ),
)

/** Historial con la moderación de cada autor (33): verificadas y rechazos previos. El de Ana sale de sus publicaciones. */
internal val sampleReviewHistory: Map<String, Pair<Int, Int>> = mapOf(
    "camilo-r" to (18 to 0),
    "juan-david" to (2 to 1),
    "laura-g" to (25 to 1),
    "maria-paula" to (7 to 0),
)
