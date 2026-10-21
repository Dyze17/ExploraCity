package co.edu.uniquindio.exploracity.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.Rejection
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.RequiredFix

// v1.2.0 · Lo que la API escribe en español (las insignias del catálogo, V3, y los motivos de moderación) dicho en el
// idioma de la app, por su id o su código. Lo que la app no conoce se muestra tal como llegó.

private class BadgeTexts(@StringRes val name: Int, @StringRes val howTo: Int, @StringRes val tip: Int? = null)

/** Las insignias del catálogo (V3), por su id. */
private val badgeCatalog = mapOf(
    "primera-publicacion" to BadgeTexts(R.string.badge_first_publication_name, R.string.badge_first_publication_how),
    "amigo-del-verde" to BadgeTexts(R.string.badge_green_friend_name, R.string.badge_green_friend_how),
    "diez-verificadas" to BadgeTexts(R.string.badge_ten_verified_name, R.string.badge_ten_verified_how, R.string.badge_ten_verified_tip),
    "cincuenta-comentarios" to
        BadgeTexts(R.string.badge_fifty_comments_name, R.string.badge_fifty_comments_how, R.string.badge_fifty_comments_tip),
    "guardian-de-la-historia" to
        BadgeTexts(R.string.badge_history_keeper_name, R.string.badge_history_keeper_how, R.string.badge_history_keeper_tip),
    "voz-de-la-comunidad" to
        BadgeTexts(R.string.badge_community_voice_name, R.string.badge_community_voice_how, R.string.badge_community_voice_tip),
    "caminante" to BadgeTexts(R.string.badge_walker_name, R.string.badge_walker_how, R.string.badge_walker_tip),
    "buen-provecho" to BadgeTexts(R.string.badge_bon_appetit_name, R.string.badge_bon_appetit_how, R.string.badge_bon_appetit_tip),
    "cultura-viva" to BadgeTexts(R.string.badge_living_culture_name, R.string.badge_living_culture_how, R.string.badge_living_culture_tip),
)

/** El nombre de la insignia [id]; [fallback], el del servidor, si la app no la conoce (o el aviso es de antes). */
@Composable
fun badgeName(id: String?, fallback: String): String = badgeCatalog[id]?.let { stringResource(it.name) } ?: fallback

val Badge.localName: String
    @Composable get() = badgeName(id, name)

/** «Cómo se obtiene» (27A). */
val Badge.localHowTo: String
    @Composable get() = badgeCatalog[id]?.let { stringResource(it.howTo) } ?: howTo

val Badge.localTip: String?
    @Composable get() = badgeCatalog[id]?.tip?.let { stringResource(it) } ?: tip

/** 25 · «Mirador de La Peña fue rechazada: [la foto no permite reconocer el lugar]». Con OTHER, lo que escribió el moderador. */
@Composable
fun rejectionSentence(reason: RejectionReason?, fallback: String): String = when (reason) {
    RejectionReason.DUPLICATE -> stringResource(R.string.notice_reason_duplicate)
    RejectionReason.PHOTO -> stringResource(R.string.notice_reason_photo)
    RejectionReason.LOCATION -> stringResource(R.string.notice_reason_location)
    RejectionReason.INAPPROPRIATE -> stringResource(R.string.notice_reason_inappropriate)
    RejectionReason.OTHER, null -> fallback
}

/** 25 · «… pasó a finalizada: [el lugar cerró de forma permanente]»; null si el aviso no trae motivo. */
@Composable
fun finalizeSentence(reason: FinalizeReason?, fallback: String?): String? = when (reason) {
    FinalizeReason.CLOSED -> stringResource(R.string.notice_finalized_closed)
    FinalizeReason.EVENT_ENDED -> stringResource(R.string.notice_finalized_event)
    FinalizeReason.MERGED -> stringResource(R.string.notice_finalized_merged)
    null -> fallback
}

/** 24 · El mensaje del rechazo: el que escribió el moderador o, si no escribió nada, el de su motivo. */
@Composable
fun rejectionMessage(rejection: Rejection): String = if (rejection.customMessage) {
    rejection.message
} else {
    when (rejection.reason) {
        RejectionReason.DUPLICATE -> stringResource(R.string.rejected_default_duplicate)
        RejectionReason.PHOTO -> stringResource(R.string.rejected_default_photo)
        RejectionReason.LOCATION -> stringResource(R.string.rejected_default_location)
        RejectionReason.INAPPROPRIATE -> stringResource(R.string.rejected_default_inappropriate)
        RejectionReason.OTHER -> rejection.message
    }
}

/** 24.a · «Qué revisar» por su tipo (E1: la API solo manda foto y ubicación). */
@Composable
fun fixText(fix: RequiredFix): String = when (fix.kind) {
    FixKind.PHOTOS -> stringResource(R.string.rejected_fix_photos)
    FixKind.LOCATION -> stringResource(R.string.rejected_fix_location)
    else -> fix.text
}

/** Quien decidió; si su cuenta ya no existe, la API manda [FORMER_MODERATOR] (docs/api). */
@Composable
fun reviewerName(name: String): String = if (name == FORMER_MODERATOR) stringResource(R.string.rejected_reviewer_deleted) else name

private const val FORMER_MODERATOR = "Moderación"
