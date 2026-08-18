package ai.wiro.wirokit

import kotlin.time.Duration

public enum class WiroLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

public data class WiroLogEvent(
    public val level: WiroLogLevel,
    public val message: String,
    public val method: String? = null,
    public val url: String? = null,
    public val statusCode: Int? = null,
    public val duration: Duration? = null,
    public val retryCount: Int? = null,
    public val error: String? = null,
)

public fun interface WiroLogger {
    public fun log(event: WiroLogEvent)
}
