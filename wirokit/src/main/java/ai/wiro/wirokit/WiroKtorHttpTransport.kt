package ai.wiro.wirokit

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Default [WiroHttpTransport] backed by Ktor Client with the OkHttp engine.
 */
public class WiroKtorHttpTransport internal constructor(
    private val httpClient: HttpClient,
    private val ownsHttpClient: Boolean,
    private val ownsOkHttpClient: Boolean,
    private val okHttpClient: OkHttpClient?,
) : WiroHttpTransport {
    public constructor(
        okHttpClient: OkHttpClient? = null,
        closeOkHttpClientOnClose: Boolean = false,
    ) : this(
        httpClient = createHttpClient(okHttpClient),
        ownsHttpClient = true,
        ownsOkHttpClient = closeOkHttpClientOnClose && okHttpClient != null,
        okHttpClient = okHttpClient,
    )

    override suspend fun perform(request: WiroHttpRequest): WiroHttpResponse = execute(request)

    override suspend fun upload(
        request: WiroHttpRequest,
        filePath: String,
    ): WiroHttpResponse {
        val bytes = java.io.File(filePath).readBytes()
        return execute(
            WiroHttpRequest(
                method = request.method,
                url = request.url,
                headers = request.headers,
                body = bytes,
                timeout = request.timeout,
            ),
        )
    }

    private suspend fun execute(request: WiroHttpRequest): WiroHttpResponse {
        try {
            val response =
                httpClient.request {
                    method = HttpMethod.parse(request.method.uppercase())
                    url.takeFrom(request.url)
                    request.headers.forEach { (name, value) ->
                        header(name, value)
                    }
                    val body = request.body
                    if (body != null) {
                        setBody(body)
                    }
                    timeout {
                        requestTimeoutMillis =
                            request.timeout.inWholeMilliseconds
                                .coerceAtLeast(1L)
                        connectTimeoutMillis =
                            request.timeout.inWholeMilliseconds
                                .coerceAtLeast(1L)
                        socketTimeoutMillis =
                            request.timeout.inWholeMilliseconds
                                .coerceAtLeast(1L)
                    }
                }
            val headers = LinkedHashMap<String, String>()
            response.headers.entries().forEach { (name, values) ->
                headers[name] = values.joinToString(", ")
            }
            return WiroHttpResponse(
                statusCode = response.status.value,
                headers = headers,
                body = response.bodyAsBytes(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            throw mapTransportError(error)
        }
    }

    override fun close() {
        if (ownsHttpClient) {
            httpClient.close()
        }
        if (ownsOkHttpClient) {
            okHttpClient?.dispatcher?.executorService?.shutdown()
            okHttpClient?.connectionPool?.evictAll()
        }
    }

    public companion object {
        internal fun mapTransportError(error: Throwable): WiroException {
            if (error is WiroException) {
                return error
            }
            return WiroNetworkException(
                message = "The network request failed.",
                underlyingType = WiroRedaction.throwableType(error),
            )
        }

        private fun createHttpClient(okHttpClient: OkHttpClient?): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                if (okHttpClient != null) {
                    preconfigured = okHttpClient
                } else {
                    config {
                        retryOnConnectionFailure(false)
                        callTimeout(0, TimeUnit.MILLISECONDS)
                    }
                }
            }
            install(HttpTimeout)
        }
    }
}

internal fun createOwnedKtorTransport(
    okHttpClient: OkHttpClient? = null,
    closeOkHttpClientOnClose: Boolean = false,
): WiroKtorHttpTransport = WiroKtorHttpTransport(
    okHttpClient = okHttpClient,
    closeOkHttpClientOnClose = closeOkHttpClientOnClose,
)
