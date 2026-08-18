package ai.wiro.wirokit

/**
 * Selects how a subscription follows task progress.
 */
public enum class WiroTaskTrackingMode {
    /** Polls `/Task/Detail` using the client poll interval. */
    POLLING,

    /** Streams task events over the Wiro WebSocket, with polling fallback. */
    WEB_SOCKET,
}

/**
 * A single update emitted while a task is tracked.
 */
public sealed class WiroTaskUpdate {
    /** A task snapshot returned by `/Task/Detail`. */
    public class Snapshot(
        public val task: WiroTask,
    ) : WiroTaskUpdate()

    /** A typed WebSocket lifecycle or progress message. */
    public class Event(
        public val message: WiroSocketMessage,
    ) : WiroTaskUpdate()

    /** A binary WebSocket frame from a realtime model. */
    public class Binary(
        bytes: ByteArray,
    ) : WiroTaskUpdate() {
        public val bytes: ByteArray = bytes.copyOf()

        override fun equals(other: Any?): Boolean = other is Binary && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** The task status carried by this update, when the update reports one. */
    public val status: WiroTaskStatus?
        get() =
            when (this) {
                is Snapshot -> task.status
                is Event -> message.status
                is Binary -> null
            }

    /** True when this update reports a terminal task status. */
    public val isTerminal: Boolean
        get() = status?.isTerminal == true

    public companion object {
        /** Creates a normalized update from a socket event. */
        public fun from(socketEvent: WiroSocketEvent): WiroTaskUpdate = when (socketEvent) {
            is WiroSocketEvent.Message -> Event(socketEvent.message)
            is WiroSocketEvent.Binary -> Binary(socketEvent.bytes)
        }
    }
}
