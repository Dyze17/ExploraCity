package co.edu.uniquindio.exploracity.domain.model

import java.time.DayOfWeek
import java.time.Instant
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

/**
 * 18 · Un tramo del horario: unos días con la misma franja. Si [closes] no es posterior a [opens], cierra al día
 * siguiente: «viernes y sábado, 22:00 a 3:00» (v1.3.0).
 */
data class HoursBlock(val days: Set<DayOfWeek>, val opens: LocalTime, val closes: LocalTime) {
    val pastMidnight: Boolean get() = !closes.isAfter(opens)
}

/** 18 · El horario de un lugar; sin horario exacto, el lugar no lo tiene (null). */
sealed interface OpeningHours {
    /** Por tramos de días, en orden: «lunes a jueves, 16:00 a 2:00» y «viernes y sábado, 22:00 a 3:00». */
    data class Blocks(val blocks: List<HoursBlock>) : OpeningHours

    /** v1.3.0 · Sin puertas ni horario: una plaza, un parque, una cascada. */
    data object AlwaysOpen : OpeningHours

    companion object {
        /** Un solo tramo, como los horarios de antes de la 1.3.0. */
        fun of(days: Set<DayOfWeek>, opens: LocalTime, closes: LocalTime): OpeningHours = Blocks(listOf(HoursBlock(days, opens, closes)))
    }
}

/**
 * Niveles de reputación (README · LevelChip) según los puntos. El primero es «Recién llegado» y no «Turista», que se
 * confundía con quien se presenta «De visita».
 */
enum class UserLevel(val minPoints: Int) {
    NEWCOMER(0),
    EXPLORER(100),
    ADVENTURER(250),
    LOCAL_AMBASSADOR(500),
    ;

    /** El nivel siguiente; null en el máximo. */
    val next: UserLevel? get() = entries.getOrNull(ordinal + 1)

    /** Último punto del nivel («250 a 499 puntos»); null en el máximo («Desde 500 puntos»). */
    val maxPoints: Int? get() = next?.let { it.minPoints - 1 }

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
    /** null si la cuenta que lo publicó se eliminó: el lugar sigue publicado sin su nombre (30). */
    val author: Author?,
    val voted: Boolean,
    val visited: Boolean,
    /** Cuándo se guardó, si viene de lo guardado para ver sin conexión (12.a); null si llegó del servidor. */
    val savedAt: Instant? = null,
)

/** 14.b · Todo opcional salvo la marca de visitado. */
data class VisitExperience(val recommends: Boolean? = null, val text: String = "", val showName: Boolean = false) {
    companion object {
        /** README 14: los textos de la comunidad tienen como máximo 300 caracteres. */
        const val MAX_LENGTH = 300
    }
}

/**
 * Respuesta al marcar visitado: los puntos los decide el servidor (componente de Reputación); 0 si no hay. Con [queued]
 * no había red: la visita quedó en la cola y se envía al volver (14.b: «se guarda y se envía luego»).
 */
data class VisitResult(val pointsAwarded: Int, val queued: Boolean = false)

/** Voto «Es importante» resultante; con [queued] quedó en la cola hasta que vuelva la red. */
data class VoteResult(val votes: Int, val queued: Boolean = false)
