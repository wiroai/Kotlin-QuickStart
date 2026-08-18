package ai.wiro.wirokit

import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal object WiroResponseEnvelope {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
            allowSpecialFloatingPointValues = false
        }

    fun decodeSuccessObject(
        body: ByteArray,
        statusCode: Int,
        retryAfter: Duration? = null,
    ): WiroJson {
        val bodyString = body.toString(Charsets.UTF_8)
        val responseBody = bodyString.takeIf { body.isNotEmpty() }

        if (body.isEmpty()) {
            if (statusCode in 200..299) {
                return emptyMap()
            }
            throw mapHttpError(
                statusCode = statusCode,
                message = "Wiro API request failed.",
                retryAfter = retryAfter,
                responseBody = responseBody,
            )
        }

        val decoded =
            try {
                WiroValue.fromJsonElement(json.parseToJsonElement(bodyString))
            } catch (_: Throwable) {
                if (statusCode in 200..299) {
                    throw WiroUnknownApiException(
                        message = bodyString,
                        statusCode = statusCode,
                        rawResponseBody = responseBody,
                    )
                }
                throw mapHttpError(
                    statusCode = statusCode,
                    message = bodyString,
                    retryAfter = retryAfter,
                    responseBody = responseBody,
                )
            }

        val objectValue =
            decoded.objectValue
                ?: throw WiroUnknownApiException(
                    message = "Wiro API returned a non-object JSON body.",
                    statusCode = statusCode,
                    rawResponseBody = bodyString,
                )

        if (statusCode in 200..299) {
            if (WiroJsonReader.boolean(objectValue, "result") == false) {
                val message =
                    extractMessage(objectValue)
                        ?: "Wiro API request failed."
                throw WiroApiResultException(
                    message = message,
                    code = extractCode(objectValue),
                    statusCode = statusCode,
                    rawResponseBody = bodyString,
                )
            }
            return objectValue
        }

        val message =
            extractMessage(objectValue)
                ?: "Wiro API request failed."
        throw mapHttpError(
            statusCode = statusCode,
            message = message,
            retryAfter = retryAfter,
            responseBody = bodyString,
        )
    }

    fun mapHttpError(
        statusCode: Int,
        message: String,
        retryAfter: Duration?,
        responseBody: String?,
    ): WiroException = when (statusCode) {
        401, 403 -> {
            WiroAuthenticationException(
                message = message,
                statusCode = statusCode,
                rawResponseBody = responseBody,
            )
        }

        400, 422 -> {
            WiroValidationException(
                message = message,
                statusCode = statusCode,
                rawResponseBody = responseBody,
            )
        }

        429 -> {
            WiroRateLimitException(
                message = message,
                statusCode = statusCode,
                retryAfter = retryAfter,
                rawResponseBody = responseBody,
            )
        }

        else -> {
            WiroUnknownApiException(
                message = message,
                statusCode = statusCode,
                rawResponseBody = responseBody,
            )
        }
    }

    fun extractMessage(objectValue: WiroJson): String? {
        val errors = WiroJsonReader.list(objectValue, "errors")
        val first = errors?.firstOrNull()?.objectValue
        val fromErrors = first?.let { WiroJsonReader.string(it, "message") }
        if (!fromErrors.isNullOrEmpty()) {
            return fromErrors
        }
        val message = WiroJsonReader.string(objectValue, "message")
        return message?.takeIf { it.isNotEmpty() }
    }

    fun extractCode(objectValue: WiroJson): String? {
        val errors = WiroJsonReader.list(objectValue, "errors")
        val first = errors?.firstOrNull()?.objectValue ?: return null
        WiroJsonReader.string(first, "code")?.let { return it }
        WiroJsonReader.integer(first, "code")?.let { return it.toString() }
        return null
    }

    fun retryAfterInterval(response: WiroHttpResponse): Duration? {
        val raw =
            response
                .header("Retry-After")
                ?.trim()
                ?: return null
        val seconds = raw.toDoubleOrNull() ?: return null
        if (seconds < 0.0 || !seconds.isFinite()) {
            return null
        }
        return seconds.seconds
    }
}
