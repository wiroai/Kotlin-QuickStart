package ai.wiro.wirokit

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Runs a typed model request.
 *
 * Unresolved file inputs are uploaded before `/Run` starts.
 */
public suspend fun WiroClient.run(
    request: WiroModelRequest,
    callbackUrl: String? = null,
    contentSource: WiroUriContentSource? = null,
): WiroRunResult = runModel(
    modelId = request.model,
    parameters = request.parameters(),
    callbackUrl = callbackUrl,
    contentSource = contentSource,
)

/**
 * Runs a typed model request and tracks it until it finishes.
 */
public suspend fun WiroClient.subscribe(
    request: WiroModelRequest,
    callbackUrl: String? = null,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
    trackingMode: WiroTaskTrackingMode = WiroTaskTrackingMode.POLLING,
    contentSource: WiroUriContentSource? = null,
    onUpdate: (suspend (WiroTaskUpdate) -> Unit)? = null,
): WiroTaskResult = subscribe(
    modelId = request.model,
    parameters = request.parameters(),
    callbackUrl = callbackUrl,
    timeout = timeout,
    trackingMode = trackingMode,
    contentSource = contentSource,
    onUpdate = onUpdate,
)

/**
 * Runs a typed model request and returns a flow of tracking updates.
 *
 * The billable `/Run` call completes before this function returns.
 */
public suspend fun WiroClient.subscribeStream(
    request: WiroModelRequest,
    callbackUrl: String? = null,
    timeout: Duration = WiroTracking.DEFAULT_TIMEOUT,
    trackingMode: WiroTaskTrackingMode = WiroTaskTrackingMode.POLLING,
    contentSource: WiroUriContentSource? = null,
): Flow<WiroTaskUpdate> = subscribeStream(
    modelId = request.model,
    parameters = request.parameters(),
    callbackUrl = callbackUrl,
    timeout = timeout,
    trackingMode = trackingMode,
    contentSource = contentSource,
)
