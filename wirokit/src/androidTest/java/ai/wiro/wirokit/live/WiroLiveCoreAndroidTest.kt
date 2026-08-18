package ai.wiro.wirokit.live

import ai.wiro.wirokit.Wiro
import ai.wiro.wirokit.WiroFileInput
import ai.wiro.wirokit.WiroFlux2ProOutputFormat
import ai.wiro.wirokit.WiroModelId
import ai.wiro.wirokit.WiroSocketEvent
import ai.wiro.wirokit.WiroTaskStatus
import ai.wiro.wirokit.WiroTaskTrackingMode
import ai.wiro.wirokit.WiroValue
import ai.wiro.wirokit.run
import ai.wiro.wirokit.subscribeStream
import ai.wiro.wirokit.uploadFile
import ai.wiro.wirokit.waitForTask
import ai.wiro.wirokit.watchTask
import ai.wiro.wirokit.watchTaskSocket
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Environment-gated live core API coverage. Skips when credentials are absent.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WiroLiveCoreAndroidTest {
    @Before
    fun requireCredentials() {
        LiveCredentials.assumeEnabled()
        LiveSupport.clearOutcomes()
    }

    @After
    fun printSanitizedOutcomes() {
        LiveSupport.dumpOutcomes().forEach { line ->
            println("LIVE_OUTCOME $line")
        }
    }

    @Test
    fun a01_searchExploreAndSchema() = liveTest {
        LiveSupport.createClient().use { client ->
            val search = client.searchModels(search = "flux", limit = 5)
            assertTrue(search.items.isNotEmpty())
            LiveSupport.record(
                "search",
                "ok",
                "count=${search.items.size}",
            )

            val explore = client.explore()
            assertTrue(explore.isNotEmpty())
            LiveSupport.record(
                "explore",
                "ok",
                "categories=${explore.size}",
            )

            val modelId =
                search.items.firstOrNull()?.modelId
                    ?: WiroModelId("black-forest-labs", "flux-2-pro")
            val schema = client.getModelSchema(modelId)
            assertNotNull(schema.model.modelId)
            LiveSupport.record(
                "schema",
                "ok",
                "slug=${schema.model.owner}/${schema.model.slug}",
            )
        }
    }

    @Test
    fun a02_byteAndContentUriUpload() = liveTest {
        LiveSupport.createClient().use { client ->
            val bytesName = LiveSupport.uniqueFileName("live-bytes")
            val bytesResult =
                client.uploadFile(
                    data = LiveSupport.TINY_PNG,
                    fileName = bytesName,
                )
            assertTrue(bytesResult.files.isNotEmpty())
            LiveSupport.record(
                "upload_bytes",
                "ok",
                "files=${bytesResult.files.size}",
            )

            val uriName = LiveSupport.uniqueFileName("live-uri")
            val uri = LiveSupport.insertTinyPngContentUri(uriName)
            val uriResult =
                client.uploadFile(
                    input =
                    WiroFileInput.ContentUri(
                        uri = uri,
                        fileName = uriName,
                    ),
                    contentResolver =
                    LiveSupport.targetContext().contentResolver,
                )
            assertTrue(uriResult.files.isNotEmpty())
            LiveSupport.record(
                "upload_content_uri",
                "ok",
                "files=${uriResult.files.size}",
            )
        }
    }

    @Test
    fun a03_runTaskDetailPollingAndWait() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.flux2Pro(
                    prompt = "a tiny red square, flat color",
                    width = 512,
                    height = 512,
                    outputFormat = WiroFlux2ProOutputFormat.PNG,
                )
            val run = client.run(request)
            LiveSupport.assertHasTaskIds(run)
            val taskId = requireNotNull(run.taskId)
            val taskToken = requireNotNull(run.taskToken)
            LiveSupport.record(
                "run",
                "ok",
                "taskId=${LiveSupport.sanitizeTaskId(taskId)}",
            )

            val byToken = client.getTask(taskToken)
            assertNotNull(byToken.status)
            LiveSupport.record(
                "task_by_token",
                "ok",
                "status=${byToken.statusRawValue}",
            )

            val byId = client.getTaskById(taskId)
            assertNotNull(byId.status)
            LiveSupport.record(
                "task_by_id",
                "ok",
                "status=${byId.statusRawValue}",
            )

            val polled =
                client
                    .watchTask(taskToken, LiveSupport.CORE_TIMEOUT)
                    .first { it.isFinished || it.status is WiroTaskStatus.Running }
            LiveSupport.record(
                "poll_watch",
                "ok",
                "status=${polled.statusRawValue}",
            )

            val waited =
                client.waitForTask(
                    taskToken,
                    LiveSupport.MODEL_TIMEOUT,
                )
            assertTrue(
                "waitForTask should finish",
                waited.isFinished,
            )
            LiveSupport.record(
                "wait_for_task",
                if (waited.isSuccessful) "ok" else "model_failure",
                "status=${waited.statusRawValue} exit=${waited.exitCode}",
            )
            assertTrue(waited.isSuccessful)
        }
    }

    @Test
    fun a04_subscribeStreamWebSocket() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.flux2Pro(
                    prompt = "simple blue circle icon",
                    width = 512,
                    height = 512,
                    outputFormat = WiroFlux2ProOutputFormat.PNG,
                )
            val updates =
                client
                    .subscribeStream(
                        request = request,
                        timeout = LiveSupport.MODEL_TIMEOUT,
                        trackingMode = WiroTaskTrackingMode.WEB_SOCKET,
                    ).toList()
            assertTrue(updates.isNotEmpty())
            val lastSnapshot =
                updates
                    .asReversed()
                    .filterIsInstance<ai.wiro.wirokit.WiroTaskUpdate.Snapshot>()
                    .firstOrNull()
                    ?.task
            assertNotNull(lastSnapshot)
            LiveSupport.record(
                "subscribe_stream_ws",
                if (lastSnapshot!!.isSuccessful) "ok" else "model_failure",
                "updates=${updates.size} status=${lastSnapshot.statusRawValue}",
            )
            assertTrue(lastSnapshot.isSuccessful)
        }
    }

    @Test
    fun a05_directWebSocketWatch() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.flux2Pro(
                    prompt = "minimal geometric mark",
                    width = 512,
                    height = 512,
                )
            val run = client.run(request)
            LiveSupport.assertHasTaskIds(run)
            val events =
                withTimeout(LiveSupport.CORE_TIMEOUT.inWholeMilliseconds) {
                    client
                        .watchTaskSocket(run.taskToken!!)
                        .take(3)
                        .toList()
                }
            assertTrue(events.isNotEmpty())
            assertTrue(events.any { it is WiroSocketEvent.Message })
            LiveSupport.record(
                "watch_task_socket",
                "ok",
                "events=${events.size}",
            )
            // Best-effort cleanup so the task does not keep billing.
            runCatching { client.killTask(run.taskToken!!) }
        }
    }

    @Test
    fun a06_cancelQueuedTaskById() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.flux2Pro(
                    prompt = "cancel candidate image",
                    width = 512,
                    height = 512,
                )
            val run = client.run(request)
            LiveSupport.assertHasTaskIds(run)
            val taskId = requireNotNull(run.taskId)
            val cancelled = client.cancelTask(taskId)
            LiveSupport.record(
                "cancel_by_id",
                if (cancelled) "ok" else "api_false",
                "taskId=${LiveSupport.sanitizeTaskId(taskId)}",
            )
            // API may return false if the task already left the queue; that is
            // not an SDK failure. Verify detail still resolves.
            val detail = client.getTaskById(taskId)
            assertNotNull(detail.status)
            assertFalse(detail.statusRawValue.isBlank())
        }
    }

    @Test
    fun a07_killActiveTaskBySocketAccessToken() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.flux2Pro(
                    prompt = "kill-by-token candidate",
                    width = 768,
                    height = 768,
                )
            val run = client.run(request)
            LiveSupport.assertHasTaskIds(run)
            val taskId = requireNotNull(run.taskId)
            val taskToken = requireNotNull(run.taskToken)
            LiveSupport.waitUntilAssigned(client, taskId)
            val killed = client.killTask(taskToken)
            LiveSupport.record(
                "kill_by_token",
                if (killed) "ok" else "api_false",
                "token=${LiveSupport.sanitizeToken(taskToken)}",
            )
            assertTrue(killed)
        }
    }

    @Test
    fun a08_killActiveTaskByIdAfterAssignment() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.flux2Pro(
                    prompt = "kill-by-id candidate",
                    width = 768,
                    height = 768,
                )
            val run = client.run(request)
            LiveSupport.assertHasTaskIds(run)
            val taskId = requireNotNull(run.taskId)
            LiveSupport.waitUntilAssigned(client, taskId)
            val killed = client.killTask(taskId)
            LiveSupport.record(
                "kill_by_id",
                if (killed) "ok" else "api_false",
                "taskId=${LiveSupport.sanitizeTaskId(taskId)}",
            )
            assertTrue(killed)
        }
    }

    @Test
    fun a09_dynamicModelRequest() = liveTest {
        LiveSupport.createClient().use { client ->
            val request =
                Wiro.model(
                    slug = "black-forest-labs/flux-2-pro",
                    parameters =
                    mapOf(
                        "prompt" to
                            WiroValue.StringValue(
                                "dynamic request smoke",
                            ),
                        "width" to WiroValue.number(512),
                        "height" to WiroValue.number(512),
                    ),
                )
            val task =
                LiveSupport.assertSuccessfulModel(
                    client = client,
                    label = "dynamic_flux2pro",
                    request = request,
                )
            assertTrue(task.outputs.isNotEmpty() || task.isSuccessful)
        }
    }
}
