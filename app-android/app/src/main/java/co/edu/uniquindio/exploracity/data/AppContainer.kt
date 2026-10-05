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
import co.edu.uniquindio.exploracity.data.local.DataStoreAccountStore
import co.edu.uniquindio.exploracity.data.local.DataStoreAppPreferences
import co.edu.uniquindio.exploracity.data.local.DataStoreDraftRepository
import co.edu.uniquindio.exploracity.data.local.DataStoreSessionStore
import co.edu.uniquindio.exploracity.data.local.DataStoreTokenStore
import co.edu.uniquindio.exploracity.data.local.DocumentWriter
import co.edu.uniquindio.exploracity.data.local.DraftRepository
import co.edu.uniquindio.exploracity.data.local.ExploraDatabase
import co.edu.uniquindio.exploracity.data.local.LocalSessionManager
import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.location.AddressResolver
import co.edu.uniquindio.exploracity.data.location.FusedLocationProvider
import co.edu.uniquindio.exploracity.data.location.GeocoderAddressResolver
import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.location.OnlineOnlyAddressResolver
import co.edu.uniquindio.exploracity.data.photos.AndroidPhotoStore
import co.edu.uniquindio.exploracity.data.photos.ApiPhotoUploader
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.remote.AccountApi
import co.edu.uniquindio.exploracity.data.remote.ApiSession
import co.edu.uniquindio.exploracity.data.remote.AuthApi
import co.edu.uniquindio.exploracity.data.remote.CityApi
import co.edu.uniquindio.exploracity.data.remote.DevMailboxApi
import co.edu.uniquindio.exploracity.data.remote.ModerationApi
import co.edu.uniquindio.exploracity.data.remote.NotificationApi
import co.edu.uniquindio.exploracity.data.remote.PoiApi
import co.edu.uniquindio.exploracity.data.remote.ProfileApi
import co.edu.uniquindio.exploracity.data.remote.PublicationApi
import co.edu.uniquindio.exploracity.data.remote.apiHttpClient
import co.edu.uniquindio.exploracity.data.remote.sessionHttpClient
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.data.repository.ApiAccountRepository
import co.edu.uniquindio.exploracity.data.repository.ApiAuthRepository
import co.edu.uniquindio.exploracity.data.repository.ApiCategorySuggester
import co.edu.uniquindio.exploracity.data.repository.ApiDevMailbox
import co.edu.uniquindio.exploracity.data.repository.ApiDuplicateFinder
import co.edu.uniquindio.exploracity.data.repository.ApiModerationRepository
import co.edu.uniquindio.exploracity.data.repository.ApiNotificationRepository
import co.edu.uniquindio.exploracity.data.repository.ApiPoiRepository
import co.edu.uniquindio.exploracity.data.repository.ApiPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.ApiUserRepository
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.data.repository.CategorySuggester
import co.edu.uniquindio.exploracity.data.repository.CityRepository
import co.edu.uniquindio.exploracity.data.repository.DevMailbox
import co.edu.uniquindio.exploracity.data.repository.DuplicateFinder
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.NotificationRepository
import co.edu.uniquindio.exploracity.data.repository.OfflineModerationRepository
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
import co.edu.uniquindio.exploracity.data.repository.SessionUser
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.data.sync.PendingSender
import co.edu.uniquindio.exploracity.data.sync.PublicationDelivery
import co.edu.uniquindio.exploracity.data.sync.PublicationOutbox
import co.edu.uniquindio.exploracity.data.sync.RoomPublicationOutbox
import co.edu.uniquindio.exploracity.data.sync.WorkManagerScheduler
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Un solo archivo de DataStore por proceso: el delegado lo garantiza. */
private val Context.draftsDataStore: DataStore<Preferences> by preferencesDataStore(name = "borradores")

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

/** El rol, los tokens de la API y la cuenta de la sesión. */
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

/** La ciudad que atiende la app (B1): es del teléfono, no de la cuenta, y se conserva al cerrar sesión. */
private val Context.cityDataStore: DataStore<Preferences> by preferencesDataStore(name = "ciudad")

/**
 * Dependencias de la app (inyección manual). Los datos vienen de la API (data/remote, Ktor Client) en
 * [BuildConfig.API_BASE_URL]; lo guardado para ver sin conexión y la cola de envío viven en Room (data/local). Los
 * envoltorios `Offline*` y `OnlineOnly*` deciden qué se puede hacer sin red.
 */
class AppContainer(context: Context) {
    /** Trabajo que no pertenece a una pantalla: descargas para ver sin conexión y el estado de la red. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val connectivity: ConnectivityObserver = AndroidConnectivityObserver(context, appScope)

    private val database = ExploraDatabase.build(context)

    /** 1 y 3 · La sesión abierta; con ella el arranque va directo al feed. */
    val sessionStore: SessionStore = DataStoreSessionStore(context.sessionDataStore)

    // La API: un cliente para lo público (la ciudad) y otro con la sesión, que renueva el token con un 401.

    private val apiSession = ApiSession(DataStoreTokenStore(context.sessionDataStore), DataStoreAccountStore(context.sessionDataStore))

    private val sessionEndedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** La API rechazó la renovación del token: la sesión terminó en pleno uso y hay que volver a entrar. */
    val sessionEnded: SharedFlow<Unit> = sessionEndedEvents.asSharedFlow()

    private val engine = OkHttp.create()

    private val publicClient = apiHttpClient(BuildConfig.API_BASE_URL, engine)

    private val client = sessionHttpClient(BuildConfig.API_BASE_URL, engine, apiSession) { sessionEndedEvents.tryEmit(Unit) }

    /** B1 · La ciudad de la API, guardada para abrir sin conexión. */
    val city = CityRepository(CityApi(publicClient), context.cityDataStore)

    /** La ubicación del teléfono: la API mide desde ahí las distancias; sin ella, desde el centro de la ciudad. */
    val locationProvider: LocationProvider = FusedLocationProvider(context)

    /** 19 · Fotos del formulario: comprimidas y guardadas en el teléfono hasta que el servidor las tiene. */
    val photoStore: PhotoStore = AndroidPhotoStore(context)

    private val publicationApi = PublicationApi(client)

    /** 19 · Sube las fotos a la API (SAD: Media Store en Cloudinary), con su progreso. */
    val photoUploader: PhotoUploader = ApiPhotoUploader(publicationApi)

    private val poiServer = ApiPoiRepository(PoiApi(client), locationProvider)

    private val notificationServer = ApiNotificationRepository(NotificationApi(client))

    private val publicationServer = ApiPublicationRepository(publicationApi)

    /** Lo usa el worker de WorkManager para enviar la cola, también con la app cerrada. */
    val pendingSender = PendingSender(
        remote = poiServer,
        notifications = notificationServer,
        pending = database.pendingActionsDao(),
        saved = database.savedPlacesDao(),
        publications = PublicationDelivery(publicationServer, photoUploader, photoStore),
    )

    private val scheduler = WorkManagerScheduler(context)

    /** 20 sin conexión y fotos que terminan de subir después del envío. */
    val publicationOutbox: PublicationOutbox = RoomPublicationOutbox(database.pendingActionsDao(), scheduler)

    private val userServer = ApiUserRepository(ProfileApi(client), locationProvider)

    val userRepository: UserRepository = OfflineUserRepository(userServer, database.profileDao(), connectivity)

    /** La persona de la sesión: su id, su nombre y sus puntos. */
    val sessionUser = SessionUser(apiSession, database.profileDao(), appScope)

    val poiRepository: PoiRepository = OfflinePoiRepository(
        remote = poiServer,
        dao = database.savedPlacesDao(),
        pending = database.pendingActionsDao(),
        scheduler = scheduler,
        connectivity = connectivity,
        scope = appScope,
        currentUser = { sessionUser.author.value },
    )

    val notificationRepository: NotificationRepository = OfflineNotificationRepository(
        remote = notificationServer,
        dao = database.notificationsDao(),
        pending = database.pendingActionsDao(),
        scheduler = scheduler,
        connectivity = connectivity,
        scope = appScope,
    )

    private val authServer = ApiAuthRepository(AuthApi(client), apiSession)

    /** 3–6 · El acceso con la API (JWT y BCrypt en el backend, ADR-06). */
    val authRepository: AuthRepository = OnlineOnlyAuthRepository(authServer, connectivity)

    /** 29 · El correo de la sesión, «Descargar mis datos», «Cambiar correo» y eliminar la cuenta. */
    val accountRepository: AccountRepository = OnlineOnlyAccountRepository(ApiAccountRepository(AccountApi(client), apiSession, appScope), connectivity)

    /** 6.a y «Confirma tu correo nuevo» · Los enlaces del buzón de desarrollo de la API; solo en compilaciones de desarrollo. */
    val devMailbox: DevMailbox? = if (BuildConfig.DEBUG) ApiDevMailbox(DevMailboxApi(client), apiSession) else null

    /** Borradores del formulario de publicación (15–19), en DataStore. */
    val draftRepository: DraftRepository = DataStoreDraftRepository(context.draftsDataStore)

    /** 29A · Cerrar sesión avisa a la API, borra lo de la cuenta y deja los borradores. */
    val sessionManager: SessionManager = LocalSessionManager(
        database = database,
        photos = photoStore,
        drafts = draftRepository,
        sessions = sessionStore,
        cancelSending = scheduler::cancel,
        closeRemote = authServer::signOut,
        forgetRemote = apiSession::end,
    )

    /** Preferencias del teléfono (el tema de 29), aparte de la cuenta. */
    val preferences: AppPreferences = DataStoreAppPreferences(context.preferencesDataStore)

    /** 29 · Guarda «Descargar mis datos» donde la persona elija. */
    val documentWriter: DocumentWriter = AndroidDocumentWriter(context)

    val publicationRepository: PublicationRepository = OnlineOnlyPublicationRepository(publicationServer, connectivity)

    /** 16 · La sugerencia la hace la API con IA (SAD: Componente de Clasificación IA). */
    val categorySuggester: CategorySuggester = OnlineOnlyCategorySuggester(ApiCategorySuggester(publicationApi), connectivity)

    /** 17 · La búsqueda la hace la API con PostGIS (SAD: Componente de Detección de Duplicados). */
    val duplicateFinder: DuplicateFinder = OnlineOnlyDuplicateFinder(ApiDuplicateFinder(publicationApi), connectivity)

    /** Dirección aproximada del pin y búsqueda por dirección (17), con el Geocoder de Android. */
    val addressResolver: AddressResolver = OnlineOnlyAddressResolver(GeocoderAddressResolver(context), connectivity)

    /** 32–37 · La cola de moderación, guardada en el teléfono para leerla sin conexión (32.c). */
    val moderationRepository: ModerationRepository = OfflineModerationRepository(
        remote = ApiModerationRepository(ModerationApi(client)),
        dao = database.reviewsDao(),
        connectivity = connectivity,
        scope = appScope,
    )

    init {
        appScope.launch {
            connectivity.isOnline.filter { it }.collect {
                // La ciudad, por si la API la cambió; sin red queda la guardada.
                quietly { city.refresh() }
                // El badge de «Avisos» se pone al día al abrir la app con red y cada vez que vuelve la red, con sesión.
                if (hasSession()) quietly { notificationRepository.notifications() }
            }
        }
    }

    /** Hay tokens de la API guardados. */
    suspend fun hasApiSession(): Boolean = apiSession.tokens() != null

    /** La ciudad está guardada o se pudo traer. */
    suspend fun cityReady(): Boolean = quietly { city.ensure() } != null

    /**
     * Antes de entrar a la app tras el inicio de sesión (3) o el registro (4): la ciudad, sin la cual no entra (lanza
     * excepción si no se puede traer), y el perfil propio, que da el nombre y los puntos de la persona.
     */
    suspend fun prepareSession() {
        city.ensure()
        quietly { userRepository.ownProfile() }
    }

    private suspend fun hasSession(): Boolean = sessionStore.role.first() != null && hasApiSession()

    /** Lo que se intenta de nuevo más adelante: si falla, no pasa nada. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
