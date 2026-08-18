package ai.wiro.wirokit

import kotlinx.coroutines.cancelAndJoin
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
import kotlin.time.Duration.Companion.seconds

private const val RUN_BODY =
    """{"result":true,"taskid":"1","socketaccesstoken":"tok"}"""

private fun taskBody(
    status: String,
    exitCode: String = "0",
): String = """{"tasklist":[{"taskid":"1","status":"$status",""" +
    """"pexit":"$exitCode"}]}"""

private fun socketJson(
    type: String,
    message: String? = null,
    result: Boolean = true,
): String {
    val messagePart =
        if (message == null) {
            ""
        } else {
            ""","message":$message"""
        }
    return """{"type":"$type","result":$result$messagePart}"""
}

private val token = WiroTaskToken("tok")
private val model = WiroModelId("black-forest-labs", "flux-2-pro")

class WiroSocketParseTest {
    @Test
    fun `progress payload parses step fields`() {
        val message =
            WiroSocketMessage.parse(
                mapOf(
                    "type" to WiroValue.StringValue("task_start"),
                    "result" to WiroValue.BooleanValue(true),
                    "message" to
                        WiroValue.ObjectValue(
                            mapOf(
                                "percentage" to WiroValue.NumberValue("40"),
                                "stepCurrent" to WiroValue.NumberValue("2"),
                                "stepTotal" to WiroValue.NumberValue("5"),
                            ),
                        ),
                ),
            )

        assertEquals(40.0, message.progress?.percentage)
        assertEquals(2, message.progress?.currentStep)
        assertEquals(5, message.progress?.totalSteps)
        assertTrue(message.outputs.isEmpty())
    }

    @Test
    fun `completed status maps message array to outputs`() {
        val message =
            WiroSocketMessage.parse(
                mapOf(
                    "type" to WiroValue.StringValue("task_postprocess_end"),
                    "message" to
                        WiroValue.ArrayValue(
                            listOf(
                                WiroValue.ObjectValue(
                                    mapOf(
                                        "name" to WiroValue.StringValue("out.png"),
                                        "contenttype" to
                                            WiroValue.StringValue("image/png"),
                                        "url" to
                                            WiroValue.StringValue(
                                                "https://cdn.example.com/out.png",
                                            ),
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        assertTrue(message.isTerminal)
        assertEquals(1, message.outputs.size)
        assertEquals("out.png", message.outputs.single().name)
    }

    @Test
    fun `unknown payloads remain inspectable`() {
        val message =
            WiroSocketMessage.parse(
                mapOf(
                    "type" to WiroValue.StringValue("task_future"),
                    "message" to
                        WiroValue.ArrayValue(
                            listOf(WiroValue.NumberValue("1")),
                        ),
                ),
            )

        assertTrue(message.payload is WiroSocketPayload.Unknown)
        assertFalse(message.isTerminal)
    }

    @Test
    fun `string progress json becomes a progress payload`() {
        val message =
            WiroSocketMessage.parse(
                mapOf(
                    "type" to WiroValue.StringValue("task_start"),
                    "message" to
                        WiroValue.StringValue(
                            """{"percentage":12,"stepCurrent":1}""",
                        ),
                ),
            )

        assertEquals(12.0, message.progress?.percentage)
        assertEquals(1, message.progress?.currentStep)
    }

    @Test
    fun `task update accessors cover event and binary`() {
        val running =
            WiroTaskUpdate.from(
                WiroSocketEvent.Message(
                    WiroSocketMessage.parse(
                        mapOf(
                            "type" to WiroValue.StringValue("task_start"),
                        ),
                    ),
                ),
            )
        val cancelled =
            WiroTaskUpdate.from(
                WiroSocketEvent.Message(
                    WiroSocketMessage.parse(
                        mapOf(
                            "type" to WiroValue.StringValue("task_cancel"),
                        ),
                    ),
                ),
            )
        val binary =
            WiroTaskUpdate.from(
                WiroSocketEvent.Binary(byteArrayOf(1, 2, 3)),
            )

        assertEquals(WiroTaskStatus.Running, running.status)
        assertFalse(running.isTerminal)
        assertTrue(cancelled.isTerminal)
        assertEquals(null, binary.status)
        assertFalse(binary.isTerminal)
    }
}

class WiroWatchTaskSocketTest {
    @Test
    fun `handshake sends task_info with the task token`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Text(socketJson("task_postprocess_end")),
            ),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        client
            .watchTaskSocket(WiroTaskToken("tok-abc"), 30.seconds)
            .toList()

        assertEquals(
            listOf("""{"type":"task_info","tasktoken":"tok-abc"}"""),
            world.session.sentTexts,
        )
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `happy path yields progress then terminal and closes`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Text(
                    socketJson("task_start", message = "\"running\""),
                ),
                WiroSocketFrame.Text(
                    socketJson(
                        type = "task_postprocess_end",
                        message =
                        """[{"name":"out.png",""" +
                            """"contenttype":"image/png",""" +
                            """"url":"https://cdn.wiro.ai/out.png"}]""",
                    ),
                ),
            ),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        val events = client.watchTaskSocket(token, 60.seconds).toList()

        assertEquals(2, events.size)
        val first = events[0] as WiroSocketEvent.Message
        assertEquals(WiroTaskStatus.Running, first.message.status)
        assertEquals("running", first.message.messageText)
        val last = events[1] as WiroSocketEvent.Message
        assertTrue(last.isTerminal)
        assertEquals(1, last.message.outputs.size)
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `binary frames pass through`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Binary(byteArrayOf(1, 2, 3)),
                WiroSocketFrame.Text(
                    socketJson("task_cancel", result = false),
                ),
            ),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        val events = client.watchTaskSocket(token).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is WiroSocketEvent.Binary)
        assertEquals(
            byteArrayOf(1, 2, 3).toList(),
            (events[0] as WiroSocketEvent.Binary).bytes.toList(),
        )
        assertEquals(
            WiroTaskStatus.Cancelled,
            (events[1] as WiroSocketEvent.Message).message.status,
        )
    }

    @Test
    fun `invalid JSON throws and closes the socket`() {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames = listOf(WiroSocketFrame.Text("{")),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        val error =
            assertThrows(WiroWebSocketException::class.java) {
                runBlocking {
                    client.watchTaskSocket(token, 30.seconds).toList()
                }
            }

        assertTrue(
            error.message!!.contains(
                "invalid JSON",
                ignoreCase = true,
            ),
        )
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `non-object JSON throws and closes the socket`() {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames = listOf(WiroSocketFrame.Text("[1,2]")),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        val error =
            assertThrows(WiroWebSocketException::class.java) {
                runBlocking {
                    client.watchTaskSocket(token, 30.seconds).toList()
                }
            }

        assertTrue(error.message!!.contains("non-object"))
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `oversized text frames are rejected`() {
        val world = ScriptedSocketWorld()
        val huge = "x".repeat(WiroSocketLimits.MAX_TEXT_BYTES + 1)
        world.session.configure(
            frames = listOf(WiroSocketFrame.Text(huge)),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        val error =
            assertThrows(WiroWebSocketException::class.java) {
                runBlocking {
                    client.watchTaskSocket(token, 30.seconds).toList()
                }
            }

        assertTrue(error.message!!.contains("size limit"))
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `socket timeout throws timedOut and closes`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(frames = emptyList(), closeAfter = false)
        val timeline = FakeTimeline()
        val client =
            testClient(
                transport = FakeHttpTransport(),
                timeline = timeline,
                socketSessionFactory = world.factory,
            )

        val error =
            runCatching {
                client.watchTaskSocket(token, 5.seconds).toList()
            }.exceptionOrNull()

        assertTrue(error is WiroTimeoutException)
        assertEquals(5.seconds, (error as WiroTimeoutException).timeout)
        assertTrue(world.session.closeCount >= 1)
        assertEquals(listOf<Duration>(5.seconds), timeline.slept)
    }

    @Test
    fun `cancelling collection closes the socket`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(frames = emptyList(), closeAfter = false)
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )
        var caught: Throwable? = null

        val job =
            launch {
                try {
                    client.watchTaskSocket(token, 600.seconds).collect { }
                } catch (error: CancellationException) {
                    caught = error
                    throw error
                }
            }

        yield()
        job.cancelAndJoin()

        assertTrue(caught is CancellationException)
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `watchTaskSocket rejects a non positive timeout`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            client.watchTaskSocket(token, Duration.ZERO)
        }
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `proxy client connects directly to socketUrl`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Text(socketJson("task_postprocess_end")),
            ),
        )
        val transport = FakeHttpTransport()
        val client =
            WiroClient.createForTests(
                apiKey = null,
                apiSecret = null,
                proxyHeaders = mapOf("Authorization" to "Bearer tok"),
                authType = WiroAuthType.PROXY,
                baseUrl = "https://proxy.example.com/v1",
                transport = transport,
                delay = parkingDelay(),
                socketSessionFactory = world.factory,
            )

        client.watchTaskSocket(token, 30.seconds).toList()

        assertEquals(
            WiroClient.DEFAULT_SOCKET_URL,
            world.session.connectedUrl?.toASCIIString(),
        )
        assertEquals(WiroAuthType.PROXY, client.authType)
        assertTrue(world.session.closeCount >= 1)
    }
}

class WiroSubscribeSocketTest {
    @Test
    fun `premature close falls back to polling`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(frames = emptyList(), closeAfter = true)
        val timeline = FakeTimeline()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, RUN_BODY)
        transport.enqueueJson(200, taskBody("task_start"))
        transport.enqueueJson(200, taskBody("task_postprocess_end"))
        val client =
            testClient(
                transport = transport,
                timeline = timeline,
                pollInterval = 1.seconds,
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
        assertTrue(updates.any { it is WiroTaskUpdate.Snapshot })
        assertEquals(3, transport.requests.size)
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `subscribeStream webSocket yields events then finishes`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Text(socketJson("task_postprocess_end")),
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

        val updates =
            client.subscribeStream(
                modelId = model,
                timeout = 60.seconds,
                trackingMode = WiroTaskTrackingMode.WEB_SOCKET,
            )

        assertEquals(1, transport.requests.size)
        val collected = updates.toList()
        assertEquals(2, collected.size)
        assertTrue(collected.first() is WiroTaskUpdate.Event)
        val terminal = collected.last()
        assertTrue(terminal is WiroTaskUpdate.Snapshot)
        assertTrue(terminal.isTerminal)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `handshake escapes quotes and backslashes in tokens`() = runBlocking {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Text(socketJson("task_postprocess_end")),
            ),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
            )

        client
            .watchTaskSocket(WiroTaskToken("""a\b"c"""), 30.seconds)
            .toList()

        assertEquals(
            listOf("""{"type":"task_info","tasktoken":"a\\b\"c"}"""),
            world.session.sentTexts,
        )
    }
}
