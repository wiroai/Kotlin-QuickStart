package ai.wiro.wirokit

/**
 * A typed model invocation that knows its target model and parameters.
 */
public interface WiroModelRequest {
    /** The model this request targets. */
    public val model: WiroModelId

    /** Wire parameters for `/Run`, including unresolved file inputs. */
    public fun parameters(): WiroJson
}

/**
 * A dynamic request for any `owner/project` model without a typed factory.
 */
public class WiroDynamicRequest(
    override val model: WiroModelId,
    parameters: WiroJson,
) : WiroModelRequest {
    private val parametersMap: WiroJson = LinkedHashMap(parameters)

    override fun parameters(): WiroJson = LinkedHashMap(parametersMap)

    override fun equals(other: Any?): Boolean = other is WiroDynamicRequest &&
        model == other.model &&
        parametersMap == other.parametersMap

    override fun hashCode(): Int {
        var result = model.hashCode()
        result = 31 * result + parametersMap.hashCode()
        return result
    }
}

internal object WiroRequestEncoding {
    fun files(files: List<WiroFileInput>?): WiroValue? {
        if (files == null) return null
        return WiroValue.ArrayValue(files.map(::fileValue))
    }

    fun filesRequired(files: List<WiroFileInput>): WiroValue = WiroValue.ArrayValue(files.map(::fileValue))

    fun fileValue(file: WiroFileInput): WiroValue = when (file) {
        is WiroFileInput.Url -> {
            WiroValue.StringValue(file.uri.toASCIIString())
        }

        is WiroFileInput.Bytes,
        is WiroFileInput.ContentUri,
        -> {
            WiroValue.FileInputValue(file)
        }
    }

    fun stringBool(value: Boolean): WiroValue = WiroValue.StringValue(if (value) "true" else "false")

    fun stringInt(value: Int): WiroValue = WiroValue.StringValue(value.toString())

    fun onOff(value: Boolean): WiroValue = WiroValue.StringValue(if (value) "on" else "off")

    fun number(value: Int): WiroValue = WiroValue.number(value)
}

internal object WiroRequestValidation {
    fun fail(message: String): Nothing = throw WiroValidationException(message, statusCode = 0)

    fun requireNonEmpty(
        value: String,
        label: String,
    ) {
        if (value.isEmpty()) {
            fail("$label cannot be empty.")
        }
    }

    fun requireMaxLength(
        value: String,
        max: Int,
        label: String,
    ) {
        if (value.length > max) {
            fail("$label cannot exceed $max characters.")
        }
    }

    fun requireRange(
        value: Int,
        min: Int,
        max: Int,
        label: String,
    ) {
        if (value < min || value > max) {
            fail("$label must be between $min and $max.")
        }
    }

    fun requireOptionalRange(
        value: Int?,
        min: Int,
        max: Int,
        label: String,
    ) {
        if (value != null) {
            requireRange(value, min, max, label)
        }
    }

    fun requireNonNegative(
        value: Int?,
        label: String,
    ) {
        if (value != null && value < 0) {
            fail("$label cannot be negative.")
        }
    }

    fun requireFluxDimension(
        value: Int?,
        label: String,
    ) {
        if (value == null) return
        val ok =
            value == 0 ||
                (value in 64..2048 && value % 16 == 0)
        if (!ok) {
            fail(
                "$label must be 0 or a multiple of 16 between 64 and 2048.",
            )
        }
    }

    fun requireOneOf(
        value: Int,
        allowed: List<Int>,
        label: String,
    ) {
        if (value !in allowed) {
            val list = allowed.joinToString(", ")
            fail("$label must be one of: $list.")
        }
    }

    fun requireOptionalCount(
        files: List<WiroFileInput>?,
        max: Int,
        label: String,
    ) {
        if (files != null && files.size > max) {
            fail("$label cannot exceed $max references.")
        }
    }

    fun requireOptionalCountRange(
        files: List<WiroFileInput>?,
        min: Int,
        max: Int,
        label: String,
    ) {
        if (files == null) return
        if (files.size < min || files.size > max) {
            fail("$label must contain between $min and $max items.")
        }
    }

    fun model(
        owner: String,
        project: String,
    ): WiroModelId = WiroModelId(owner, project)
}
