package ai.wiro.quickstart.core.error

import ai.wiro.wirokit.WiroApiResultException
import ai.wiro.wirokit.WiroAuthenticationException
import ai.wiro.wirokit.WiroException
import ai.wiro.wirokit.WiroNetworkException
import ai.wiro.wirokit.WiroRateLimitException
import ai.wiro.wirokit.WiroSchemaValidationException
import ai.wiro.wirokit.WiroTimeoutException
import ai.wiro.wirokit.WiroUnknownApiException
import ai.wiro.wirokit.WiroValidationException
import ai.wiro.wirokit.WiroWebSocketException
import kotlin.coroutines.cancellation.CancellationException

/** User-facing copy for cancelled generations. */
public const val CANCELLED_MESSAGE: String =
    "Generation was cancelled."

/**
 * Maps SDK and unexpected errors to safe UI strings.
 *
 * Never includes credentials or raw response bodies.
 */
public fun Throwable.toFriendlyMessage(): String {
    if (this is CancellationException) {
        return CANCELLED_MESSAGE
    }
    return when (this) {
        is WiroApiResultException -> {
            "The API declined the request: $message"
        }

        is WiroAuthenticationException -> {
            "Authentication failed. Check your API key, secret, " +
                "or proxy headers."
        }

        is WiroValidationException -> {
            "Invalid request: $message"
        }

        is WiroRateLimitException -> {
            val retry = retryAfter
            if (retry != null) {
                "Rate limited. Try again in ${retry.inWholeSeconds}s."
            } else {
                "Rate limited. Please wait and try again."
            }
        }

        is WiroUnknownApiException -> {
            val code = statusCode ?: 0
            "Unexpected API response ($code): $message"
        }

        is WiroSchemaValidationException -> {
            if (messages.isEmpty()) {
                "Schema validation failed."
            } else {
                "Schema validation failed: ${messages.joinToString("; ")}"
            }
        }

        is WiroNetworkException -> {
            "Network error: $message"
        }

        is WiroWebSocketException -> {
            "WebSocket error: $message"
        }

        is WiroTimeoutException -> {
            message.orEmpty().ifEmpty { "The request timed out." }
        }

        is WiroException -> {
            message.orEmpty().ifEmpty { "Something went wrong." }
        }

        else -> {
            localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Something went wrong."
        }
    }
}
