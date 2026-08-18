package ai.wiro.wirokit

import kotlinx.serialization.json.Json
import java.net.URI
import java.time.Instant
import kotlin.math.abs

/**
 * Lenient extraction helpers for server-provided JSON values.
 */
internal object WiroJsonReader {
    fun interface MalformedJsonHandler {
        fun onMalformed(raw: String)
    }

    private val nestedJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

    fun string(value: WiroValue?): String? = when (value) {
        is WiroValue.StringValue -> {
            value.value
        }

        is WiroValue.NumberValue -> {
            val intExact = value.intValue
            if (intExact != null) {
                intExact.toString()
            } else {
                value.rawValue
            }
        }

        is WiroValue.BooleanValue -> {
            if (value.value) "true" else "false"
        }

        else -> {
            null
        }
    }

    fun string(
        objectValue: WiroJson,
        key: String,
    ): String? = string(objectValue[key])

    fun integer(value: WiroValue?): Int? = value?.intValue

    fun integer(
        objectValue: WiroJson,
        key: String,
    ): Int? = integer(objectValue[key])

    fun double(value: WiroValue?): Double? = value?.doubleValue

    fun double(
        objectValue: WiroJson,
        key: String,
    ): Double? = double(objectValue[key])

    fun boolean(
        value: WiroValue?,
        fallback: Boolean? = null,
    ): Boolean? = when (value) {
        is WiroValue.BooleanValue -> {
            value.value
        }

        is WiroValue.StringValue -> {
            when (
                value.value.trim().lowercase()
            ) {
                "true", "1" -> true
                "false", "0" -> false
                else -> fallback
            }
        }

        is WiroValue.NumberValue -> {
            when (value.rawValue) {
                "1" -> true
                "0" -> false
                else -> fallback
            }
        }

        null -> {
            fallback
        }

        else -> {
            fallback
        }
    }

    fun boolean(
        objectValue: WiroJson,
        key: String,
        fallback: Boolean? = null,
    ): Boolean? = boolean(objectValue[key], fallback)

    fun list(value: WiroValue?): List<WiroValue>? = value?.arrayValue

    fun list(
        objectValue: WiroJson,
        key: String,
    ): List<WiroValue>? = list(objectValue[key])

    fun values(value: WiroValue?): List<WiroValue> = value?.arrayValue.orEmpty()

    fun values(
        objectValue: WiroJson,
        key: String,
    ): List<WiroValue> = values(objectValue[key])

    fun stringList(value: WiroValue?): List<String> = values(value).mapNotNull(::string)

    fun stringList(
        objectValue: WiroJson,
        key: String,
    ): List<String> = stringList(objectValue[key])

    fun map(
        value: WiroValue?,
        onMalformedJson: MalformedJsonHandler? = null,
    ): WiroJson? = when (value) {
        is WiroValue.ObjectValue -> {
            value.value
        }

        is WiroValue.StringValue -> {
            decodeNestedObject(
                value.value,
                onMalformedJson,
            )
        }

        else -> {
            null
        }
    }

    fun map(
        objectValue: WiroJson,
        key: String,
        onMalformedJson: MalformedJsonHandler? = null,
    ): WiroJson? = map(objectValue[key], onMalformedJson)

    fun objects(
        value: WiroValue?,
        onMalformedJson: MalformedJsonHandler? = null,
    ): List<WiroJson> = values(value).mapNotNull { element ->
        val objectValue = map(element, onMalformedJson)
        if (objectValue.isNullOrEmpty()) {
            null
        } else {
            objectValue
        }
    }

    fun objects(
        objectValue: WiroJson,
        key: String,
        onMalformedJson: MalformedJsonHandler? = null,
    ): List<WiroJson> = objects(objectValue[key], onMalformedJson)

    fun url(value: WiroValue?): URI? {
        val raw = (value as? WiroValue.StringValue)?.value ?: return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return runCatching { URI(trimmed) }.getOrNull()
    }

    fun url(
        objectValue: WiroJson,
        key: String,
    ): URI? = url(objectValue[key])

    fun date(value: WiroValue?): Instant? {
        double(value)?.let { return dateFromTimestamp(it) }
        val raw = string(value)?.trim() ?: return null
        val number = raw.toDoubleOrNull() ?: return null
        return dateFromTimestamp(number)
    }

    fun date(
        objectValue: WiroJson,
        key: String,
    ): Instant? = date(objectValue[key])

    private fun decodeNestedObject(
        string: String,
        onMalformedJson: MalformedJsonHandler?,
    ): WiroJson {
        val trimmed = string.trim()
        if (trimmed.isEmpty()) {
            onMalformedJson?.onMalformed(string)
            return emptyMap()
        }
        return try {
            val decoded =
                WiroValue.fromJsonElement(
                    nestedJson.parseToJsonElement(trimmed),
                )
            decoded.objectValue ?: run {
                onMalformedJson?.onMalformed(string)
                emptyMap()
            }
        } catch (_: Throwable) {
            onMalformedJson?.onMalformed(string)
            emptyMap()
        }
    }

    private fun dateFromTimestamp(number: Double): Instant? {
        if (!number.isFinite()) {
            return null
        }
        val epochSeconds =
            if (abs(number) >= 1_000_000_000_000.0) {
                number / 1000.0
            } else {
                number
            }
        val seconds = epochSeconds.toLong()
        val nanos = ((epochSeconds - seconds) * 1_000_000_000.0).toLong()
        return Instant.ofEpochSecond(seconds, nanos)
    }
}
