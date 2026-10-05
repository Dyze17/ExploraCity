package co.edu.uniquindio.exploracity.ui.preview

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

// Solo para las vistas previas (@Preview) de Android Studio: la app nunca los muestra. Los datos llegan de la API.

/** Quien mira las vistas previas. */
internal val previewUser = Author("ana-rios", "Ana Ríos", points = 340)

private val previewOther = Author("laura-gomez", "Laura Gómez", points = 860)

/** Lugares de Armenia con lo que muestran el feed, el mapa y las tarjetas. */
internal val previewPlaces = listOf(
    Poi(
        id = "cafe-las-acacias",
        title = "Café Las Acacias",
        category = Category.GASTRONOMY,
        status = PublicationStatus.VERIFIED,
        location = GeoPoint(4.5402, -75.6721),
        distanceMeters = 320,
        votes = 48,
        comments = 12,
        price = PriceRange.LOW,
        openNow = true,
        summary = "Café de origen con tostión propia y vista al parque principal.",
    ),
    Poi(
        id = "mirador-de-la-secreta",
        title = "Mirador de la Secreta",
        category = Category.NATURE,
        status = PublicationStatus.VERIFIED,
        location = GeoPoint(4.5521, -75.6589),
        distanceMeters = 1_850,
        votes = 31,
        comments = 4,
        price = PriceRange.FREE,
        openNow = false,
        summary = "Un mirador con vista a toda la ciudad, ideal al atardecer.",
    ),
    Poi(
        id = "plaza-de-bolivar",
        title = "Plaza de Bolívar",
        category = Category.HISTORY,
        status = PublicationStatus.VERIFIED,
        location = GeoPoint(4.5339, -75.6811),
        distanceMeters = 1_218,
        votes = 26,
        comments = 7,
        price = PriceRange.FREE,
        summary = "La plaza principal, con la catedral y el monumento al esfuerzo.",
    ),
    Poi(
        id = "museo-del-oro-quimbaya",
        title = "Museo del Oro Quimbaya",
        category = Category.CULTURE,
        status = PublicationStatus.VERIFIED,
        location = GeoPoint(4.5565, -75.6582),
        distanceMeters = 2_430,
        votes = 19,
        comments = 3,
        price = PriceRange.FREE,
        openNow = true,
        summary = "Orfebrería quimbaya y la historia del valle.",
    ),
    Poi(
        id = "feria-de-la-calle-14",
        title = "Feria de la calle 14",
        category = Category.ENTERTAINMENT,
        status = PublicationStatus.FINALIZED,
        location = GeoPoint(4.5345, -75.6765),
        distanceMeters = 980,
        votes = 9,
        comments = 2,
        price = PriceRange.MEDIUM,
        summary = "Música en vivo y comida callejera los fines de semana.",
    ),
)

/** El detalle (13) de [poi], publicado por otra persona. */
internal fun previewDetails(poi: Poi): PoiDetails = PoiDetails(
    poi = poi,
    description = "${poi.summary.orEmpty()} Vale la pena llegar temprano: después del mediodía se llena.",
    photos = (1..3).map { PoiPhoto(url = null, description = "${poi.title}. Foto $it de 3") },
    address = "Cra. 14 #12-30, Armenia",
    hours = OpeningHours(DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.toSet(), LocalTime.of(7, 0), LocalTime.of(19, 0)),
    author = previewOther,
    voted = false,
    visited = false,
)

/** Dos comentarios (14) de [poi], el más reciente primero. */
internal fun previewComments(poi: Poi, now: Instant): List<Comment> = listOf(
    Comment("${poi.id}-1", previewOther, "Fui un sábado en la mañana y estaba tranquilo. El café de la casa, muy bueno.", now - Duration.ofHours(3)),
    Comment("${poi.id}-2", null, "Queda cerca del parque: se puede llegar caminando.", now - Duration.ofDays(2)),
)

/** Insignias (27): una desbloqueada y dos en camino. */
internal val previewBadges = listOf(
    Badge(
        id = "primera-publicacion",
        name = "Primera publicación",
        metric = BadgeMetric.PUBLICATIONS,
        progress = 1,
        target = 1,
        howTo = "Publica tu primer lugar en ExploraCity.",
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
        id = "amigo-del-verde",
        name = "Amigo del verde",
        metric = BadgeMetric.CATEGORY_PLACES,
        category = Category.NATURE,
        progress = 0,
        target = 1,
        howTo = "Consigue que se verifique un lugar de naturaleza publicado por ti.",
    ),
)
