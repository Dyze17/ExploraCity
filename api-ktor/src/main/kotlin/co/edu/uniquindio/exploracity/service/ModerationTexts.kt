package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.FinalizeReason
import co.edu.uniquindio.exploracity.model.FixKind
import co.edu.uniquindio.exploracity.model.FixResponse
import co.edu.uniquindio.exploracity.model.RejectionReason

/** Lo que el servidor escribe en los avisos (25) y en la publicación rechazada (24), en español, como hacía la app. */
object ModerationTexts {
    /** El motivo dentro de una frase: «Mirador de La Peña fue rechazada: la foto no permite reconocer el lugar». */
    fun sentence(reason: RejectionReason): String = when (reason) {
        RejectionReason.DUPLICATE -> "el lugar ya está publicado"
        RejectionReason.PHOTO -> "la foto no permite reconocer el lugar"
        RejectionReason.LOCATION -> "la ubicación no corresponde"
        RejectionReason.INAPPROPRIATE -> "contenido inapropiado o publicidad"
        RejectionReason.OTHER -> "otro motivo"
    }

    /** 35 · Sin mensaje del moderador, el del motivo: «La foto no permite reconocer el lugar.». */
    fun defaultMessage(reason: RejectionReason): String = sentence(reason).replaceFirstChar { it.uppercase() } + "."

    /** El motivo del aviso de rechazo: con «Otro motivo», lo que escribió el moderador. */
    fun noticeReason(reason: RejectionReason, message: String): String =
        if (reason == RejectionReason.OTHER) message.trim().trimEnd('.').replaceFirstChar { it.lowercase() } else sentence(reason)

    /** E1 · «Qué revisar antes de reenviar» (24.a) sale del motivo. Los demás motivos llevan solo el mensaje. */
    fun fixes(reason: RejectionReason): List<FixResponse> = when (reason) {
        RejectionReason.PHOTO -> listOf(FixResponse(FixKind.PHOTOS, "Una foto donde se reconozca el lugar"))
        RejectionReason.LOCATION -> listOf(FixResponse(FixKind.LOCATION, "El pin sobre la entrada del lugar"))
        else -> emptyList()
    }

    /** «Casa de la Independencia pasó a finalizada: el lugar cerró de forma permanente». */
    fun sentence(reason: FinalizeReason): String = when (reason) {
        FinalizeReason.CLOSED -> "el lugar cerró de forma permanente"
        FinalizeReason.EVENT_ENDED -> "fue un evento temporal que ya pasó"
        FinalizeReason.MERGED -> "se unificó con otra publicación"
    }

    /** Quien decidió, si su cuenta ya no existe. */
    const val FORMER_MODERATOR = "Moderación"
}
