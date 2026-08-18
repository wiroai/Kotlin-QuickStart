package ai.wiro.wirokit

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.yield
import java.net.URI
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class FakeHttpTransport(
    handlers: List<(WiroHttpRequest) -> WiroHttpResponse> = emptyList(),
) : WiroHttpTransport {
    private val queue = ArrayDeque(handlers)
    private val recorded = mutableListOf<WiroHttpRequest>()
    private val closed = AtomicBoolean(false)

    val requests: List<WiroHttpRequest>
        get() = synchronized(this) { recorded.toList() }

    val closeCount: Int
        get() = if (closed.get()) 1 else 0

    fun enqueue(handler: (WiroHttpRequest) -> WiroHttpResponse) {
        synchronized(this) {
            queue.addLast(handler)
        }
    }

    fun enqueueJson(
        statusCode: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        enqueue { _ ->
            response(statusCode, body, headers)
        }
    }

    override suspend fun perform(request: WiroHttpRequest): WiroHttpResponse {
        val handler =
            synchronized(this) {
                recorded += request
                if (queue.isEmpty()) {
                    null
                } else {
                    queue.removeFirst()
                }
            } ?: throw WiroNetworkException(
                message = "FakeHttpTransport has no queued handlers.",
                underlyingType = null,
            )
        return handler(request)
    }

    override suspend fun upload(
        request: WiroHttpRequest,
        filePath: String,
    ): WiroHttpResponse {
        val bytes = java.io.File(filePath).readBytes()
        return perform(
            WiroHttpRequest(
                method = request.method,
                url = request.url,
                headers = request.headers,
                body = bytes,
                timeout = request.timeout,
            ),
        )
    }

    override fun close() {
        closed.set(true)
    }

    companion object {
        fun response(
            statusCode: Int,
            body: String,
            headers: Map<String, String> = emptyMap(),
        ): WiroHttpResponse = WiroHttpResponse(
            statusCode = statusCode,
            headers = headers,
            body = body.toByteArray(Charsets.UTF_8),
        )
    }
}

/**
 * Virtual clock that advances only when the client sleeps.
 *
 * Keeps tracking tests free of real waiting while still exercising the
 * monotonic deadline math.
 */
internal class FakeTimeline(
    startNanos: Long = 0L,
) {
    private var nanos: Long = startNanos
    private val recorded = mutableListOf<Duration>()

    val slept: List<Duration>
        get() = recorded.toList()

    val clock: WiroMonotonicClock = WiroMonotonicClock { nanos }

    val delay: WiroDelay =
        WiroDelay { duration ->
            recorded += duration
            nanos += duration.inWholeNanoseconds
            yield()
        }
}

internal fun testClient(
    transport: FakeHttpTransport,
    apiKey: String = "test-api-key",
    apiSecret: String? = null,
    closeTransportOnClose: Boolean = false,
    retryPolicy: WiroRetryPolicy = WiroRetryPolicy.Default,
    logger: WiroLogger? = null,
    delays: MutableList<Duration>? = null,
    timeline: FakeTimeline? = null,
    pollInterval: Duration = 3.seconds,
    requestTimeout: Duration = 30.seconds,
    socketSessionFactory: WiroSocketSessionFactory? = null,
    parkLongSleeps: Boolean = false,
    limits: WiroClientLimits = WiroClientLimits.Default,
): WiroClient {
    val baseDelay =
        timeline?.delay ?: WiroDelay { duration ->
            delays?.add(duration)
        }
    val delay =
        if (parkLongSleeps) {
            WiroDelay { duration ->
                if (duration >= 30.seconds) {
                    awaitCancellation()
                } else {
                    baseDelay.sleep(duration)
                }
            }
        } else {
            baseDelay
        }
    return WiroClient.createForTests(
        apiKey = apiKey,
        apiSecret = apiSecret,
        transport = transport,
        closeTransportOnClose = closeTransportOnClose,
        pollInterval = pollInterval,
        requestTimeout = requestTimeout,
        retryPolicy = retryPolicy,
        limits = limits,
        logger = logger,
        delay = delay,
        monotonicClock = timeline?.clock ?: WiroMonotonicClock { 0L },
        socketSessionFactory =
        socketSessionFactory
            ?: WiroDefaultSocketSessionFactory,
    )
}

internal fun parkingDelay(): WiroDelay = WiroDelay {
    awaitCancellation()
}

/**
 * Scripted WebSocket session for unit tests.
 */
internal class ScriptedSocketSession : WiroSocketSession {
    private val frames =
        ArrayDeque<Result<WiroSocketFrame>>()
    private val waiters =
        ArrayDeque<Channel<Result<WiroSocketFrame>>>()
    private val sent = mutableListOf<String>()
    private var closed = false
    var closeCount: Int = 0
        private set
    var connectedUrl: URI? = null
        private set
    var connectTimeout: Duration? = null
        private set

    val sentTexts: List<String>
        get() = sent.toList()

    fun configure(
        frames: List<WiroSocketFrame>,
        closeAfter: Boolean = true,
    ) {
        this.frames.clear()
        frames.forEach { this.frames.addLast(Result.success(it)) }
        if (closeAfter) {
            this.frames.addLast(Result.failure(WiroSocketClosedException()))
        }
    }

    fun markConnected(
        url: URI,
        timeout: Duration,
    ) {
        connectedUrl = url
        connectTimeout = timeout
    }

    override suspend fun sendText(text: String) {
        ensureOpen()
        sent += text
    }

    override suspend fun receiveFrame(): WiroSocketFrame {
        ensureOpen()
        if (frames.isNotEmpty()) {
            return frames.removeFirst().getOrThrow()
        }
        val channel = Channel<Result<WiroSocketFrame>>(1)
        waiters.addLast(channel)
        return try {
            channel.receive().getOrThrow()
        } finally {
            waiters.remove(channel)
        }
    }

    override suspend fun close() {
        if (closed) {
            return
        }
        closed = true
        closeCount += 1
        while (waiters.isNotEmpty()) {
            waiters.removeFirst().trySend(
                Result.failure(WiroSocketClosedException()),
            )
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw WiroSocketClosedException()
        }
    }
}

internal class ScriptedSocketWorld {
    val session: ScriptedSocketSession = ScriptedSocketSession()

    val factory: WiroSocketSessionFactory =
        WiroSocketSessionFactory { url, timeout ->
            session.markConnected(url, timeout)
            session
        }
}

internal fun testProxyClient(
    transport: FakeHttpTransport,
    headers: Map<String, String> =
        mapOf(
            "Authorization" to "Bearer tok",
        ),
    logger: WiroLogger? = null,
): WiroClient = WiroClient.createForTests(
    apiKey = null,
    apiSecret = null,
    proxyHeaders = headers,
    authType = WiroAuthType.PROXY,
    baseUrl = "https://proxy.example.com/v1",
    transport = transport,
    logger = logger,
)
