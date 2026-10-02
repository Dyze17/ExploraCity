package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.location.AddressResolver
import co.edu.uniquindio.exploracity.data.location.ApproximateAddress
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.photos.UploadProgress
import co.edu.uniquindio.exploracity.data.repository.DuplicateFinder
import co.edu.uniquindio.exploracity.data.sync.PublicationOutbox
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Servidores de prueba del formulario (15–19) y de la edición (23): parecidos, direcciones, fotos y cola de envío.

/** Búsqueda de parecidos con respuesta, demora y fallo a mano; cuenta las búsquedas. */
internal class FakeFinder : DuplicateFinder {
    var result: List<SimilarPlace> = emptyList()
    var latency: Duration = 100.milliseconds
    var error: Exception? = null
    var calls = 0
    var excluded: String? = null

    override suspend fun similarPlaces(title: String, location: GeoPoint, excludeId: String?): List<SimilarPlace> {
        calls++
        excluded = excludeId
        delay(latency)
        error?.let { throw it }
        return result
    }
}

/** Direcciones de prueba: todo punto está en Chapinero; la búsqueda encuentra lo que haya en [places]. */
internal class FakeAddresses : AddressResolver {
    val address = ApproximateAddress("Cl. 45 #19-32", "Chapinero", "Bogotá")
    val places = mutableMapOf<String, GeoPoint>()
    var offline = false

    override suspend fun addressOf(point: GeoPoint): ApproximateAddress {
        delay(200.milliseconds)
        if (offline) throw OfflineException()
        return address
    }

    override suspend fun search(query: String, bounds: GeoBounds): GeoPoint? {
        delay(200.milliseconds)
        return places[query]
    }
}

/** Fotos de prueba: cualquier dirección se «comprime» al instante; guarda lo que se borra. */
internal class FakePhotos : PhotoStore {
    val deleted = mutableListOf<String>()
    var unreadable = setOf<String>()
    private var next = 0

    override suspend fun import(uri: String, fallbackName: String?): DraftPhoto? {
        if (uri in unreadable) return null
        next++
        return DraftPhoto("foto-$next", "/fotos/foto-$next.jpg", uri.substringAfterLast('/'))
    }

    override fun newCameraShot(): String = "content://camara/foto-${next + 1}.jpg"

    override suspend fun delete(photo: DraftPhoto) {
        deleted += photo.id
    }

    var deletedAll = false

    override suspend fun deleteAll() {
        deletedAll = true
    }
}

/** Subida de prueba: 1 s por foto (o lo de [slow]) de 25 en 25 %; sin red, o si está en [failing], falla. */
internal class FakeUploads(private val connectivity: FakeConnectivity) : PhotoUploader {
    val slow = mutableMapOf<String, Duration>()
    val failing = mutableSetOf<String>()

    override fun upload(photo: DraftPhoto): Flow<UploadProgress> = flow {
        val total = slow[photo.id] ?: 1.seconds
        for (percent in 0..100 step 25) {
            if (!connectivity.online || photo.id in failing) throw OfflineException()
            emit(UploadProgress.Sending(percent))
            if (percent < 100) delay(total / 4)
        }
        emit(UploadProgress.Done("fake://${photo.id}"))
    }
}

internal class MemoryOutbox : PublicationOutbox {
    val publications = mutableListOf<PublicationSubmission>()
    val photos = mutableMapOf<String, List<DraftPhoto>>()

    override suspend fun enqueue(submission: PublicationSubmission) {
        publications += submission
    }

    override suspend fun enqueuePhotos(publicationId: String, photos: List<DraftPhoto>) {
        if (photos.isNotEmpty()) this.photos[publicationId] = photos
    }
}
