@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ai.wiro.wirokit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.util.Collections

public typealias WiroJson = Map<String, WiroValue>

@Serializable(with = WiroValueSerializer::class)
public sealed interface WiroValue {
    public val stringValue: String?
        get() = (this as? StringValue)?.value

    public val intValue: Int?
        get() {
            val raw =
                when (this) {
                    is NumberValue -> rawValue
                    is StringValue -> value.trim()
                    else -> return null
                }
            return runCatching { raw.toBigDecimal().intValueExact() }
                .getOrNull()
        }

    public val doubleValue: Double?
        get() {
            val value =
                when (this) {
                    is NumberValue -> rawValue.toDoubleOrNull()
                    is StringValue -> value.trim().toDoubleOrNull()
                    else -> null
                }
            return value?.takeIf(Double::isFinite)
        }

    public val booleanValue: Boolean?
        get() = (this as? BooleanValue)?.value

    public val objectValue: WiroJson?
        get() = (this as? ObjectValue)?.value

    public val arrayValue: List<WiroValue>?
        get() = (this as? ArrayValue)?.value

    public val fileInputValue: WiroFileInput?
        get() = (this as? FileInputValue)?.value

    public val isNull: Boolean
        get() = this === NullValue

    public fun toJsonElement(): JsonElement

    public data class StringValue(
        public val value: String,
    ) : WiroValue {
        override fun toJsonElement(): JsonElement = JsonPrimitive(value)
    }

    public class NumberValue(
        public val rawValue: String,
    ) : WiroValue {
        init {
            requireValidNumber(rawValue)
        }

        public val decimalValue: BigDecimal
            get() = rawValue.toBigDecimal()

        override fun toJsonElement(): JsonElement = JsonUnquotedLiteral(rawValue)

        override fun equals(other: Any?): Boolean = other is NumberValue && rawValue == other.rawValue

        override fun hashCode(): Int = rawValue.hashCode()

        override fun toString(): String = rawValue

        private companion object {
            private val numberPattern =
                Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

            private fun requireValidNumber(rawValue: String) {
                if (!numberPattern.matches(rawValue)) {
                    throw WiroValidationException(
                        "Invalid JSON number lexeme.",
                        statusCode = 0,
                    )
                }
                if (runCatching { rawValue.toBigDecimal() }.isFailure) {
                    throw WiroValidationException(
                        "Invalid JSON number lexeme.",
                        statusCode = 0,
                    )
                }
            }
        }
    }

    public data class BooleanValue(
        public val value: Boolean,
    ) : WiroValue {
        override fun toJsonElement(): JsonElement = JsonPrimitive(value)
    }

    public class ObjectValue(
        value: WiroJson,
    ) : WiroValue {
        public val value: WiroJson =
            Collections.unmodifiableMap(LinkedHashMap(value))

        override fun toJsonElement(): JsonElement = JsonObject(
            value.mapValues { (_, nested) ->
                nested.toJsonElement()
            },
        )

        override fun equals(other: Any?): Boolean = other is ObjectValue && value == other.value

        override fun hashCode(): Int = value.hashCode()
    }

    public class ArrayValue(
        value: List<WiroValue>,
    ) : WiroValue {
        public val value: List<WiroValue> =
            Collections.unmodifiableList(value.toList())

        override fun toJsonElement(): JsonElement = JsonArray(value.map(WiroValue::toJsonElement))

        override fun equals(other: Any?): Boolean = other is ArrayValue && value == other.value

        override fun hashCode(): Int = value.hashCode()
    }

    public data object NullValue : WiroValue {
        override fun toJsonElement(): JsonElement = JsonNull
    }

    public class FileInputValue(
        public val value: WiroFileInput,
    ) : WiroValue {
        override fun toJsonElement(): JsonElement = throw WiroValidationException(
            "Cannot serialize an unresolved WiroFileInput; " +
                "resolve file inputs before encoding.",
            statusCode = 0,
        )

        override fun equals(other: Any?): Boolean = other is FileInputValue && value == other.value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "WiroValue.FileInputValue([REDACTED])"
    }

    public companion object {
        public fun number(value: Number): NumberValue = NumberValue(value.toString())

        public fun fromJsonElement(element: JsonElement): WiroValue = when (element) {
            JsonNull -> {
                NullValue
            }

            is JsonObject -> {
                ObjectValue(
                    element.mapValues { (_, nested) ->
                        fromJsonElement(nested)
                    },
                )
            }

            is JsonArray -> {
                ArrayValue(element.map(::fromJsonElement))
            }

            is JsonPrimitive -> {
                when {
                    element.isString -> {
                        StringValue(element.content)
                    }

                    element.booleanOrNull != null -> {
                        BooleanValue(element.booleanOrNull == true)
                    }

                    else -> {
                        NumberValue(element.content)
                    }
                }
            }
        }
    }
}

internal object WiroValueSerializer : KSerializer<WiroValue> {
    override val descriptor: SerialDescriptor =
        JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: WiroValue,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw WiroValidationException(
                    "WiroValue supports JSON serialization only.",
                    statusCode = 0,
                )
        jsonEncoder.encodeJsonElement(value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): WiroValue {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw WiroValidationException(
                    "WiroValue supports JSON serialization only.",
                    statusCode = 0,
                )
        return WiroValue.fromJsonElement(jsonDecoder.decodeJsonElement())
    }
}
