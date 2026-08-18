package ai.wiro.wirokit.live

import ai.wiro.wirokit.Wiro
import ai.wiro.wirokit.WiroFileInput
import ai.wiro.wirokit.WiroFlux2ProOutputFormat
import ai.wiro.wirokit.WiroGptImage2Quality
import ai.wiro.wirokit.WiroGptImage2Ratio
import ai.wiro.wirokit.WiroGptImage2Resolution
import ai.wiro.wirokit.WiroGrokImagineImageResolution
import ai.wiro.wirokit.WiroGrokImagineVideoRatio
import ai.wiro.wirokit.WiroGrokImagineVideoResolution
import ai.wiro.wirokit.WiroHailuo23FastResolution
import ai.wiro.wirokit.WiroKlingV3Mode
import ai.wiro.wirokit.WiroKlingV3Ratio
import ai.wiro.wirokit.WiroRunwayGen45Ratio
import ai.wiro.wirokit.WiroSeedance20Ratio
import ai.wiro.wirokit.WiroSeedance20Resolution
import ai.wiro.wirokit.WiroSeedreamV4Size
import ai.wiro.wirokit.WiroSora2ProRatio
import ai.wiro.wirokit.WiroSora2ProResolution
import ai.wiro.wirokit.WiroVeo31Resolution
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Environment-gated typed model live coverage. Skips when credentials absent.
 *
 * Uses low-cost valid parameters. Does not cover `google/upscaler`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WiroLiveModelsAndroidTest {
    @Before
    fun requireCredentials() {
        LiveCredentials.assumeEnabled()
    }

    @After
    fun printSanitizedOutcomes() {
        LiveSupport.dumpOutcomes().forEach { line ->
            println("LIVE_OUTCOME $line")
        }
    }

    @Test
    fun m01_flux2Pro() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "flux2Pro",
                request =
                Wiro.flux2Pro(
                    prompt = "tiny orange square icon",
                    width = 512,
                    height = 512,
                    outputFormat = WiroFlux2ProOutputFormat.PNG,
                ),
            )
        }
    }

    @Test
    fun m02_gptImage2() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "gptImage2",
                request =
                Wiro.gptImage2(
                    prompt = "simple flat logo mark",
                    resolution = WiroGptImage2Resolution.R1K,
                    ratio = WiroGptImage2Ratio.SQUARE,
                    quality = WiroGptImage2Quality.LOW,
                    samples = 1,
                ),
            )
        }
    }

    @Test
    fun m03_nanoBananaPro() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "nanoBananaPro",
                request =
                Wiro.nanoBananaPro(
                    prompt = "minimal banana icon",
                ),
            )
        }
    }

    @Test
    fun m04_seedreamV4() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "seedreamV4",
                request =
                Wiro.seedreamV4(
                    prompt = "soft abstract gradient",
                    size = WiroSeedreamV4Size.SQUARE_2048,
                    maxImages = 1,
                    watermark = false,
                ),
            )
        }
    }

    @Test
    fun m05_grokImagineImage() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "grokImagineImage",
                request =
                Wiro.grokImagineImage(
                    prompt = "simple mountain silhouette",
                    samples = 1,
                    resolution = WiroGrokImagineImageResolution.R1K,
                ),
            )
        }
    }

    @Test
    fun m06_runwayGen45() = liveTest {
        LiveSupport.createClient().use { client ->
            // `auto` resolves to 16:9 for text-to-video, which is the ratio the
            // provider documents for prompt-only runs.
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "runwayGen45",
                request =
                Wiro.runwayGen45(
                    prompt = "calm ocean waves rolling onto a quiet shore",
                    ratio = WiroRunwayGen45Ratio.AUTO,
                    duration = 5,
                ),
            )
        }
    }

    @Test
    fun m07_seedance20() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "seedance20",
                request =
                Wiro.seedance20(
                    resolution = WiroSeedance20Resolution.R480P,
                    ratio = WiroSeedance20Ratio.SQUARE,
                    duration = 4,
                    generateAudio = false,
                    prompt = "slow pan across a desk",
                ),
            )
        }
    }

    @Test
    fun m08_klingV3() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "klingV3",
                request =
                Wiro.klingV3(
                    mode = WiroKlingV3Mode.STD,
                    duration = 5,
                    ratio = WiroKlingV3Ratio.SQUARE,
                    sound = false,
                    prompt = "gentle cloud drift",
                ),
            )
        }
    }

    @Test
    fun m09_veo31() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "veo31",
                request =
                Wiro.veo31(
                    durationSeconds = 4,
                    prompt = "quiet rainy street",
                    resolution = WiroVeo31Resolution.R720P,
                ),
            )
        }
    }

    @Test
    fun m10_sora2Pro() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "sora2Pro",
                request =
                Wiro.sora2Pro(
                    prompt = "candle flame close-up",
                    seconds = 4,
                    resolution = WiroSora2ProResolution.R720P,
                    ratio = WiroSora2ProRatio.AUTO,
                ),
            )
        }
    }

    @Test
    fun m11_hailuo23Fast() = liveTest {
        LiveSupport.createClient().use { client ->
            val fileName = LiveSupport.uniqueFileName("hailuo-input")
            val upload =
                client.uploadFile(
                    data = LiveSupport.generatePngBytes(),
                    fileName = fileName,
                )
            val url =
                upload.files
                    .first()
                    .url
                    ?: error("Hailuo upload missing URL")
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "hailuo23Fast",
                request =
                Wiro.hailuo23Fast(
                    inputImage = WiroFileInput.Url(url),
                    duration = 6,
                    prompt = "subtle camera push-in",
                    resolution = WiroHailuo23FastResolution.R768P,
                ),
            )
        }
    }

    @Test
    fun m12_grokImagineVideo() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "grokImagineVideo",
                request =
                Wiro.grokImagineVideo(
                    prompt = "falling autumn leaves",
                    duration = 5,
                    aspectRatio = WiroGrokImagineVideoRatio.SQUARE,
                    resolution = WiroGrokImagineVideoResolution.R480P,
                ),
            )
        }
    }

    @Test
    fun m13_lyria3() = liveTest {
        LiveSupport.createClient().use { client ->
            LiveSupport.assertSuccessfulModel(
                client = client,
                label = "lyria3",
                request =
                Wiro.lyria3(
                    prompt = "soft lo-fi beat, short loop",
                ),
            )
        }
    }
}
