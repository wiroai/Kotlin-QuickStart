package ai.wiro.wirokit

/**
 * Configurable size limits for REST bodies, WebSocket frames, and in-memory
 * uploads.
 *
 * Streaming Content URI uploads are not bounded by
 * [maxInMemoryUploadBytes]; that limit applies only to fully buffered
 * `ByteArray` uploads.
 */
public class WiroClientLimits(
    public val maxRestBodyBytes: Int = DEFAULT_MAX_REST_BODY_BYTES,
    public val maxWebSocketTextBytes: Int = DEFAULT_MAX_WEB_SOCKET_TEXT_BYTES,
    public val maxWebSocketBinaryBytes: Int =
        DEFAULT_MAX_WEB_SOCKET_BINARY_BYTES,
    public val maxInMemoryUploadBytes: Int =
        DEFAULT_MAX_IN_MEMORY_UPLOAD_BYTES,
) {
    init {
        requirePositive(maxRestBodyBytes, "maxRestBodyBytes")
        requirePositive(maxWebSocketTextBytes, "maxWebSocketTextBytes")
        requirePositive(maxWebSocketBinaryBytes, "maxWebSocketBinaryBytes")
        requirePositive(maxInMemoryUploadBytes, "maxInMemoryUploadBytes")
    }

    override fun equals(other: Any?): Boolean = other is WiroClientLimits &&
        maxRestBodyBytes == other.maxRestBodyBytes &&
        maxWebSocketTextBytes == other.maxWebSocketTextBytes &&
        maxWebSocketBinaryBytes == other.maxWebSocketBinaryBytes &&
        maxInMemoryUploadBytes == other.maxInMemoryUploadBytes

    override fun hashCode(): Int {
        var result = maxRestBodyBytes
        result = 31 * result + maxWebSocketTextBytes
        result = 31 * result + maxWebSocketBinaryBytes
        result = 31 * result + maxInMemoryUploadBytes
        return result
    }

    public companion object {
        public const val DEFAULT_MAX_REST_BODY_BYTES: Int = 16 * 1024 * 1024
        public const val DEFAULT_MAX_WEB_SOCKET_TEXT_BYTES: Int =
            8 * 1024 * 1024
        public const val DEFAULT_MAX_WEB_SOCKET_BINARY_BYTES: Int =
            8 * 1024 * 1024
        public const val DEFAULT_MAX_IN_MEMORY_UPLOAD_BYTES: Int =
            16 * 1024 * 1024

        public val Default: WiroClientLimits = WiroClientLimits()

        private fun requirePositive(
            value: Int,
            label: String,
        ) {
            if (value <= 0) {
                throw WiroValidationException(
                    "$label must be greater than zero.",
                    statusCode = 0,
                )
            }
        }
    }
}
