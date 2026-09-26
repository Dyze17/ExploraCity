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
 * «Borrador en DataStore + rememberSaveable»). Los pasos 3 a 5 agregan aquí sus campos.
 */
data class PublicationDraft(
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val categoryOrigin: CategoryOrigin? = null,
    /** Paso en el que quedó, para retomarlo ahí. */
    val step: PublishStep = PublishStep.BASICS,
) {
    val titleMissing: Int get() = PublicationLimits.titleMissing(title)

    val descriptionMissing: Int get() = PublicationLimits.descriptionMissing(description)

    /** Hay algo que valga la pena guardar: sin nada escrito ni elegido, cerrar no pregunta (15A). */
    val hasContent: Boolean get() = title.isNotBlank() || description.isNotBlank() || category != null

    companion object {
        /** «Corregir y reenviar» (24): parte de lo que ya se envió, en el paso que hay que corregir. */
        fun from(publication: OwnPublication, step: PublishStep) = PublicationDraft(
            title = publication.title,
            description = publication.description,
            category = publication.category,
            categoryOrigin = CategoryOrigin.CHOSEN,
            step = step,
        )
    }
}
