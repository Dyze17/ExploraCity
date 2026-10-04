package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.PublicationApi
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import io.ktor.http.HttpStatusCode

/**
 * Las publicaciones propias con la API (SAD: API de Publicaciones y Feed). Sin red avisa OnlineOnlyPublicationRepository,
 * que envuelve a este, y lo que espera la red va en la cola de envío (PublicationOutbox). Los puntos de la primera
 * publicación (D1) y la marca de posible duplicado (ADR-14) los decide la API.
 */
class ApiPublicationRepository(private val api: PublicationApi) : PublicationRepository {

    override suspend fun myPublications(): List<OwnPublication> = api.mine()

    override suspend fun publication(id: String): OwnPublication? = api.one(id)

    /** Si ya no existía, queda como se pedía: borrada. */
    override suspend fun delete(id: String) {
        try {
            api.delete(id)
        } catch (e: ApiException) {
            if (e.status != HttpStatusCode.NotFound.value) throw e
        }
    }

    override suspend fun update(id: String, changes: PublicationChanges): OwnPublication = api.update(id, changes)

    override suspend fun submit(submission: PublicationSubmission): SubmitResult = api.submit(submission)

    override suspend fun addPhoto(publicationId: String, photoUrl: String) = api.addPhoto(publicationId, photoUrl)
}

/**
 * 16 · La sugerencia la hace la API con OpenRouter (SAD: Componente de Clasificación IA): la clave nunca está en la
 * app. Si la IA no respondió, la ApiException 503 deja al formulario con la elección manual (16.c).
 */
class ApiCategorySuggester(private val api: PublicationApi) : CategorySuggester {
    override suspend fun suggest(title: String, description: String): Category? = api.suggestCategory(title, description)
}

/** 17 · La API compara con PostGIS y pg_trgm (ADR-13) los lugares públicos y las pendientes propias. */
class ApiDuplicateFinder(private val api: PublicationApi) : DuplicateFinder {
    override suspend fun similarPlaces(title: String, location: GeoPoint, excludeId: String?): List<SimilarPlace> =
        api.similar(title, location, excludeId)
}
