package ai.wiro.quickstart.generate

import ai.wiro.quickstart.core.credentials.CredentialsRepository
import ai.wiro.quickstart.core.error.CANCELLED_MESSAGE
import ai.wiro.quickstart.core.error.toFriendlyMessage
import ai.wiro.wirokit.Wiro
import ai.wiro.wirokit.WiroFlux2ProOutputFormat
import ai.wiro.wirokit.WiroTaskId
import ai.wiro.wirokit.WiroTaskToken
import ai.wiro.wirokit.WiroTaskUpdate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drives the Flux 2 Pro generate-image demo.
 */
public class GenerateImageViewModel(
    private val credentials: CredentialsRepository,
    private val sessionFactory: WiroSessionFactory =
        DefaultWiroSessionFactory,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            GenerateImageUiState(
                allowsDirectCredentials =
                credentials.allowsDirectCredentials,
                useProxy =
                !credentials.allowsDirectCredentials ||
                    credentials.useProxy,
                apiKey = credentials.apiKey,
                apiSecret = credentials.apiSecret,
                proxyUrlString = credentials.proxyUrlString,
            ),
        )
    public val uiState: StateFlow<GenerateImageUiState> =
        _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var activeSession: WiroSession? = null

    public fun onPromptChange(value: String) {
        _uiState.update { it.copy(prompt = value) }
    }

    public fun onWidthChange(value: Int) {
        _uiState.update { it.copy(width = value) }
    }

    public fun onHeightChange(value: Int) {
        _uiState.update { it.copy(height = value) }
    }

    public fun openSettings() {
        syncCredentialsFromStore()
        _uiState.update { it.copy(showSettings = true) }
    }

    public fun dismissSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    public fun onUseProxyChange(value: Boolean) {
        val useProxy = value || !credentials.allowsDirectCredentials
        credentials.useProxy = useProxy
        _uiState.update { it.copy(useProxy = useProxy) }
    }

    public fun onApiKeyChange(value: String) {
        if (!credentials.allowsDirectCredentials) return
        credentials.apiKey = value
        _uiState.update { it.copy(apiKey = value) }
    }

    public fun onApiSecretChange(value: String) {
        if (!credentials.allowsDirectCredentials) return
        credentials.apiSecret = value
        _uiState.update { it.copy(apiSecret = value) }
    }

    public fun onProxyUrlChange(value: String) {
        credentials.proxyUrlString = value
        _uiState.update { it.copy(proxyUrlString = value) }
    }

    public fun generate() {
        val state = _uiState.value
        if (state.isRunning) {
            return
        }

        val trimmed = state.prompt.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    generation =
                    GenerationState.Failed(
                        "Enter a prompt before generating.",
                    ),
                )
            }
            return
        }
        if (!credentials.hasCredentials) {
            _uiState.update {
                it.copy(
                    generation =
                    GenerationState.Failed(
                        if (credentials.allowsDirectCredentials) {
                            "Add an API key or proxy URL in Settings."
                        } else {
                            "Add a backend proxy URL in Settings."
                        },
                    ),
                )
            }
            return
        }

        generationJob?.cancel()
        closeSession()
        _uiState.update {
            it.copy(
                taskId = null,
                taskToken = null,
                showCancelOptions = false,
                generation = GenerationState.Running("Submitting…"),
            )
        }

        generationJob =
            viewModelScope.launch {
                runGeneration(
                    prompt = trimmed,
                    width = state.width,
                    height = state.height,
                )
            }
    }

    public fun requestCancelOptions() {
        if (_uiState.value.isRunning) {
            _uiState.update { it.copy(showCancelOptions = true) }
        }
    }

    public fun dismissCancelOptions() {
        _uiState.update { it.copy(showCancelOptions = false) }
    }

    /** Cancels the local coroutine (cooperative cancellation). */
    public fun cancelLocal() {
        generationJob?.cancel()
        generationJob = null
        closeSession()
        _uiState.update {
            if (it.isRunning) {
                it.copy(
                    showCancelOptions = false,
                    generation = GenerationState.Failed(CANCELLED_MESSAGE),
                )
            } else {
                it.copy(showCancelOptions = false)
            }
        }
    }

    /** Cancels a queued task via the Wiro API. */
    public fun cancelRemote() {
        val id = _uiState.value.taskId ?: return
        viewModelScope.launch {
            try {
                val session = sessionFactory.open(credentials)
                try {
                    session.cancelTask(WiroTaskId(id))
                } finally {
                    session.close()
                }
                cancelLocal()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        showCancelOptions = false,
                        generation =
                        GenerationState.Failed(
                            error.toFriendlyMessage(),
                        ),
                    )
                }
            }
        }
    }

    /** Kills a running task via the Wiro API. */
    public fun killRemote() {
        val state = _uiState.value
        if (state.taskToken == null && state.taskId == null) {
            return
        }
        viewModelScope.launch {
            try {
                val session = sessionFactory.open(credentials)
                try {
                    val token = state.taskToken
                    if (token != null) {
                        session.killTask(WiroTaskToken(token))
                    } else if (state.taskId != null) {
                        session.killTask(WiroTaskId(state.taskId))
                    }
                } finally {
                    session.close()
                }
                cancelLocal()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        showCancelOptions = false,
                        generation =
                        GenerationState.Failed(
                            error.toFriendlyMessage(),
                        ),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        generationJob?.cancel()
        closeSession()
        super.onCleared()
    }

    private suspend fun runGeneration(
        prompt: String,
        width: Int,
        height: Int,
    ) {
        try {
            val session = sessionFactory.open(credentials)
            activeSession = session
            val request =
                Wiro.flux2Pro(
                    prompt = prompt,
                    width = width,
                    height = height,
                    outputFormat = WiroFlux2ProOutputFormat.PNG,
                )
            val stream = session.subscribeStream(request)
            var outputUrls = emptyList<String>()

            stream.collect { update ->
                update.status?.let { status ->
                    _uiState.update {
                        it.copy(
                            generation =
                            GenerationState.Running(
                                status.apiValue,
                            ),
                        )
                    }
                }

                when (update) {
                    is WiroTaskUpdate.Snapshot -> {
                        val task = update.task
                        _uiState.update { current ->
                            current.copy(
                                taskId =
                                task.id?.rawValue
                                    ?: current.taskId,
                                taskToken =
                                task.taskToken?.rawValue
                                    ?: current.taskToken,
                            )
                        }
                        val urls =
                            task.outputs.mapNotNull {
                                it.url?.toASCIIString()
                            }
                        if (urls.isNotEmpty()) {
                            outputUrls = urls
                        }
                        if (task.status.isTerminal) {
                            if (task.isSuccessful && outputUrls.isNotEmpty()) {
                                _uiState.update {
                                    it.copy(
                                        generation =
                                        GenerationState.Succeeded(
                                            outputUrls,
                                        ),
                                    )
                                }
                            } else if (task.isSuccessful) {
                                _uiState.update {
                                    it.copy(
                                        generation =
                                        GenerationState.Failed(
                                            "Task completed without " +
                                                "image URLs.",
                                        ),
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        generation =
                                        GenerationState.Failed(
                                            task.debugOutput
                                                ?: "Generation failed " +
                                                "(${task.status.apiValue}).",
                                        ),
                                    )
                                }
                            }
                            return@collect
                        }
                    }

                    is WiroTaskUpdate.Event -> {
                        val message = update.message
                        _uiState.update { current ->
                            current.copy(
                                taskId =
                                message.id?.rawValue
                                    ?: current.taskId,
                                taskToken =
                                message.taskToken?.rawValue
                                    ?: current.taskToken,
                            )
                        }
                        val urls =
                            message.outputs.mapNotNull {
                                it.url?.toASCIIString()
                            }
                        if (urls.isNotEmpty()) {
                            outputUrls = urls
                        }
                    }

                    is WiroTaskUpdate.Binary -> {}
                }
            }

            val current = _uiState.value.generation
            if (current is GenerationState.Running) {
                if (outputUrls.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            generation =
                            GenerationState.Succeeded(
                                outputUrls,
                            ),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            generation =
                            GenerationState.Failed(
                                "Stream ended without a terminal result.",
                            ),
                        )
                    }
                }
            }
        } catch (error: CancellationException) {
            _uiState.update {
                it.copy(
                    generation = GenerationState.Failed(CANCELLED_MESSAGE),
                )
            }
            throw error
        } catch (error: Throwable) {
            _uiState.update {
                it.copy(
                    generation =
                    GenerationState.Failed(
                        error.toFriendlyMessage(),
                    ),
                )
            }
        } finally {
            closeSession()
        }
    }

    private fun syncCredentialsFromStore() {
        _uiState.update {
            it.copy(
                allowsDirectCredentials =
                credentials.allowsDirectCredentials,
                useProxy =
                !credentials.allowsDirectCredentials ||
                    credentials.useProxy,
                apiKey = credentials.apiKey,
                apiSecret = credentials.apiSecret,
                proxyUrlString = credentials.proxyUrlString,
            )
        }
    }

    private fun closeSession() {
        activeSession?.close()
        activeSession = null
    }

    public companion object {
        public val DIMENSION_CHOICES: List<Int> =
            listOf(
                512,
                768,
                1024,
                1280,
                1536,
                1792,
                2048,
            )

        public fun factory(
            credentials: CredentialsRepository,
            sessionFactory: WiroSessionFactory =
                DefaultWiroSessionFactory,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
            ): T = GenerateImageViewModel(
                credentials = credentials,
                sessionFactory = sessionFactory,
            ) as T
        }
    }
}
