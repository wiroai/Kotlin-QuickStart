package ai.wiro.wirokit

public enum class WiroModelSort(
    public val apiValue: String,
) {
    RELEVANCE("relevance"),
    TIME("time"),
    RATED_USER_COUNT("ratedusercount"),
    COMMENT_COUNT("commentcount"),
    AVERAGE_POINT("averagepoint"),
}

public enum class WiroSortOrder(
    public val apiValue: String,
) {
    ASCENDING("ASC"),
    DESCENDING("DESC"),
}
