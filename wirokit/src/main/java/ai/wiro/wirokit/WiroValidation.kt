package ai.wiro.wirokit

import java.net.URI
import kotlin.time.Duration

internal enum class WiroUrlKind(
    val schemes: Set<String>,
) {
    HTTP(setOf("http", "https")),
    WEB_SOCKET(setOf("ws", "wss")),
}

internal object WiroValidation {
    private val headerNamePattern =
        Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    fun validateUrl(
        value: String,
        kind: WiroUrlKind,
        label: String,
        allowQuery: Boolean = false,
        allowFragment: Boolean = false,
    ): URI {
        val uri =
            runCatching { URI(value) }.getOrNull()
                ?: throw WiroValidationException(
                    "$label is not a valid URL.",
                    statusCode = 0,
                )
        val scheme =
            uri.scheme?.lowercase()
                ?: throw WiroValidationException(
                    "$label is missing a URL scheme.",
                    statusCode = 0,
                )

        if (scheme !in kind.schemes) {
            val schemes = kind.schemes.sorted().joinToString(" or ")
            throw WiroValidationException(
                "$label must use $schemes scheme.",
                statusCode = 0,
            )
        }
        if (uri.host.isNullOrEmpty()) {
            throw WiroValidationException(
                "$label must include a host.",
                statusCode = 0,
            )
        }
        if (uri.userInfo != null) {
            throw WiroValidationException(
                "$label must not contain userinfo.",
                statusCode = 0,
            )
        }
        if (!allowQuery && uri.rawQuery != null) {
            throw WiroValidationException(
                "$label must not contain a query string.",
                statusCode = 0,
            )
        }
        if (!allowFragment && uri.rawFragment != null) {
            throw WiroValidationException(
                "$label must not contain a fragment.",
                statusCode = 0,
            )
        }
        return uri
    }

    fun trimTrailingSlashes(uri: URI): URI {
        val trimmed = uri.toASCIIString().trimEnd('/')
        return URI(trimmed.ifEmpty { uri.toASCIIString() })
    }

    fun requirePositiveDuration(
        duration: Duration,
        label: String,
    ) {
        if (!duration.isFinite() || duration <= Duration.ZERO) {
            throw WiroValidationException(
                "$label must be finite and greater than zero.",
                statusCode = 0,
            )
        }
    }

    fun requireNonNegativeDuration(
        duration: Duration,
        label: String,
    ) {
        if (!duration.isFinite() || duration < Duration.ZERO) {
            throw WiroValidationException(
                "$label must be finite and must not be negative.",
                statusCode = 0,
            )
        }
    }

    fun validateHeader(
        name: String,
        value: String,
    ) {
        if (!headerNamePattern.matches(name)) {
            throw WiroValidationException(
                "Invalid HTTP header name.",
                statusCode = 0,
            )
        }
        if ('\r' in value || '\n' in value || '\u0000' in value) {
            throw WiroValidationException(
                "Invalid HTTP header value.",
                statusCode = 0,
            )
        }
    }

    fun requireResolvedJson(value: WiroValue) {
        value.toJsonElement()
    }
}
