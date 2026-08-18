package ai.wiro.quickstart.generate

import ai.wiro.quickstart.core.credentials.CredentialsRepository
import ai.wiro.quickstart.core.credentials.InMemoryCredentialsStore
import ai.wiro.quickstart.core.error.CANCELLED_MESSAGE
import ai.wiro.wirokit.WiroModelRequest
import ai.wiro.wirokit.WiroNetworkException
import ai.wiro.wirokit.WiroTask
import ai.wiro.wirokit.WiroTaskId
import ai.wiro.wirokit.WiroTaskOutput
import ai.wiro.wirokit.WiroTaskStatus
import ai.wiro.wirokit.WiroTaskToken
import ai.wiro.wirokit.WiroTaskUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateImageViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty prompt fails without opening a session`() = runTest {
        val factory = RecordingSessionFactory()
        val viewModel =
            GenerateImageViewModel(
                credentials = InMemoryCredentialsStore(apiKey = "key"),
                sessionFactory = factory,
            )
        viewModel.onPromptChange("   ")
        viewModel.generate()

        val generation = viewModel.uiState.value.generation
        assertTrue(generation is GenerationState.Failed)
        assertEquals(
            "Enter a prompt before generating.",
            (generation as GenerationState.Failed).message,
        )
        assertEquals(0, factory.openCount)
    }

    @Test
    fun `missing credentials fails without opening a session`() = runTest {
        val factory = RecordingSessionFactory()
        val viewModel =
            GenerateImageViewModel(
                credentials = InMemoryCredentialsStore(),
                sessionFactory = factory,
            )
        viewModel.generate()

        val generation = viewModel.uiState.value.generation
        assertTrue(generation is GenerationState.Failed)
        assertEquals(
            "Add an API key or proxy URL in Settings.",
            (generation as GenerationState.Failed).message,
        )
        assertEquals(0, factory.openCount)
    }

    @Test
    fun `direct and proxy mode toggle updates credentials`() {
        val credentials = InMemoryCredentialsStore(apiKey = "key")
        val viewModel = GenerateImageViewModel(credentials)
        assertFalse(viewModel.uiState.value.useProxy)

        viewModel.onUseProxyChange(true)
        viewModel.onProxyUrlChange("https://proxy.example.com/v1")
        assertTrue(credentials.useProxy)
        assertTrue(credentials.hasCredentials)
        assertEquals(
            "https://proxy.example.com/v1",
            viewModel.uiState.value.proxyUrlString,
        )

        viewModel.onUseProxyChange(false)
        assertFalse(credentials.useProxy)
        assertTrue(credentials.hasCredentials)
    }

    @Test
    fun `successful generation collects image urls`() = runTest {
        val outputs =
            listOf(
                WiroTaskOutput(
                    contentType = "image/png",
                    url = URI("https://cdn.example/out.png"),
                    raw = emptyMap(),
                ),
            )
        val factory =
            RecordingSessionFactory(
                updates =
                listOf(
                    WiroTaskUpdate.Snapshot(
                        WiroTask(
                            id = WiroTaskId("1"),
                            taskToken = WiroTaskToken("tok"),
                            status = WiroTaskStatus.Completed,
                            statusRawValue = "task_postprocess_end",
                            exitCode = 0,
                            outputs = outputs,
                            raw = emptyMap(),
                        ),
                    ),
                ),
            )
        val viewModel =
            GenerateImageViewModel(
                credentials = InMemoryCredentialsStore(apiKey = "key"),
                sessionFactory = factory,
            )

        viewModel.generate()

        val generation = viewModel.uiState.value.generation
        assertTrue(generation is GenerationState.Succeeded)
        assertEquals(
            listOf("https://cdn.example/out.png"),
            (generation as GenerationState.Succeeded).outputs,
        )
        assertEquals("1", viewModel.uiState.value.taskId)
        assertEquals("tok", viewModel.uiState.value.taskToken)
        assertTrue(viewModel.uiState.value.canCancelViaApi)
        assertTrue(viewModel.uiState.value.canKillViaApi)
    }

    @Test
    fun `local cancellation stops a hanging stream`() = runTest {
        val factory = RecordingSessionFactory(hangForever = true)
        val viewModel =
            GenerateImageViewModel(
                credentials = InMemoryCredentialsStore(apiKey = "key"),
                sessionFactory = factory,
            )

        viewModel.generate()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.cancelLocal()

        val generation = viewModel.uiState.value.generation
        assertTrue(generation is GenerationState.Failed)
        assertEquals(
            CANCELLED_MESSAGE,
            (generation as GenerationState.Failed).message,
        )
    }

    @Test
    fun `cancel and kill visibility follow identifiers`() {
        val stateWithId = GenerateImageUiState(taskId = "1")
        assertTrue(stateWithId.canCancelViaApi)
        assertTrue(stateWithId.canKillViaApi)

        val stateWithToken = GenerateImageUiState(taskToken = "tok")
        assertFalse(stateWithToken.canCancelViaApi)
        assertTrue(stateWithToken.canKillViaApi)

        val empty = GenerateImageUiState()
        assertFalse(empty.canCancelViaApi)
        assertFalse(empty.canKillViaApi)
    }

    @Test
    fun `errors map to friendly messages`() = runTest {
        val factory =
            RecordingSessionFactory(
                openError =
                WiroNetworkException(
                    message = "offline",
                    underlyingType = null,
                ),
            )
        val viewModel =
            GenerateImageViewModel(
                credentials = InMemoryCredentialsStore(apiKey = "key"),
                sessionFactory = factory,
            )
        viewModel.generate()

        val generation = viewModel.uiState.value.generation
        assertTrue(generation is GenerationState.Failed)
        assertEquals(
            "Network error: offline",
            (generation as GenerationState.Failed).message,
        )
    }

    @Test
    fun `completed generation remains stable`() = runTest {
        val factory =
            RecordingSessionFactory(
                updates =
                listOf(
                    WiroTaskUpdate.Snapshot(
                        WiroTask(
                            id = WiroTaskId("1"),
                            status = WiroTaskStatus.Completed,
                            statusRawValue = "task_postprocess_end",
                            exitCode = 0,
                            outputs =
                            listOf(
                                WiroTaskOutput(
                                    contentType = "image/png",
                                    url = URI("https://cdn.example/a.png"),
                                    raw = emptyMap(),
                                ),
                            ),
                            raw = emptyMap(),
                        ),
                    ),
                ),
            )
        val viewModel =
            GenerateImageViewModel(
                credentials = InMemoryCredentialsStore(apiKey = "key"),
                sessionFactory = factory,
            )
        viewModel.generate()
        assertEquals(1, factory.openCount)
        assertTrue(
            viewModel.uiState.value.generation
                is GenerationState.Succeeded,
        )
        assertEquals(1, factory.openCount)
    }
}

private class RecordingSessionFactory(
    private val updates: List<WiroTaskUpdate> = emptyList(),
    private val hangForever: Boolean = false,
    private val openError: Throwable? = null,
) : WiroSessionFactory {
    var openCount: Int = 0
        private set

    override fun open(
        credentials: CredentialsRepository,
    ): WiroSession {
        openCount += 1
        openError?.let { throw it }
        return object : WiroSession {
            override suspend fun subscribeStream(
                request: WiroModelRequest,
            ): Flow<WiroTaskUpdate> {
                if (hangForever) {
                    return callbackFlow {
                        awaitClose { }
                    }
                }
                return flow {
                    updates.forEach { emit(it) }
                }
            }

            override suspend fun cancelTask(id: WiroTaskId): Boolean = true

            override suspend fun killTask(token: WiroTaskToken): Boolean = true

            override suspend fun killTask(id: WiroTaskId): Boolean = true

            override fun close() = Unit
        }
    }
}
