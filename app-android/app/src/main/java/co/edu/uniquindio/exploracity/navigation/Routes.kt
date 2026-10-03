package co.edu.uniquindio.exploracity.navigation

import androidx.annotation.Keep
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.serialization.Serializable

// Rutas tipadas de la app. El número de cada una es el de la pantalla en el README del diseño.
// Hojas inferiores y diálogos (9, 14.b, 15A, 17A/17B, 23, 27A, 29A, 31A, 34) no son rutas: viven en su pantalla.
// Los enums de las rutas llevan @Keep: su serializador no puede ofuscarse en la compilación minificada.

// ── Acceso y cuenta ──
@Serializable data object AuthGraph

@Serializable data object Splash // 1

@Serializable data object Onboarding // 2

@Serializable data object Login // 3

@Serializable data object Register // 4

@Serializable data class LegalDocuments(val tab: LegalTab = LegalTab.POLICY) // 4A

@Keep @Serializable enum class LegalTab { POLICY, PRIVACY_NOTICE }

/** 5. Abre con [email] escrito: el del inicio de sesión (3) o el del enlace vencido (6C). */
@Serializable data class RecoverPassword(val email: String = "") // 5

/** 6.a. [sentAtMillis] es la hora del envío: de ahí sale la cuenta regresiva del reenvío. */
@Serializable data class RecoveryEmailSent(val email: String, val sentAtMillis: Long) // 6

/** 6.b. [token] es el del enlace del correo. */
@Serializable data class NewPassword(val token: String) // 6.b

/** 6C. [email] es la cuenta del enlace vencido, para pedir otro con el correo escrito (vacío si no se sabe). */
@Serializable data class ExpiredLink(val email: String = "") // 6C

/** Aviso con que el feed (7) recibe a quien acaba de crear su cuenta (4). */
enum class WelcomeNotice { ACCOUNT_READY, WELCOME_EMAIL_FAILED }

// ── App principal: una sección por pestaña ──
@Serializable data object MainGraph

@Serializable data object ExploreGraph

@Serializable data object Feed // 7 (10, 11 y 12 son estados del feed)

/** 8. Con [focusPoiId] abre centrado en ese lugar y con él seleccionado (mapa pequeño del detalle, 13). */
@Serializable data class FeedMap(val focusPoiId: String? = null) // 8

/** 13. Con [focusComment] el foco inicial va al botón de comentar (desde «Ir al lugar existente», 24). */
@Serializable data class PoiDetail(val poiId: String, val focusComment: Boolean = false) // 13

/** 14. Con [write] abre con el teclado listo («Agregar comentario» del detalle). */
@Serializable data class Comments(val poiId: String, val write: Boolean = false) // 14

@Serializable data class PublicProfile(val userId: String) // 31

@Serializable data object PublishGraph

/**
 * 15–19 y 21: los 5 pasos comparten destino y borrador. Con [resubmitId] corrige esa publicación rechazada y abre
 * en [step], el paso relevante (24 · «Corregir y reenviar»).
 */
@Serializable data class PublishForm(val resubmitId: String? = null, val step: Int = 1)

/** 20 · Lo necesario para la confirmación; [firstPublicationPoints] 0 = sin insignia. */
@Serializable data class PublishSent(
    val title: String,
    val possibleDuplicate: Boolean = false,
    val queued: Boolean = false,
    val firstPublicationPoints: Int = 0,
) // 20

@Serializable data object NotificationsGraph

@Serializable data object Notifications // 25

@Serializable data object ProfileGraph

@Serializable data object Profile // 26

@Serializable data object Badges // 27

@Serializable data object EditProfile // 28

/** 22. Con [filter] abre filtrada por ese estado (las cifras tocables del perfil, 26). */
@Serializable data class MyPublications(val filter: PublicationFilter = PublicationFilter.ALL) // 22

/** Filtros por estado de 22 («Todas · 7», «Pendientes · 2»…). */
@Keep @Serializable enum class PublicationFilter { ALL, PENDING, VERIFIED, REJECTED, FINALIZED }

fun PublicationStatus?.toFilter(): PublicationFilter = when (this) {
    null -> PublicationFilter.ALL
    PublicationStatus.PENDING -> PublicationFilter.PENDING
    PublicationStatus.VERIFIED -> PublicationFilter.VERIFIED
    PublicationStatus.REJECTED -> PublicationFilter.REJECTED
    PublicationStatus.FINALIZED -> PublicationFilter.FINALIZED
}

fun PublicationFilter.toStatus(): PublicationStatus? = when (this) {
    PublicationFilter.ALL -> null
    PublicationFilter.PENDING -> PublicationStatus.PENDING
    PublicationFilter.VERIFIED -> PublicationStatus.VERIFIED
    PublicationFilter.REJECTED -> PublicationStatus.REJECTED
    PublicationFilter.FINALIZED -> PublicationStatus.FINALIZED
}

@Serializable data class EditPublication(val publicationId: String) // 23

@Serializable data class RejectedPublication(val publicationId: String) // 24

@Serializable data object Settings // 29

/**
 * Sin número en el diseño: «Cambiar correo» de Ajustes › Cuenta. Abre con [newEmail] escrito al pedir otro enlace desde
 * el vencido.
 */
@Serializable data class ChangeEmail(val newEmail: String = "")

/** «Confirma tu correo nuevo»: el enlace salió hacia [email]; se sigue entrando con [currentEmail]. */
@Serializable data class EmailChangeSent(val email: String, val currentEmail: String, val sentAtMillis: Long)

/** El enlace que llegó al correo nuevo ([token]): confirma el cambio. */
@Serializable data class ConfirmEmail(val token: String)

/** El enlace del correo nuevo ya no sirve; [email] es ese correo, para pedir otro con él escrito. */
@Serializable data class EmailLinkExpired(val email: String = "")

/** Aviso que el inicio de sesión (3) muestra una vez al llegar desde la app. */
@Keep @Serializable enum class SessionNotice { SIGNED_OUT, ACCOUNT_DELETED, PASSWORD_CHANGED }

@Serializable data object DeleteAccount // 30

@Serializable data object ModerationGraph

@Serializable data object ModerationQueue // 32 (37 es su estado vacío)

@Serializable data class ReviewDetail(val publicationId: String) // 33

@Serializable data class CompareDuplicates(val publicationId: String) // 33A

/** 35 · Con [duplicate], «Duplicado de un lugar existente» llega elegido; [originalId] es el original, si ya se sabe (33A). */
@Serializable data class RejectPublication(val publicationId: String, val duplicate: Boolean = false, val originalId: String? = null) // 35

@Serializable data class FinalizePublication(val publicationId: String) // 36

/** Sin lienzo (E1): las publicaciones ya decididas; una verificada o finalizada abre 36. */
@Serializable data object ResolvedPublications

/** Solo desarrollo: muestrario del sistema de diseño, accesible desde Ajustes. */
@Serializable data object DesignSystemCatalog
