package ai.wiro.wirokit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class WiroTaskStatusTest {
    @Test
    fun `known statuses parse and expose wire values`() {
        assertEquals(
            WiroTaskStatus.Queued,
            WiroTaskStatus.parse("task_queue"),
        )
        assertEquals(
            "task_postprocess_end",
            WiroTaskStatus.Completed.apiValue,
        )
        assertTrue(WiroTaskStatus.Completed.isTerminal)
        assertTrue(WiroTaskStatus.Cancelled.isTerminal)
        assertFalse(WiroTaskStatus.Running.isTerminal)
    }

    @Test
    fun `future statuses remain inspectable and non terminal`() {
        val status = WiroTaskStatus.parse("task_future_thing")

        assertTrue(status is WiroTaskStatus.Unknown)
        assertEquals("task_future_thing", status.apiValue)
        assertFalse(status.isTerminal)
    }
}

class WiroTaskParseTest {
    @Test
    fun `task parses numeric and string fields with output fallback`() {
        val json =
            mapOf(
                "taskid" to WiroValue.NumberValue("12345"),
                "socketaccesstoken" to WiroValue.StringValue("tok-abc"),
                "status" to WiroValue.StringValue("task_postprocess_end"),
                "pexit" to WiroValue.StringValue("0"),
                "elapsedseconds" to WiroValue.StringValue("1.5"),
                "starttime" to WiroValue.NumberValue("1700000000"),
                "parameters" to
                    WiroValue.ObjectValue(
                        mapOf("prompt" to WiroValue.StringValue("lake")),
                    ),
                "output" to
                    WiroValue.ArrayValue(
                        listOf(
                            WiroValue.ObjectValue(
                                mapOf(
                                    "name" to WiroValue.StringValue("out.png"),
                                    "contenttype" to
                                        WiroValue.StringValue("image/png"),
                                    "size" to WiroValue.StringValue("12"),
                                    "url" to
                                        WiroValue.StringValue(
                                            "https://cdn.example.com/out.png",
                                        ),
                                    "content" to WiroValue.ObjectValue(emptyMap()),
                                ),
                            ),
                        ),
                    ),
            )

        val task = WiroTask.parse(json)

        assertEquals("12345", task.id?.rawValue)
        assertEquals("tok-abc", task.taskToken?.rawValue)
        assertTrue(task.status is WiroTaskStatus.Completed)
        assertEquals(0, task.exitCode)
        assertEquals(1500.milliseconds, task.elapsed)
        assertEquals(
            Instant.ofEpochSecond(1_700_000_000L),
            task.startTime,
        )
        assertEquals(1, task.outputs.size)
        assertTrue(task.outputs[0].isImage)
        assertNull(task.outputs[0].content)
        assertTrue(task.isSuccessful)
        assertTrue(task.isFinished)
    }

    @Test
    fun `task result classifies success cancel and non zero exit`() {
        val success =
            WiroTask(
                status = WiroTaskStatus.Completed,
                statusRawValue = "task_postprocess_end",
                exitCode = 0,
                raw = emptyMap(),
            )
        val cancelled =
            WiroTask(
                status = WiroTaskStatus.Cancelled,
                statusRawValue = "task_cancel",
                exitCode = 0,
                raw = emptyMap(),
            )
        val failed =
            WiroTask(
                status = WiroTaskStatus.Completed,
                statusRawValue = "task_postprocess_end",
                exitCode = 2,
                raw = emptyMap(),
            )
        val incomplete =
            WiroTask(
                status = WiroTaskStatus.Completed,
                statusRawValue = "task_postprocess_end",
                exitCode = null,
                raw = emptyMap(),
            )

        assertTrue(WiroTaskResult.from(success) is WiroTaskResult.Success)
        val cancelResult = WiroTaskResult.from(cancelled) as WiroTaskResult.Failure
        assertEquals(WiroTaskFailureReason.CANCELLED, cancelResult.reason)
        val failedResult = WiroTaskResult.from(failed) as WiroTaskResult.Failure
        assertEquals(WiroTaskFailureReason.NON_ZERO_EXIT, failedResult.reason)
        val incompleteResult =
            WiroTaskResult.from(incomplete) as WiroTaskResult.Failure
        assertEquals(
            WiroTaskFailureReason.NON_ZERO_EXIT,
            incompleteResult.reason,
        )
        val running =
            WiroTask(
                status = WiroTaskStatus.Running,
                statusRawValue = "task_start",
                exitCode = null,
                raw = emptyMap(),
            )
        val other = WiroTaskResult.from(running) as WiroTaskResult.Failure
        assertEquals(WiroTaskFailureReason.OTHER, other.reason)
    }

    @Test
    fun `run result coerces numeric task ids`() {
        val result =
            WiroRunResult.parse(
                mapOf(
                    "result" to WiroValue.BooleanValue(true),
                    "taskid" to WiroValue.NumberValue("99"),
                    "socketaccesstoken" to WiroValue.StringValue("tok"),
                ),
            )

        assertTrue(result.isSuccess)
        assertEquals("99", result.taskId?.rawValue)
        assertEquals("tok", result.taskToken?.rawValue)
    }
}

class WiroTaskClientTest {
    @Test
    fun `runModel posts encoded path callback and never retries`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(503, """{"message":"busy"}""")
        transport.enqueueJson(
            200,
            """{"result":true,"taskid":"1","socketaccesstoken":"tok"}""",
        )
        val client = testClient(transport)

        val error =
            runCatching {
                client.runModel(
                    modelId = WiroModelId("black-forest-labs", "flux-2-pro"),
                    parameters =
                    mapOf(
                        "prompt" to WiroValue.StringValue("lake"),
                    ),
                    callbackUrl = "https://example.com/hook?x=1",
                )
            }.exceptionOrNull()

        assertTrue(error is WiroUnknownApiException)
        assertEquals(1, transport.requests.size)
        assertEquals(
            "https://api.wiro.ai/v1/Run/black-forest-labs/flux-2-pro",
            transport.requests.single().url,
        )
        val body = decodeTaskBody(transport.requests.single().body!!)
        assertEquals("lake", body.string("prompt"))
        assertEquals(
            "https://example.com/hook?x=1",
            body.string("callbackUrl"),
        )
    }

    @Test
    fun `runModel rejects invalid callbacks and unresolved files`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                client.runModel(
                    WiroModelId("openai", "gpt-image-2"),
                    callbackUrl = "ftp://hooks.example.com/x",
                )
            }
        }
        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                client.runModel(
                    WiroModelId("openai", "gpt-image-2"),
                    callbackUrl = "https://user:pass@hooks.example.com/x",
                )
            }
        }
        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                client.runModel(
                    WiroModelId("openai", "gpt-image-2"),
                    callbackUrl = "https://hooks.example.com/x#frag",
                )
            }
        }
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `getTask and getTaskById use distinct request keys`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"tasklist":[{"id":"1","status":"task_start"}]}""",
        )
        transport.enqueueJson(
            200,
            """{"tasklist":[{"taskid":"2","status":"task_start"}]}""",
        )
        val client = testClient(transport)

        val byToken = client.getTask(WiroTaskToken("tok-abc"))
        val byId = client.getTaskById(WiroTaskId("task-123"))

        assertEquals("1", byToken.id?.rawValue)
        assertEquals("2", byId.id?.rawValue)
        assertEquals(
            "tok-abc",
            decodeTaskBody(transport.requests[0].body!!).string("tasktoken"),
        )
        assertEquals(
            "task-123",
            decodeTaskBody(transport.requests[1].body!!).string("taskid"),
        )
    }

    @Test
    fun `cancel and kill payloads match live contract`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"result":true}""")
        transport.enqueueJson(200, """{"result":true}""")
        transport.enqueueJson(200, """{"result":true}""")
        val client = testClient(transport)

        assertTrue(client.cancelTask(WiroTaskId("task-123")))
        assertTrue(client.killTask(WiroTaskToken("tok-abc")))
        assertTrue(client.killTask(WiroTaskId("42")))

        assertEquals(
            "https://api.wiro.ai/v1/Task/Cancel",
            transport.requests[0].url,
        )
        assertEquals(
            "task-123",
            decodeTaskBody(transport.requests[0].body!!).string("taskid"),
        )
        assertEquals(
            "tok-abc",
            decodeTaskBody(transport.requests[1].body!!)
                .string("socketaccesstoken"),
        )
        assertEquals(
            "42",
            decodeTaskBody(transport.requests[2].body!!).string("taskid"),
        )
    }

    @Test
    fun `missing tasklist throws unknown api error`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"tasklist":[]}""")
        val client =
            testClient(
                transport = transport,
                retryPolicy = WiroRetryPolicy.None,
            )

        val error =
            runCatching {
                client.getTask(WiroTaskToken("tok"))
            }.exceptionOrNull()

        assertTrue(error is WiroUnknownApiException)
        assertTrue(
            error?.message.orEmpty().contains("did not contain a task"),
        )
    }

    @Test
    fun `percent encode keeps unreserved characters`() {
        assertEquals("a%20b", WiroClient.percentEncodePathSegment("a b"))
        assertEquals(
            "flux-2-pro",
            WiroClient.percentEncodePathSegment("flux-2-pro"),
        )
        assertEquals(
            "black.forest",
            WiroClient.percentEncodePathSegment("black.forest"),
        )
    }

    @Test
    fun `output media helpers classify content types`() {
        val image =
            WiroTaskOutput(
                contentType = "image/png",
                raw = emptyMap(),
            )
        val video =
            WiroTaskOutput(
                contentType = "video/mp4",
                raw = emptyMap(),
            )
        val text =
            WiroTaskOutput(
                contentType = "application/json",
                raw = emptyMap(),
            )

        assertTrue(image.isImage)
        assertTrue(video.isVideo)
        assertTrue(text.isText)
        assertFalse(image.isAudio)
    }
}

private fun decodeTaskBody(bytes: ByteArray): WiroJson {
    val value =
        WiroValue.fromJsonElement(
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)),
        )
    return value.objectValue ?: error("expected JSON object body")
}

private fun WiroJson.string(key: String): String? = WiroJsonReader.string(this, key)
