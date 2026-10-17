package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.repository.CategorySuggester
/** Sin red no se intenta: el formulario sigue con la elección manual (16.c). */
class OnlineOnlyCategorySuggester(
    private val remote: CategorySuggester,
    private val connectivity: ConnectivityObserver,
) : CategorySuggester {
    override suspend fun suggest(title: String, description: String): Category? {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.suggest(title, description)
    }
}
