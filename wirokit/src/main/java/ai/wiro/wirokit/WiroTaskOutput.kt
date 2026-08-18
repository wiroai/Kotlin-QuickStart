package ai.wiro.wirokit

import java.net.URI
import java.util.Collections

/**
 * Structured output returned by text and language models.
 */
public class WiroTaskOutputContent(
    public val prompt: String? = null,
    public val rawText: String? = null,
    thinking: List<String> = emptyList(),
    answers: List<String> = emptyList(),
) {
    public val thinking: List<String> =
        Collections.unmodifiableList(thinking.toList())
    public val answers: List<String> =
        Collections.unmodifiableList(answers.toList())

    override fun equals(other: Any?): Boolean = other is WiroTaskOutputContent &&
        prompt == other.prompt &&
        rawText == other.rawText &&
        thinking == other.thinking &&
        answers == other.answers

    override fun hashCode(): Int {
        var result = prompt?.hashCode() ?: 0
        result = 31 * result + (rawText?.hashCode() ?: 0)
        result = 31 * result + thinking.hashCode()
        result = 31 * result + answers.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroTaskOutputContent = WiroTaskOutputContent(
            prompt = WiroJsonReader.string(json, "prompt"),
            rawText = WiroJsonReader.string(json, "raw"),
            thinking = WiroJsonReader.stringList(json, "thinking"),
            answers = WiroJsonReader.stringList(json, "answer"),
        )
    }
}

/**
 * A file or structured value produced by a Wiro task.
 */
public class WiroTaskOutput(
    public val name: String? = null,
    public val contentType: String,
    public val size: Int? = null,
    public val url: URI? = null,
    public val content: WiroTaskOutputContent? = null,
    raw: WiroJson,
) {
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public val isImage: Boolean
        get() = contentType.lowercase().startsWith("image/")

    public val isVideo: Boolean
        get() = contentType.lowercase().startsWith("video/")

    public val isAudio: Boolean
        get() = contentType.lowercase().startsWith("audio/")

    public val isText: Boolean
        get() {
            val normalized = contentType.lowercase()
            return normalized.startsWith("text/") ||
                normalized == "raw" ||
                normalized == "application/json"
        }

    override fun equals(other: Any?): Boolean = other is WiroTaskOutput &&
        name == other.name &&
        contentType == other.contentType &&
        size == other.size &&
        url == other.url &&
        content == other.content &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + contentType.hashCode()
        result = 31 * result + (size ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroTaskOutput = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroTaskOutput {
            val contentJson =
                WiroJsonReader.map(
                    json,
                    "content",
                    onMalformedJson,
                )
            return WiroTaskOutput(
                name = WiroJsonReader.string(json, "name"),
                contentType =
                WiroJsonReader.string(json, "contenttype")
                    ?: "",
                size = WiroJsonReader.integer(json, "size"),
                url = WiroJsonReader.url(json, "url"),
                content =
                contentJson
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(WiroTaskOutputContent::parse),
                raw = json,
            )
        }
    }
}
