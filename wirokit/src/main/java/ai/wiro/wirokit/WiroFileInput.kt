package ai.wiro.wirokit

import android.net.Uri
import java.net.URI

public sealed interface WiroFileInput {
    public val wireValue: String?

    public class Url(
        public val uri: URI,
    ) : WiroFileInput {
        init {
            WiroValidation.validateUrl(
                value = uri.toASCIIString(),
                kind = WiroUrlKind.HTTP,
                label = "file URL",
                allowQuery = true,
            )
        }

        override val wireValue: String = uri.toASCIIString()

        override fun equals(other: Any?): Boolean = other is Url && uri == other.uri

        override fun hashCode(): Int = uri.hashCode()

        override fun toString(): String = "WiroFileInput.Url([REDACTED])"
    }

    public class Bytes(
        bytes: ByteArray,
        public val fileName: String,
        public val mediaType: String? = null,
    ) : WiroFileInput {
        private val storedBytes: ByteArray = bytes.copyOf()

        public val bytes: ByteArray
            get() = storedBytes.copyOf()

        override val wireValue: String? = null

        override fun equals(other: Any?): Boolean = other is Bytes &&
            storedBytes.contentEquals(other.storedBytes) &&
            fileName == other.fileName &&
            mediaType == other.mediaType

        override fun hashCode(): Int {
            var result = storedBytes.contentHashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + mediaType.hashCode()
            return result
        }

        override fun toString(): String = "WiroFileInput.Bytes(size=${storedBytes.size})"
    }

    public class ContentUri(
        public val uri: Uri,
        public val fileName: String? = null,
        public val mediaType: String? = null,
        public val sizeBytes: Long? = null,
    ) : WiroFileInput {
        override val wireValue: String? = null

        override fun equals(other: Any?): Boolean = other is ContentUri &&
            uri == other.uri &&
            fileName == other.fileName &&
            mediaType == other.mediaType &&
            sizeBytes == other.sizeBytes

        override fun hashCode(): Int {
            var result = uri.hashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + mediaType.hashCode()
            result = 31 * result + sizeBytes.hashCode()
            return result
        }

        override fun toString(): String = "WiroFileInput.ContentUri([REDACTED])"
    }
}
