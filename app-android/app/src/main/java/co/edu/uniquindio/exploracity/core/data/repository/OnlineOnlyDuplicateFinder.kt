package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.domain.repository.DuplicateFinder
/** Sin red no se busca: el formulario sigue al paso 4 y el servidor repite la búsqueda al recibir la publicación. */
class OnlineOnlyDuplicateFinder(
    private val remote: DuplicateFinder,
    private val connectivity: ConnectivityObserver,
) : DuplicateFinder {
    override suspend fun similarPlaces(title: String, location: GeoPoint, excludeId: String?): List<SimilarPlace> {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.similarPlaces(title, location, excludeId)
    }
}
