package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import java.time.YearMonth

// Temporal hasta que exista la API: lo que solo ve Ana de sí misma (26 y 27). Sus lugares públicos son los de
// currentUserPlaces; aquí están los que aún no se ven en el feed. Con 3 verificadas cuadran el «3 de 10» de 27.a y el
// aviso «3 lugares verificados · te faltan 7» (25).

/** Publicación propia que el feed no muestra: pendiente de verificación o rechazada (22). */
internal class HiddenPublication(val id: String, val title: String, val category: Category, val status: PublicationStatus)

internal val sampleHiddenPublications = listOf(
    HiddenPublication("panaderia-la-candelaria", "Panadería La Candelaria", Category.GASTRONOMY, PublicationStatus.PENDING),
    HiddenPublication("murales-calle-26", "Murales de la calle 26", Category.CULTURE, PublicationStatus.PENDING),
    // Las dos de los avisos de rechazo (25).
    HiddenPublication("mirador-de-la-pena", "Mirador de La Peña", Category.NATURE, PublicationStatus.REJECTED),
    HiddenPublication("puerta-falsa-tamales", "Puerta Falsa, tamales", Category.GASTRONOMY, PublicationStatus.REJECTED),
)

/** «Residente · Bogotá · desde marzo» (26.a). */
internal val sampleMemberSince: YearMonth = YearMonth.of(2026, 3)

/**
 * Las 9 insignias de «Insignias · 2 de 9». Las cuatro primeras son las del diseño (26.a y 27.a), con sus textos de
 * 27A; las demás son ejemplos hasta que el catálogo llegue del servidor. Ana tiene dos: con cuatro, como dice el
 * encabezado del lienzo, la cuadrícula de 27.a y la vista previa de 26.a no podrían verse como están dibujadas.
 */
internal val sampleBadges = listOf(
    Badge(
        id = "primera-publicacion",
        name = "Primera publicación",
        metric = BadgeMetric.PUBLICATIONS,
        progress = 1,
        target = 1,
        howTo = "Publica tu primer lugar en ExploraCity.",
    ),
    Badge(
        id = "amigo-del-verde",
        name = "Amigo del verde",
        metric = BadgeMetric.CATEGORY_PLACES,
        category = Category.NATURE,
        progress = 1,
        target = 1,
        howTo = "Consigue que se verifique un lugar de naturaleza publicado por ti.",
    ),
    Badge(
        id = "diez-verificadas",
        name = "10 verificadas",
        metric = BadgeMetric.VERIFIED_PLACES,
        progress = 3,
        target = 10,
        howTo = "Consigue que 10 de tus publicaciones queden verificadas por un moderador.",
        tip = "Las fotos claras y el pin sobre la entrada ayudan a que la verificación sea más rápida.",
    ),
    Badge(
        id = "cincuenta-comentarios",
        name = "50 comentarios",
        metric = BadgeMetric.COMMENTS,
        progress = 12,
        target = 50,
        howTo = "Escribe 50 comentarios en lugares de la comunidad.",
        tip = "Los comentarios más útiles cuentan qué pediste, a qué hora fuiste o cómo se llega.",
    ),
    Badge(
        id = "guardian-de-la-historia",
        name = "Guardián de la historia",
        metric = BadgeMetric.CATEGORY_PLACES,
        category = Category.HISTORY,
        progress = 2,
        target = 10,
        howTo = "Consigue que se verifiquen 10 lugares de historia publicados por ti.",
        tip = "Casas antiguas, iglesias y plazas con su historia cuentan en esta categoría.",
    ),
    Badge(
        id = "voz-de-la-comunidad",
        name = "Voz de la comunidad",
        metric = BadgeMetric.VOTES_RECEIVED,
        progress = 209,
        target = 1000,
        howTo = "Suma 1.000 votos «Es importante» entre todos tus lugares.",
        tip = "Los lugares con buenas fotos y la descripción completa reciben más votos.",
    ),
    Badge(
        id = "caminante",
        name = "Caminante",
        metric = BadgeMetric.VISITS,
        progress = 3,
        target = 20,
        howTo = "Marca 20 lugares como visitados.",
        tip = "Cuando estés en un lugar, márcalo como visitado desde su detalle.",
    ),
    Badge(
        id = "buen-provecho",
        name = "Buen provecho",
        metric = BadgeMetric.CATEGORY_PLACES,
        category = Category.GASTRONOMY,
        progress = 0,
        target = 5,
        howTo = "Consigue que se verifiquen 5 lugares de gastronomía publicados por ti.",
        tip = "Las tiendas de barrio y los puestos de mercado también cuentan.",
    ),
    Badge(
        id = "cultura-viva",
        name = "Cultura viva",
        metric = BadgeMetric.CATEGORY_PLACES,
        category = Category.CULTURE,
        progress = 0,
        target = 5,
        howTo = "Consigue que se verifiquen 5 lugares de cultura publicados por ti.",
        tip = "Galerías, murales y teatros cuentan como cultura.",
    ),
)
