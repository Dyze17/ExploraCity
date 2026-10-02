package co.edu.uniquindio.exploracity.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import co.edu.uniquindio.exploracity.BuildConfig
import co.edu.uniquindio.exploracity.data.connectivity.AndroidConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.local.AndroidDocumentWriter
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.DataStoreAppPreferences
import co.edu.uniquindio.exploracity.data.local.DataStoreDraftRepository
import co.edu.uniquindio.exploracity.data.local.DataStoreSessionStore
import co.edu.uniquindio.exploracity.data.local.DocumentWriter
import co.edu.uniquindio.exploracity.data.local.DraftRepository
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.data.local.LocalSessionManager
import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.location.AddressResolver
import co.edu.uniquindio.exploracity.data.location.GeocoderAddressResolver
import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.location.OnlineOnlyAddressResolver
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.photos.AndroidPhotoStore
import co.edu.uniquindio.exploracity.data.photos.FakePhotoUploader
import co.edu.uniquindio.exploracity.data.photos.LocalProfilePhotoHost
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.data.repository.CategorySuggester
import co.edu.uniquindio.exploracity.data.repository.DemoEmailChangeMailbox
import co.edu.uniquindio.exploracity.data.repository.DemoMailbox
import co.edu.uniquindio.exploracity.data.repository.DuplicateFinder
import co.edu.uniquindio.exploracity.data.repository.FakeAccountRepository
import co.edu.uniquindio.exploracity.data.repository.FakeAuthRepository
import co.edu.uniquindio.exploracity.data.repository.FakeCategorySuggester
import co.edu.uniquindio.exploracity.data.repository.FakeDuplicateFinder
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
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyAccountRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyAuthRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyCategorySuggester
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyDuplicateFinder
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.data.repository.sampleDemoAccounts
import co.edu.uniquindio.exploracity.data.sync.PendingSender
import co.edu.uniquindio.exploracity.data.sync.PublicationDelivery
import co.edu.uniquindio.exploracity.data.sync.PublicationOutbox
import co.edu.uniquindio.exploracity.data.sync.RoomPublicationOutbox
import co.edu.uniquindio.exploracity.data.sync.WorkManagerScheduler
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.DemoAccount
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Un solo archivo de DataStore por proceso: el delegado lo garantiza. */
private val Context.draftsDataStore: DataStore<Preferences> by preferencesDataStore(name = "borradores")

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

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

    /** 19 · Fotos del formulario: comprimidas y guardadas en el teléfono hasta que el servidor las tiene. */
    val photoStore: PhotoStore = AndroidPhotoStore(context)

    /** Temporal: la subida real irá a la API (SAD: Media Store en Cloudinary). */
    val photoUploader: PhotoUploader = FakePhotoUploader(connectivity)

    /** Lo usa el worker de WorkManager para enviar la cola, también con la app cerrada. */
    val pendingSender = PendingSender(
        remote = server,
        notifications = notificationServer,
        pending = database.pendingActionsDao(),
        saved = database.savedPlacesDao(),
        publications = PublicationDelivery(publicationServer, photoUploader, photoStore),
    )

    private val scheduler = WorkManagerScheduler(context)

    /** 20 sin conexión y fotos que terminan de subir después del envío. */
    val publicationOutbox: PublicationOutbox = RoomPublicationOutbox(database.pendingActionsDao(), scheduler)

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

    private val profilePhotoHost = LocalProfilePhotoHost(context)

    private val userServer =
        FakeUserRepository(server, publicationServer, currentUser, photoHost = profilePhotoHost::host, photoRemover = profilePhotoHost::clear)

    val userRepository: UserRepository = OfflineUserRepository(userServer, database.profileDao(), connectivity)

    private val authServer = FakeAuthRepository()

    private val accountServer =
        FakeAccountRepository(server, publicationServer, userServer, credentials = authServer, onDeleted = userServer::deleteOwnAccount)

    /** 29 · El correo de la sesión, «Descargar mis datos» y «Cambiar correo». */
    val accountRepository: AccountRepository = OnlineOnlyAccountRepository(accountServer, connectivity)

    /** «Cambiar correo» · El enlace que llegaría al correo nuevo; solo en compilaciones de desarrollo. */
    val demoEmailChangeMailbox: DemoEmailChangeMailbox? = if (BuildConfig.DEBUG) accountServer else null

    /** Borradores del formulario de publicación (15–19), en DataStore. */
    val draftRepository: DraftRepository = DataStoreDraftRepository(context.draftsDataStore)

    /** 1 y 3 · La sesión abierta; con ella el arranque va directo al feed. */
    val sessionStore: SessionStore = DataStoreSessionStore(context.sessionDataStore)

    /** 29A · Cerrar sesión borra lo de la cuenta y deja los borradores. */
    val sessionManager: SessionManager =
        LocalSessionManager(database, photoStore, draftRepository, sessionStore, cancelSending = scheduler::cancel)

    /** Temporal: el acceso real irá a la API (JWT y BCrypt en el backend, ADR-06). */
    val authRepository: AuthRepository = OnlineOnlyAuthRepository(authServer, connectivity)

    /** 6.a · Lo que llegaría al correo, para abrir 6.b y 6C; solo en compilaciones de desarrollo. */
    val demoMailbox: DemoMailbox? = if (BuildConfig.DEBUG) authServer else null

    /** 3 · Cuentas de prueba que el inicio de sesión rellena; solo en compilaciones de desarrollo. */
    val demoAccounts: List<DemoAccount> = if (BuildConfig.DEBUG) sampleDemoAccounts else emptyList()

    /** Preferencias del teléfono (el tema de 29), aparte de la cuenta. */
    val preferences: AppPreferences = DataStoreAppPreferences(context.preferencesDataStore)

    /** 29 · Guarda «Descargar mis datos» donde la persona elija. */
    val documentWriter: DocumentWriter = AndroidDocumentWriter(context)

    val publicationRepository: PublicationRepository = OnlineOnlyPublicationRepository(publicationServer, connectivity)

    /** Temporal: la sugerencia real la hará el backend con IA (SAD: Componente de Clasificación IA). */
    val categorySuggester: CategorySuggester = OnlineOnlyCategorySuggester(FakeCategorySuggester(), connectivity)

    /** Temporal: la búsqueda real la hará el backend con PostGIS (SAD: Componente de Detección de Duplicados). */
    val duplicateFinder: DuplicateFinder = OnlineOnlyDuplicateFinder(FakeDuplicateFinder(server, publicationServer), connectivity)

    /** Dirección aproximada del pin y búsqueda por dirección (17), con el Geocoder de Android. */
    val addressResolver: AddressResolver = OnlineOnlyAddressResolver(GeocoderAddressResolver(context), connectivity)
    val moderationRepository: ModerationRepository = FakeModerationRepository()
    val locationProvider: LocationProvider = SimulatedLocationProvider()

    /** Temporal: llegará de la ubicación del dispositivo y la geocodificación de Google Maps (ADR-08). */
    val areaName: String = "Bogotá"

    /** Centro del área: donde abre el mapa (8) mientras no haya permiso de ubicación. Temporal como [areaName]. */
    val areaCenter: GeoPoint = GeoPoint(4.6097, -74.0817)

    /** Límites del área: la búsqueda por dirección (17.b) no sale de la ciudad. Temporal como [areaName]. */
    val areaBounds: GeoBounds = GeoBounds(southwest = GeoPoint(4.4600, -74.2300), northeast = GeoPoint(4.8400, -73.9900))
}
