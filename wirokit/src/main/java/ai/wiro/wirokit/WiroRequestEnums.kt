package ai.wiro.wirokit

public enum class WiroFlux2ProOutputFormat(
    public val apiValue: String,
) {
    JPEG("jpeg"),
    PNG("png"),
}

public enum class WiroGptImage2Resolution(
    public val apiValue: String,
) {
    R1K("1k"),
    R2K("2k"),
    R4K("4k"),
}

public enum class WiroGptImage2Ratio(
    public val apiValue: String,
) {
    SQUARE("1:1"),
    LANDSCAPE_3X2("3:2"),
    PORTRAIT_2X3("2:3"),
    STANDARD_4X3("4:3"),
    PORTRAIT_3X4("3:4"),
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
}

public enum class WiroGptImage2Quality(
    public val apiValue: String,
) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

public enum class WiroGptImage2Background(
    public val apiValue: String,
) {
    AUTO("auto"),
    OPAQUE("opaque"),
}

public enum class WiroGptImage2OutputFormat(
    public val apiValue: String,
) {
    PNG("png"),
    JPEG("jpeg"),
    WEBP("webp"),
}

public enum class WiroGptImage2Moderation(
    public val apiValue: String,
) {
    AUTO("auto"),
    LOW("low"),
}

public enum class WiroNanoBananaProRatio(
    public val apiValue: String,
) {
    SQUARE("1:1"),
    PORTRAIT_2X3("2:3"),
    LANDSCAPE_3X2("3:2"),
    PORTRAIT_3X4("3:4"),
    STANDARD_4X3("4:3"),
    PORTRAIT_4X5("4:5"),
    LANDSCAPE_5X4("5:4"),
    PORTRAIT_9X16("9:16"),
    LANDSCAPE_16X9("16:9"),
    ULTRAWIDE_21X9("21:9"),
}

public enum class WiroNanoBananaProResolution(
    public val apiValue: String,
) {
    R1K("1K"),
    R2K("2K"),
    R4K("4K"),
}

public enum class WiroNanoBananaProSafetySetting(
    public val apiValue: String,
) {
    BLOCK_LOW_AND_ABOVE("BLOCK_LOW_AND_ABOVE"),
    BLOCK_MEDIUM_AND_ABOVE("BLOCK_MEDIUM_AND_ABOVE"),
    BLOCK_ONLY_HIGH("BLOCK_ONLY_HIGH"),
    BLOCK_NONE("BLOCK_NONE"),
    OFF("OFF"),
}

public enum class WiroSeedreamV4Size(
    public val apiValue: String,
) {
    SQUARE_2048("2048x2048"),
    LANDSCAPE_2304X1728("2304x1728"),
    PORTRAIT_1728X2304("1728x2304"),
    LANDSCAPE_2560X1440("2560x1440"),
    PORTRAIT_1440X2560("1440x2560"),
    LANDSCAPE_2496X1664("2496x1664"),
    PORTRAIT_1664X2496("1664x2496"),
    PANORAMA_3024X1296("3024x1296"),
}

public enum class WiroGrokImagineImageRatio(
    public val apiValue: String,
) {
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
    SQUARE("1:1"),
    STANDARD_4X3("4:3"),
    PORTRAIT_3X4("3:4"),
    LANDSCAPE_3X2("3:2"),
    PORTRAIT_2X3("2:3"),
    LANDSCAPE_2X1("2:1"),
    PORTRAIT_1X2("1:2"),
    LANDSCAPE_19_5X9("19.5:9"),
    PORTRAIT_9X19_5("9:19.5"),
    LANDSCAPE_20X9("20:9"),
    PORTRAIT_9X20("9:20"),
}

public enum class WiroGrokImagineImageResolution(
    public val apiValue: String,
) {
    R1K("1k"),
    R2K("2k"),
}

public enum class WiroRunwayGen45Ratio(
    public val apiValue: String,
) {
    AUTO("auto"),
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
    SQUARE("1:1"),
    STANDARD_4X3("4:3"),
    PORTRAIT_3X4("3:4"),
    ULTRAWIDE_21X9("21:9"),
}

public enum class WiroRunwayGen45Moderation(
    public val apiValue: String,
) {
    AUTO("auto"),
    LOW("low"),
}

public enum class WiroSeedance20Resolution(
    public val apiValue: String,
) {
    R480P("480p"),
    R720P("720p"),
    R1080P("1080p"),
    R4K("4k"),
}

public enum class WiroSeedance20Ratio(
    public val apiValue: String,
) {
    ADAPTIVE("adaptive"),
    LANDSCAPE_16X9("16:9"),
    STANDARD_4X3("4:3"),
    SQUARE("1:1"),
    PORTRAIT_3X4("3:4"),
    PORTRAIT_9X16("9:16"),
    ULTRAWIDE_21X9("21:9"),
}

public enum class WiroKlingV3Mode(
    public val apiValue: String,
) {
    STD("std"),
    PRO("pro"),
    ULTRA_4K("4k"),
}

public enum class WiroKlingV3Ratio(
    public val apiValue: String,
) {
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
    SQUARE("1:1"),
}

public enum class WiroKlingV3ShotType(
    public val apiValue: String,
) {
    CUSTOMIZE("customize"),
    INTELLIGENCE("intelligence"),
}

public enum class WiroVeo31Ratio(
    public val apiValue: String,
) {
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
    MATCH_INPUT_IMAGE("match_input_image"),
}

public enum class WiroVeo31Resolution(
    public val apiValue: String,
) {
    R720P("720p"),
    R1080P("1080p"),
    R4K("4k"),
}

public enum class WiroSora2ProResolution(
    public val apiValue: String,
) {
    R720P("720p"),
    R1024P("1024p"),
    R1080P("1080p"),
}

public enum class WiroSora2ProRatio(
    public val apiValue: String,
) {
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
    AUTO("auto"),
}

public enum class WiroHailuo23FastResolution(
    public val apiValue: String,
) {
    R768P("768P"),
    R1080P("1080P"),
}

public enum class WiroGrokImagineVideoRatio(
    public val apiValue: String,
) {
    AUTO("auto"),
    LANDSCAPE_16X9("16:9"),
    PORTRAIT_9X16("9:16"),
    SQUARE("1:1"),
    STANDARD_4X3("4:3"),
    PORTRAIT_3X4("3:4"),
    LANDSCAPE_3X2("3:2"),
    PORTRAIT_2X3("2:3"),
}

public enum class WiroGrokImagineVideoResolution(
    public val apiValue: String,
) {
    R480P("480p"),
    R720P("720p"),
}
