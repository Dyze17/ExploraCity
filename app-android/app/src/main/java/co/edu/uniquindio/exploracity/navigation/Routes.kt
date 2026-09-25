package co.edu.uniquindio.exploracity.navigation

import androidx.annotation.Keep
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

@Serializable data object RecoverPassword // 5

@Serializable data object RecoveryEmailSent // 6

@Serializable data object NewPassword // 6.b

@Serializable data object ExpiredLink // 6C

// ── App principal: una sección por pestaña ──
@Serializable data object MainGraph

@Serializable data object ExploreGraph

@Serializable data object Feed // 7 (10, 11 y 12 son estados del feed)

/** 8. Con [focusPoiId] abre centrado en ese lugar y con él seleccionado (mapa pequeño del detalle, 13). */
@Serializable data class FeedMap(val focusPoiId: String? = null) // 8

@Serializable data class PoiDetail(val poiId: String) // 13

/** 14. Con [write] abre con el teclado listo («Agregar comentario» del detalle). */
@Serializable data class Comments(val poiId: String, val write: Boolean = false) // 14

@Serializable data class PublicProfile(val userId: String) // 31

@Serializable data object PublishGraph

@Serializable data object PublishForm // 15–19 y 21: los 5 pasos comparten destino y borrador

@Serializable data object PublishSent // 20

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

@Serializable data class EditPublication(val publicationId: String) // 23

@Serializable data class RejectedPublication(val publicationId: String) // 24

@Serializable data object Settings // 29

@Serializable data object DeleteAccount // 30

@Serializable data object ModerationGraph

@Serializable data object ModerationQueue // 32 (37 es su estado vacío)

@Serializable data class ReviewDetail(val publicationId: String) // 33

@Serializable data class CompareDuplicates(val publicationId: String) // 33A

@Serializable data class RejectPublication(val publicationId: String) // 35

@Serializable data class FinalizePublication(val publicationId: String) // 36

/** Solo desarrollo: muestrario del sistema de diseño, accesible desde Ajustes. */
@Serializable data object DesignSystemCatalog
