package ai.wiro.wirokit

import java.time.Instant
import java.util.Collections
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A task running on Wiro.
 */
public class WiroTask(
    public val id: WiroTaskId? = null,
    public val taskToken: WiroTaskToken? = null,
    parameters: WiroJson = emptyMap(),
    public val status: WiroTaskStatus,
    public val statusRawValue: String,
    public val exitCode: Int? = null,
    public val debugOutput: String? = null,
    public val startTime: Instant? = null,
    public val endTime: Instant? = null,
    public val elapsed: Duration? = null,
    public val totalCost: Double? = null,
    outputs: List<WiroTaskOutput> = emptyList(),
    public val modelDescription: String? = null,
    public val modelOwner: String? = null,
    public val modelSlug: String? = null,
    raw: WiroJson,
) {
    public val parameters: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(parameters))
    public val outputs: List<WiroTaskOutput> =
        Collections.unmodifiableList(outputs.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public val isFinished: Boolean
        get() = status.isTerminal

    /**
     * A completed task succeeds only when its process exit code is `0`.
     */
    public val isSuccessful: Boolean
        get() = status is WiroTaskStatus.Completed && exitCode == 0

    override fun equals(other: Any?): Boolean = other is WiroTask &&
        id == other.id &&
        taskToken == other.taskToken &&
        parameters == other.parameters &&
        status == other.status &&
        statusRawValue == other.statusRawValue &&
        exitCode == other.exitCode &&
        debugOutput == other.debugOutput &&
        startTime == other.startTime &&
        endTime == other.endTime &&
        elapsed == other.elapsed &&
        totalCost == other.totalCost &&
        outputs == other.outputs &&
        modelDescription == other.modelDescription &&
        modelOwner == other.modelOwner &&
        modelSlug == other.modelSlug &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (taskToken?.hashCode() ?: 0)
        result = 31 * result + parameters.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + statusRawValue.hashCode()
        result = 31 * result + (exitCode ?: 0)
        result = 31 * result + (debugOutput?.hashCode() ?: 0)
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + (endTime?.hashCode() ?: 0)
        result = 31 * result + (elapsed?.hashCode() ?: 0)
        result = 31 * result + (totalCost?.hashCode() ?: 0)
        result = 31 * result + outputs.hashCode()
        result = 31 * result + (modelDescription?.hashCode() ?: 0)
        result = 31 * result + (modelOwner?.hashCode() ?: 0)
        result = 31 * result + (modelSlug?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroTask = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroTask {
            val statusRawValue = WiroJsonReader.string(json, "status") ?: ""
            val outputValue = json["outputs"] ?: json["output"]
            val outputs =
                WiroJsonReader
                    .objects(
                        outputValue,
                        onMalformedJson,
                    ).map {
                        WiroTaskOutput.parse(it, onMalformedJson)
                    }
            val idString =
                WiroJsonReader.string(json, "id")
                    ?: WiroJsonReader.string(json, "taskid")

            return WiroTask(
                id = idString?.let(WiroTaskId::parse),
                taskToken =
                WiroJsonReader
                    .string(json, "socketaccesstoken")
                    ?.let(WiroTaskToken::parse),
                parameters =
                WiroJsonReader.map(
                    json,
                    "parameters",
                    onMalformedJson,
                ) ?: emptyMap(),
                status = WiroTaskStatus.parse(statusRawValue),
                statusRawValue = statusRawValue,
                exitCode = WiroJsonReader.integer(json, "pexit"),
                debugOutput = WiroJsonReader.string(json, "debugoutput"),
                startTime = WiroJsonReader.date(json, "starttime"),
                endTime = WiroJsonReader.date(json, "endtime"),
                elapsed = durationFromSeconds(json["elapsedseconds"]),
                totalCost = WiroJsonReader.double(json, "totalcost"),
                outputs = outputs,
                modelDescription =
                WiroJsonReader.string(
                    json,
                    "modeldescription",
                ),
                modelOwner = WiroJsonReader.string(json, "modelslugowner"),
                modelSlug = WiroJsonReader.string(json, "modelslugproject"),
                raw = json,
            )
        }

        private fun durationFromSeconds(value: WiroValue?): Duration? {
            val seconds = WiroJsonReader.double(value) ?: return null
            if (!seconds.isFinite()) {
                return null
            }
            val millis = (seconds * 1000.0).roundToLong()
            return millis.milliseconds
        }
    }
}
