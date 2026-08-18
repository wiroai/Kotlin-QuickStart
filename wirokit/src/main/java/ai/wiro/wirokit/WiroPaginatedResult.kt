package ai.wiro.wirokit

import java.util.Collections

/**
 * An error included in a Wiro API response payload.
 */
public class WiroApiError(
    public val code: String?,
    public val message: String,
) {
    override fun equals(other: Any?): Boolean = other is WiroApiError &&
        code == other.code &&
        message == other.message

    override fun hashCode(): Int {
        var result = code?.hashCode() ?: 0
        result = 31 * result + message.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroApiError {
            val code =
                WiroJsonReader.string(json, "code")
                    ?: WiroJsonReader.integer(json, "code")?.toString()
            return WiroApiError(
                code = code,
                message =
                WiroJsonReader.string(json, "message")
                    ?: "Unknown Wiro API error",
            )
        }

        public fun parseList(from: WiroValue?): List<WiroApiError> = parseList(from, onMalformedJson = null)

        internal fun parseList(
            from: WiroValue?,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): List<WiroApiError> = WiroJsonReader
            .objects(from, onMalformedJson)
            .map(::parse)
    }
}

/**
 * A typed paginated response from Wiro.
 */
public class WiroPaginatedResult<Item>(
    public val isSuccess: Boolean,
    public val total: Int,
    items: List<Item>,
    errors: List<WiroApiError>,
    raw: WiroJson,
) {
    public val items: List<Item> =
        Collections.unmodifiableList(items.toList())
    public val errors: List<WiroApiError> =
        Collections.unmodifiableList(errors.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public companion object {
        public fun <Item> parse(
            json: WiroJson,
            itemsKey: String,
            itemFromJson: (WiroJson) -> Item,
        ): WiroPaginatedResult<Item> = parse(
            json = json,
            itemsKey = itemsKey,
            onMalformedJson = null,
            itemFromJson = itemFromJson,
        )

        internal fun <Item> parse(
            json: WiroJson,
            itemsKey: String,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
            itemFromJson: (WiroJson) -> Item,
        ): WiroPaginatedResult<Item> {
            val items =
                WiroJsonReader
                    .objects(
                        json,
                        itemsKey,
                        onMalformedJson,
                    ).map(itemFromJson)

            return WiroPaginatedResult(
                isSuccess =
                WiroJsonReader.boolean(
                    json,
                    "result",
                    fallback = false,
                ) ?: false,
                total = WiroJsonReader.integer(json, "total") ?: items.size,
                items = items,
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
