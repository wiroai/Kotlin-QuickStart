package ai.wiro.wirokit

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Shared defaults for task tracking. */
public object WiroTracking {
    /** Timeout applied when a caller does not pass one. */
    public val DEFAULT_TIMEOUT: Duration = 600.seconds
}

/**
 * Emits task snapshots until the task reaches a terminal status.
 *
 * The returned flow is cold: nothing happens until it is collected, and each
 * collection starts its own poll loop. Snapshots arrive in poll order and the
 * terminal snapshot is emitted before the flow completes. Cancelling
 * collection stops polling immediately and propagates the cancellation.
 *
 * @throws WiroValidationException when [timeout] is not positive.
 * @throws WiroTimeoutException when the task is still running at the deadline.
 */
public fun WiroClient.watchTask(
    token: WiroTaskToken,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
): Flow<WiroTask> {
    WiroValidation.requirePositiveDuration(timeout, "timeout")
    return flow {
        pollUntilTerminal(token, timeout) { task -> emit(task) }
    }
}

/**
 * Polls until the task reaches a terminal status and returns that snapshot.
 *
 * @throws WiroValidationException when [timeout] is not positive.
 * @throws WiroTimeoutException when the task is still running at the deadline.
 */
public suspend fun WiroClient.waitForTask(
    token: WiroTaskToken,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
): WiroTask {
    WiroValidation.requirePositiveDuration(timeout, "timeout")
    return pollUntilTerminal(token, timeout) { }
}

/**
 * Streams WebSocket events for a running task.
 *
 * Connects to [WiroClient.socketUrl], sends the `task_info` handshake, and
 * emits text/binary frames until a terminal status, timeout, cancellation, or
 * unrecoverable socket failure. The session is always closed when collection
 * ends.
 *
 * @throws WiroValidationException when [timeout] is not positive.
 * @throws WiroTimeoutException when no terminal event arrives in time.
 * @throws WiroWebSocketException when the socket fails or closes early.
 */
public fun WiroClient.watchTaskSocket(
    token: WiroTaskToken,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
): Flow<WiroSocketEvent> {
    WiroValidation.requirePositiveDuration(timeout, "timeout")
    return flow {
        runSocketSession(token, timeout) { event -> emit(event) }
    }
}

/**
 * Runs a model and tracks it until it finishes.
 *
 * Exactly one billable `/Run` call is made. [onUpdate] receives every
 * update in emission order. Transport and API problems are thrown; a task
 * that finishes unsuccessfully is reported as [WiroTaskResult.Failure].
 *
 * WebSocket mode streams socket events, then fetches `/Task/Detail` for the
 * canonical terminal task. If the socket closes early without a terminal
 * event, tracking falls back to polling for the remaining timeout.
 *
 * @throws WiroValidationException when [timeout] is not positive.
 * @throws WiroUnknownApiException when the run response omits a task token.
 */
public suspend fun WiroClient.subscribe(
    modelId: WiroModelId,
    parameters: WiroJson = emptyMap(),
    callbackUrl: String? = null,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
    trackingMode: WiroTaskTrackingMode = WiroTaskTrackingMode.POLLING,
    contentSource: WiroUriContentSource? = null,
    onUpdate: (suspend (WiroTaskUpdate) -> Unit)? = null,
): WiroTaskResult {
    WiroValidation.requirePositiveDuration(timeout, "timeout")
    val token =
        startTrackedRun(
            modelId = modelId,
            parameters = parameters,
            callbackUrl = callbackUrl,
            contentSource = contentSource,
        )
    val terminal =
        when (trackingMode) {
            WiroTaskTrackingMode.POLLING -> {
                pollUntilTerminal(token, timeout) { task ->
                    onUpdate?.invoke(WiroTaskUpdate.Snapshot(task))
                }
            }

            WiroTaskTrackingMode.WEB_SOCKET -> {
                trackWithSocket(token, timeout, onUpdate)
            }
        }
    return WiroTaskResult.from(terminal)
}

/**
 * Runs a model and returns a flow of tracking updates.
 *
 * The billable `/Run` call completes before this function returns, so the
 * returned flow only tracks an already started task. Collecting the flow
 * never starts another run, and collecting it more than once re-tracks the
 * same task instead of duplicating the run.
 *
 * @throws WiroValidationException when [timeout] is not positive.
 * @throws WiroUnknownApiException when the run response omits a task token.
 */
public suspend fun WiroClient.subscribeStream(
    modelId: WiroModelId,
    parameters: WiroJson = emptyMap(),
    callbackUrl: String? = null,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
    trackingMode: WiroTaskTrackingMode = WiroTaskTrackingMode.POLLING,
    contentSource: WiroUriContentSource? = null,
): Flow<WiroTaskUpdate> {
    WiroValidation.requirePositiveDuration(timeout, "timeout")
    val token =
        startTrackedRun(
            modelId = modelId,
            parameters = parameters,
            callbackUrl = callbackUrl,
            contentSource = contentSource,
        )
    return when (trackingMode) {
        WiroTaskTrackingMode.POLLING -> {
            flow {
                pollUntilTerminal(token, timeout) { task ->
                    emit(WiroTaskUpdate.Snapshot(task))
                }
            }
        }

        WiroTaskTrackingMode.WEB_SOCKET -> {
            flow {
                trackWithSocket(token, timeout) { update ->
                    emit(update)
                }
            }
        }
    }
}

private suspend fun WiroClient.startTrackedRun(
    modelId: WiroModelId,
    parameters: WiroJson,
    callbackUrl: String?,
    contentSource: WiroUriContentSource?,
): WiroTaskToken {
    val run =
        runModel(
            modelId = modelId,
            parameters = parameters,
            callbackUrl = callbackUrl,
            contentSource = contentSource,
        )
    return run.taskToken ?: throw WiroUnknownApiException(
        message = "The model run response did not contain a task token.",
        statusCode = 200,
        rawResponseBody = null,
    )
}

private suspend fun WiroClient.trackWithSocket(
    token: WiroTaskToken,
    timeout: Duration,
    onUpdate: (suspend (WiroTaskUpdate) -> Unit)?,
): WiroTask {
    val startNanos = trackingClock.nanoTime()
    var earlyClose = false

    try {
        runSocketSession(token, timeout) { event ->
            onUpdate?.invoke(WiroTaskUpdate.from(event))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: WiroTimeoutException) {
        throw error
    } catch (_: WiroWebSocketException) {
        earlyClose = true
    }

    currentCoroutineContext().ensureActive()
    val task = getTask(token)
    if (task.status.isTerminal) {
        // Socket messages alone do not carry the canonical detail payload, so
        // both tracking modes must end on an equivalent terminal snapshot.
        onUpdate?.invoke(WiroTaskUpdate.Snapshot(task))
        return task
    }
    if (!earlyClose) {
        // Stream finished without a terminal socket event and detail is
        // still non-terminal — continue with the remaining budget.
    }

    val remainingNanos =
        timeout.inWholeNanoseconds -
            (trackingClock.nanoTime() - startNanos)
    if (remainingNanos <= 0L) {
        throw WiroTimeoutException(
            message = "Task did not finish within $timeout.",
            timeout = timeout,
        )
    }

    return pollUntilTerminal(
        token = token,
        timeout = remainingNanos.nanoseconds,
        onSnapshot = { snapshot ->
            onUpdate?.invoke(WiroTaskUpdate.Snapshot(snapshot))
        },
    )
}

private suspend fun WiroClient.runSocketSession(
    token: WiroTaskToken,
    timeout: Duration,
    onEvent: suspend (WiroSocketEvent) -> Unit,
) {
    var session: WiroSocketSession? = null
    var timedOut = false
    try {
        coroutineScope {
            session =
                trackingSocketFactory.connect(
                    url = socketUrl,
                    timeout = requestTimeout,
                )
            val active = checkNotNull(session)
            active.sendText(taskInfoHandshakeJson(token))

            val timeoutJob =
                launch {
                    try {
                        trackingDelay.sleep(timeout)
                        timedOut = true
                        active.close()
                    } catch (_: CancellationException) {
                        // Receive loop finished first.
                    }
                }

            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val frame =
                        try {
                            active.receiveFrame()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: WiroSocketClosedException) {
                            currentCoroutineContext().ensureActive()
                            if (timedOut) {
                                throw WiroTimeoutException(
                                    message =
                                    "Task socket did not finish " +
                                        "within $timeout.",
                                    timeout = timeout,
                                )
                            }
                            throw WiroWebSocketException(
                                message =
                                "The Wiro task WebSocket closed " +
                                    "before a terminal event.",
                                underlyingType = null,
                            )
                        }

                    val event =
                        decodeSocketFrame(
                            frame = frame,
                            maxTextBytes = limits.maxWebSocketTextBytes,
                            maxBinaryBytes = limits.maxWebSocketBinaryBytes,
                        )
                    onEvent(event)
                    if (event.isTerminal) {
                        active.close()
                        return@coroutineScope
                    }
                }
            } finally {
                timeoutJob.cancel()
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: WiroException) {
        throw error
    } catch (error: Throwable) {
        throw WiroWebSocketException(
            message = "The Wiro task WebSocket failed.",
            underlyingType = error::class.java.simpleName,
        )
    } finally {
        session?.close()
    }
}

private suspend fun WiroClient.pollUntilTerminal(
    token: WiroTaskToken,
    timeout: Duration,
    onSnapshot: suspend (WiroTask) -> Unit,
): WiroTask {
    val startNanos = trackingClock.nanoTime()
    val timeoutNanos = timeout.inWholeNanoseconds

    while (elapsedNanos(startNanos) < timeoutNanos) {
        currentCoroutineContext().ensureActive()
        val task = getTask(token)
        onSnapshot(task)
        if (task.status.isTerminal) {
            return task
        }

        currentCoroutineContext().ensureActive()
        val remainingNanos = timeoutNanos - elapsedNanos(startNanos)
        if (remainingNanos <= 0L) {
            break
        }
        val sleepNanos =
            minOf(
                remainingNanos,
                pollInterval.inWholeNanoseconds,
            )
        trackingDelay.sleep(sleepNanos.nanoseconds)
        currentCoroutineContext().ensureActive()
    }

    throw WiroTimeoutException(
        message = "Task did not finish within $timeout.",
        timeout = timeout,
    )
}

private fun WiroClient.elapsedNanos(startNanos: Long): Long = trackingClock.nanoTime() - startNanos

internal fun taskInfoHandshakeJson(token: WiroTaskToken): String {
    val escaped =
        token.rawValue
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    return "{\"type\":\"task_info\",\"tasktoken\":\"$escaped\"}"
}

internal fun decodeSocketFrame(
    frame: WiroSocketFrame,
    maxTextBytes: Int = WiroClientLimits.DEFAULT_MAX_WEB_SOCKET_TEXT_BYTES,
    maxBinaryBytes: Int = WiroClientLimits.DEFAULT_MAX_WEB_SOCKET_BINARY_BYTES,
): WiroSocketEvent = when (frame) {
    is WiroSocketFrame.Binary -> {
        if (frame.bytes.size > maxBinaryBytes) {
            throw WiroWebSocketException(
                message =
                "The Wiro task WebSocket returned a " +
                    "binary frame that exceeds the size limit.",
                underlyingType = null,
            )
        }
        WiroSocketEvent.Binary(frame.bytes)
    }

    is WiroSocketFrame.Text -> {
        val utf8Size = frame.text.toByteArray(Charsets.UTF_8).size
        if (utf8Size > maxTextBytes) {
            throw WiroWebSocketException(
                message =
                "The Wiro task WebSocket returned a " +
                    "text frame that exceeds the size limit.",
                underlyingType = null,
            )
        }
        val decoded =
            try {
                Json.parseToJsonElement(frame.text)
            } catch (_: Throwable) {
                throw WiroWebSocketException(
                    message =
                    "The Wiro task WebSocket returned " +
                        "invalid JSON.",
                    underlyingType = null,
                )
            }
        if (decoded !is JsonObject) {
            throw WiroWebSocketException(
                message =
                "The Wiro task WebSocket returned a " +
                    "non-object JSON payload.",
                underlyingType = null,
            )
        }
        val value = WiroValue.fromJsonElement(decoded)
        val objectValue =
            (value as? WiroValue.ObjectValue)?.value
                ?: throw WiroWebSocketException(
                    message =
                    "The Wiro task WebSocket returned a " +
                        "non-object JSON payload.",
                    underlyingType = null,
                )
        WiroSocketEvent.Message(WiroSocketMessage.parse(objectValue))
    }
}
