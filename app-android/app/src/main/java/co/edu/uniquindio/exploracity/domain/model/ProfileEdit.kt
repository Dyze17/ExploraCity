package co.edu.uniquindio.exploracity.domain.model

/** 28 · Reglas del README: nombre de 2 a 40 caracteres y «Sobre mí» hasta 150. */
object ProfileLimits {
    const val NAME_MIN = 2
    const val NAME_MAX = 40
    const val BIO_MAX = 150
}

/** 28 · Lo que se puede cambiar del perfil. [photo] es la foto que se ve: la de antes, una nueva del teléfono o ninguna. */
data class ProfileForm(
    val name: String,
    val bio: String,
    val residency: Residency,
    val photo: String? = null,
) {
    private val nameLength: Int get() = name.trim().length

    /** Faltan caracteres para el mínimo; 0 si alcanza. */
    val nameMissing: Int get() = (ProfileLimits.NAME_MIN - nameLength).coerceAtLeast(0)

    /** Caracteres de más; 0 si cabe. */
    val nameExcess: Int get() = (nameLength - ProfileLimits.NAME_MAX).coerceAtLeast(0)

    /** «Te pasaste por 12 caracteres» (28.a): cuenta lo escrito, como el contador. */
    val bioExcess: Int get() = (bio.length - ProfileLimits.BIO_MAX).coerceAtLeast(0)

    val isValid: Boolean get() = nameMissing == 0 && nameExcess == 0 && bioExcess == 0

    /** Sin espacios de sobra, como se guarda. */
    fun trimmed() = copy(name = name.trim(), bio = bio.trim())

    companion object {
        fun of(profile: OwnProfile) = ProfileForm(profile.author.name, profile.bio.orEmpty(), profile.residency, profile.photo)
    }
}

/** Qué pasa con la foto al guardar: se queda, se quita o se reemplaza por un archivo del teléfono que hay que subir. */
sealed interface PhotoChange {
    data object Keep : PhotoChange

    data object Remove : PhotoChange

    data class Replace(val path: String) : PhotoChange
}

/** 28 · Lo que se envía al servidor; «Sobre mí» vacío se guarda como null. */
data class ProfileUpdate(val name: String, val bio: String?, val residency: Residency, val photo: PhotoChange)
