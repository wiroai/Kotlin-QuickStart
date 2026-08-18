package ai.wiro.wirokit

/**
 * Lifecycle state of a Wiro task.
 */
public sealed class WiroTaskStatus {
    public abstract val apiValue: String

    public val isTerminal: Boolean
        get() = this is Completed || this is Cancelled

    public data object Queued : WiroTaskStatus() {
        override val apiValue: String = "task_queue"
    }

    public data object Accepted : WiroTaskStatus() {
        override val apiValue: String = "task_accept"
    }

    public data object Preprocessing : WiroTaskStatus() {
        override val apiValue: String = "task_preprocess_start"
    }

    public data object Preprocessed : WiroTaskStatus() {
        override val apiValue: String = "task_preprocess_end"
    }

    public data object Assigned : WiroTaskStatus() {
        override val apiValue: String = "task_assign"
    }

    public data object Running : WiroTaskStatus() {
        override val apiValue: String = "task_start"
    }

    public data object Output : WiroTaskStatus() {
        override val apiValue: String = "task_output"
    }

    public data object OutputComplete : WiroTaskStatus() {
        override val apiValue: String = "task_output_full"
    }

    public data object ErrorOutput : WiroTaskStatus() {
        override val apiValue: String = "task_error"
    }

    public data object ErrorOutputComplete : WiroTaskStatus() {
        override val apiValue: String = "task_error_full"
    }

    public data object ProcessEnded : WiroTaskStatus() {
        override val apiValue: String = "task_end"
    }

    public data object PostProcessing : WiroTaskStatus() {
        override val apiValue: String = "task_postprocess_start"
    }

    public data object Completed : WiroTaskStatus() {
        override val apiValue: String = "task_postprocess_end"
    }

    public data object Cancelled : WiroTaskStatus() {
        override val apiValue: String = "task_cancel"
    }

    public data object StreamReady : WiroTaskStatus() {
        override val apiValue: String = "task_stream_ready"
    }

    public data object StreamEnded : WiroTaskStatus() {
        override val apiValue: String = "task_stream_end"
    }

    public class Unknown(
        public val rawValue: String,
    ) : WiroTaskStatus() {
        override val apiValue: String = rawValue

        override fun equals(other: Any?): Boolean = other is Unknown && rawValue == other.rawValue

        override fun hashCode(): Int = rawValue.hashCode()

        override fun toString(): String = "Unknown(rawValue=$rawValue)"
    }

    public companion object {
        public fun parse(rawValue: String): WiroTaskStatus = when (rawValue) {
            "task_queue" -> Queued
            "task_accept" -> Accepted
            "task_preprocess_start" -> Preprocessing
            "task_preprocess_end" -> Preprocessed
            "task_assign" -> Assigned
            "task_start" -> Running
            "task_output" -> Output
            "task_output_full" -> OutputComplete
            "task_error" -> ErrorOutput
            "task_error_full" -> ErrorOutputComplete
            "task_end" -> ProcessEnded
            "task_postprocess_start" -> PostProcessing
            "task_postprocess_end" -> Completed
            "task_cancel" -> Cancelled
            "task_stream_ready" -> StreamReady
            "task_stream_end" -> StreamEnded
            else -> Unknown(rawValue)
        }
    }
}
