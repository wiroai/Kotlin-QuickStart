package ai.wiro.wirokit

import java.io.Closeable
import java.util.Collections
import java.util.Locale
import kotlin.time.Duration

/**
 * Minimal HTTP transport used by [WiroClient].
 *
 * Production code uses [WiroKtorHttpTransport]. Tests inject a fake or
 * MockEngine-backed transport so unit tests never hit the network.
 */
public interface WiroHttpTransport : Closeable {
    public suspend fun perform(request: WiroHttpRequest): WiroHttpResponse

    public suspend fun upload(
        request: WiroHttpRequest,
        filePath: String,
    ): WiroHttpResponse = throw WiroValidationException(
        "Upload is not supported by this transport.",
        statusCode = 0,
    )

    override fun close() {
        // Default: no resources to release.
    }
}

public class WiroHttpRequest(
    public val method: String,
    public val url: String,
    headers: Map<String, String>,
    body: ByteArray? = null,
    public val timeout: Duration,
) {
    public val headers: Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(headers))

    private val storedBody: ByteArray? = body?.copyOf()

    public val body: ByteArray?
        get() = storedBody?.copyOf()
}

public class WiroHttpResponse(
    public val statusCode: Int,
    headers: Map<String, String>,
    body: ByteArray,
) {
    private val storedHeaders: Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(headers))
    private val storedBody: ByteArray = body.copyOf()

    public val headers: Map<String, String>
        get() = storedHeaders

    public val body: ByteArray
        get() = storedBody.copyOf()

    public fun header(name: String): String? {
        val target = name.lowercase(Locale.ROOT)
        return storedHeaders.entries
            .firstOrNull { it.key.lowercase(Locale.ROOT) == target }
            ?.value
    }
}
