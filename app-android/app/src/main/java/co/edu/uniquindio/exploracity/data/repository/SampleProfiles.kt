package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Residency

// Temporal hasta que exista la API: lo público de los autores de prueba y de la persona de la sesión. Camilo R. trae
// la biografía del lienzo 31.a; su nivel sigue siendo Aventurero (320 puntos), como en el detalle (13.a).

internal class ProfileSeed(val author: Author, val residency: Residency, val city: String, val bio: String?, val badges: Int)

private fun seed(author: Author, residency: Residency, bio: String?, badges: Int) = ProfileSeed(author, residency, "Bogotá", bio, badges)

private val authorsById = sampleAuthors.associateBy { it.id }

internal val sampleProfiles: Map<String, ProfileSeed> = listOf(
    seed(
        authorsById.getValue("camilo-r"),
        Residency.RESIDENT,
        "Guío caminatas por el centro los sábados. Me obsesionan las tiendas de barrio que llevan más de 40 años abiertas.",
        badges = 9,
    ),
    seed(authorsById.getValue("maria-paula"), Residency.RESIDENT, "Ilustradora. Busco cafés con buena luz para dibujar.", badges = 4),
    seed(authorsById.getValue("juan-david"), Residency.VISITOR, bio = null, badges = 1),
    seed(authorsById.getValue("laura-g"), Residency.RESIDENT, "Bióloga. Si hay un sendero o un humedal cerca, ahí estoy.", badges = 12),
    seed(sampleCurrentUser, Residency.RESIDENT, bio = null, badges = 2),
).associateBy { it.author.id }
