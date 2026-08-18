package ai.wiro.wirokit

/**
 * Discoverable entry point for every typed model request in the SDK.
 *
 * Type `Wiro.` in your IDE to list all models with typed parameters.
 */
public object Wiro {
    /**
     * Runs any Wiro model with dynamic [parameters].
     *
     * @throws WiroValidationException when [slug] is malformed.
     */
    @Throws(WiroValidationException::class)
    public fun model(
        slug: String,
        parameters: WiroJson,
    ): WiroDynamicRequest {
        val id =
            WiroModelId.parse(slug)
                ?: throw WiroValidationException(
                    "slug must be a valid owner/project identifier.",
                    statusCode = 0,
                )
        return WiroDynamicRequest(model = id, parameters = parameters)
    }

    // Image

    /** Generates images with `black-forest-labs/flux-2-pro`. */
    @Throws(WiroValidationException::class)
    public fun flux2Pro(
        prompt: String,
        inputImages: List<WiroFileInput>? = null,
        width: Int? = null,
        height: Int? = null,
        safetyTolerance: Int? = null,
        seed: Int? = null,
        outputFormat: WiroFlux2ProOutputFormat? = null,
    ): WiroFlux2ProRequest = WiroFlux2ProRequest(
        prompt = prompt,
        inputImages = inputImages,
        width = width,
        height = height,
        safetyTolerance = safetyTolerance,
        seed = seed,
        outputFormat = outputFormat,
    )

    /** Generates or edits images with `openai/gpt-image-2`. */
    @Throws(WiroValidationException::class)
    public fun gptImage2(
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
    ): WiroGptImage2Request = WiroGptImage2Request(
        prompt = prompt,
        resolution = resolution,
        ratio = ratio,
        quality = quality,
        samples = samples,
        inputImages = inputImages,
        inputImageMasks = inputImageMasks,
        background = background,
        outputFormat = outputFormat,
        outputCompression = outputCompression,
        moderation = moderation,
    )

    /** Generates or edits images with `google/nano-banana-pro`. */
    @Throws(WiroValidationException::class)
    public fun nanoBananaPro(
        prompt: String,
        inputImages: List<WiroFileInput>? = null,
        aspectRatio: WiroNanoBananaProRatio? = null,
        resolution: WiroNanoBananaProResolution? = null,
        safetySetting: WiroNanoBananaProSafetySetting? = null,
    ): WiroNanoBananaProRequest = WiroNanoBananaProRequest(
        prompt = prompt,
        inputImages = inputImages,
        aspectRatio = aspectRatio,
        resolution = resolution,
        safetySetting = safetySetting,
    )

    /** Generates images with `bytedance/seedream-v4`. */
    @Throws(WiroValidationException::class)
    public fun seedreamV4(
        prompt: String,
        size: WiroSeedreamV4Size,
        maxImages: Int,
        watermark: Boolean,
        inputImages: List<WiroFileInput>? = null,
    ): WiroSeedreamV4Request = WiroSeedreamV4Request(
        prompt = prompt,
        size = size,
        maxImages = maxImages,
        watermark = watermark,
        inputImages = inputImages,
    )

    /** Generates images with `xai/grok-imagine-image`. */
    @Throws(WiroValidationException::class)
    public fun grokImagineImage(
        prompt: String,
        samples: Int,
        resolution: WiroGrokImagineImageResolution,
        inputImages: List<WiroFileInput>? = null,
        aspectRatio: WiroGrokImagineImageRatio? = null,
    ): WiroGrokImagineImageRequest = WiroGrokImagineImageRequest(
        prompt = prompt,
        samples = samples,
        resolution = resolution,
        inputImages = inputImages,
        aspectRatio = aspectRatio,
    )

    // Video

    /** Generates video with `runway/gen-4-5`. */
    @Throws(WiroValidationException::class)
    public fun runwayGen45(
        prompt: String,
        ratio: WiroRunwayGen45Ratio,
        duration: Int,
        inputImages: List<WiroFileInput>? = null,
        contentModeration: WiroRunwayGen45Moderation? = null,
        seed: Int? = null,
    ): WiroRunwayGen45Request = WiroRunwayGen45Request(
        prompt = prompt,
        ratio = ratio,
        duration = duration,
        inputImages = inputImages,
        contentModeration = contentModeration,
        seed = seed,
    )

    /** Generates video with `bytedance/seedance-2-0`. */
    @Throws(WiroValidationException::class)
    public fun seedance20(
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
    ): WiroSeedance20Request = WiroSeedance20Request(
        resolution = resolution,
        ratio = ratio,
        duration = duration,
        generateAudio = generateAudio,
        prompt = prompt,
        inputImage = inputImage,
        lastFrameImage = lastFrameImage,
        referenceImages = referenceImages,
        referenceAudios = referenceAudios,
        promptEnhancement = promptEnhancement,
        watermark = watermark,
        seed = seed,
    )

    /** Generates video with `klingai/kling-v3`. */
    @Throws(WiroValidationException::class)
    public fun klingV3(
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
    ): WiroKlingV3Request = WiroKlingV3Request(
        mode = mode,
        duration = duration,
        ratio = ratio,
        sound = sound,
        prompt = prompt,
        inputImage = inputImage,
        lastFrameImage = lastFrameImage,
        multiShot = multiShot,
        shotType = shotType,
        multiPrompt = multiPrompt,
    )

    /** Generates video with `google/veo3-1`. */
    @Throws(WiroValidationException::class)
    public fun veo31(
        durationSeconds: Int,
        prompt: String? = null,
        inputImage: List<WiroFileInput>? = null,
        lastFrameImage: List<WiroFileInput>? = null,
        referenceImages: List<WiroFileInput>? = null,
        aspectRatio: WiroVeo31Ratio? = null,
        resolution: WiroVeo31Resolution? = null,
        negativePrompt: String? = null,
        seed: Int? = null,
    ): WiroVeo31Request = WiroVeo31Request(
        durationSeconds = durationSeconds,
        prompt = prompt,
        inputImage = inputImage,
        lastFrameImage = lastFrameImage,
        referenceImages = referenceImages,
        aspectRatio = aspectRatio,
        resolution = resolution,
        negativePrompt = negativePrompt,
        seed = seed,
    )

    /** Generates video with `openai/sora-2-pro`. */
    @Throws(WiroValidationException::class)
    public fun sora2Pro(
        prompt: String,
        seconds: Int,
        inputImages: List<WiroFileInput>? = null,
        resolution: WiroSora2ProResolution? = null,
        ratio: WiroSora2ProRatio? = null,
    ): WiroSora2ProRequest = WiroSora2ProRequest(
        prompt = prompt,
        seconds = seconds,
        inputImages = inputImages,
        resolution = resolution,
        ratio = ratio,
    )

    /** Generates video with `minimax/hailuo-2-3-fast`. */
    @Throws(WiroValidationException::class)
    public fun hailuo23Fast(
        inputImage: WiroFileInput,
        duration: Int,
        prompt: String? = null,
        promptOptimizer: Boolean? = null,
        resolution: WiroHailuo23FastResolution? = null,
    ): WiroHailuo23FastRequest = WiroHailuo23FastRequest(
        inputImage = inputImage,
        duration = duration,
        prompt = prompt,
        promptOptimizer = promptOptimizer,
        resolution = resolution,
    )

    /** Generates video with `xai/grok-imagine-video`. */
    @Throws(WiroValidationException::class)
    public fun grokImagineVideo(
        prompt: String,
        duration: Int,
        aspectRatio: WiroGrokImagineVideoRatio,
        resolution: WiroGrokImagineVideoResolution,
        inputImages: List<WiroFileInput>? = null,
    ): WiroGrokImagineVideoRequest = WiroGrokImagineVideoRequest(
        prompt = prompt,
        duration = duration,
        aspectRatio = aspectRatio,
        resolution = resolution,
        inputImages = inputImages,
    )

    // Audio

    /** Generates music with `google/lyria-3`. */
    @Throws(WiroValidationException::class)
    public fun lyria3(
        prompt: String,
        inputImages: List<WiroFileInput>? = null,
    ): WiroLyria3Request = WiroLyria3Request(
        prompt = prompt,
        inputImages = inputImages,
    )
}
