package ai.wiro.wirokit

/** Typed request for `black-forest-labs/flux-2-pro`. */
public class WiroFlux2ProRequest private constructor(
    public val prompt: String,
    public val inputImages: List<WiroFileInput>?,
    public val width: Int?,
    public val height: Int?,
    public val safetyTolerance: Int?,
    public val seed: Int?,
    public val outputFormat: WiroFlux2ProOutputFormat?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("black-forest-labs", "flux-2-pro")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        width?.let { json["width"] = WiroRequestEncoding.number(it) }
        height?.let { json["height"] = WiroRequestEncoding.number(it) }
        safetyTolerance?.let {
            json["safetyTolerance"] = WiroRequestEncoding.number(it)
        }
        seed?.let { json["seed"] = WiroRequestEncoding.number(it) }
        outputFormat?.let {
            json["outputFormat"] = WiroValue.StringValue(it.apiValue)
        }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            inputImages: List<WiroFileInput>? = null,
            width: Int? = null,
            height: Int? = null,
            safetyTolerance: Int? = null,
            seed: Int? = null,
            outputFormat: WiroFlux2ProOutputFormat? = null,
        ): WiroFlux2ProRequest {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireFluxDimension(width, "width")
            WiroRequestValidation.requireFluxDimension(height, "height")
            WiroRequestValidation.requireOptionalRange(
                safetyTolerance,
                0,
                5,
                "safetyTolerance",
            )
            WiroRequestValidation.requireNonNegative(seed, "seed")
            return WiroFlux2ProRequest(
                prompt,
                inputImages,
                width,
                height,
                safetyTolerance,
                seed,
                outputFormat,
            )
        }
    }
}

/** Typed request for `openai/gpt-image-2`. */
public class WiroGptImage2Request private constructor(
    public val prompt: String,
    public val resolution: WiroGptImage2Resolution,
    public val ratio: WiroGptImage2Ratio,
    public val quality: WiroGptImage2Quality,
    public val samples: Int,
    public val inputImages: List<WiroFileInput>?,
    public val inputImageMasks: List<WiroFileInput>?,
    public val background: WiroGptImage2Background?,
    public val outputFormat: WiroGptImage2OutputFormat?,
    public val outputCompression: Int?,
    public val moderation: WiroGptImage2Moderation?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("openai", "gpt-image-2")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        json["resolution"] = WiroValue.StringValue(resolution.apiValue)
        json["ratio"] = WiroValue.StringValue(ratio.apiValue)
        json["quality"] = WiroValue.StringValue(quality.apiValue)
        json["samples"] = WiroRequestEncoding.number(samples)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        WiroRequestEncoding.files(inputImageMasks)?.let {
            json["inputImageMask"] = it
        }
        background?.let {
            json["background"] = WiroValue.StringValue(it.apiValue)
        }
        outputFormat?.let {
            json["outputFormat"] = WiroValue.StringValue(it.apiValue)
        }
        outputCompression?.let {
            json["outputCompression"] = WiroRequestEncoding.number(it)
        }
        moderation?.let {
            json["moderation"] = WiroValue.StringValue(it.apiValue)
        }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            resolution: WiroGptImage2Resolution,
            ratio: WiroGptImage2Ratio,
            quality: WiroGptImage2Quality,
            samples: Int,
            inputImages: List<WiroFileInput>? = null,
            inputImageMasks: List<WiroFileInput>? = null,
            background: WiroGptImage2Background? = null,
            outputFormat: WiroGptImage2OutputFormat? = null,
            outputCompression: Int? = null,
            moderation: WiroGptImage2Moderation? = null,
        ): WiroGptImage2Request {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireMaxLength(prompt, 32_000, "prompt")
            WiroRequestValidation.requireRange(samples, 1, 10, "samples")
            WiroRequestValidation.requireOptionalRange(
                outputCompression,
                0,
                100,
                "outputCompression",
            )
            return WiroGptImage2Request(
                prompt,
                resolution,
                ratio,
                quality,
                samples,
                inputImages,
                inputImageMasks,
                background,
                outputFormat,
                outputCompression,
                moderation,
            )
        }
    }
}

/** Typed request for `google/nano-banana-pro`. */
public class WiroNanoBananaProRequest private constructor(
    public val prompt: String,
    public val inputImages: List<WiroFileInput>?,
    public val aspectRatio: WiroNanoBananaProRatio?,
    public val resolution: WiroNanoBananaProResolution?,
    public val safetySetting: WiroNanoBananaProSafetySetting?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("google", "nano-banana-pro")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        aspectRatio?.let {
            json["aspectRatio"] = WiroValue.StringValue(it.apiValue)
        }
        resolution?.let {
            json["resolution"] = WiroValue.StringValue(it.apiValue)
        }
        safetySetting?.let {
            json["safetySetting"] = WiroValue.StringValue(it.apiValue)
        }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            inputImages: List<WiroFileInput>? = null,
            aspectRatio: WiroNanoBananaProRatio? = null,
            resolution: WiroNanoBananaProResolution? = null,
            safetySetting: WiroNanoBananaProSafetySetting? = null,
        ): WiroNanoBananaProRequest {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireOptionalCount(
                inputImages,
                14,
                "inputImages",
            )
            return WiroNanoBananaProRequest(
                prompt,
                inputImages,
                aspectRatio,
                resolution,
                safetySetting,
            )
        }
    }
}

/** Typed request for `bytedance/seedream-v4`. */
public class WiroSeedreamV4Request private constructor(
    public val prompt: String,
    public val size: WiroSeedreamV4Size,
    public val maxImages: Int,
    public val watermark: Boolean,
    public val inputImages: List<WiroFileInput>?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("bytedance", "seedream-v4")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        json["size"] = WiroValue.StringValue(size.apiValue)
        json["maxImages"] = WiroRequestEncoding.number(maxImages)
        json["watermark"] = WiroRequestEncoding.stringBool(watermark)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            size: WiroSeedreamV4Size,
            maxImages: Int,
            watermark: Boolean,
            inputImages: List<WiroFileInput>? = null,
        ): WiroSeedreamV4Request {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireRange(maxImages, 1, 15, "maxImages")
            return WiroSeedreamV4Request(
                prompt,
                size,
                maxImages,
                watermark,
                inputImages,
            )
        }
    }
}

/** Typed request for `xai/grok-imagine-image`. */
public class WiroGrokImagineImageRequest private constructor(
    public val prompt: String,
    public val samples: Int,
    public val resolution: WiroGrokImagineImageResolution,
    public val inputImages: List<WiroFileInput>?,
    public val aspectRatio: WiroGrokImagineImageRatio?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("xai", "grok-imagine-image")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        json["samples"] = WiroRequestEncoding.number(samples)
        json["resolution"] = WiroValue.StringValue(resolution.apiValue)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        aspectRatio?.let {
            json["aspectRatio"] = WiroValue.StringValue(it.apiValue)
        }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            samples: Int,
            resolution: WiroGrokImagineImageResolution,
            inputImages: List<WiroFileInput>? = null,
            aspectRatio: WiroGrokImagineImageRatio? = null,
        ): WiroGrokImagineImageRequest {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireRange(samples, 1, 10, "samples")
            WiroRequestValidation.requireOptionalCount(
                inputImages,
                1,
                "inputImages",
            )
            return WiroGrokImagineImageRequest(
                prompt,
                samples,
                resolution,
                inputImages,
                aspectRatio,
            )
        }
    }
}
