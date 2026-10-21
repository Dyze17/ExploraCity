package co.edu.uniquindio.exploracity.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// Caché para ver sin conexión (12.a): lo último que cargó el feed y el detalle de esos lugares (opción A de Daniel).

/** Lugar del feed guardado; [position] conserva el orden en que se vio. */
@Entity(tableName = "saved_pois")
data class SavedPoiEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val votes: Int,
    val comments: Int,
    val photoUrl: String?,
    val price: PriceRange?,
    val openNow: Boolean?,
    val summary: String?,
)

/** Detalle (13) de un lugar guardado. Se borra con el lugar: no se guardan detalles sueltos. */
@Entity(
    tableName = "saved_details",
    foreignKeys = [ForeignKey(entity = SavedPoiEntity::class, parentColumns = ["id"], childColumns = ["poiId"], onDelete = ForeignKey.CASCADE)],
)
data class SavedDetailsEntity(
    @PrimaryKey val poiId: String,
    val description: String,
    val photos: List<SavedPhoto>,
    val address: String,
    /** Días del horario separados por coma (MONDAY,TUESDAY…): lo que guardó la 1.2. Desde la 1.3.0 se usa [hoursJson]. */
    val openDays: String?,
    val opensAt: String?,
    val closesAt: String?,
    /** Vacío si la cuenta que lo publicó se eliminó ([DELETED_AUTHOR]). */
    val authorId: String,
    val authorName: String,
    val authorPoints: Int,
    val voted: Boolean,
    val visited: Boolean,
    val savedAtMillis: Long,
    /** v1.3.0 · El horario por tramos o siempre abierto ([StoredHours]); null sin horario o en lo que guardó la 1.2. */
    val hoursJson: String? = null,
)

@Serializable
data class SavedPhoto(val url: String?, val description: String)

/** Sin autor (cuenta eliminada) en saved_details: un id vacío, así la tabla no cambia de esquema. */
internal const val DELETED_AUTHOR = ""

/** Cuándo se guardó cada conjunto (hoy solo el feed): «guardados hace 2 horas». */
@Entity(tableName = "cache_info")
data class CacheInfoEntity(@PrimaryKey val key: String, val savedAtMillis: Long)

class SavedPlacesConverters {
    @TypeConverter
    fun photosToJson(photos: List<SavedPhoto>): String = Json.encodeToString(photos)

    @TypeConverter
    fun photosFromJson(json: String): List<SavedPhoto> = Json.decodeFromString(json)
}

fun Poi.toEntity(position: Int) = SavedPoiEntity(
    id = id,
    position = position,
    title = title,
    category = category,
    status = status,
    latitude = location.latitude,
    longitude = location.longitude,
    distanceMeters = distanceMeters,
    votes = votes,
    comments = comments,
    photoUrl = photoUrl,
    price = price,
    openNow = openNow,
    summary = summary,
)

fun SavedPoiEntity.toDomain() = Poi(
    id = id,
    title = title,
    category = category,
    status = status,
    location = GeoPoint(latitude, longitude),
    distanceMeters = distanceMeters,
    votes = votes,
    comments = comments,
    photoUrl = photoUrl,
    price = price,
    openNow = openNow,
    summary = summary,
)

fun PoiDetails.toEntity(savedAt: Instant) = SavedDetailsEntity(
    poiId = poi.id,
    description = description,
    photos = photos.map { SavedPhoto(it.url, it.description) },
    address = address,
    openDays = null,
    opensAt = null,
    closesAt = null,
    hoursJson = hours?.let { StoredHours.of(it).encode() },
    authorId = author?.id ?: DELETED_AUTHOR,
    authorName = author?.name.orEmpty(),
    authorPoints = author?.points ?: 0,
    voted = voted,
    visited = visited,
    savedAtMillis = savedAt.toEpochMilli(),
)

fun SavedDetailsEntity.toDomain(poi: SavedPoiEntity) = PoiDetails(
    poi = poi.toDomain(),
    description = description,
    photos = photos.map { PoiPhoto(it.url, it.description) },
    address = address,
    hours = hoursJson?.let(StoredHours::decode) ?: if (openDays != null && opensAt != null && closesAt != null) {
        OpeningHours.of(openDays.split(',').map(DayOfWeek::valueOf).toSet(), LocalTime.parse(opensAt), LocalTime.parse(closesAt))
    } else {
        null
    },
    author = Author(authorId, authorName, authorPoints).takeUnless { authorId == DELETED_AUTHOR },
    voted = voted,
    visited = visited,
    savedAt = Instant.ofEpochMilli(savedAtMillis),
)
