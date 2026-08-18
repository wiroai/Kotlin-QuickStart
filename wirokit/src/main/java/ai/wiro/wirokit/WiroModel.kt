package ai.wiro.wirokit

import java.net.URI
import java.time.Instant
import java.util.Collections

/**
 * Aggregate execution statistics for a model.
 */
public class WiroModelTaskStats(
    public val runCount: Int,
    public val successCount: Int,
    public val errorCount: Int,
    public val lastRunTime: Instant?,
) {
    override fun equals(other: Any?): Boolean = other is WiroModelTaskStats &&
        runCount == other.runCount &&
        successCount == other.successCount &&
        errorCount == other.errorCount &&
        lastRunTime == other.lastRunTime

    override fun hashCode(): Int {
        var result = runCount
        result = 31 * result + successCount
        result = 31 * result + errorCount
        result = 31 * result + (lastRunTime?.hashCode() ?: 0)
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroModelTaskStats = WiroModelTaskStats(
            runCount = WiroJsonReader.integer(json, "runcount") ?: 0,
            successCount =
            WiroJsonReader.integer(json, "successcount")
                ?: 0,
            errorCount = WiroJsonReader.integer(json, "errorcount") ?: 0,
            lastRunTime = WiroJsonReader.date(json, "lastruntime"),
        )
    }
}

/**
 * A model available through Wiro.
 */
public class WiroModel(
    public val id: String,
    public val owner: String,
    public val slug: String,
    public val title: String? = null,
    public val description: String? = null,
    public val seoDescription: String? = null,
    public val imageUrl: URI? = null,
    categories: List<String> = emptyList(),
    tags: List<String> = emptyList(),
    samples: List<String> = emptyList(),
    public val computingTime: String? = null,
    public val approximateCost: String? = null,
    public val dynamicPrice: String? = null,
    public val cps: String? = null,
    public val taskStats: WiroModelTaskStats? = null,
    raw: WiroJson,
) {
    public val categories: List<String> =
        Collections.unmodifiableList(categories.toList())
    public val tags: List<String> =
        Collections.unmodifiableList(tags.toList())
    public val samples: List<String> =
        Collections.unmodifiableList(samples.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public val modelId: WiroModelId?
        get() = WiroModelId.parse("$owner/$slug")

    override fun equals(other: Any?): Boolean = other is WiroModel &&
        id == other.id &&
        owner == other.owner &&
        slug == other.slug &&
        title == other.title &&
        description == other.description &&
        seoDescription == other.seoDescription &&
        imageUrl == other.imageUrl &&
        categories == other.categories &&
        tags == other.tags &&
        samples == other.samples &&
        computingTime == other.computingTime &&
        approximateCost == other.approximateCost &&
        dynamicPrice == other.dynamicPrice &&
        cps == other.cps &&
        taskStats == other.taskStats &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + slug.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (seoDescription?.hashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + categories.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + samples.hashCode()
        result = 31 * result + (computingTime?.hashCode() ?: 0)
        result = 31 * result + (approximateCost?.hashCode() ?: 0)
        result = 31 * result + (dynamicPrice?.hashCode() ?: 0)
        result = 31 * result + (cps?.hashCode() ?: 0)
        result = 31 * result + (taskStats?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroModel = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroModel {
            val taskStatsJson =
                WiroJsonReader.map(
                    json,
                    "taskstat",
                    onMalformedJson,
                )
            return WiroModel(
                id = WiroJsonReader.string(json, "id") ?: "",
                owner =
                WiroJsonReader.string(json, "cleanslugowner")
                    ?: WiroJsonReader.string(json, "slugowner")
                    ?: "",
                slug =
                WiroJsonReader.string(json, "cleanslugproject")
                    ?: WiroJsonReader.string(json, "slugproject")
                    ?: "",
                title = WiroJsonReader.string(json, "title"),
                description = WiroJsonReader.string(json, "description"),
                seoDescription =
                WiroJsonReader.string(
                    json,
                    "seodescription",
                ),
                imageUrl = WiroJsonReader.url(json, "image"),
                categories = WiroJsonReader.stringList(json, "categories"),
                tags = WiroJsonReader.stringList(json, "tags"),
                samples = WiroJsonReader.stringList(json, "samples"),
                computingTime = WiroJsonReader.string(json, "computingtime"),
                approximateCost =
                WiroJsonReader.string(
                    json,
                    "approximatelycost",
                ),
                dynamicPrice = WiroJsonReader.string(json, "dynamicprice"),
                cps = WiroJsonReader.string(json, "cps"),
                taskStats = taskStatsJson?.let(WiroModelTaskStats::parse),
                raw = json,
            )
        }
    }
}
