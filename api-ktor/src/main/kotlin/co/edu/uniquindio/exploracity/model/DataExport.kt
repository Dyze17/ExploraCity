package co.edu.uniquindio.exploracity.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 29 · «Descargar mis datos» (Ley 1581, derecho de acceso). El archivo es para que la persona lo lea: las claves y los
// valores van en español, igual que el que armaba FakeAccountRepository en la app.

@Serializable
data class ExportFile(
    @SerialName("generado") val generatedAt: String,
    @SerialName("cuenta") val account: ExportAccount,
    @SerialName("reputacion") val reputation: ExportReputation,
    @SerialName("publicaciones") val publications: List<ExportPublication>,
    @SerialName("comentarios") val comments: List<ExportComment>,
    @SerialName("votos") val votes: List<String>,
    @SerialName("visitados") val visits: List<ExportVisit>,
)

@Serializable
data class ExportAccount(
    @SerialName("correo") val email: String,
    @SerialName("nombre") val name: String,
    @SerialName("comoSePresenta") val residency: String,
    @SerialName("ciudad") val city: String,
    @SerialName("miembroDesde") val memberSince: String,
    @SerialName("sobreMi") val bio: String? = null,
    @SerialName("foto") val photo: String? = null,
)

@Serializable
data class ExportReputation(
    @SerialName("puntos") val points: Int,
    @SerialName("nivel") val level: String,
    @SerialName("insignias") val badges: List<ExportBadge>,
)

@Serializable
data class ExportBadge(
    @SerialName("nombre") val name: String,
    @SerialName("avance") val progress: Int,
    @SerialName("meta") val target: Int,
    @SerialName("desbloqueada") val unlocked: Boolean,
)

@Serializable
data class ExportPublication(
    val id: String,
    @SerialName("titulo") val title: String,
    @SerialName("categoria") val category: String,
    @SerialName("estado") val status: String,
    @SerialName("descripcion") val description: String,
    @SerialName("latitud") val latitude: Double,
    @SerialName("longitud") val longitude: Double,
    @SerialName("horario") val hours: ExportHours?,
    @SerialName("precio") val price: String?,
    @SerialName("fotos") val photos: List<String>,
    @SerialName("enviada") val submittedAt: String,
    @SerialName("votos") val votes: Int,
    @SerialName("comentarios") val comments: Int,
    @SerialName("posibleDuplicado") val possibleDuplicate: Boolean,
)

@Serializable
data class ExportHours(
    @SerialName("dias") val days: List<String>,
    @SerialName("abre") val opens: String,
    @SerialName("cierra") val closes: String,
)

@Serializable
data class ExportComment(
    @SerialName("lugar") val place: String,
    @SerialName("texto") val text: String,
    @SerialName("fecha") val createdAt: String,
)

@Serializable
data class ExportVisit(
    @SerialName("lugar") val place: String,
    @SerialName("loRecomienda") val recommends: Boolean?,
    @SerialName("experiencia") val experience: String?,
    @SerialName("conMiNombre") val showName: Boolean,
)

/** Etiquetas en español del archivo: las mismas de la app. */
object ExportLabels {
    fun of(category: Category): String = when (category) {
        Category.GASTRONOMY -> "Gastronomía"
        Category.CULTURE -> "Cultura"
        Category.NATURE -> "Naturaleza"
        Category.ENTERTAINMENT -> "Entretenimiento"
        Category.HISTORY -> "Historia"
    }

    fun of(status: PublicationStatus): String = when (status) {
        PublicationStatus.PENDING -> "Pendiente de verificación"
        PublicationStatus.VERIFIED -> "Verificada"
        PublicationStatus.REJECTED -> "Rechazada"
        PublicationStatus.FINALIZED -> "Resuelta / finalizada"
    }

    fun of(price: PriceRange): String = when (price) {
        PriceRange.FREE -> "Entrada libre"
        PriceRange.LOW -> "Hasta 25.000"
        PriceRange.MEDIUM -> "Entre 25.000 y 60.000"
        PriceRange.HIGH -> "Más de 60.000"
    }

    fun of(residency: Residency): String = if (residency == Residency.RESIDENT) "Residente" else "De visita"

    fun of(level: UserLevel): String = when (level) {
        UserLevel.TOURIST -> "Turista"
        UserLevel.EXPLORER -> "Explorador"
        UserLevel.ADVENTURER -> "Aventurero"
        UserLevel.LOCAL_AMBASSADOR -> "Embajador Local"
    }
}
