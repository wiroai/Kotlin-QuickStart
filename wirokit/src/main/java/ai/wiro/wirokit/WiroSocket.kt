package ai.wiro.wirokit

import java.util.Collections

/**
 * A frame received from a Wiro task WebSocket.
 */
public sealed class WiroSocketEvent {
    /** A JSON lifecycle, progress, or output event. */
    public class Message(
        public val message: WiroSocketMessage,
    ) : WiroSocketEvent()

    /** A binary frame from a realtime model. */
    public class Binary(
        bytes: ByteArray,
    ) : WiroSocketEvent() {
        public val bytes: ByteArray = bytes.copyOf()

        override fun equals(other: Any?): Boolean = other is Binary && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Whether this event ends a standard task stream. */
    public val isTerminal: Boolean
        get() =
            when (this) {
                is Message -> message.isTerminal
                is Binary -> false
            }
}

/**
 * A typed JSON event produced by WebSocket tracking.
 */
public class WiroSocketMessage(
    public val id: WiroTaskId? = null,
    public val taskToken: WiroTaskToken? = null,
    public val status: WiroTaskStatus,
    public val statusRawValue: String,
    public val result: Boolean = true,
    public val payload: WiroSocketPayload = WiroSocketPayload.Unknown(null),
    raw: WiroJson,
) {
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public val isTerminal: Boolean
        get() = status.isTerminal

    /** Plain log text when [payload] is [WiroSocketPayload.Log]. */
    public val messageText: String?
        get() = (payload as? WiroSocketPayload.Log)?.text

    /** Parsed progress when [payload] is [WiroSocketPayload.Progress]. */
    public val progress: WiroTaskProgress?
        get() = (payload as? WiroSocketPayload.Progress)?.progress

    /** Final outputs when [payload] is [WiroSocketPayload.Outputs]. */
    public val outputs: List<WiroTaskOutput>
        get() =
            (payload as? WiroSocketPayload.Outputs)?.outputs
                ?: emptyList()

    public companion object {
        public fun parse(json: WiroJson): WiroSocketMessage {
            val statusRaw = WiroJsonReader.string(json, "type") ?: ""
            val messageValue = json["message"]
            return WiroSocketMessage(
                id =
                WiroJsonReader
                    .string(json, "id")
                    ?.let(WiroTaskId::parse),
                taskToken =
                WiroJsonReader
                    .string(json, "tasktoken")
                    ?.let(WiroTaskToken::parse),
                status = WiroTaskStatus.parse(statusRaw),
                statusRawValue = statusRaw,
                result =
                WiroJsonReader.boolean(
                    json,
                    "result",
                    fallback = false,
                ) ?: false,
                payload =
                WiroSocketPayload.parse(
                    statusRawValue = statusRaw,
                    message = messageValue,
                ),
                raw = json,
            )
        }
    }
}

/**
 * Typed content carried by a [WiroSocketMessage].
 */
public sealed class WiroSocketPayload {
    /** Plain text log line. */
    public class Log(
        public val text: String,
    ) : WiroSocketPayload()

    /** Structured progress or streaming language-model chunks. */
    public class Progress(
        public val progress: WiroTaskProgress,
    ) : WiroSocketPayload()

    /** Final task outputs, typically on completion. */
    public class Outputs(
        outputs: List<WiroTaskOutput>,
    ) : WiroSocketPayload() {
        public val outputs: List<WiroTaskOutput> =
            Collections.unmodifiableList(outputs.toList())
    }

    /** Unrecognized payload preserved for forward compatibility. */
    public class Unknown(
        public val value: WiroValue?,
    ) : WiroSocketPayload()

    internal companion object {
        private val progressKeys: Set<String> =
            setOf(
                "type",
                "task",
                "percentage",
                "stepCurrent",
                "stepTotal",
                "speed",
                "speedType",
                "elapsedTime",
                "remainingTime",
                "raw",
                "thinking",
                "answer",
                "isThinking",
            )

        fun parse(
            statusRawValue: String,
            message: WiroValue?,
        ): WiroSocketPayload {
            if (statusRawValue == WiroTaskStatus.Completed.apiValue) {
                val outputs =
                    WiroJsonReader.objects(message).map {
                        WiroTaskOutput.parse(it)
                    }
                return Outputs(outputs)
            }

            if (message is WiroValue.StringValue) {
                val text = message.value
                val trimmed = text.trim()
                if (trimmed.startsWith("{")) {
                    val objectValue = WiroJsonReader.map(message) ?: emptyMap()
                    if (
                        objectValue.isNotEmpty() &&
                        objectValue.keys.any { it in progressKeys }
                    ) {
                        return Progress(WiroTaskProgress.parse(objectValue))
                    }
                }
                return Log(text)
            }

            val objectValue = WiroJsonReader.map(message)
            if (
                objectValue != null &&
                objectValue.keys.any { it in progressKeys }
            ) {
                return Progress(WiroTaskProgress.parse(objectValue))
            }

            return Unknown(message)
        }
    }
}

/**
 * Structured progress or language-model output from a socket event.
 */
public class WiroTaskProgress(
    public val type: String? = null,
    public val task: String? = null,
    public val percentage: Double? = null,
    public val currentStep: Int? = null,
    public val totalSteps: Int? = null,
    public val speed: String? = null,
    public val speedType: String? = null,
    public val elapsedTime: String? = null,
    public val remainingTime: String? = null,
    public val rawText: String? = null,
    thinking: List<String> = emptyList(),
    answers: List<String> = emptyList(),
    public val isThinking: Boolean? = null,
    raw: WiroJson,
) {
    public val thinking: List<String> =
        Collections.unmodifiableList(thinking.toList())
    public val answers: List<String> =
        Collections.unmodifiableList(answers.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public companion object {
        public fun parse(json: WiroJson): WiroTaskProgress = WiroTaskProgress(
            type = WiroJsonReader.string(json, "type"),
            task = WiroJsonReader.string(json, "task"),
            percentage = WiroJsonReader.double(json, "percentage"),
            currentStep = WiroJsonReader.integer(json, "stepCurrent"),
            totalSteps = WiroJsonReader.integer(json, "stepTotal"),
            speed = WiroJsonReader.string(json, "speed"),
            speedType = WiroJsonReader.string(json, "speedType"),
            elapsedTime = WiroJsonReader.string(json, "elapsedTime"),
            remainingTime = WiroJsonReader.string(json, "remainingTime"),
            rawText = WiroJsonReader.string(json, "raw"),
            thinking = WiroJsonReader.stringList(json, "thinking"),
            answers = WiroJsonReader.stringList(json, "answer"),
            isThinking = WiroJsonReader.boolean(json, "isThinking"),
            raw = json,
        )
    }
}
