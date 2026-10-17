package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import kotlinx.coroutines.flow.update
/** Las publicaciones de la persona de la sesión (SAD: API de Publicaciones y Feed). */
interface PublicationRepository {
    /** 22 · Todas, de la más reciente a la más antigua. Lanza excepción si falla la red. */
    suspend fun myPublications(): List<OwnPublication>

    /** 24 · Una publicación propia; null si ya no existe. Lanza excepción si falla la red. */
    suspend fun publication(id: String): OwnPublication?

    /** 22–24 · La borra con sus fotos, comentarios y votos, sin vuelta atrás. Lanza excepción si falla la red. */
    suspend fun delete(id: String)

    /**
     * 23 · Guarda título, categoría y descripción. La publicación vuelve a verificación: una verificada deja de verse en
     * el feed hasta que un moderador la apruebe. Devuelve cómo quedó. Lanza excepción si falla la red.
     */
    suspend fun update(id: String, changes: PublicationChanges): OwnPublication

    /**
     * 19 → 20 · Envía la publicación a verificación, o reenvía una rechazada ([PublicationSubmission.resubmitId]). Van
     * las fotos ya subidas (al menos una); las demás se agregan después con [addPhoto]. Lanza excepción si falla la red.
     */
    suspend fun submit(submission: PublicationSubmission): SubmitResult

    /** 19 · Una foto que terminó de subir después del envío (se reintentan en segundo plano). */
    suspend fun addPhoto(publicationId: String, photoUrl: String)
}
