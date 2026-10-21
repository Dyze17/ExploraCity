package co.edu.uniquindio.exploracity.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/** Los 5 pasos del formulario de publicación (15–19), con el nombre corto del indicador («Paso 1 de 5 · Lo básico»). */
enum class PublishStep {
    /** 15 · Título y descripción. */
    BASICS,

    /** 16 · Categoría con sugerencia automática. */
    CATEGORY,

    /** 17 · Ubicación en el mapa. */
    LOCATION,

    /** 18 · Horario y rango de precio. */
    SCHEDULE,

    /** 19 · Fotografías. */
    PHOTOS,
    ;

    /** 1 a 5. */
    val number: Int get() = ordinal + 1

    val next: PublishStep? get() = entries.getOrNull(ordinal + 1)

    val previous: PublishStep? get() = entries.getOrNull(ordinal - 1)

    companion object {
        /** El paso con ese número (el de la ruta al reenviar desde 24); fuera de rango, el primero. */
        fun of(number: Int): PublishStep = entries.getOrNull(number - 1) ?: BASICS
    }
}

/** De dónde salió la categoría: se guarda para las métricas de la sugerencia (README 16). */
enum class CategoryOrigin { SUGGESTED, CHOSEN }

/**
 * Borrador de una publicación (15–19). Sobrevive a la rotación, al cierre de la app y a la falta de red (README ·
 * «Borrador en DataStore + rememberSaveable»).
 */
data class PublicationDraft(
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val categoryOrigin: CategoryOrigin? = null,
    /** 17 · Dónde puso el pin la persona; null mientras no lo haya puesto (el mapa empieza en el centro de la ciudad). */
    val location: GeoPoint? = null,
    /** 17A/17B · La búsqueda de parecidos de la última ubicación confirmada. */
    val duplicateCheck: DuplicateCheck? = null,
    /** 18 · El horario, tal como se va eligiendo: por tramos, siempre abierto o sin saberlo. */
    val schedule: DraftSchedule = DraftSchedule(),
    /** 18 · Opcional: el feed muestra el lugar sin precio. */
    val price: PriceRange? = null,
    /** 19 · En el orden en que se agregaron; la primera es la portada. */
    val photos: List<DraftPhoto> = emptyList(),
    /** Paso en el que quedó, para retomarlo ahí. */
    val step: PublishStep = PublishStep.BASICS,
) {
    val titleMissing: Int get() = PublicationLimits.titleMissing(title)

    val descriptionMissing: Int get() = PublicationLimits.descriptionMissing(description)

    /** 18 · El horario que se publica: null con «No sé el horario» o si aún no está completo. */
    val openingHours: OpeningHours? get() = schedule.complete

    /** 19 · Cuántas fotos más caben. */
    val photosLeft: Int get() = (PhotoRules.MAX - photos.size).coerceAtLeast(0)

    /** 17B · La persona confirmó que es otro lugar que uno cercano: el moderador la revisa como posible duplicado. */
    val possibleDuplicate: Boolean get() = duplicateCheck?.possibleDuplicate == true

    /**
     * Ya se buscaron parecidos para el pin actual: confirmar otra vez sin moverlo pasa directo al paso 4. Si la búsqueda
     * falló, se intenta de nuevo.
     */
    val duplicatesChecked: Boolean get() = duplicateCheck?.let { it.location == location && !it.failed } == true

    /** Hay algo que valga la pena guardar: sin nada escrito ni elegido, cerrar no pregunta (15A). */
    val hasContent: Boolean
        get() = title.isNotBlank() || description.isNotBlank() || category != null || location != null ||
            !schedule.isEmpty || price != null || photos.isNotEmpty()

    companion object {
        /**
         * «Corregir y reenviar» (24): parte de todo lo que ya se envió, en el paso que hay que corregir. Si quedó como
         * posible duplicado, la búsqueda de parecidos de ese pin viaja igual: la confirmación (20) lo dice y el servidor
         * no la repite. Si se mueve el pin, se busca otra vez.
         */
        fun from(publication: OwnPublication, step: PublishStep): PublicationDraft {
            val changes = PublicationChanges.of(publication)
            return PublicationDraft(
                title = changes.title,
                description = changes.description,
                category = changes.category,
                categoryOrigin = CategoryOrigin.CHOSEN,
                location = changes.location,
                schedule = changes.schedule,
                price = changes.price,
                photos = changes.photos,
                duplicateCheck = if (publication.possibleDuplicate) {
                    DuplicateCheck(publication.location, publication.similarIds, publication.duplicateNote.orEmpty())
                } else {
                    null
                },
                step = step,
            )
        }
    }
}

/**
 * 17 · Resultado de buscar lugares parecidos al confirmar [location] (README · «Duplicados»). La nota y los parecidos
 * viajan con la publicación para que el moderador la compare (33, 33A).
 */
data class DuplicateCheck(
    val location: GeoPoint,
    /** 17B · Los parecidos que la persona dijo que son otro lugar; vacío si no había ninguno. */
    val similarIds: List<String> = emptyList(),
    /** 17B · «¿En qué se diferencia?», opcional. */
    val note: String = "",
    /**
     * Falló o no respondió en 500 ms: se pasó al paso 4 sin aviso y el servidor repite la búsqueda al recibir la
     * publicación (README 17).
     */
    val failed: Boolean = false,
) {
    val possibleDuplicate: Boolean get() = similarIds.isNotEmpty()
}

/** 17A · Un lugar cercano con nombre parecido (DuplicateCandidateCard). */
data class SimilarPlace(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPoint,
    /** Distancia al pin de la persona. */
    val distanceMeters: Int,
    val photoUrl: String? = null,
) {
    /** Tiene página pública (13): una pendiente todavía no. */
    val isPublic: Boolean get() = status == PublicationStatus.VERIFIED || status == PublicationStatus.FINALIZED
}

/** Reglas de la búsqueda de parecidos (README · «Duplicados» y ADR-13). */
object DuplicateRules {
    /** Radio de búsqueda alrededor del pin. */
    const val RADIUS_METERS = 50

    /** 17A muestra de 1 a 3 parecidos, del más cercano al más lejano. */
    const val MAX_RESULTS = 3

    /** 17B · «¿En qué se diferencia?». */
    const val NOTE_MAX = 200
}

/** 18 · Cómo se cuenta el horario en el paso 4 (v1.3.0). */
enum class HoursMode {
    /** Por tramos de días, cada uno con su franja. */
    HOURS,

    /** «Siempre abierto · acceso libre»: sin puertas ni horario, como una plaza, un parque o una cascada. */
    ALWAYS_OPEN,

    /** «No sé el horario»: el lugar se publica sin horario. */
    UNKNOWN,
}

/** 18 · Un tramo del horario mientras se elige: cada parte puede faltar todavía. */
data class DraftHours(
    val days: Set<DayOfWeek> = emptySet(),
    val opens: LocalTime? = null,
    val closes: LocalTime? = null,
) {
    val isEmpty: Boolean get() = days.isEmpty() && opens == null && closes == null

    /** No abre y cierra a la misma hora. Mientras falte una de las dos, no hay error. */
    val timesDiffer: Boolean get() = opens == null || closes == null || closes != opens

    /** v1.3.0 · Cierra antes de abrir: es al día siguiente («22:00 a 3:00»). */
    val pastMidnight: Boolean get() = opens != null && closes != null && closes < opens

    /** El tramo completo y válido; null si falta algo o abre y cierra a la misma hora. */
    val complete: HoursBlock?
        get() {
            val opens = opens ?: return null
            val closes = closes ?: return null
            return if (days.isNotEmpty() && closes != opens) HoursBlock(days, opens, closes) else null
        }
}

/**
 * 18 · El horario mientras se elige: cómo se cuenta ([mode]) y sus tramos. Los tramos se conservan al cambiar de modo,
 * por si la persona vuelve a «Tiene horario».
 */
data class DraftSchedule(val mode: HoursMode = HoursMode.HOURS, val blocks: List<DraftHours> = listOf(DraftHours())) {
    /** Todavía no se eligió nada. */
    val isEmpty: Boolean get() = mode == HoursMode.HOURS && blocks.all { it.isEmpty }

    /** El horario que se publica: null sin saberlo o si a algún tramo le falta algo. */
    val complete: OpeningHours?
        get() = when (mode) {
            HoursMode.UNKNOWN -> null
            HoursMode.ALWAYS_OPEN -> OpeningHours.AlwaysOpen
            HoursMode.HOURS -> OpeningHours.Blocks(blocks.map { it.complete ?: return null })
        }

    /** Se puede publicar: sin saberlo o siempre abierto, sí; por tramos, con todos completos. */
    val isValid: Boolean get() = mode != HoursMode.HOURS || complete != null

    /** Quedan días sin tramo y cabe otro. */
    val canAddBlock: Boolean get() = blocks.size < MAX_BLOCKS && blocks.sumOf { it.days.size } < DayOfWeek.entries.size

    /** Los días que ya tiene otro tramo: en el tramo [index] no se pueden elegir. */
    fun takenByOthers(index: Int): Set<DayOfWeek> = blocks.filterIndexed { i, _ -> i != index }.flatMapTo(mutableSetOf()) { it.days }

    fun withBlock(index: Int, transform: (DraftHours) -> DraftHours): DraftSchedule =
        copy(blocks = blocks.mapIndexed { i, block -> if (i == index) transform(block) else block })

    /** Marca o desmarca [day] en el tramo [index]; un día de otro tramo no cambia. */
    fun toggleDay(index: Int, day: DayOfWeek): DraftSchedule {
        if (day in takenByOthers(index)) return this
        return withBlock(index) { block -> block.copy(days = if (day in block.days) block.days - day else block.days + day) }
    }

    fun addBlock(): DraftSchedule = if (canAddBlock) copy(blocks = blocks + DraftHours()) else this

    /** El último que queda no se quita: se vacía. */
    fun removeBlock(index: Int): DraftSchedule = copy(blocks = blocks.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(DraftHours()) })

    /** Como se guarda y como se compara: con otro modo, sin los tramos a medio elegir. */
    fun trimmed(): DraftSchedule = if (mode == HoursMode.HOURS) this else DraftSchedule(mode)

    companion object {
        /** Un tramo por día, como mucho. */
        const val MAX_BLOCKS = 7

        fun of(hours: OpeningHours?): DraftSchedule = when (hours) {
            null -> DraftSchedule(HoursMode.UNKNOWN)
            OpeningHours.AlwaysOpen -> DraftSchedule(HoursMode.ALWAYS_OPEN)
            is OpeningHours.Blocks -> DraftSchedule(HoursMode.HOURS, hours.blocks.map { DraftHours(it.days, it.opens, it.closes) })
        }
    }
}

/**
 * 19 · Una foto del borrador: el archivo ya comprimido en el teléfono ([path]), el nombre con que se muestra y, cuando
 * termina de subir, su dirección en el servidor. El archivo se conserva si la subida falla.
 */
data class DraftPhoto(
    val id: String,
    val path: String,
    val name: String,
    val remoteUrl: String? = null,
) {
    val uploaded: Boolean get() = remoteUrl != null
}

/** Reglas del paso 5 (README · «fotos 1 a 5, ≤ 8 MB c/u, se comprimen»). */
object PhotoRules {
    const val MIN = 1
    const val MAX = 5
    const val MAX_BYTES = 8L * 1024 * 1024

    /** Lado mayor tras comprimir: se ve bien en el detalle (13) y pesa muy por debajo de los 8 MB. */
    const val MAX_SIDE_PX = 2048
}
