package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.markedRead
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** Los avisos, de la más reciente a la más antigua. [savedAt] no es null si vienen de lo guardado (sin conexión). */
data class NotificationList(val items: List<Notification>, val savedAt: Instant? = null)

/** 25 · Avisos dentro de la app (SAD: sin push). */
interface NotificationRepository {
    /** Sin leer: el badge de la pestaña «Avisos». */
    val unreadCount: StateFlow<Int>

    /** Lanza excepción si falla la red y no hay nada guardado. */
    suspend fun notifications(): NotificationList

    suspend fun markRead(id: String)

    suspend fun markAllRead()
}

/**
 * Temporal hasta que exista la API: un aviso de cada tipo para Ana (la persona de la sesión), sobre sus lugares
 * (currentUserPlaces). El comentario es el más reciente que tiene de verdad Quinta de Bolívar.
 */
class FakeNotificationRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val latency: Duration = 700.milliseconds,
    private val actionLatency: Duration = 300.milliseconds,
) : NotificationRepository {

    private val items = MutableStateFlow(sampleNotifications(clock.instant()))
    private val unread = MutableStateFlow(items.value.count { !it.read })
    override val unreadCount: StateFlow<Int> = unread.asStateFlow()

    override suspend fun notifications(): NotificationList {
        delay(latency)
        return NotificationList(items.value)
    }

    override suspend fun markRead(id: String) {
        delay(actionLatency)
        update { list -> list.map { if (it.id == id) it.markedRead() else it } }
    }

    override suspend fun markAllRead() {
        delay(actionLatency)
        update { list -> list.map { it.markedRead() } }
    }

    private fun update(change: (List<Notification>) -> List<Notification>) {
        items.value = change(items.value)
        unread.value = items.value.count { !it.read }
    }
}

private fun sampleNotifications(now: Instant): List<Notification> {
    fun ago(duration: Duration) = now - duration.toJavaDuration()
    val quinta = samplePois.first { it.id == "quinta-de-bolivar" }
    val latestComment = sampleComments(quinta, now).first()
    return listOf(
        Notification.Verified("n-verificada", ago(20.minutes), read = false, quinta.id, quinta.title, points = 15),
        Notification.DuplicateRejected(
            "n-duplicado",
            ago(45.minutes),
            read = false,
            publicationId = "puerta-falsa-tamales",
            title = "Puerta Falsa, tamales",
            existingPoiId = "la-puerta-falsa",
            existingTitle = "La Puerta Falsa",
        ),
        Notification.Achievement("n-logro", ago(1.hours), read = false, "3 lugares verificados", nextBadge = "Explorador constante", remaining = 7),
        Notification.Rejected(
            "n-rechazo",
            ago(3.hours),
            read = false,
            publicationId = "mirador-de-la-pena",
            title = "Mirador de La Peña",
            reason = "la foto no permite reconocer el lugar",
        ),
        Notification.Commented(
            "n-comentario",
            latestComment.createdAt,
            read = true,
            poiId = quinta.id,
            poiTitle = quinta.title,
            authorName = latestComment.author.name,
            excerpt = latestComment.text,
        ),
        Notification.Finalized("n-finalizada", ago(5.days), read = true, "casa-independencia", "Casa de la Independencia"),
    )
}
