package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.CommentsPage
import co.edu.uniquindio.exploracity.data.repository.FakeOfflineRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.domain.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-24T15:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun savedState(poiId: String = "cafe-las-acacias", write: Boolean = false) =
        SavedStateHandle(mapOf(CommentsViewModel.POI_ID_KEY to poiId, CommentsViewModel.WRITE_KEY to write))

    private fun viewModel(
        repository: PoiRepository = FakePoiRepository(clock = clock),
        savedState: SavedStateHandle = savedState(),
        connectivity: FakeConnectivity = FakeConnectivity(),
    ) = CommentsViewModel(repository, connectivity, sampleCurrentUser, savedState, clock)

    private val CommentsViewModel.loaded: CommentsContent.Loaded
        get() = state.value.content as? CommentsContent.Loaded ?: error("Los comentarios deberían estar cargados: ${state.value.content}")

    @Test
    fun `carga los comentarios del lugar, del más reciente al más antiguo`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(CommentsContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        val loaded = vm.loaded
        assertEquals("Café Las Acacias", loaded.poiTitle)
        assertEquals(12, loaded.total)
        assertEquals(12, loaded.comments.size)
        assertNull(loaded.nextCursor)
        assertEquals("María Paula", loaded.comments.first().author.name)
        assertEquals(loaded.comments.sortedByDescending { it.createdAt }, loaded.comments)
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun `un lugar sin comentarios muestra el vacío`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("galeria-santa-fe"))
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
        assertEquals(0, vm.loaded.total)
    }

    @Test
    fun `un lugar que ya no existe muestra no encontrado`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("no-existe"))
        advanceUntilIdle()

        assertEquals(CommentsContent.NotFound, vm.state.value.content)
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FlakyRepository(failComments = true)
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(CommentsContent.Error, vm.state.value.content)

        repository.failComments = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(12, vm.loaded.total)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakePoiRepository(latency = 10.seconds, clock = clock))

        advanceTimeBy(7.9.seconds)
        assertEquals(CommentsContent.Loading, vm.state.value.content)
        advanceTimeBy(0.2.seconds)
        assertEquals(CommentsContent.Error, vm.state.value.content)
    }

    @Test
    fun `carga de 20 en 20 al desplazar hasta traerlos todos, sin repetir`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("museo-del-oro"))
        advanceUntilIdle()
        assertEquals(20, vm.loaded.comments.size)
        assertEquals(88, vm.loaded.total)

        vm.onLoadMore()
        assertTrue(vm.loaded.loadingMore)
        advanceUntilIdle()
        assertEquals(40, vm.loaded.comments.size)

        repeat(5) {
            vm.onLoadMore()
            advanceUntilIdle()
        }
        val comments = vm.loaded.comments
        assertEquals(88, comments.size)
        assertEquals(88, comments.map { it.id }.distinct().size)
        assertNull(vm.loaded.nextCursor)
    }

    @Test
    fun `si falla la página siguiente la lista sigue y se puede reintentar`() = runTest(dispatcher) {
        val repository = FlakyRepository(failNextPages = true)
        val vm = viewModel(repository, savedState("museo-del-oro"))
        advanceUntilIdle()

        vm.onLoadMore()
        advanceUntilIdle()
        assertTrue(vm.loaded.loadMoreFailed)
        assertEquals(20, vm.loaded.comments.size)

        repository.failNextPages = false
        vm.onLoadMore()
        advanceUntilIdle()
        assertFalse(vm.loaded.loadMoreFailed)
        assertEquals(40, vm.loaded.comments.size)
    }

    @Test
    fun `enviar muestra el comentario al instante y se publica sin puntos`() = runTest(dispatcher) {
        val repository = FakePoiRepository(clock = clock)
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertTrue(vm.onSend("  El patio es perfecto para trabajar temprano.  "))

        val sending = vm.state.value.own.single()
        assertEquals(SendStatus.SENDING, sending.status)
        assertEquals("El patio es perfecto para trabajar temprano.", sending.text)
        assertEquals(12, vm.loaded.total)

        advanceUntilIdle()
        val sent = vm.state.value.own.single()
        assertEquals(SendStatus.SENT, sent.status)
        assertNotNull(sent.sentId)
        assertEquals(13, vm.loaded.total)
        val published = repository.comments("cafe-las-acacias")!!.items.first()
        assertEquals(sent.sentId, published.id)
        assertTrue(published.mine)
        assertEquals(sampleCurrentUser, published.author)
    }

    @Test
    fun `si falla el envío el texto sigue ahí y reintentar lo publica`() = runTest(dispatcher) {
        val repository = FlakyRepository(failSend = true)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSend("Ojo con el horario del domingo.")
        advanceUntilIdle()
        val failed = vm.state.value.own.single()
        assertEquals(SendStatus.FAILED, failed.status)
        assertEquals("Ojo con el horario del domingo.", failed.text)
        assertEquals(12, vm.loaded.total)

        repository.failSend = false
        vm.onRetrySend(failed.localId)
        assertEquals(SendStatus.SENDING, vm.state.value.own.single().status)
        advanceUntilIdle()
        assertEquals(SendStatus.SENT, vm.state.value.own.single().status)
        assertEquals(13, vm.loaded.total)
    }

    @Test
    fun `no se envía un comentario en blanco ni antes de cargar`() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.onSend("Muy bueno"))

        advanceUntilIdle()
        assertFalse(vm.onSend("   \n "))
        assertTrue(vm.state.value.own.isEmpty())
    }

    @Test
    fun `el comentario se corta en 300 caracteres`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSend("a".repeat(350))

        assertEquals(Comment.MAX_LENGTH, vm.state.value.own.single().text.length)
    }

    @Test
    fun `lo que no se envió sobrevive si el sistema cierra la app`() = runTest(dispatcher) {
        val savedState = savedState()
        val before = viewModel(FlakyRepository(failSend = true), savedState)
        advanceUntilIdle()
        before.onSend("Sin señal en el patio.")
        advanceUntilIdle()
        before.onSend("Este se estaba enviando.")

        val after = viewModel(FakePoiRepository(clock = clock), savedState)
        advanceUntilIdle()

        // Del que se estaba enviando no se sabe si llegó: vuelve como «No se envió» para decidir.
        assertEquals(listOf("Este se estaba enviando.", "Sin señal en el patio."), after.state.value.own.map { it.text })
        assertTrue(after.state.value.own.all { it.status == SendStatus.FAILED })
    }

    @Test
    fun `al recargar no se repite el comentario propio que ya trae el servidor`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSend("Recomendado para ir temprano.")
        advanceUntilIdle()

        vm.onRetry()
        advanceUntilIdle()

        assertTrue(vm.state.value.own.isEmpty())
        assertEquals(13, vm.loaded.total)
        assertEquals("Recomendado para ir temprano.", vm.loaded.comments.first().text)
    }

    @Test
    fun `agregar comentario desde el detalle abre con el teclado listo`() = runTest(dispatcher) {
        assertTrue(viewModel(savedState = savedState(write = true)).startWriting)
        assertFalse(viewModel().startWriting)
    }

    @Test
    fun `sin conexión lo dice y al volver la red carga solo`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(online = false)
        val repository = FakeOfflineRepository(connectivity, delegate = FakePoiRepository(clock = clock))
        val vm = viewModel(repository, connectivity = connectivity)
        advanceUntilIdle()
        assertEquals(CommentsContent.Offline, vm.state.value.content)

        connectivity.online = true
        advanceUntilIdle()
        assertEquals(12, vm.loaded.total)
    }

    private inner class FlakyRepository(
        var failComments: Boolean = false,
        var failNextPages: Boolean = false,
        var failSend: Boolean = false,
        private val delegate: FakePoiRepository = FakePoiRepository(clock = clock),
    ) : PoiRepository by delegate {
        override suspend fun comments(poiId: String, cursor: String?, pageSize: Int): CommentsPage? {
            if (failComments || (cursor != null && failNextPages)) throw IOException("sin red")
            return delegate.comments(poiId, cursor, pageSize)
        }

        override suspend fun addComment(poiId: String, text: String): Comment {
            if (failSend) throw IOException("sin red")
            return delegate.addComment(poiId, text)
        }
    }
}
