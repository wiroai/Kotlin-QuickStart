package ai.wiro.wirokit

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val RUN_BODY =
    """{"result":true,"taskid":"1","socketaccesstoken":"tok"}"""

private fun taskBody(
    status: String,
    exitCode: String = "0",
): String = """{"tasklist":[{"taskid":"1","status":"$status",""" +
    """"pexit":"$exitCode"}]}"""

private val model = WiroModelId("black-forest-labs", "flux-2-pro")
private val token = WiroTaskToken("tok")

private fun runPathCount(transport: FakeHttpTransport): Int = transport.requests.count { it.url.contains("/Run/") }

class WiroWatchTaskTest {
    @Test
    fun `watchTask emits snapshots until a terminal status`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, taskBody("task_queue"))
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = timeline)

        val statuses =
            client
                .watchTask(token, timeout = 60.seconds)
                .toList()
                .map { it.status }

        assertEquals(
            listOf(
                WiroTaskStatus.Queued,
                WiroTaskStatus.Running,
                WiroTaskStatus.Completed,
            ),
            statuses,
        )
        assertEquals(3, transport.requests.size)
        assertEquals(listOf<Duration>(3.seconds, 3.seconds), timeline.slept)
    }

    @Test
    fun `a terminal first response never sleeps`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = timeline)

        val tasks = client.watchTask(token, timeout = 60.seconds).toList()

        assertEquals(1, tasks.size)
        assertTrue(tasks.single().isFinished)
        assertEquals(1, transport.requests.size)
        assertTrue(timeline.slept.isEmpty())
    }

    @Test
    fun `watchTask times out on the monotonic deadline`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_start"))
        val client = testClient(transport, timeline = timeline)

        val error =
            runCatching {
                client.watchTask(token, timeout = 5.seconds).toList()
            }.exceptionOrNull()

        assertTrue(error is WiroTimeoutException)
        assertEquals(5.seconds, (error as WiroTimeoutException).timeout)
        assertEquals(2, transport.requests.size)
        assertEquals(listOf<Duration>(3.seconds, 2.seconds), timeline.slept)
    }

    @Test
    fun `polling retries a transient failure`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(503, """{"message":"busy"}""")
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = timeline)

        val tasks = client.watchTask(token, timeout = 60.seconds).toList()

        assertEquals(2, tasks.size)
        assertEquals(3, transport.requests.size)
        assertEquals(
            listOf<Duration>(500.milliseconds, 3.seconds),
            timeline.slept,
        )
    }

    @Test
    fun `abandoning collection stops polling`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_start"))
        val client = testClient(transport, timeline = timeline)

        val tasks =
            client
                .watchTask(token, timeout = 60.seconds)
                .take(1)
                .toList()

        assertEquals(1, tasks.size)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `cancelling the collector stops polling and cancels`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        repeat(4) { transport.enqueueJson(200, taskBody("task_start")) }
        val client = testClient(transport, timeline = timeline)
        var caught: Throwable? = null

        val job =
            launch {
                try {
                    client.watchTask(token, timeout = 600.seconds).collect { }
                } catch (error: CancellationException) {
                    caught = error
                    throw error
                }
            }

        yield()
        job.cancelAndJoin()

        assertEquals(1, transport.requests.size)
        assertTrue(caught is CancellationException)
    }

    @Test
    fun `watchTask rejects a non positive timeout before polling`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            client.watchTask(token, timeout = Duration.ZERO)
        }
        assertEquals(0, transport.requests.size)
    }
}

class WiroWaitForTaskTest {
    @Test
    fun `waitForTask returns the terminal snapshot`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = timeline)

        val task = client.waitForTask(token, timeout = 60.seconds)

        assertTrue(task.isSuccessful)
        assertEquals(2, transport.requests.size)
        assertEquals(listOf<Duration>(3.seconds), timeline.slept)
    }

    @Test
    fun `waitForTask reports the timeout`() {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, taskBody("task_start"))
        val client = testClient(transport, timeline = timeline)

        assertThrows(WiroTimeoutException::class.java) {
            runBlocking { client.waitForTask(token, timeout = 3.seconds) }
        }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `waitForTask rejects a non positive timeout`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            runBlocking { client.waitForTask(token, timeout = Duration.ZERO) }
        }
        assertEquals(0, transport.requests.size)
    }
}

class WiroSubscribeTest {
    @Test
    fun `subscribe runs once and reports updates in order`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_queue"))
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = timeline)
        val updates = mutableListOf<WiroTaskUpdate>()

        val result =
            client.subscribe(
                modelId = model,
                parameters = mapOf("prompt" to WiroValue.StringValue("lake")),
                timeout = 60.seconds,
            ) { update ->
                updates += update
            }

        assertTrue(result is WiroTaskResult.Success)
        assertEquals(
            listOf(
                WiroTaskStatus.Queued,
                WiroTaskStatus.Running,
                WiroTaskStatus.Completed,
            ),
            updates.map { it.status },
        )
        assertFalse(updates.first().isTerminal)
        assertTrue(updates.last().isTerminal)
        assertEquals(4, transport.requests.size)
        assertEquals(1, runPathCount(transport))
    }

    @Test
    fun `subscribe maps a non zero exit to a failure`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(
            200,
            taskBody("task_postprocess_end", exitCode = "2"),
        )
        val client = testClient(transport, timeline = FakeTimeline())

        val result = client.subscribe(model, timeout = 60.seconds)

        assertTrue(result is WiroTaskResult.Failure)
        assertEquals(
            WiroTaskFailureReason.NON_ZERO_EXIT,
            (result as WiroTaskResult.Failure).reason,
        )
    }

    @Test
    fun `subscribe maps a cancelled task to a failure`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_cancel"))
        val client = testClient(transport, timeline = FakeTimeline())

        val result = client.subscribe(model, timeout = 60.seconds)

        assertEquals(
            WiroTaskFailureReason.CANCELLED,
            (result as WiroTaskResult.Failure).reason,
        )
    }

    @Test
    fun `subscribe webSocket confirms terminal via task detail`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Text(
                    """{"type":"task_start","result":true}""",
                ),
                WiroSocketFrame.Text(
                    """{"type":"task_postprocess_end","result":true}""",
                ),
            ),
        )
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client =
            testClient(
                transport = transport,
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )
        val updates = mutableListOf<WiroTaskUpdate>()

        val result =
            client.subscribe(
                modelId = model,
                timeout = 60.seconds,
                trackingMode = WiroTaskTrackingMode.WEB_SOCKET,
            ) { update ->
                updates += update
            }

        assertTrue(result is WiroTaskResult.Success)
        assertEquals(
            listOf(
                WiroTaskStatus.Running,
                WiroTaskStatus.Completed,
                WiroTaskStatus.Completed,
            ),
            updates.map { it.status },
        )
        assertTrue(updates.last() is WiroTaskUpdate.Snapshot)
        assertEquals(2, transport.requests.size)
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `subscribe fails when the run omits a task token`() {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"result":true,"taskid":"1"}""")
        val client = testClient(transport, timeline = FakeTimeline())

        assertThrows(WiroUnknownApiException::class.java) {
            runBlocking { client.subscribe(model, timeout = 60.seconds) }
        }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `subscribe rejects a non positive timeout before running`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            runBlocking { client.subscribe(model, timeout = Duration.ZERO) }
        }
        assertEquals(0, transport.requests.size)
    }
}

class WiroSubscribeStreamTest {
    @Test
    fun `the billable run completes before the flow is returned`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = FakeTimeline())

        val updates = client.subscribeStream(model, timeout = 60.seconds)

        assertEquals(1, transport.requests.size)
        assertEquals(1, runPathCount(transport))

        val collected = updates.toList()

        assertEquals(1, collected.size)
        assertTrue(collected.single().isTerminal)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `collecting twice repolls without repeating the run`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client = testClient(transport, timeline = FakeTimeline())

        val updates = client.subscribeStream(model, timeout = 60.seconds)
        val first = updates.toList()
        val second = updates.toList()

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(3, transport.requests.size)
        assertEquals(1, runPathCount(transport))
    }

    @Test
    fun `abandoning the stream stops polling`() = runBlocking {
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_start"))
        val client = testClient(transport, timeline = timeline)

        val updates =
            client
                .subscribeStream(model, timeout = 600.seconds)
                .take(1)
                .toList()

        assertEquals(1, updates.size)
        assertEquals(2, transport.requests.size)
        assertTrue(timeline.slept.isEmpty())
    }

    @Test
    fun `subscribeStream rejects a non positive timeout before running`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                client.subscribeStream(model, timeout = Duration.ZERO)
            }
        }
        assertEquals(0, transport.requests.size)
    }
}
