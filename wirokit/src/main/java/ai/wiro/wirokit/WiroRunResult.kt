package ai.wiro.wirokit

import java.util.Collections

/**
 * Result returned immediately after starting a model.
 */
public class WiroRunResult(
    public val isSuccess: Boolean,
    public val taskId: WiroTaskId?,
    public val taskToken: WiroTaskToken?,
    errors: List<WiroApiError>,
    raw: WiroJson,
) {
    public val errors: List<WiroApiError> =
        Collections.unmodifiableList(errors.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    override fun equals(other: Any?): Boolean = other is WiroRunResult &&
        isSuccess == other.isSuccess &&
        taskId == other.taskId &&
        taskToken == other.taskToken &&
        errors == other.errors &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = isSuccess.hashCode()
        result = 31 * result + (taskId?.hashCode() ?: 0)
        result = 31 * result + (taskToken?.hashCode() ?: 0)
        result = 31 * result + errors.hashCode()
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroRunResult = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroRunResult = WiroRunResult(
            isSuccess =
            WiroJsonReader.boolean(
                json,
                "result",
                fallback = false,
            ) ?: false,
            taskId =
            WiroJsonReader
                .string(json, "taskid")
                ?.let(WiroTaskId::parse),
            taskToken =
            WiroJsonReader
                .string(json, "socketaccesstoken")
                ?.let(WiroTaskToken::parse),
            errors =
            WiroApiError.parseList(
                from = json["errors"],
                onMalformedJson = onMalformedJson,
            ),
            raw = json,
        )
    }
}

/**
 * Reason a subscribed Wiro task did not succeed.
 */
public enum class WiroTaskFailureReason {
    CANCELLED,
    NON_ZERO_EXIT,
    OTHER,
}

/**
 * Terminal result of a subscribed Wiro task.
 */
public sealed class WiroTaskResult {
    public abstract val task: WiroTask

    public class Success(
        override val task: WiroTask,
    ) : WiroTaskResult() {
        override fun equals(other: Any?): Boolean = other is Success && task == other.task

        override fun hashCode(): Int = task.hashCode()
    }

    public class Failure(
        override val task: WiroTask,
        public val reason: WiroTaskFailureReason,
    ) : WiroTaskResult() {
        override fun equals(other: Any?): Boolean = other is Failure &&
            task == other.task &&
            reason == other.reason

        override fun hashCode(): Int {
            var result = task.hashCode()
            result = 31 * result + reason.hashCode()
            return result
        }
    }

    public companion object {
        public fun from(task: WiroTask): WiroTaskResult {
            if (task.isSuccessful) {
                return Success(task)
            }
            val reason =
                when {
                    task.status is WiroTaskStatus.Cancelled -> {
                        WiroTaskFailureReason.CANCELLED
                    }

                    task.status is WiroTaskStatus.Completed &&
                        task.exitCode != 0 -> {
                        WiroTaskFailureReason.NON_ZERO_EXIT
                    }

                    else -> {
                        WiroTaskFailureReason.OTHER
                    }
                }
            return Failure(task, reason)
        }
    }
}
