package ai.wiro.wirokit

/** Typed request for `runway/gen-4-5`. */
public class WiroRunwayGen45Request private constructor(
    public val prompt: String,
    public val ratio: WiroRunwayGen45Ratio,
    public val duration: Int,
    public val inputImages: List<WiroFileInput>?,
    public val contentModeration: WiroRunwayGen45Moderation?,
    public val seed: Int?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("runway", "gen-4-5")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        json["ratio"] = WiroValue.StringValue(ratio.apiValue)
        json["duration"] = WiroRequestEncoding.number(duration)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        contentModeration?.let {
            json["contentModeration"] = WiroValue.StringValue(it.apiValue)
        }
        seed?.let { json["seed"] = WiroRequestEncoding.number(it) }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            ratio: WiroRunwayGen45Ratio,
            duration: Int,
            inputImages: List<WiroFileInput>? = null,
            contentModeration: WiroRunwayGen45Moderation? = null,
            seed: Int? = null,
        ): WiroRunwayGen45Request {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireMaxLength(prompt, 1000, "prompt")
            if (duration <= 0) {
                WiroRequestValidation.fail("duration must be positive.")
            }
            if (seed != null && seed < 0) {
                WiroRequestValidation.fail(
                    "seed must be between 0 and 4294967295.",
                )
            }
            return WiroRunwayGen45Request(
                prompt,
                ratio,
                duration,
                inputImages,
                contentModeration,
                seed,
            )
        }
    }
}

/** Typed request for `bytedance/seedance-2-0`. */
public class WiroSeedance20Request private constructor(
    public val resolution: WiroSeedance20Resolution,
    public val ratio: WiroSeedance20Ratio,
    public val duration: Int,
    public val generateAudio: Boolean,
    public val prompt: String?,
    public val inputImage: List<WiroFileInput>?,
    public val lastFrameImage: List<WiroFileInput>?,
    public val referenceImages: List<WiroFileInput>?,
    public val referenceAudios: List<WiroFileInput>?,
    public val promptEnhancement: Boolean?,
    public val watermark: Boolean?,
    public val seed: Int?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("bytedance", "seedance-2-0")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["resolution"] = WiroValue.StringValue(resolution.apiValue)
        json["ratio"] = WiroValue.StringValue(ratio.apiValue)
        json["duration"] = WiroRequestEncoding.stringInt(duration)
        json["generateAudio"] = WiroRequestEncoding.stringBool(generateAudio)
        prompt?.let { json["prompt"] = WiroValue.StringValue(it) }
        WiroRequestEncoding.files(inputImage)?.let { json["inputImage"] = it }
        WiroRequestEncoding.files(lastFrameImage)?.let {
            json["inputImageLast"] = it
        }
        WiroRequestEncoding.files(referenceImages)?.let {
            json["inputImageReference"] = it
        }
        WiroRequestEncoding.files(referenceAudios)?.let {
            json["inputAudio"] = it
        }
        promptEnhancement?.let {
            json["promptEnhancement"] = WiroRequestEncoding.stringBool(it)
        }
        watermark?.let {
            json["watermark"] = WiroRequestEncoding.stringBool(it)
        }
        seed?.let { json["seed"] = WiroRequestEncoding.number(it) }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            resolution: WiroSeedance20Resolution,
            ratio: WiroSeedance20Ratio,
            duration: Int,
            generateAudio: Boolean,
            prompt: String? = null,
            inputImage: List<WiroFileInput>? = null,
            lastFrameImage: List<WiroFileInput>? = null,
            referenceImages: List<WiroFileInput>? = null,
            referenceAudios: List<WiroFileInput>? = null,
            promptEnhancement: Boolean? = null,
            watermark: Boolean? = null,
            seed: Int? = null,
        ): WiroSeedance20Request {
            WiroRequestValidation.requireRange(duration, 4, 15, "duration")
            WiroRequestValidation.requireOptionalCountRange(
                referenceImages,
                1,
                9,
                "referenceImages",
            )
            WiroRequestValidation.requireOptionalCountRange(
                referenceAudios,
                1,
                3,
                "referenceAudios",
            )
            WiroRequestValidation.requireNonNegative(seed, "seed")
            return WiroSeedance20Request(
                resolution,
                ratio,
                duration,
                generateAudio,
                prompt,
                inputImage,
                lastFrameImage,
                referenceImages,
                referenceAudios,
                promptEnhancement,
                watermark,
                seed,
            )
        }
    }
}

/** Typed request for `klingai/kling-v3`. */
public class WiroKlingV3Request private constructor(
    public val mode: WiroKlingV3Mode,
    public val duration: Int,
    public val ratio: WiroKlingV3Ratio,
    public val sound: Boolean,
    public val prompt: String?,
    public val inputImage: List<WiroFileInput>?,
    public val lastFrameImage: List<WiroFileInput>?,
    public val multiShot: Boolean?,
    public val shotType: WiroKlingV3ShotType?,
    public val multiPrompt: String?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("klingai", "kling-v3")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["mode"] = WiroValue.StringValue(mode.apiValue)
        json["duration"] = WiroRequestEncoding.stringInt(duration)
        json["ratio"] = WiroValue.StringValue(ratio.apiValue)
        json["sound"] = WiroRequestEncoding.onOff(sound)
        json["multiPrompt"] = WiroValue.StringValue(multiPrompt ?: "")
        prompt?.let { json["prompt"] = WiroValue.StringValue(it) }
        WiroRequestEncoding.files(inputImage)?.let { json["inputImage"] = it }
        WiroRequestEncoding.files(lastFrameImage)?.let {
            json["inputImage2"] = it
        }
        multiShot?.let {
            json["multiShot"] = WiroRequestEncoding.stringBool(it)
        }
        shotType?.let {
            json["shotType"] = WiroValue.StringValue(it.apiValue)
        }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            mode: WiroKlingV3Mode,
            duration: Int,
            ratio: WiroKlingV3Ratio,
            sound: Boolean,
            prompt: String? = null,
            inputImage: List<WiroFileInput>? = null,
            lastFrameImage: List<WiroFileInput>? = null,
            multiShot: Boolean? = null,
            shotType: WiroKlingV3ShotType? = null,
            multiPrompt: String? = null,
        ): WiroKlingV3Request {
            WiroRequestValidation.requireOneOf(
                duration,
                listOf(5, 10, 15),
                "duration",
            )
            if (
                multiShot == true &&
                shotType == WiroKlingV3ShotType.CUSTOMIZE &&
                multiPrompt == null
            ) {
                WiroRequestValidation.fail(
                    "multiPrompt is required when multiShot is true and " +
                        "shotType is customize.",
                )
            }
            return WiroKlingV3Request(
                mode,
                duration,
                ratio,
                sound,
                prompt,
                inputImage,
                lastFrameImage,
                multiShot,
                shotType,
                multiPrompt,
            )
        }
    }
}

/** Typed request for `google/veo3-1`. */
public class WiroVeo31Request private constructor(
    public val durationSeconds: Int,
    public val prompt: String?,
    public val inputImage: List<WiroFileInput>?,
    public val lastFrameImage: List<WiroFileInput>?,
    public val referenceImages: List<WiroFileInput>?,
    public val aspectRatio: WiroVeo31Ratio?,
    public val resolution: WiroVeo31Resolution?,
    public val negativePrompt: String?,
    public val seed: Int?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("google", "veo3-1")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["durationSeconds"] =
            WiroRequestEncoding.stringInt(durationSeconds)
        prompt?.let { json["prompt"] = WiroValue.StringValue(it) }
        WiroRequestEncoding.files(inputImage)?.let { json["inputImage"] = it }
        WiroRequestEncoding.files(lastFrameImage)?.let {
            json["inputImage2"] = it
        }
        WiroRequestEncoding.files(referenceImages)?.let {
            json["inputImage3"] = it
        }
        aspectRatio?.let {
            json["aspectRatio"] = WiroValue.StringValue(it.apiValue)
        }
        resolution?.let {
            json["resolution"] = WiroValue.StringValue(it.apiValue)
        }
        negativePrompt?.let {
            json["negativePrompt"] = WiroValue.StringValue(it)
        }
        seed?.let { json["seed"] = WiroRequestEncoding.number(it) }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            durationSeconds: Int,
            prompt: String? = null,
            inputImage: List<WiroFileInput>? = null,
            lastFrameImage: List<WiroFileInput>? = null,
            referenceImages: List<WiroFileInput>? = null,
            aspectRatio: WiroVeo31Ratio? = null,
            resolution: WiroVeo31Resolution? = null,
            negativePrompt: String? = null,
            seed: Int? = null,
        ): WiroVeo31Request {
            WiroRequestValidation.requireOneOf(
                durationSeconds,
                listOf(4, 6, 8),
                "durationSeconds",
            )
            WiroRequestValidation.requireOptionalCountRange(
                referenceImages,
                1,
                3,
                "referenceImages",
            )
            WiroRequestValidation.requireNonNegative(seed, "seed")
            return WiroVeo31Request(
                durationSeconds,
                prompt,
                inputImage,
                lastFrameImage,
                referenceImages,
                aspectRatio,
                resolution,
                negativePrompt,
                seed,
            )
        }
    }
}

/** Typed request for `openai/sora-2-pro`. */
public class WiroSora2ProRequest private constructor(
    public val prompt: String,
    public val seconds: Int,
    public val inputImages: List<WiroFileInput>?,
    public val resolution: WiroSora2ProResolution?,
    public val ratio: WiroSora2ProRatio?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("openai", "sora-2-pro")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        json["seconds"] = WiroRequestEncoding.stringInt(seconds)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        resolution?.let {
            json["resolution"] = WiroValue.StringValue(it.apiValue)
        }
        ratio?.let { json["ratio"] = WiroValue.StringValue(it.apiValue) }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            seconds: Int,
            inputImages: List<WiroFileInput>? = null,
            resolution: WiroSora2ProResolution? = null,
            ratio: WiroSora2ProRatio? = null,
        ): WiroSora2ProRequest {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireOneOf(
                seconds,
                listOf(4, 8, 12, 16, 20),
                "seconds",
            )
            return WiroSora2ProRequest(
                prompt,
                seconds,
                inputImages,
                resolution,
                ratio,
            )
        }
    }
}

/** Typed request for `minimax/hailuo-2-3-fast`. */
public class WiroHailuo23FastRequest private constructor(
    public val inputImages: List<WiroFileInput>,
    public val duration: Int,
    public val prompt: String?,
    public val promptOptimizer: Boolean?,
    public val resolution: WiroHailuo23FastResolution?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("minimax", "hailuo-2-3-fast")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["inputImage"] = WiroRequestEncoding.filesRequired(inputImages)
        json["duration"] = WiroRequestEncoding.stringInt(duration)
        prompt?.let { json["prompt"] = WiroValue.StringValue(it) }
        promptOptimizer?.let {
            json["promptOptimizer"] = WiroRequestEncoding.stringBool(it)
        }
        resolution?.let {
            json["resolution"] = WiroValue.StringValue(it.apiValue)
        }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            inputImage: WiroFileInput,
            duration: Int,
            prompt: String? = null,
            promptOptimizer: Boolean? = null,
            resolution: WiroHailuo23FastResolution? = null,
        ): WiroHailuo23FastRequest {
            WiroRequestValidation.requireOneOf(
                duration,
                listOf(6, 10),
                "duration",
            )
            if (
                duration == 10 &&
                resolution == WiroHailuo23FastResolution.R1080P
            ) {
                WiroRequestValidation.fail(
                    "10-second videos are only available at 768P.",
                )
            }
            return WiroHailuo23FastRequest(
                listOf(inputImage),
                duration,
                prompt,
                promptOptimizer,
                resolution,
            )
        }
    }
}

/** Typed request for `xai/grok-imagine-video`. */
public class WiroGrokImagineVideoRequest private constructor(
    public val prompt: String,
    public val duration: Int,
    public val aspectRatio: WiroGrokImagineVideoRatio,
    public val resolution: WiroGrokImagineVideoResolution,
    public val inputImages: List<WiroFileInput>?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("xai", "grok-imagine-video")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        json["duration"] = WiroRequestEncoding.stringInt(duration)
        json["aspectRatio"] = WiroValue.StringValue(aspectRatio.apiValue)
        json["resolution"] = WiroValue.StringValue(resolution.apiValue)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            duration: Int,
            aspectRatio: WiroGrokImagineVideoRatio,
            resolution: WiroGrokImagineVideoResolution,
            inputImages: List<WiroFileInput>? = null,
        ): WiroGrokImagineVideoRequest {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            WiroRequestValidation.requireOneOf(
                duration,
                listOf(5, 10, 15),
                "duration",
            )
            WiroRequestValidation.requireOptionalCount(
                inputImages,
                1,
                "inputImages",
            )
            return WiroGrokImagineVideoRequest(
                prompt,
                duration,
                aspectRatio,
                resolution,
                inputImages,
            )
        }
    }
}

/** Typed request for `google/lyria-3`. */
public class WiroLyria3Request private constructor(
    public val prompt: String,
    public val inputImages: List<WiroFileInput>?,
) : WiroModelRequest {
    override val model: WiroModelId =
        WiroRequestValidation.model("google", "lyria-3")

    override fun parameters(): WiroJson {
        val json = LinkedHashMap<String, WiroValue>()
        json["prompt"] = WiroValue.StringValue(prompt)
        WiroRequestEncoding.files(inputImages)?.let { json["inputImage"] = it }
        return json
    }

    public companion object {
        @Throws(WiroValidationException::class)
        public operator fun invoke(
            prompt: String,
            inputImages: List<WiroFileInput>? = null,
        ): WiroLyria3Request {
            WiroRequestValidation.requireNonEmpty(prompt, "prompt")
            return WiroLyria3Request(prompt, inputImages)
        }
    }
}
