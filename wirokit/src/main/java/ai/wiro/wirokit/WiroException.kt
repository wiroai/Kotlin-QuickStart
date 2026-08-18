package ai.wiro.wirokit

import kotlin.time.Duration

public sealed class WiroException(
    message: String,
) : Exception(message) {
    public open val statusCode: Int? = null
    public open val retryAfter: Duration? = null
    public open val rawResponseBody: String? = null

    final override fun toString(): String = "${this::class.java.name}: ${message.orEmpty()}"
}

public class WiroApiResultException(
    message: String,
    public val code: String? = null,
    override val statusCode: Int? = null,
    override val rawResponseBody: String? = null,
) : WiroException(message)

public class WiroAuthenticationException(
    message: String,
    override val statusCode: Int? = null,
    override val rawResponseBody: String? = null,
) : WiroException(message)

public class WiroValidationException(
    message: String,
    override val statusCode: Int? = null,
    override val rawResponseBody: String? = null,
) : WiroException(message)

public class WiroSchemaValidationException(
    messages: List<String>,
) : WiroException(
    messages.takeIf { it.isNotEmpty() }?.joinToString("; ")
        ?: "Schema validation failed.",
) {
    public val messages: List<String> = messages.toList()
}

public class WiroRateLimitException(
    message: String,
    override val statusCode: Int? = 429,
    override val retryAfter: Duration? = null,
    override val rawResponseBody: String? = null,
) : WiroException(message)

public class WiroUnknownApiException(
    message: String,
    override val statusCode: Int? = null,
    override val rawResponseBody: String? = null,
) : WiroException(message)

public class WiroNetworkException(
    message: String,
    public val underlyingType: String? = null,
) : WiroException(message)

public class WiroWebSocketException(
    message: String,
    public val underlyingType: String? = null,
) : WiroException(message)

public class WiroTimeoutException(
    message: String,
    public val timeout: Duration,
) : WiroException(message)
