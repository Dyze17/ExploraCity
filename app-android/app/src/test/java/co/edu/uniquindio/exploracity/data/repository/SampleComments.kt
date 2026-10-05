package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.Poi
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

// Temporal hasta que exista la API: comentarios de los lugares de prueba. Café Las Acacias trae los del lienzo 14.a
// y Sendero La Vieja abre con el de Laura, al que lleva el aviso de 25. El resto se completa con frases generales,
// tantas como diga el contador del lugar.

/** Persona de la sesión mientras no haya inicio de sesión real: Ana Ríos, Aventurera con 340 puntos como en los lienzos (26.a). */
val sampleCurrentUser = Author("ana-rios", "Ana Ríos", points = 340)

private val commenters = sampleAuthors + listOf(
    Author("andres-m", "Andrés M.", points = 90),
    Author("valentina-c", "Valentina C.", points = 140),
    Author("sofia-t", "Sofía T.", points = 275),
    Author("felipe-o", "Felipe O.", points = 20),
    Author("natalia-p", "Natalia P.", points = 520),
)

private class Seed(val authorId: String, val text: String, val age: Duration)

private val seeds = mapOf(
    "cafe-las-acacias" to listOf(
        Seed("maria-paula", "El pan de queso de las 8 vale el madrugón. Pidan el café de Nariño.", 2.days),
        Seed("juan-david", "Fui un domingo y estaba cerrado. Ojo con el horario.", 5.days),
        Seed("laura-g", "El patio tiene enchufes y el wifi aguanta videollamadas.", 9.days),
        Seed("andres-m", "Precios justos para la zona. El capuchino es de lo mejor que he probado en Chapinero.", 16.days),
    ),
    "sendero-la-vieja" to listOf(
        Seed("laura-g", "Subimos a las 7 y el bosque de niebla estaba precioso. Lleven buenos zapatos: hay barro.", 20.minutes),
        Seed("sofia-t", "Hay que inscribirse antes en la página del acueducto; los cupos se acaban rápido.", 3.days),
    ),
)

private val phrases = listOf(
    "Fuimos en familia y nos encantó. Volveremos.",
    "Vale la pena ir temprano, antes de que se llene.",
    "Lleven chaqueta: en la tarde hace frío.",
    "Fácil de llegar en TransMilenio y bien señalizado.",
    "Los fines de semana hay mucha gente; entre semana es más tranquilo.",
    "Buen plan con amigos. De día el sector se siente seguro.",
    "Me gustó más de lo que esperaba. Recomendado.",
    "Hay que tener paciencia con la fila, pero vale la pena.",
    "Con la luz de la mañana salen fotos muy lindas.",
    "Cerca hay cafés y tiendas para completar el paseo.",
    "Fui con mis papás y lo disfrutaron mucho.",
    "Queda un poco escondido, pero se encuentra fácil con el mapa.",
    "Nos atendieron muy bien y nos explicaron todo con calma.",
    "No tiene mucha sombra: lleven agua y gorra.",
)

/** Comentarios de prueba de [poi], del más reciente al más antiguo, contados desde [now]. */
fun sampleComments(poi: Poi, now: Instant): List<Comment> {
    val offset = samplePois.indexOfFirst { it.id == poi.id }.coerceAtLeast(0)
    val fixed = seeds[poi.id].orEmpty().take(poi.comments)
    val firstAge = fixed.lastOrNull()?.age ?: 3.hours
    val general = (fixed.size until poi.comments).map { i ->
        val step = i - fixed.size + 1
        // Paso 4 sobre las 9 personas (coprimos): van saliendo todas, no siempre las mismas tres.
        Seed(
            authorId = commenters[(i * 4 + offset) % commenters.size].id,
            text = phrases[(i + offset) % phrases.size],
            age = firstAge + 4.days * step + ((i * 7) % 5).hours,
        )
    }
    return (fixed + general).mapIndexed { i, seed ->
        Comment(
            id = "${poi.id}-c$i",
            author = commenters.first { it.id == seed.authorId },
            text = seed.text,
            createdAt = now - seed.age.toJavaDuration(),
        )
    }
}
