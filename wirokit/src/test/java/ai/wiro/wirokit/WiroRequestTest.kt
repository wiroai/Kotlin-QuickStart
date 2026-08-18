package ai.wiro.wirokit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

private val sampleFile = WiroFileInput.Url(URI("https://example.com/in.png"))
private val maskFile = WiroFileInput.Url(URI("https://example.com/mask.png"))

private fun encodeParameters(json: WiroJson): JsonObject = JsonObject(
    json.mapValues { (_, value) -> value.toJsonElement() },
)

private fun loadGolden(name: String): JsonObject {
    val loader =
        checkNotNull(
            Thread.currentThread().contextClassLoader,
        )
    val stream =
        checkNotNull(
            loader.getResourceAsStream("wire/requests/$name.json"),
        ) { "Missing golden fixture wire/requests/$name.json" }
    val text = stream.bufferedReader().use { it.readText() }
    return Json.parseToJsonElement(text) as JsonObject
}

private fun assertJsonEqual(
    actual: JsonElement,
    expected: JsonElement,
    path: String = "$",
) {
    when {
        expected is JsonObject && actual is JsonObject -> {
            assertEquals(
                "keys at $path",
                expected.keys,
                actual.keys,
            )
            expected.keys.forEach { key ->
                assertJsonEqual(
                    actual.getValue(key),
                    expected.getValue(key),
                    "$path.$key",
                )
            }
        }

        expected is JsonArray && actual is JsonArray -> {
            assertEquals("size at $path", expected.size, actual.size)
            expected.indices.forEach { index ->
                assertJsonEqual(
                    actual[index],
                    expected[index],
                    "$path[$index]",
                )
            }
        }

        else -> {
            assertEquals("value at $path", expected, actual)
        }
    }
}

private fun assertGolden(
    name: String,
    request: WiroModelRequest,
) {
    assertJsonEqual(
        encodeParameters(request.parameters()),
        loadGolden(name),
        name,
    )
}

class WiroRequestGoldenTest {
    @Test
    fun `typed request parameters match golden fixtures`() {
        assertGolden(
            "flux2_pro",
            Wiro.flux2Pro(
                prompt = "A mountain",
                inputImages = listOf(sampleFile),
                width = 1024,
                height = 768,
                safetyTolerance = 2,
                seed = 42,
                outputFormat = WiroFlux2ProOutputFormat.PNG,
            ),
        )
        assertGolden(
            "gpt_image_2",
            Wiro.gptImage2(
                prompt = "A mug",
                resolution = WiroGptImage2Resolution.R1K,
                ratio = WiroGptImage2Ratio.SQUARE,
                quality = WiroGptImage2Quality.LOW,
                samples = 2,
                inputImages = listOf(sampleFile),
                inputImageMasks = listOf(maskFile),
                background = WiroGptImage2Background.OPAQUE,
                outputFormat = WiroGptImage2OutputFormat.WEBP,
                outputCompression = 80,
                moderation = WiroGptImage2Moderation.LOW,
            ),
        )
        assertGolden(
            "nano_banana_pro",
            Wiro.nanoBananaPro(
                prompt = "A fox",
                inputImages = listOf(sampleFile),
                aspectRatio = WiroNanoBananaProRatio.ULTRAWIDE_21X9,
                resolution = WiroNanoBananaProResolution.R2K,
                safetySetting =
                WiroNanoBananaProSafetySetting.BLOCK_ONLY_HIGH,
            ),
        )
        assertGolden(
            "seedream_v4",
            Wiro.seedreamV4(
                prompt = "One poster",
                size = WiroSeedreamV4Size.PANORAMA_3024X1296,
                maxImages = 1,
                watermark = false,
            ),
        )
        assertGolden(
            "grok_imagine_image",
            Wiro.grokImagineImage(
                prompt = "A neon alley",
                samples = 3,
                resolution = WiroGrokImagineImageResolution.R2K,
                aspectRatio = WiroGrokImagineImageRatio.LANDSCAPE_19_5X9,
            ),
        )
        assertGolden(
            "runway_gen45",
            Wiro.runwayGen45(
                prompt = "A drone shot",
                ratio = WiroRunwayGen45Ratio.LANDSCAPE_16X9,
                duration = 5,
                inputImages = listOf(sampleFile),
                contentModeration = WiroRunwayGen45Moderation.LOW,
                seed = 7,
            ),
        )
        assertGolden(
            "seedance_20",
            Wiro.seedance20(
                resolution = WiroSeedance20Resolution.R480P,
                ratio = WiroSeedance20Ratio.ADAPTIVE,
                duration = 4,
                generateAudio = false,
                prompt = "A time-lapse",
                promptEnhancement = true,
                watermark = false,
                seed = 1,
            ),
        )
        assertGolden(
            "kling_v3",
            Wiro.klingV3(
                mode = WiroKlingV3Mode.PRO,
                duration = 5,
                ratio = WiroKlingV3Ratio.SQUARE,
                sound = true,
                prompt = "walk",
            ),
        )
        assertGolden(
            "veo31",
            Wiro.veo31(
                durationSeconds = 4,
                prompt = "ocean",
                inputImage = listOf(sampleFile),
                lastFrameImage = listOf(sampleFile),
                referenceImages = listOf(sampleFile),
                aspectRatio = WiroVeo31Ratio.LANDSCAPE_16X9,
                resolution = WiroVeo31Resolution.R720P,
                negativePrompt = "blur",
                seed = 3,
            ),
        )
        assertGolden(
            "sora2_pro",
            Wiro.sora2Pro(
                prompt = "city",
                seconds = 8,
                inputImages = listOf(sampleFile),
                resolution = WiroSora2ProResolution.R1080P,
                ratio = WiroSora2ProRatio.LANDSCAPE_16X9,
            ),
        )
        assertGolden(
            "hailuo_23_fast",
            Wiro.hailuo23Fast(
                inputImage = sampleFile,
                duration = 6,
                prompt = "zoom",
                promptOptimizer = true,
                resolution = WiroHailuo23FastResolution.R768P,
            ),
        )
        assertGolden(
            "grok_imagine_video",
            Wiro.grokImagineVideo(
                prompt = "rain",
                duration = 5,
                aspectRatio = WiroGrokImagineVideoRatio.AUTO,
                resolution = WiroGrokImagineVideoResolution.R720P,
            ),
        )
        assertGolden(
            "lyria_3",
            Wiro.lyria3(
                prompt = "lofi",
                inputImages = listOf(sampleFile),
            ),
        )
        assertGolden(
            "dynamic",
            Wiro.model(
                "owner/project",
                mapOf(
                    "prompt" to WiroValue.StringValue("hi"),
                    "seed" to WiroValue.number(1),
                ),
            ),
        )
    }
}

class WiroRequestValidationTest {
    @Test
    fun `flux2Pro full minimal and validation`() {
        val full =
            Wiro.flux2Pro(
                prompt = "A mountain",
                inputImages = listOf(sampleFile),
                width = 1024,
                height = 768,
                safetyTolerance = 2,
                seed = 42,
                outputFormat = WiroFlux2ProOutputFormat.PNG,
            )
        assertEquals("black-forest-labs/flux-2-pro", full.model.slug)
        assertEquals(7, full.parameters().size)

        val minimal = Wiro.flux2Pro(prompt = "hi")
        assertEquals(setOf("prompt"), minimal.parameters().keys)
        Wiro.flux2Pro(prompt = "ok", width = 0)

        assertThrows(WiroValidationException::class.java) {
            Wiro.flux2Pro(prompt = "")
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.flux2Pro(prompt = "x", width = 10)
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.flux2Pro(prompt = "x", safetyTolerance = 6)
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.flux2Pro(prompt = "x", seed = -1)
        }
    }

    @Test
    fun `gptImage2 rejects invalid samples`() {
        assertThrows(WiroValidationException::class.java) {
            Wiro.gptImage2(
                prompt = "A mug",
                resolution = WiroGptImage2Resolution.R1K,
                ratio = WiroGptImage2Ratio.SQUARE,
                quality = WiroGptImage2Quality.LOW,
                samples = 11,
            )
        }
    }

    @Test
    fun `seedreamV4 encodes watermark as string boolean`() {
        val request =
            Wiro.seedreamV4(
                prompt = "One poster",
                size = WiroSeedreamV4Size.SQUARE_2048,
                maxImages = 1,
                watermark = false,
            )
        assertEquals(
            WiroValue.StringValue("false"),
            request.parameters()["watermark"],
        )
        assertThrows(WiroValidationException::class.java) {
            Wiro.seedreamV4(
                prompt = "x",
                size = WiroSeedreamV4Size.SQUARE_2048,
                maxImages = 0,
                watermark = true,
            )
        }
    }

    @Test
    fun `klingV3 always sends multiPrompt and on off sound`() {
        val request =
            Wiro.klingV3(
                mode = WiroKlingV3Mode.STD,
                duration = 10,
                ratio = WiroKlingV3Ratio.SQUARE,
                sound = false,
            )
        assertEquals(
            WiroValue.StringValue(""),
            request.parameters()["multiPrompt"],
        )
        assertEquals(
            WiroValue.StringValue("off"),
            request.parameters()["sound"],
        )
        assertThrows(WiroValidationException::class.java) {
            Wiro.klingV3(
                mode = WiroKlingV3Mode.PRO,
                duration = 7,
                ratio = WiroKlingV3Ratio.SQUARE,
                sound = true,
            )
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.klingV3(
                mode = WiroKlingV3Mode.PRO,
                duration = 5,
                ratio = WiroKlingV3Ratio.SQUARE,
                sound = true,
                multiShot = true,
                shotType = WiroKlingV3ShotType.CUSTOMIZE,
            )
        }
    }

    @Test
    fun `hailuo rejects ten second 1080p`() {
        assertThrows(WiroValidationException::class.java) {
            Wiro.hailuo23Fast(
                inputImage = sampleFile,
                duration = 10,
                resolution = WiroHailuo23FastResolution.R1080P,
            )
        }
    }

    @Test
    fun `dynamic request rejects malformed slug`() {
        assertThrows(WiroValidationException::class.java) {
            Wiro.model("bad", emptyMap())
        }
    }

    @Test
    fun `lyria3 rejects empty prompt`() {
        assertThrows(WiroValidationException::class.java) {
            Wiro.lyria3(prompt = "")
        }
    }

    @Test
    fun `runway rejects empty and overlong prompts`() {
        assertThrows(WiroValidationException::class.java) {
            Wiro.runwayGen45(
                prompt = "",
                ratio = WiroRunwayGen45Ratio.AUTO,
                duration = 5,
            )
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.runwayGen45(
                prompt = "x".repeat(1001),
                ratio = WiroRunwayGen45Ratio.AUTO,
                duration = 5,
            )
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.runwayGen45(
                prompt = "ok",
                ratio = WiroRunwayGen45Ratio.AUTO,
                duration = 0,
            )
        }
    }

    @Test
    fun `sora and veo and seedance and grok reject bad durations`() {
        assertThrows(WiroValidationException::class.java) {
            Wiro.sora2Pro(prompt = "city", seconds = 7)
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.veo31(durationSeconds = 5)
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.seedance20(
                resolution = WiroSeedance20Resolution.R480P,
                ratio = WiroSeedance20Ratio.SQUARE,
                duration = 3,
                generateAudio = false,
            )
        }
        assertThrows(WiroValidationException::class.java) {
            Wiro.grokImagineVideo(
                prompt = "rain",
                duration = 7,
                aspectRatio = WiroGrokImagineVideoRatio.AUTO,
                resolution = WiroGrokImagineVideoResolution.R480P,
            )
        }
    }
}

class WiroTypedClientOverloadTest {
    @Test
    fun `typed run posts resolved parameters`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"taskid":"1","socketaccesstoken":"tok"}""",
        )
        val client = testClient(transport)

        client.run(
            Wiro.flux2Pro(prompt = "lake", width = 1024),
            callbackUrl = "https://example.com/hook",
        )

        assertEquals(1, transport.requests.size)
        assertTrue(
            transport.requests
                .single()
                .url
                .contains("/Run/black-forest-labs/flux-2-pro"),
        )
        val body =
            Json.parseToJsonElement(
                transport.requests
                    .single()
                    .body!!
                    .toString(Charsets.UTF_8),
            ) as JsonObject
        assertEquals(JsonPrimitive("lake"), body["prompt"])
        assertEquals(JsonPrimitive(1024), body["width"])
        assertEquals(
            JsonPrimitive("https://example.com/hook"),
            body["callbackUrl"],
        )
    }
}
