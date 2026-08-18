package ai.wiro.wirokit

import java.util.Collections
import kotlin.math.roundToLong

/**
 * An option accepted by a select-like parameter.
 */
public class WiroModelParameterOption(
    public val label: String,
    public val value: String,
) {
    override fun equals(other: Any?): Boolean = other is WiroModelParameterOption &&
        label == other.label &&
        value == other.value

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroModelParameterOption = WiroModelParameterOption(
            label = WiroJsonReader.string(json, "label") ?: "",
            value = WiroJsonReader.string(json, "value") ?: "",
        )
    }
}

/**
 * Shared metadata carried by every model parameter kind.
 */
public class WiroModelParameterInfo(
    public val name: String,
    public val label: String,
    public val description: String? = null,
    public val isRequired: Boolean,
    public val placeholder: String? = null,
    public val note: String? = null,
    raw: WiroJson,
) {
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    override fun equals(other: Any?): Boolean = other is WiroModelParameterInfo &&
        name == other.name &&
        label == other.label &&
        description == other.description &&
        isRequired == other.isRequired &&
        placeholder == other.placeholder &&
        note == other.note &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + isRequired.hashCode()
        result = 31 * result + (placeholder?.hashCode() ?: 0)
        result = 31 * result + (note?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }
}

/**
 * A model input parameter, discriminated by wire type.
 */
public sealed class WiroModelParameter {
    public abstract val info: WiroModelParameterInfo

    public val name: String
        get() = info.name

    public val isRequired: Boolean
        get() = info.isRequired

    public class Select(
        override val info: WiroModelParameterInfo,
        options: List<WiroModelParameterOption>,
        public val defaultValue: String?,
    ) : WiroModelParameter() {
        public val options: List<WiroModelParameterOption> =
            Collections.unmodifiableList(options.toList())

        override fun equals(other: Any?): Boolean = other is Select &&
            info == other.info &&
            options == other.options &&
            defaultValue == other.defaultValue

        override fun hashCode(): Int {
            var result = info.hashCode()
            result = 31 * result + options.hashCode()
            result = 31 * result + (defaultValue?.hashCode() ?: 0)
            return result
        }
    }

    public class Number(
        override val info: WiroModelParameterInfo,
        public val defaultValue: Double?,
        public val minimum: Double?,
        public val maximum: Double?,
        public val step: Double?,
    ) : WiroModelParameter() {
        override fun equals(other: Any?): Boolean = other is Number &&
            info == other.info &&
            defaultValue == other.defaultValue &&
            minimum == other.minimum &&
            maximum == other.maximum &&
            step == other.step

        override fun hashCode(): Int {
            var result = info.hashCode()
            result = 31 * result + (defaultValue?.hashCode() ?: 0)
            result = 31 * result + (minimum?.hashCode() ?: 0)
            result = 31 * result + (maximum?.hashCode() ?: 0)
            result = 31 * result + (step?.hashCode() ?: 0)
            return result
        }
    }

    public class Text(
        override val info: WiroModelParameterInfo,
        public val defaultValue: String?,
    ) : WiroModelParameter() {
        override fun equals(other: Any?): Boolean = other is Text &&
            info == other.info &&
            defaultValue == other.defaultValue

        override fun hashCode(): Int {
            var result = info.hashCode()
            result = 31 * result + (defaultValue?.hashCode() ?: 0)
            return result
        }
    }

    public class File(
        override val info: WiroModelParameterInfo,
    ) : WiroModelParameter() {
        override fun equals(other: Any?): Boolean = other is File && info == other.info

        override fun hashCode(): Int = info.hashCode()
    }

    public class Unknown(
        override val info: WiroModelParameterInfo,
        public val type: String,
        public val defaultValue: WiroValue?,
    ) : WiroModelParameter() {
        override fun equals(other: Any?): Boolean = other is Unknown &&
            info == other.info &&
            type == other.type &&
            defaultValue == other.defaultValue

        override fun hashCode(): Int {
            var result = info.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + (defaultValue?.hashCode() ?: 0)
            return result
        }
    }

    public companion object {
        public fun parse(json: WiroJson): WiroModelParameter = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroModelParameter {
            val type = WiroJsonReader.string(json, "type") ?: ""
            val info =
                WiroModelParameterInfo(
                    name = WiroJsonReader.string(json, "id") ?: "",
                    label = WiroJsonReader.string(json, "label") ?: "",
                    description = WiroJsonReader.string(json, "description"),
                    isRequired =
                    WiroJsonReader.boolean(
                        json,
                        "required",
                        fallback = false,
                    ) ?: false,
                    placeholder = WiroJsonReader.string(json, "placeholder"),
                    note = WiroJsonReader.string(json, "note"),
                    raw = json,
                )
            val options =
                WiroJsonReader
                    .objects(
                        json,
                        "options",
                        onMalformedJson,
                    ).map(WiroModelParameterOption::parse)

            return when (type.lowercase()) {
                "select" -> {
                    Select(
                        info = info,
                        options = options,
                        defaultValue = WiroJsonReader.string(json, "default"),
                    )
                }

                "range", "number", "numeric", "integer", "float" -> {
                    Number(
                        info = info,
                        defaultValue = WiroJsonReader.double(json, "default"),
                        minimum = WiroJsonReader.double(json, "min"),
                        maximum = WiroJsonReader.double(json, "max"),
                        step = WiroJsonReader.double(json, "step"),
                    )
                }

                "text", "textarea" -> {
                    Text(
                        info = info,
                        defaultValue = WiroJsonReader.string(json, "default"),
                    )
                }

                "fileinput", "multifileinput", "combinefileinput" -> {
                    File(
                        info = info,
                    )
                }

                else -> {
                    Unknown(
                        info = info,
                        type = type,
                        defaultValue = json["default"],
                    )
                }
            }
        }
    }
}

/**
 * A visual group of model parameters.
 */
public class WiroModelParameterGroup(
    public val title: String,
    parameters: List<WiroModelParameter>,
    raw: WiroJson,
) {
    public val parameters: List<WiroModelParameter> =
        Collections.unmodifiableList(parameters.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    override fun equals(other: Any?): Boolean = other is WiroModelParameterGroup &&
        title == other.title &&
        parameters == other.parameters &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + parameters.hashCode()
        result = 31 * result + raw.hashCode()
        return result
    }

    public companion object {
        public fun parse(json: WiroJson): WiroModelParameterGroup = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroModelParameterGroup {
            val parameters =
                WiroJsonReader
                    .objects(
                        json,
                        "items",
                        onMalformedJson,
                    ).map {
                        WiroModelParameter.parse(it, onMalformedJson)
                    }
            return WiroModelParameterGroup(
                title = WiroJsonReader.string(json, "title") ?: "",
                parameters = parameters,
                raw = json,
            )
        }
    }
}

/**
 * Full input schema for a Wiro model.
 */
public class WiroModelSchema(
    public val model: WiroModel,
    parameterGroups: List<WiroModelParameterGroup>,
    public val readme: String? = null,
    raw: WiroJson,
) {
    public val parameterGroups: List<WiroModelParameterGroup> =
        Collections.unmodifiableList(parameterGroups.toList())
    public val raw: WiroJson =
        Collections.unmodifiableMap(LinkedHashMap(raw))

    public val parameters: List<WiroModelParameter>
        get() = parameterGroups.flatMap { it.parameters }

    /**
     * Validates dynamic [parameters] against this model schema.
     *
     * Unknown key names are allowed. Returns human-readable problems for
     * missing required fields and select/number constraint mismatches.
     */
    public fun validate(parameters: WiroJson): List<String> {
        val errors = mutableListOf<String>()

        for (parameter in this.parameters) {
            val value = parameters[parameter.name]
            val isPresent = value != null && value !is WiroValue.NullValue

            if (parameter.isRequired && !isPresent) {
                errors += "${parameter.name} is required"
                continue
            }
            if (!isPresent) {
                continue
            }

            when (parameter) {
                is WiroModelParameter.Select -> {
                    val optionValues =
                        parameter.options
                            .map { it.value }
                            .toSet()
                    val selected = WiroJsonReader.string(value)
                    if (selected == null || selected !in optionValues) {
                        val joined =
                            parameter.options
                                .joinToString(", ") { it.value }
                        errors += "${parameter.info.name} must be one of: " +
                            joined
                    }
                }

                is WiroModelParameter.Number -> {
                    val number = WiroJsonReader.double(value)
                    if (number == null) {
                        errors += "${parameter.info.name} must be numeric"
                        continue
                    }
                    val minimum = parameter.minimum
                    if (minimum != null && number < minimum) {
                        errors += "${parameter.info.name} must be at least " +
                            formatNumber(minimum)
                    }
                    val maximum = parameter.maximum
                    if (maximum != null && number > maximum) {
                        errors += "${parameter.info.name} must be at most " +
                            formatNumber(maximum)
                    }
                }

                is WiroModelParameter.Text,
                is WiroModelParameter.File,
                is WiroModelParameter.Unknown,
                -> {}
            }
        }

        return errors
    }

    override fun equals(other: Any?): Boolean = other is WiroModelSchema &&
        model == other.model &&
        parameterGroups == other.parameterGroups &&
        readme == other.readme &&
        raw == other.raw

    override fun hashCode(): Int {
        var result = model.hashCode()
        result = 31 * result + parameterGroups.hashCode()
        result = 31 * result + (readme?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }

    private fun formatNumber(value: Double): String {
        if (value.isFinite() && value.roundToLong().toDouble() == value) {
            return value.toLong().toString()
        }
        return value.toString()
    }

    public companion object {
        public fun parse(json: WiroJson): WiroModelSchema = parse(json, onMalformedJson = null)

        internal fun parse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroModelSchema {
            val groups =
                WiroJsonReader
                    .objects(
                        json,
                        "parameters",
                        onMalformedJson,
                    ).map {
                        WiroModelParameterGroup.parse(it, onMalformedJson)
                    }
            return WiroModelSchema(
                model = WiroModel.parse(json, onMalformedJson),
                parameterGroups = groups,
                readme = WiroJsonReader.string(json, "readme"),
                raw = json,
            )
        }
    }
}
