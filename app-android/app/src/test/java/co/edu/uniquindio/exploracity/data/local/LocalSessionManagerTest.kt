package co.edu.uniquindio.exploracity.data.local

import androidx.room.Room
import co.edu.uniquindio.exploracity.data.sync.PendingScheduler
import co.edu.uniquindio.exploracity.data.sync.RoomPublicationOutbox
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.viewmodel.FakePhotos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 29A · Cerrar sesión borra lo de la cuenta en el teléfono y deja los borradores con sus fotos. */
@RunWith(RobolectricTestRunner::class)
class LocalSessionManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val photos = FakePhotos()
    private var cancelled = false
    private lateinit var database: ExploraDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ExploraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    private val drafts = DataStoreDraftRepository(MemoryDataStore())

    private val sessions = DataStoreSessionStore(MemoryDataStore())

    /** Lo que se le pidió a la sesión con la API: cerrarla («cerrar») o solo borrar sus tokens («olvidar»). */
    private val remote = mutableListOf<String>()

    private fun session() = LocalSessionManager(
        database,
        photos,
        drafts,
        sessions,
        cancelSending = { cancelled = true },
        closeRemote = { remote += "cerrar" },
        forgetRemote = { remote += "olvidar" },
        io = dispatcher,
    )

    private fun submission(vararg photos: DraftPhoto) = PublicationSubmission(
        title = "Mirador del Tunal",
        description = "Un mirador tranquilo al sur de la ciudad con vista a los cerros.",
        category = Category.NATURE,
        categoryOrigin = CategoryOrigin.CHOSEN,
        location = GeoPoint(4.57, -74.13),
        hours = null,
        price = null,
        photos = photos.toList(),
        duplicateCheck = null,
    )

    @Test
    fun `cuenta los envíos pendientes`() = runTest(dispatcher) {
        val outbox = RoomPublicationOutbox(database.pendingActionsDao(), PendingScheduler { })
        outbox.enqueue(submission(DraftPhoto("f1", "/fotos/f1.jpg", "f1.jpg")))
        outbox.enqueuePhotos("mirador", listOf(DraftPhoto("f2", "/fotos/f2.jpg", "f2.jpg")))

        assertEquals(2, session().pendingSends())
    }

    @Test
    fun `borra la cola con sus fotos, el perfil guardado y deja de enviar`() = runTest(dispatcher) {
        val outbox = RoomPublicationOutbox(database.pendingActionsDao(), PendingScheduler { })
        outbox.enqueue(submission(DraftPhoto("f1", "/fotos/f1.jpg", "f1.jpg"), DraftPhoto("subida", "", "subida.jpg", remoteUrl = "https://fotos/1")))
        outbox.enqueuePhotos("mirador", listOf(DraftPhoto("f2", "/fotos/f2.jpg", "f2.jpg")))
        database.pendingActionsDao().insert(PendingActionEntity(type = PendingType.VOTE, poiId = "cafe", payload = "{}", createdAtMillis = 0))
        database.profileDao().save(SavedProfileEntity(json = "{}", savedAtMillis = 0))

        sessions.open(UserRole.USER)

        session().signOut()

        assertNull("La sesión se cierra", sessions.role.first())
        assertTrue(cancelled)
        assertEquals(0, database.pendingActionsDao().count())
        assertNull(database.profileDao().get())
        assertEquals("Solo los archivos que esperaban envío", listOf("f1", "f2"), photos.deleted)
    }

    @Test
    fun `al eliminar la cuenta también se van los borradores y todas las fotos`() = runTest(dispatcher) {
        drafts.save(DraftKey.New, PublicationDraft(title = "Mirador del Tunal"))
        drafts.save(DraftKey.Resubmit("puerta-falsa-tamales"), PublicationDraft(title = "Puerta Falsa"))

        session().deleteAccountData()

        assertTrue(cancelled)
        assertNull(drafts.load(DraftKey.New))
        assertNull(drafts.load(DraftKey.Resubmit("puerta-falsa-tamales")))
        assertTrue(photos.deletedAll)
    }

    @Test
    fun `cerrar sesión avisa a la API, y si la sesión terminó o se eliminó la cuenta solo se olvidan los tokens`() = runTest(dispatcher) {
        sessions.open(UserRole.USER)
        session().signOut()
        sessions.open(UserRole.USER)
        session().sessionEnded()
        session().deleteAccountData()

        assertEquals(listOf("cerrar", "olvidar", "olvidar"), remote)
        assertNull(sessions.role.first())
    }

    @Test
    fun `cerrar sesión deja los borradores`() = runTest(dispatcher) {
        drafts.save(DraftKey.New, PublicationDraft(title = "Mirador del Tunal"))

        session().signOut()

        assertEquals("Mirador del Tunal", drafts.load(DraftKey.New)?.title)
        assertTrue(!photos.deletedAll)
    }
}
