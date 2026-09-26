package co.edu.uniquindio.exploracity.domain.model

/**
 * 20 · Lo que se envía a verificación: el borrador completo. Con [resubmitId] reenvía una rechazada (24), que vuelve a
 * pendiente. Las fotos sin [DraftPhoto.remoteUrl] aún están solo en el teléfono.
 */
data class PublicationSubmission(
    val title: String,
    val description: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val location: GeoPoint,
    val hours: OpeningHours?,
    val price: PriceRange?,
    val photos: List<DraftPhoto>,
    val duplicateCheck: DuplicateCheck?,
    val resubmitId: String? = null,
) {
    val possibleDuplicate: Boolean get() = duplicateCheck?.possibleDuplicate == true

    companion object {
        /** null si al borrador le falta algo de los pasos 1 a 5. */
        fun from(draft: PublicationDraft, resubmitId: String?): PublicationSubmission? {
            val category = draft.category ?: return null
            val location = draft.location ?: return null
            if (draft.titleMissing > 0 || draft.descriptionMissing > 0 || draft.photos.size < PhotoRules.MIN) return null
            if (!draft.hoursUnknown && draft.hours.complete == null) return null
            return PublicationSubmission(
                title = draft.title.trim(),
                description = draft.description.trim(),
                category = category,
                categoryOrigin = draft.categoryOrigin ?: CategoryOrigin.CHOSEN,
                location = location,
                hours = draft.openingHours,
                price = draft.price,
                photos = draft.photos,
                // Solo la de este pin: si se movió después, el servidor la repite.
                duplicateCheck = draft.duplicateCheck?.takeIf { it.location == location },
                resubmitId = resubmitId,
            )
        }
    }
}

/** Respuesta del servidor al enviar: la publicación creada y, si fue la primera, los puntos de la insignia (20.a). */
data class SubmitResult(val publicationId: String, val firstPublicationPoints: Int? = null)

/** Lo que muestra la confirmación (20) según cómo salió el envío. */
data class SentSummary(
    val title: String,
    val possibleDuplicate: Boolean,
    /** Sin red: quedó en la cola y se envía al volver el internet («Pendiente de envío»). */
    val queued: Boolean,
    /** «Ganaste la insignia «Primera publicación» · +20 puntos»; null si no aplica. */
    val firstPublicationPoints: Int? = null,
)
