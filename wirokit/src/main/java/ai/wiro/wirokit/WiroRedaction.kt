package ai.wiro.wirokit

import java.net.URI

internal object WiroRedaction {
    private val sensitiveHeaders =
        setOf(
            "authorization",
            "cookie",
            "proxy-authorization",
            "set-cookie",
            "x-api-key",
            "x-nonce",
            "x-signature",
        )

    fun headers(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) ->
        if (name.lowercase() in sensitiveHeaders) {
            "[REDACTED]"
        } else {
            value
        }
    }

    fun url(uri: URI): String = URI(
        uri.scheme,
        null,
        uri.host,
        uri.port,
        uri.path,
        null,
        null,
    ).toASCIIString()

    fun throwableType(throwable: Throwable): String = throwable::class.java.simpleName.ifEmpty { "Throwable" }
}
