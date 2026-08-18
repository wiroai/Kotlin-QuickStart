package ai.wiro.wirokit

import java.net.URI
import java.util.Collections

/**
 * Result of a file upload to `/File/Upload`.
 */
public class WiroUploadResult(
    public val isSuccess: Boolean,
    files: List<WiroUploadedFile>,
    errors: List<WiroApiError>,
    raw: WiroJson,
) {
    public val files: List<WiroUploadedFile> =
        Collections.unmodifiableList(files.toList())
    public val errors: List<WiroApiError> =
        Collections.unmodifiableList(errors.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    override fun equals(other: Any?): Boolean = other is WiroUploadResult &&
        isSuccess == other.isSuccess &&
        files == other.files &&
        errors == other.errors &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = isSuccess.hashCode()
        result = 31 * result + files.hashCode()
        result = 31 * result + errors.hashCode()
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroUploadResult = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroUploadResult {
            val files =
                WiroJsonReader
                    .objects(
                        json,
                        "list",
                        onMalformedJson,
                    ).map(WiroUploadedFile::parse)
            return WiroUploadResult(
                isSuccess =
                WiroJsonReader.boolean(
                    json,
                    "result",
                    fallback = false,
                ) ?: false,
                files = files,
                errors =
                WiroApiError.parseList(
                    from = json["errors"],
                    onMalformedJson = onMalformedJson,
                ),
                raw = json,
            )
        }
    }
}

/**
 * A file stored by Wiro after an upload.
 */
public class WiroUploadedFile(
    public val id: String = "",
    public val name: String? = null,
    public val contentType: String? = null,
    public val size: Int? = null,
    public val url: URI? = null,
    raw: WiroJson,
) {
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    override fun equals(other: Any?): Boolean = other is WiroUploadedFile &&
        id == other.id &&
        name == other.name &&
        contentType == other.contentType &&
        size == other.size &&
        url == other.url &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (size ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }

    override fun toString(): String = "WiroUploadedFile(id=$id, size=$size)"

    public companion object {
        public fun parse(json: WiroJson): WiroUploadedFile = WiroUploadedFile(
            id = WiroJsonReader.string(json, "id") ?: "",
            name = WiroJsonReader.string(json, "name"),
            contentType = WiroJsonReader.string(json, "contenttype"),
            size = WiroJsonReader.integer(json, "size"),
            url = WiroJsonReader.url(json, "url"),
            raw = json,
        )
    }
}
