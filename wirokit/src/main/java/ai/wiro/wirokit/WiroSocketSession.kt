package ai.wiro.wirokit

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/** Raw WebSocket frame before JSON decoding. */
internal sealed class WiroSocketFrame {
    class Text(
        val text: String,
    ) : WiroSocketFrame()

    class Binary(
        val bytes: ByteArray,
    ) : WiroSocketFrame()
}

/** Signals that the socket session is closed. */
internal class WiroSocketClosedException : Exception("WebSocket closed")

/**
 * Injectable WebSocket session used by task tracking.
 */
internal interface WiroSocketSession {
    suspend fun sendText(text: String)

    suspend fun receiveFrame(): WiroSocketFrame

    suspend fun close()
}

/**
 * Creates a [WiroSocketSession] for a task-tracking connection.
 */
internal fun interface WiroSocketSessionFactory {
    suspend fun connect(
        url: URI,
        timeout: Duration,
    ): WiroSocketSession
}

internal object WiroSocketLimits {
    /** Default maximum accepted text frame size in bytes. */
    const val MAX_TEXT_BYTES: Int =
        WiroClientLimits.DEFAULT_MAX_WEB_SOCKET_TEXT_BYTES

    /** Default maximum accepted binary frame size in bytes. */
    const val MAX_BINARY_BYTES: Int =
        WiroClientLimits.DEFAULT_MAX_WEB_SOCKET_BINARY_BYTES
}

internal object WiroDefaultSocketSessionFactory :
    WiroSocketSessionFactory {
    override suspend fun connect(
        url: URI,
        timeout: Duration,
    ): WiroSocketSession {
        val timeoutMs =
            timeout.inWholeMilliseconds
                .coerceAtLeast(1L)
        val okHttp =
            OkHttpClient
                .Builder()
                .retryOnConnectionFailure(false)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        val client =
            HttpClient(OkHttp) {
                engine { preconfigured = okHttp }
                install(WebSockets)
            }
        return try {
            val session =
                client.webSocketSession {
                    url(url.toASCIIString())
                }
            KtorWebSocketSession(
                httpClient = client,
                ownsHttpClient = true,
                okHttpClient = okHttp,
                ownsOkHttpClient = true,
                session = session,
            )
        } catch (error: CancellationException) {
            client.close()
            okHttp.dispatcher.executorService.shutdown()
            okHttp.connectionPool.evictAll()
            throw error
        } catch (error: Throwable) {
            client.close()
            okHttp.dispatcher.executorService.shutdown()
            okHttp.connectionPool.evictAll()
            throw WiroWebSocketException(
                message = "The Wiro task WebSocket failed.",
                underlyingType = error::class.java.simpleName,
            )
        }
    }
}

private class KtorWebSocketSession(
    private val httpClient: HttpClient,
    private val ownsHttpClient: Boolean,
    private val okHttpClient: OkHttpClient?,
    private val ownsOkHttpClient: Boolean,
    private val session: DefaultClientWebSocketSession,
) : WiroSocketSession {
    private val closed = AtomicBoolean(false)

    override suspend fun sendText(text: String) {
        ensureOpen()
        try {
            session.send(Frame.Text(text))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw WiroWebSocketException(
                message = "Failed to send a WebSocket frame.",
                underlyingType = error::class.java.simpleName,
            )
        }
    }

    override suspend fun receiveFrame(): WiroSocketFrame {
        ensureOpen()
        while (true) {
            val frame =
                try {
                    session.incoming.receive()
                } catch (_: ClosedReceiveChannelException) {
                    throw WiroSocketClosedException()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    throw WiroSocketClosedException()
                }

            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    if (text.toByteArray(Charsets.UTF_8).size >
                        WiroSocketLimits.MAX_TEXT_BYTES
                    ) {
                        throw WiroWebSocketException(
                            message =
                            "The Wiro task WebSocket " +
                                "returned a text frame that exceeds " +
                                "the size limit.",
                            underlyingType = null,
                        )
                    }
                    return WiroSocketFrame.Text(text)
                }

                is Frame.Binary -> {
                    val bytes = frame.readBytes()
                    if (bytes.size > WiroSocketLimits.MAX_BINARY_BYTES) {
                        throw WiroWebSocketException(
                            message =
                            "The Wiro task WebSocket " +
                                "returned a binary frame that " +
                                "exceeds the size limit.",
                            underlyingType = null,
                        )
                    }
                    return WiroSocketFrame.Binary(bytes)
                }

                is Frame.Close -> {
                    throw WiroSocketClosedException()
                }

                else -> {
                    // Ignore ping/pong/control frames.
                }
            }
        }
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            session.close(
                CloseReason(
                    CloseReason.Codes.GOING_AWAY,
                    "",
                ),
            )
        } catch (_: Throwable) {
            // Best-effort close.
        }
        if (ownsHttpClient) {
            httpClient.close()
        }
        if (ownsOkHttpClient) {
            okHttpClient?.dispatcher?.executorService?.shutdown()
            okHttpClient?.connectionPool?.evictAll()
        }
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw WiroSocketClosedException()
        }
    }
}
