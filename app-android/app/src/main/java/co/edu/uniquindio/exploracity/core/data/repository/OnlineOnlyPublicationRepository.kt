package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import co.edu.uniquindio.exploracity.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.update

/**
 * Ver, listar, editar o borrar publicaciones propias necesita red: sin ella se avisa sin intentar. Nada de esto se
 * encola: borrar es destructivo y deliberado, como reportar un perfil (31A), y una edición en cola podría chocar con la
 * revisión del moderador.
 */
class OnlineOnlyPublicationRepository(
    private val remote: PublicationRepository,
    private val connectivity: ConnectivityObserver,
) : PublicationRepository {
    override suspend fun myPublications(): List<OwnPublication> {
        requireOnline()
        return remote.myPublications()
    }

    override suspend fun publication(id: String): OwnPublication? {
        requireOnline()
        return remote.publication(id)
    }

    override suspend fun delete(id: String) {
        requireOnline()
        remote.delete(id)
    }

    override suspend fun update(id: String, changes: PublicationChanges): OwnPublication {
        requireOnline()
        return remote.update(id, changes)
    }

    override suspend fun submit(submission: PublicationSubmission): SubmitResult {
        requireOnline()
        return remote.submit(submission)
    }

    override suspend fun addPhoto(publicationId: String, photoUrl: String) {
        requireOnline()
        remote.addPhoto(publicationId, photoUrl)
    }

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }
}
