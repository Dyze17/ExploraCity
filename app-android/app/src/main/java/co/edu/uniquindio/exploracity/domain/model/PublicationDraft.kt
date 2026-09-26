package co.edu.uniquindio.exploracity.domain.model

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
 * «Borrador en DataStore + rememberSaveable»). El paso 4 y el 5 agregan aquí sus campos.
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
    /** Paso en el que quedó, para retomarlo ahí. */
    val step: PublishStep = PublishStep.BASICS,
) {
    val titleMissing: Int get() = PublicationLimits.titleMissing(title)

    val descriptionMissing: Int get() = PublicationLimits.descriptionMissing(description)

    /** 17B · La persona confirmó que es otro lugar que uno cercano: el moderador la revisa como posible duplicado. */
    val possibleDuplicate: Boolean get() = duplicateCheck?.possibleDuplicate == true

    /**
     * Ya se buscaron parecidos para el pin actual: confirmar otra vez sin moverlo pasa directo al paso 4. Si la búsqueda
     * falló, se intenta de nuevo.
     */
    val duplicatesChecked: Boolean get() = duplicateCheck?.let { it.location == location && !it.failed } == true

    /** Hay algo que valga la pena guardar: sin nada escrito ni elegido, cerrar no pregunta (15A). */
    val hasContent: Boolean get() = title.isNotBlank() || description.isNotBlank() || category != null || location != null

    companion object {
        /** «Corregir y reenviar» (24): parte de lo que ya se envió, en el paso que hay que corregir. */
        fun from(publication: OwnPublication, step: PublishStep) = PublicationDraft(
            title = publication.title,
            description = publication.description,
            category = publication.category,
            categoryOrigin = CategoryOrigin.CHOSEN,
            location = publication.location,
            step = step,
        )
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
