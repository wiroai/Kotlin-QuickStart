package ai.wiro.wirokit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private val modelSegmentPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

@Serializable(with = WiroModelIdSerializer::class)
public class WiroModelId(
    public val owner: String,
    public val project: String,
) {
    init {
        validateSegment(owner, "owner")
        validateSegment(project, "project")
    }

    public val slug: String
        get() = "$owner/$project"

    override fun equals(other: Any?): Boolean = other is WiroModelId &&
        owner == other.owner &&
        project == other.project

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + project.hashCode()
        return result
    }

    override fun toString(): String = slug

    public companion object {
        public fun parse(value: String): WiroModelId? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null

            val parts = trimmed.split("/")
            if (parts.size != 2) return null
            if (!modelSegmentPattern.matches(parts[0])) return null
            if (!modelSegmentPattern.matches(parts[1])) return null

            return WiroModelId(
                owner = parts[0],
                project = parts[1],
            )
        }

        private fun validateSegment(
            value: String,
            label: String,
        ) {
            if (!modelSegmentPattern.matches(value)) {
                throw WiroValidationException(
                    message =
                    "Invalid model $label '$value'. Expected a slug " +
                        "matching ^[A-Za-z0-9][A-Za-z0-9._-]*$.",
                    statusCode = 0,
                )
            }
        }
    }
}

@Serializable(with = WiroTaskIdSerializer::class)
public class WiroTaskId(
    rawValue: String,
) {
    public val rawValue: String = validateNonEmpty(rawValue, "task id")

    override fun equals(other: Any?): Boolean = other is WiroTaskId && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = rawValue

    public companion object {
        public fun parse(value: String): WiroTaskId? = runCatching { WiroTaskId(value) }.getOrNull()
    }
}

@Serializable(with = WiroTaskTokenSerializer::class)
public class WiroTaskToken(
    rawValue: String,
) {
    public val rawValue: String = validateNonEmpty(rawValue, "task token")

    override fun equals(other: Any?): Boolean = other is WiroTaskToken && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = "WiroTaskToken([REDACTED])"

    public companion object {
        public fun parse(value: String): WiroTaskToken? = runCatching { WiroTaskToken(value) }.getOrNull()
    }
}

private fun validateNonEmpty(
    value: String,
    label: String,
): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        throw WiroValidationException(
            "$label must be non-empty.",
            statusCode = 0,
        )
    }
    return trimmed
}

internal object WiroModelIdSerializer : KSerializer<WiroModelId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WiroModelId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: WiroModelId,
    ) {
        encoder.encodeString(value.slug)
    }

    override fun deserialize(decoder: Decoder): WiroModelId {
        val raw = decoder.decodeString()
        return WiroModelId.parse(raw)
            ?: throw WiroValidationException(
                "Invalid WiroModelId string '$raw'.",
                statusCode = 0,
            )
    }
}

internal object WiroTaskIdSerializer : KSerializer<WiroTaskId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WiroTaskId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: WiroTaskId,
    ) {
        encoder.encodeString(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): WiroTaskId = WiroTaskId(decoder.decodeString())
}

internal object WiroTaskTokenSerializer : KSerializer<WiroTaskToken> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WiroTaskToken", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: WiroTaskToken,
    ) {
        encoder.encodeString(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): WiroTaskToken = WiroTaskToken(decoder.decodeString())
}
