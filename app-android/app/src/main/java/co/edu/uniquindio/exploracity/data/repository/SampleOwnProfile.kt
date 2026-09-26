package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Rejection
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.RequiredFix
import java.time.Instant
import java.time.YearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

// Temporal hasta que exista la API: lo que solo ve Ana de sí misma (24, 26 y 27). Sus lugares públicos son los de
// currentUserPlaces; aquí están los que aún no se ven en el feed. Con 3 verificadas cuadran el «3 de 10» de 27.a y el
// aviso «3 lugares verificados · te faltan 7» (25).

/** Publicación propia que el feed no muestra (pendiente o rechazada). Un duplicado guarda el id del original. */
internal class PublicationSeed(val publication: OwnPublication, val duplicateOfId: String? = null)

/**
 * Las pendientes y rechazadas de Ana (22 y 24). Las dos rechazadas son las de los avisos (25) y se rechazaron a la
 * misma hora que dicen. Mirador de La Peña trae el mensaje y la lista del lienzo 24.a; Puerta Falsa, tamales está a
 * unos 23 m de La Puerta Falsa, como «La Fonda Café, a 23 m» en el lienzo de duplicado.
 */
internal fun sampleHiddenPublications(now: Instant): List<PublicationSeed> {
    fun ago(duration: Duration) = now - duration.toJavaDuration()
    return listOf(
        PublicationSeed(
            OwnPublication(
                id = "panaderia-la-candelaria",
                title = "Panadería La Candelaria",
                category = Category.GASTRONOMY,
                status = PublicationStatus.PENDING,
                location = GeoPoint(4.5966, -74.0718),
                photos = 3,
                submittedAt = ago(2.hours),
                description = "Pan de yuca y almojábanas recién horneadas desde las 6 de la mañana, a una cuadra del Chorro de Quevedo.",
                // Ana confirmó que es otro lugar que uno cercano (17B): el moderador la ve como posible duplicado.
                possibleDuplicate = true,
            ),
        ),
        PublicationSeed(
            OwnPublication(
                id = "murales-calle-26",
                title = "Murales de la calle 26",
                category = Category.CULTURE,
                status = PublicationStatus.PENDING,
                location = GeoPoint(4.6155, -74.0790),
                photos = 4,
                submittedAt = ago(1.days),
                description = "Tramo de murales entre la carrera 5 y la 13; se recorre a pie en media hora y de día hay buena luz para fotos.",
            ),
        ),
        PublicationSeed(
            OwnPublication(
                id = "mirador-de-la-pena",
                title = "Mirador de La Peña",
                category = Category.NATURE,
                status = PublicationStatus.REJECTED,
                location = GeoPoint(4.5905, -74.0590),
                photos = 2,
                submittedAt = ago(4.days),
                description = "Mirador en la subida de La Peña con vista al centro. Se llega por un sendero corto desde el barrio.",
                rejection = Rejection(
                    reason = RejectionReason.PHOTO,
                    message = "La foto no permite reconocer el lugar y el pin quedó a unas tres cuadras de la entrada. " +
                        "Si subes una foto del mirador y ajustas la ubicación, con gusto la revisamos otra vez.",
                    reviewerName = "Laura M.",
                    rejectedAt = ago(3.hours),
                    canResubmit = true,
                    fixes = listOf(
                        RequiredFix(FixKind.PHOTOS, "Una foto donde se vea el mirador completo"),
                        RequiredFix(FixKind.LOCATION, "El pin sobre la entrada del sendero"),
                    ),
                ),
            ),
        ),
        PublicationSeed(
            OwnPublication(
                id = "puerta-falsa-tamales",
                title = "Puerta Falsa, tamales",
                category = Category.GASTRONOMY,
                status = PublicationStatus.REJECTED,
                location = GeoPoint(4.59791, -74.0746),
                photos = 1,
                submittedAt = ago(1.days),
                description = "Los tamales santafereños y el chocolate con queso de siempre, en la esquina de la catedral.",
                rejection = Rejection(
                    reason = RejectionReason.DUPLICATE,
                    message = "Es La Puerta Falsa, que ya está publicada. Tu experiencia le sirve a la comunidad en sus comentarios.",
                    reviewerName = "Laura M.",
                    rejectedAt = ago(45.minutes),
                    canResubmit = false,
                ),
            ),
            duplicateOfId = "la-puerta-falsa",
        ),
    )
}

/**
 * Cuándo envió Ana sus lugares públicos (currentUserPlaces) y los puntos que ganó con cada uno: +15 al quedar
 * verificada y +20 más la primera (README · «Puntos que aparecen en el diseño»). Quinta de Bolívar se verificó hace 20
 * minutos, como dice su aviso (25).
 */
internal class PublicSubmission(val submittedAgo: Duration, val pointsEarned: Int)

internal val samplePublicSubmissions: Map<String, PublicSubmission> = mapOf(
    "casa-independencia" to PublicSubmission(40.days, pointsEarned = 35),
    "sendero-la-vieja" to PublicSubmission(12.days, pointsEarned = 15),
    "quinta-de-bolivar" to PublicSubmission(2.days, pointsEarned = 15),
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
