package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ReviewsDao
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/** Resumen de la cola para el acceso rápido del moderador en el feed (7.c). */
data class ModerationSummary(val pending: Int, val oldestWaitingDays: Int)

/** Moderación (SAD: componente de Moderación). Solo la usa quien entró con rol de moderador. */
interface ModerationRepository {
    /** Pendientes por revisar: el badge de la pestaña, que desaparece en cero (37). */
    val pendingCount: StateFlow<Int>

    /** 7.c · Cuántas esperan y desde hace cuántos días la más antigua. Lanza excepción si falla la red. */
    suspend fun summary(): ModerationSummary

    /** 32 · La cola, de la más antigua a la más reciente. Lanza excepción si falla la red y no hay nada guardado. */
    suspend fun queue(): ReviewQueue

    /** 33 · Una pendiente; null si ya no está en la cola. Lanza excepción si falla la red y no está guardada. */
    suspend fun item(id: String): ReviewItem?

    /** 33 · Los ids de la cola en orden, de la última carga: «1 de 7» y la siguiente al decidir. */
    suspend fun queueIds(): List<String>

    /**
     * 34 · Verifica la pendiente: entra al feed y al mapa, y el autor recibe el aviso y sus puntos. [note] es la nota
     * interna, que solo ven los moderadores. Lanza [AlreadyReviewedException] si otra persona ya la decidió.
     */
    suspend fun verify(id: String, note: String?)

    /** 37 · Lo decidido hoy por este moderador. Lanza excepción si falla la red. */
    suspend fun todayWork(): ModerationWork
}

/**
 * Temporal hasta que exista la API: las pendientes de otras personas (sampleReviewSeeds) y las de Ana, que salen de
 * [publications]. Verificar una de Ana la pasa al feed como suya y le llega el aviso (25); la de otra persona entra al
 * feed con su autor. La nota interna se guarda y no se muestra: no hay diseño para eso.
 */
class FakeModerationRepository internal constructor(
    private val pois: FakePoiRepository,
    private val publications: FakePublicationRepository,
    private val notifications: FakeNotificationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val latency: Duration = 1400.milliseconds,
    private val itemLatency: Duration = 500.milliseconds,
    private val actionLatency: Duration = 800.milliseconds,
    private val currentUser: Author = sampleCurrentUser,
    seeds: List<ReviewSeed> = sampleReviewSeeds,
) : ModerationRepository {

    // Las pendientes de otras personas cuentan su espera desde que se abrió la app, como las de Ana.
    private val openedAt = clock.instant()
    private val others = seeds.toMutableList()
    private val notes = mutableMapOf<String, String>()
    private var work = ModerationWork(verified = 0, rejected = 0, finalized = 0)

    private val pending = MutableStateFlow(0)
    override val pendingCount: StateFlow<Int> = pending.asStateFlow()

    override suspend fun summary(): ModerationSummary {
        delay(300.milliseconds)
        val items = items()
        val now = clock.instant()
        return ModerationSummary(items.size, items.maxOfOrNull { it.waitingDays(now).toInt() } ?: 0)
    }

    override suspend fun queue(): ReviewQueue {
        delay(latency)
        return ReviewQueue(items())
    }

    override suspend fun item(id: String): ReviewItem? {
        delay(itemLatency)
        return items().firstOrNull { it.id == id }
    }

    override suspend fun queueIds(): List<String> = items().map { it.id }

    override suspend fun verify(id: String, note: String?) {
        delay(actionLatency)
        val ana = publications.markVerified(id)
        if (ana != null) {
            notifications.deliver(Notification.Verified("verificada-$id", clock.instant(), read = false, id, ana.title, VERIFIED_POINTS))
        } else {
            val seed = others.firstOrNull { it.id == id } ?: throw AlreadyReviewedException()
            others.remove(seed)
            pois.publish(seed.toPublicDetails(authorOf(seed.authorId)))
        }
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { notes[id] = it }
        work = work.copy(verified = work.verified + 1)
        items()
    }

    override suspend fun todayWork(): ModerationWork {
        delay(itemLatency)
        return work
    }

    /** La cola al día; de paso, el badge. */
    private fun items(): List<ReviewItem> {
        val ana = ReviewAuthor(
            currentUser,
            verified = publications.publicCount(),
            rejected = publications.rejectedCount(),
        )
        val all = others.map { it.toItem() } + publications.pendingOnes().map { it.toItem(ana) }
        return all.sortedBy { it.submittedAt }.also { pending.value = it.size }
    }

    private fun authorOf(id: String): Author = sampleAuthors.first { it.id == id }

    private fun candidates(ids: List<String>, from: GeoPoint): List<DuplicateCandidate> {
        val places = pois.places().associateBy { it.id }
        return ids.mapNotNull { places[it] }.map { DuplicateCandidate(it, from.distanceTo(it.location)) }.sortedBy { it.distanceMeters }
    }

    private fun ReviewSeed.toItem(): ReviewItem {
        val (verified, rejected) = sampleReviewHistory[authorId] ?: (0 to 0)
        return ReviewItem(
            id = id,
            title = title,
            category = category,
            categoryOrigin = categoryOrigin,
            description = description,
            photos = samplePublishedPhotos(id, photoCount),
            hours = hours,
            price = price,
            address = address,
            location = location,
            submittedAt = openedAt - submittedAgo.toJavaDuration(),
            author = ReviewAuthor(authorOf(authorId), verified, rejected),
            reportReason = reportReason,
            duplicate = similarIds.takeIf { it.isNotEmpty() }?.let { DuplicateSuspicion(candidates(it, location), note) },
        )
    }

    private fun OwnPublication.toItem(author: ReviewAuthor): ReviewItem = ReviewItem(
        id = id,
        title = title,
        category = category,
        // El servidor de publicaciones aún no guarda si la categoría fue sugerida.
        categoryOrigin = CategoryOrigin.CHOSEN,
        description = description,
        photos = photos,
        hours = hours,
        price = price,
        address = null,
        location = location,
        submittedAt = submittedAt,
        author = author,
        duplicate = if (possibleDuplicate) DuplicateSuspicion(candidates(similarIds, location), duplicateNote) else null,
    )

    private fun ReviewSeed.toPublicDetails(author: Author): PoiDetails {
        val photos = samplePublishedPhotos(id, photoCount)
        return PoiDetails(
            poi = Poi(
                id = id,
                title = title,
                category = category,
                status = PublicationStatus.VERIFIED,
                location = location,
                distanceMeters = 0,
                votes = 0,
                comments = 0,
                photoUrl = photos.firstOrNull()?.url,
                price = price,
                summary = description.substringBefore('.').take(80),
            ),
            description = description,
            photos = photos.mapIndexed { i, _: PublishedPhoto -> PoiPhoto(url = null, description = "Foto ${i + 1} de $title") },
            address = address,
            hours = hours,
            author = author,
            voted = false,
            visited = false,
        )
    }
}

/**
 * Envuelve al servidor de moderación ([remote]) y guarda en Room la última cola: sin conexión se lee lo guardado con su
 * antigüedad (32.c) y no se decide nada, para no dejar decisiones a medias. El badge sale de lo guardado.
 */
class OfflineModerationRepository(
    private val remote: ModerationRepository,
    private val dao: ReviewsDao,
    private val connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : ModerationRepository {

    override val pendingCount: StateFlow<Int> = dao.count().stateIn(scope, SharingStarted.Eagerly, 0)

    /** Sale de la cola (y la guarda): la tarjeta del feed y el badge cuentan lo mismo. */
    override suspend fun summary(): ModerationSummary {
        val items = queue().items
        val now = clock.instant()
        return ModerationSummary(items.size, items.maxOfOrNull { it.waitingDays(now).toInt() } ?: 0)
    }

    override suspend fun queue(): ReviewQueue {
        if (!connectivity.isOnline.value) return saved() ?: throw OfflineException()
        val fresh = try {
            remote.queue().items
        } catch (e: IOException) {
            // Hay red pero el servidor no respondió: mejor lo guardado que un error.
            return saved() ?: throw e
        }
        dao.replace(fresh.mapIndexed { i, item -> item.toEntity(i) }, clock.millis())
        return ReviewQueue(fresh)
    }

    override suspend fun item(id: String): ReviewItem? {
        if (!connectivity.isOnline.value) return dao.item(id)?.toDomain() ?: throw OfflineException()
        return remote.item(id)
    }

    override suspend fun queueIds(): List<String> = dao.ids()

    override suspend fun verify(id: String, note: String?) {
        if (!connectivity.isOnline.value) throw OfflineException()
        try {
            remote.verify(id, note)
        } catch (e: AlreadyReviewedException) {
            // Ya la decidió otra persona: tampoco sigue en la cola guardada.
            dao.remove(id)
            throw e
        }
        dao.remove(id)
    }

    override suspend fun todayWork(): ModerationWork {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.todayWork()
    }

    private suspend fun saved(): ReviewQueue? {
        val savedAt = dao.savedAt() ?: return null
        return ReviewQueue(dao.all().map { it.toDomain() }, Instant.ofEpochMilli(savedAt))
    }
}
