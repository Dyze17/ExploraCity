package co.edu.uniquindio.exploracity.ui.screens.moderation

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Solo para las vistas previas de 32 y 33: una pendiente urgente y una reportada con posible duplicado. */
internal fun previewReviews(now: Instant): List<ReviewItem> {
    fun photos(id: String, count: Int) = (1..count).map { PublishedPhoto("$id-$it", "fake://fotos/$id-$it.jpg") }
    val near = Poi("museo-botero", "Museo Botero", Category.CULTURE, PublicationStatus.VERIFIED, GeoPoint(4.5967, -74.0730), 0, 187, 40)
    return listOf(
        ReviewItem(
            id = "mirador",
            title = "Mirador de la Cruz de Piedra",
            category = Category.NATURE,
            categoryOrigin = CategoryOrigin.SUGGESTED,
            description = "Subida de 40 minutos desde el paradero. Al llegar se ve todo el valle y hay una cruz de piedra de 1890.",
            photos = photos("mirador", 3),
            hours = OpeningHours(DayOfWeek.entries.toSet(), LocalTime.of(6, 0), LocalTime.of(17, 0)),
            price = PriceRange.FREE,
            address = "Vereda El Alto",
            location = GeoPoint(4.71203, -74.03118),
            submittedAt = now.minusSeconds(3 * 86_400 + 7_200),
            author = ReviewAuthor(Author("camilo-r", "Camilo R.", 320), verified = 18, rejected = 0),
        ),
        ReviewItem(
            id = "sala",
            title = "Sala Botero de la Biblioteca",
            category = Category.CULTURE,
            categoryOrigin = CategoryOrigin.CHOSEN,
            description = "Sala nueva con obras de la donación Botero. Se entra por la calle 11.",
            photos = photos("sala", 2),
            hours = null,
            price = PriceRange.FREE,
            address = null,
            location = GeoPoint(4.59685, -74.07285),
            submittedAt = now.minusSeconds(5 * 3_600),
            author = ReviewAuthor(Author("juan-david", "Juan David", 40), verified = 2, rejected = 1),
            reportReason = "información desactualizada",
            duplicate = DuplicateSuspicion(listOf(DuplicateCandidate(near, 23)), authorNote = "Es la sala del segundo piso."),
        ),
    )
}
