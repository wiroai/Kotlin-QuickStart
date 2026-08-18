package ai.wiro.wirokit.live

import ai.wiro.wirokit.WiroClient
import ai.wiro.wirokit.WiroException
import ai.wiro.wirokit.WiroModelRequest
import ai.wiro.wirokit.WiroRunResult
import ai.wiro.wirokit.WiroTask
import ai.wiro.wirokit.WiroTaskId
import ai.wiro.wirokit.WiroTaskStatus
import ai.wiro.wirokit.WiroTaskToken
import ai.wiro.wirokit.WiroTaskTrackingMode
import ai.wiro.wirokit.run
import ai.wiro.wirokit.subscribe
import ai.wiro.wirokit.waitForTask
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs a suspending live-test body. JUnit4 requires `void` test methods, so the
 * coroutine result must never leak into the method signature.
 */
internal fun liveTest(body: suspend CoroutineScope.() -> Unit) {
    runBlocking(block = body)
}

internal object LiveSupport {
    val MODEL_TIMEOUT: Duration = 900.seconds
    val CORE_TIMEOUT: Duration = 300.seconds
    val POLL_INTERVAL: Duration = 3.seconds

    /** 1×1 PNG (red). */
    val TINY_PNG: ByteArray =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )

    private val outcomes = ConcurrentLinkedQueue<String>()

    fun record(
        name: String,
        result: String,
        detail: String? = null,
    ) {
        val sanitized =
            detail
                ?.replace(Regex("(?i)(key|secret|signature|token)=[^\\s,]+"), "$1=***")
                ?.take(160)
        outcomes.add(
            buildString {
                append(name)
                append(": ")
                append(result)
                if (!sanitized.isNullOrBlank()) {
                    append(" — ")
                    append(sanitized)
                }
            },
        )
    }

    fun dumpOutcomes(): List<String> = outcomes.toList()

    fun clearOutcomes() {
        outcomes.clear()
    }

    fun createClient(): WiroClient {
        val config = LiveCredentials.load()
        require(config.isEnabled) { "Live credentials are not configured." }
        return if (config.useProxy) {
            WiroClient(
                proxyUrl = config.proxyUrl!!,
                socketUrl = config.socketUrl ?: WiroClient.DEFAULT_SOCKET_URL,
                pollInterval = POLL_INTERVAL,
                requestTimeout = 60.seconds,
            )
        } else {
            WiroClient(
                apiKey = config.apiKey!!,
                apiSecret = config.apiSecret,
                baseUrl = config.baseUrl ?: WiroClient.DEFAULT_BASE_URL,
                socketUrl = config.socketUrl ?: WiroClient.DEFAULT_SOCKET_URL,
                pollInterval = POLL_INTERVAL,
                requestTimeout = 60.seconds,
            )
        }
    }

    fun uniqueFileName(prefix: String, extension: String = "png"): String = "$prefix-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$extension"

    fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    fun insertTinyPngContentUri(fileName: String = uniqueFileName("wiro-live")): Uri {
        val context = targetContext()
        val resolver = context.contentResolver
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY,
                )
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/WiroLive",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
        val uri =
            resolver.insert(collection, values)
                ?: error("Failed to insert MediaStore row for live upload.")
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(TINY_PNG)
        } ?: error("Failed to open MediaStore output stream.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun decodeTinyBitmap(): Bitmap = BitmapFactory.decodeByteArray(TINY_PNG, 0, TINY_PNG.size)
        ?: error("Failed to decode tiny PNG.")

    /**
     * Renders a PNG large enough to pass provider first-frame validation.
     * Image-to-video models reject the 1×1 [TINY_PNG] used for upload checks.
     */
    fun generatePngBytes(
        width: Int = 768,
        height: Int = 768,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                Color.rgb(28, 62, 122),
                Color.rgb(226, 168, 96),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = Color.rgb(250, 246, 232)
        canvas.drawCircle(width * 0.5f, height * 0.62f, width * 0.18f, paint)
        val stream = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
            "Failed to encode the generated live-test PNG."
        }
        bitmap.recycle()
        return stream.toByteArray()
    }

    suspend fun waitUntilAssigned(
        client: WiroClient,
        taskId: WiroTaskId,
        timeout: Duration = CORE_TIMEOUT,
    ): WiroTask {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var last: WiroTask? = null
        while (System.nanoTime() < deadline) {
            last = client.getTaskById(taskId)
            val status = last.status
            if (
                status is WiroTaskStatus.Assigned ||
                status is WiroTaskStatus.Running ||
                status is WiroTaskStatus.Preprocessing ||
                status is WiroTaskStatus.Preprocessed ||
                status is WiroTaskStatus.Output ||
                status is WiroTaskStatus.OutputComplete ||
                status is WiroTaskStatus.ProcessEnded ||
                status is WiroTaskStatus.PostProcessing ||
                status.isTerminal
            ) {
                return last
            }
            delay(POLL_INTERVAL)
        }
        error(
            "Timed out waiting for worker assignment; last=" +
                (last?.statusRawValue ?: "null"),
        )
    }

    suspend fun assertSuccessfulModel(
        client: WiroClient,
        label: String,
        request: WiroModelRequest,
        timeout: Duration = MODEL_TIMEOUT,
        trackingMode: WiroTaskTrackingMode = WiroTaskTrackingMode.POLLING,
    ): WiroTask = try {
        val result =
            client.subscribe(
                request = request,
                timeout = timeout,
                trackingMode = trackingMode,
            )
        val task = result.task
        when {
            task.isSuccessful -> {
                record(
                    label,
                    "ok",
                    "taskId=${task.id?.rawValue} outputs=${task.outputs.size}",
                )
                task
            }

            else -> {
                record(
                    label,
                    "model_failure",
                    "status=${task.statusRawValue} exit=${task.exitCode}",
                )
                error(
                    "$label completed without success " +
                        "(status=${task.statusRawValue}, exit=${task.exitCode})",
                )
            }
        }
    } catch (error: Throwable) {
        val kind =
            if (error is WiroException) "sdk_or_api_failure" else "unexpected"
        record(label, kind, error.message)
        throw error
    }

    suspend fun runAndWait(
        client: WiroClient,
        request: WiroModelRequest,
        timeout: Duration = MODEL_TIMEOUT,
    ): WiroTask {
        val run: WiroRunResult = client.run(request)
        val token = run.taskToken
        assertNotNull("Run must return socketaccesstoken", token)
        return client.waitForTask(token!!, timeout)
    }

    fun assertHasTaskIds(run: WiroRunResult) {
        assertNotNull(run.taskId)
        assertNotNull(run.taskToken)
        assertTrue(run.taskId!!.rawValue.isNotBlank())
        assertTrue(run.taskToken!!.rawValue.isNotBlank())
    }

    fun sanitizeTaskId(id: WiroTaskId?): String = id?.rawValue?.take(12)?.plus("…") ?: "null"

    fun sanitizeToken(token: WiroTaskToken?): String = token?.rawValue?.take(8)?.plus("…") ?: "null"
}
