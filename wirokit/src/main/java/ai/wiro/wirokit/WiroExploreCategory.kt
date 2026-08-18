package ai.wiro.wirokit

import java.net.URI
import java.util.Collections

/**
 * A curated group returned by the Explore API.
 */
public class WiroExploreCategory(
    public val id: String,
    public val title: String,
    models: List<WiroModel>,
    public val total: Int,
    public val url: URI? = null,
    raw: WiroJson,
) {
    public val models: List<WiroModel> =
        Collections.unmodifiableList(models.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    override fun equals(other: Any?): Boolean = other is WiroExploreCategory &&
        id == other.id &&
        title == other.title &&
        models == other.models &&
        total == other.total &&
        url == other.url &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + models.hashCode()
        result = 31 * result + total
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroExploreCategory = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroExploreCategory {
            val models =
                WiroJsonReader
                    .objects(
                        json,
                        "tools",
                        onMalformedJson,
                    ).map { WiroModel.parse(it, onMalformedJson) }

            return WiroExploreCategory(
                id = WiroJsonReader.string(json, "id") ?: "",
                title =
                WiroJsonReader.string(json, "title")
                    ?: WiroJsonReader.string(json, "name")
                    ?: "",
                models = models,
                total =
                WiroJsonReader.integer(json, "total")
                    ?: models.size,
                url = WiroJsonReader.url(json, "url"),
                raw = json,
            )
        }
    }
}
