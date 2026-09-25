package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

// Temporal hasta que exista la API: autores y detalles de los lugares de prueba. Café Las Acacias trae los textos
// del lienzo 13.a; el resto se completa a partir de su resumen.

internal val sampleAuthors = listOf(
    Author("camilo-r", "Camilo R.", points = 320),
    Author("maria-paula", "María Paula", points = 180),
    Author("juan-david", "Juan David", points = 40),
    Author("laura-g", "Laura G.", points = 610),
)

/**
 * Lugares publicados por la persona de la sesión (Ana Ríos): sus avisos (25) hablan de ellos, así el aviso «tu lugar
 * quedó verificado» y el detalle («Publicado por Ana Ríos») coinciden. Sendero La Vieja es suyo como en 22.a y 25
 * («Laura comentó en Sendero La Vieja»).
 */
internal val currentUserPlaces = setOf("quinta-de-bolivar", "sendero-la-vieja", "casa-independencia")

private val mondayToSaturday = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY)
private val everyDay = DayOfWeek.entries.toSet()

private val details = mapOf(
    "cafe-las-acacias" to Extra(
        description = "Café de barrio con tostión propia y un patio interior lleno de matas. Buen wifi en la mañana y pan de queso recién hecho a las 8.",
        address = "Calle 45 # 19-32, Chapinero",
        hours = OpeningHours(mondayToSaturday, LocalTime.of(7, 0), LocalTime.of(19, 0)),
        photos = listOf("Patio interior del café", "Barra con la cafetera de tostión propia", "Pan de queso recién horneado"),
    ),
    "museo-del-oro" to Extra(
        address = "Carrera 6 # 15-88, La Candelaria",
        hours = OpeningHours(everyDay - MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
        photos = listOf("Sala de orfebrería con piezas muiscas", "Balsa muisca iluminada en su vitrina"),
    ),
    "cerro-monserrate" to Extra(
        address = "Carrera 2 Este # 21-48, Paseo Bolívar",
        hours = OpeningHours(everyDay, LocalTime.of(6, 0), LocalTime.of(22, 0)),
        photos = listOf("Vista de Bogotá desde la cima", "Funicular subiendo entre el bosque"),
    ),
    "la-puerta-falsa" to Extra(
        address = "Calle 11 # 6-50, La Candelaria",
        hours = OpeningHours(everyDay, LocalTime.of(7, 0), LocalTime.of(22, 0)),
        photos = listOf("Tamal con chocolate y queso sobre la mesa"),
    ),
)

private class Extra(
    val description: String? = null,
    val address: String? = null,
    val hours: OpeningHours? = null,
    val photos: List<String> = emptyList(),
)

/** Detalle de prueba de [poi]: el del lienzo si existe; si no, uno verosímil armado con su resumen. */
fun sampleDetails(poi: Poi): PoiDetails {
    val extra = details[poi.id]
    val index = samplePois.indexOfFirst { it.id == poi.id }.coerceAtLeast(0)
    val summary = poi.summary.orEmpty()
    return PoiDetails(
        poi = poi,
        description = extra?.description ?: "$summary Publicado por la comunidad y revisado por un moderador.".trim(),
        photos = (extra?.photos?.takeIf { it.isNotEmpty() } ?: listOf("Vista principal de ${poi.title}")).map { PoiPhoto(url = null, description = it) },
        address = extra?.address ?: "Bogotá",
        // Sin dato de «abierto ahora» se asume que el autor no dio horario exacto (casilla del paso 4).
        hours = extra?.hours ?: poi.openNow?.let { OpeningHours(mondayToSaturday, LocalTime.of(9, 0), LocalTime.of(18, 0)) },
        author = if (poi.id in currentUserPlaces) sampleCurrentUser else sampleAuthors[index % sampleAuthors.size],
        voted = false,
        visited = false,
    )
}
