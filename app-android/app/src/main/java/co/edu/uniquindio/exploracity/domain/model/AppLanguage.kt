package co.edu.uniquindio.exploracity.domain.model

/** v1.2.0 · Idioma elegido en Ajustes (29). [tag] es su etiqueta BCP 47; SYSTEM sigue el idioma del teléfono. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en"),
    ;

    companion object {
        /** El de una etiqueta como «es-CO»; uno que la app no tiene sigue al teléfono. */
        fun of(tag: String?): AppLanguage = entries.firstOrNull { it.tag != null && tag?.substringBefore('-') == it.tag } ?: SYSTEM
    }
}
