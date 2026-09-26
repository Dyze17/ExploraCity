package co.edu.uniquindio.exploracity.data

import android.content.Context
import co.edu.uniquindio.exploracity.data.connectivity.AndroidConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.repository.FakeModerationRepository
import co.edu.uniquindio.exploracity.data.repository.FakeNotificationRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.FakeUserRepository
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.NotificationRepository
import co.edu.uniquindio.exploracity.data.repository.OfflineNotificationRepository
import co.edu.uniquindio.exploracity.data.repository.OfflinePoiRepository
import co.edu.uniquindio.exploracity.data.repository.OfflineUserRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.data.sync.PendingSender
import co.edu.uniquindio.exploracity.data.sync.WorkManagerScheduler
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Dependencias de la app (inyección manual). El «servidor» todavía es un repositorio en memoria
 * (FakePoiRepository); lo guardado para ver sin conexión ya vive en Room (data/local). Al llegar la API se cambia
 * aquí por el de data/remote (Ktor Client) sin tocar la UI.
 */
class AppContainer(context: Context) {
    /** Trabajo que no pertenece a una pantalla: descargas para ver sin conexión y el estado de la red. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Temporal: llegará de la sesión (JWT en DataStore) cuando exista el inicio de sesión real. */
    val currentUser: Author = sampleCurrentUser

    val connectivity: ConnectivityObserver = AndroidConnectivityObserver(context, appScope)

    private val database = ExploraDatabase.build(context)
    private val server = FakePoiRepository(currentUser = currentUser)

    private val notificationServer: NotificationRepository = FakeNotificationRepository()

    private val publicationServer = FakePublicationRepository(server, currentUser = currentUser)

    /** Lo usa el worker de WorkManager para enviar la cola, también con la app cerrada. */
    val pendingSender = PendingSender(server, notificationServer, database.pendingActionsDao(), database.savedPlacesDao())

    private val scheduler = WorkManagerScheduler(context)

    val poiRepository: PoiRepository = OfflinePoiRepository(
        remote = server,
        dao = database.savedPlacesDao(),
        pending = database.pendingActionsDao(),
        scheduler = scheduler,
        connectivity = connectivity,
        scope = appScope,
        currentUser = currentUser,
    )

    val notificationRepository: NotificationRepository = OfflineNotificationRepository(
        remote = notificationServer,
        dao = database.notificationsDao(),
        pending = database.pendingActionsDao(),
        scheduler = scheduler,
        connectivity = connectivity,
        scope = appScope,
    )

    init {
        // El badge de «Avisos» se pone al día al abrir la app con red y cada vez que vuelve la red.
        appScope.launch {
            connectivity.isOnline.filter { it }.collect {
                try {
                    notificationRepository.notifications()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Se intenta de nuevo en la próxima conexión o al abrir «Avisos».
                }
            }
        }
    }

    val userRepository: UserRepository =
        OfflineUserRepository(FakeUserRepository(server, publicationServer, currentUser), database.profileDao(), connectivity)
    val publicationRepository: PublicationRepository = OnlineOnlyPublicationRepository(publicationServer, connectivity)
    val moderationRepository: ModerationRepository = FakeModerationRepository()
    val locationProvider: LocationProvider = SimulatedLocationProvider()

    /** Temporal: llegará de la ubicación del dispositivo y la geocodificación de Google Maps (ADR-08). */
    val areaName: String = "Bogotá"

    /** Centro del área: donde abre el mapa (8) mientras no haya permiso de ubicación. Temporal como [areaName]. */
    val areaCenter: GeoPoint = GeoPoint(4.6097, -74.0817)
}
