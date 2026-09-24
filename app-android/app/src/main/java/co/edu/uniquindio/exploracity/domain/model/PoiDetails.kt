package co.edu.uniquindio.exploracity.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/** Rango de precio tal como se captura en el paso 4 de publicación (18). */
enum class PriceRange {
    /** «Entrada libre». */
    FREE,

    /** «$ · Hasta 25.000». */
    LOW,

    /** «$$ · Entre 25.000 y 60.000». */
    MEDIUM,

    /** «$$$ · Más de 60.000». */
    HIGH,
}

/** Horario del paso 4: días de atención y una franja; sin horario exacto, el lugar no lo tiene (null). */
data class OpeningHours(val days: Set<DayOfWeek>, val opens: LocalTime, val closes: LocalTime)

/** Niveles de reputación (README · LevelChip) según los puntos. */
enum class UserLevel(val minPoints: Int) {
    TOURIST(0),
    EXPLORER(100),
    ADVENTURER(250),
    LOCAL_AMBASSADOR(500),
    ;

    companion object {
        fun fromPoints(points: Int): UserLevel = entries.last { points >= it.minPoints }
    }
}

data class Author(val id: String, val name: String, val points: Int) {
    val level: UserLevel get() = UserLevel.fromPoints(points)
}

/** Foto de la galería; [description] es lo que oye el lector («Patio interior del café»). */
data class PoiPhoto(val url: String?, val description: String)

/** 13 · Detalle del POI: el lugar del feed más todo lo que se muestra al abrirlo. */
data class PoiDetails(
    val poi: Poi,
    val description: String,
    val photos: List<PoiPhoto>,
    val address: String,
    val hours: OpeningHours?,
    val author: Author,
    val voted: Boolean,
    val visited: Boolean,
)

/** 14.b · Todo opcional salvo la marca de visitado. */
data class VisitExperience(val recommends: Boolean? = null, val text: String = "", val showName: Boolean = false) {
    companion object {
        /** README 14: los textos de la comunidad tienen como máximo 300 caracteres. */
        const val MAX_LENGTH = 300
    }
}

/** Respuesta al marcar visitado: los puntos los decide el servidor (componente de Reputación); 0 si no hay. */
data class VisitResult(val pointsAwarded: Int)
