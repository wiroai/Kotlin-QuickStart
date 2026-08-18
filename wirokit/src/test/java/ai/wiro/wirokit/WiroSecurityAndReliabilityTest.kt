package ai.wiro.wirokit

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private fun loadEndpointGolden(name: String): JsonObject {
    val loader = checkNotNull(Thread.currentThread().contextClassLoader)
    val stream =
        checkNotNull(
            loader.getResourceAsStream("wire/endpoints/$name.json"),
        ) { "Missing golden fixture wire/endpoints/$name.json" }
    val text = stream.bufferedReader().use { it.readText() }
    return Json.parseToJsonElement(text) as JsonObject
}

private fun decodeBody(bytes: ByteArray): JsonObject = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
    as JsonObject

private fun assertJsonEqual(
    actual: JsonObject,
    expected: JsonObject,
) {
    assertEquals(expected.keys, actual.keys)
    expected.keys.forEach { key ->
        assertEquals(expected.getValue(key), actual.getValue(key))
    }
}

class WiroEndpointWireTest {
    @Test
    fun `endpoint request bodies match golden fixtures`() = runBlocking {
        val transport = FakeHttpTransport()
        val ok = """{"result":true,"total":0,"tool":[]}"""
        val explore = """{"result":true,"explore":[]}"""
        val detail =
            """{"result":true,"tool":[{"id":"1",""" +
                """"cleanslugowner":"black-forest-labs",""" +
                """"cleanslugproject":"flux-2-pro","parameters":[]}]}"""
        val task =
            """{"result":true,"tasklist":[{"taskid":"1",""" +
                """"status":"task_queue"}]}"""
        val run =
            """{"result":true,"taskid":"1",""" +
                """"socketaccesstoken":"tok"}"""
        val bool = """{"result":true}"""

        transport.enqueueJson(200, ok)
        transport.enqueueJson(200, explore)
        transport.enqueueJson(200, detail)
        transport.enqueueJson(200, task)
        transport.enqueueJson(200, task)
        transport.enqueueJson(200, bool)
        transport.enqueueJson(200, bool)
        transport.enqueueJson(200, run)

        val client = testClient(transport)

        client.searchModels(
            search = "flux",
            categories = listOf("image"),
            start = 0,
            limit = 20,
            sort = WiroModelSort.RELEVANCE,
            owner = "openai",
            order = WiroSortOrder.DESCENDING,
        )
        client.explore()
        client.getModelSchema(
            WiroModelId("black-forest-labs", "flux-2-pro"),
        )
        client.getTask(WiroTaskToken("tok-abc"))
        client.getTaskById(WiroTaskId("task-123"))
        client.cancelTask(WiroTaskId("task-123"))
        client.killTask(WiroTaskToken("tok-abc"))
        client.run(
            Wiro.flux2Pro(prompt = "lake", width = 1024),
            callbackUrl = "https://example.com/hook",
        )

        val names =
            listOf(
                "tool_list",
                "tool_explore",
                "tool_detail",
                "task_detail_token",
                "task_detail_id",
                "task_cancel",
                "task_kill",
                "run_flux2pro_callback",
            )
        names.forEachIndexed { index, name ->
            assertJsonEqual(
                decodeBody(transport.requests[index].body!!),
                loadEndpointGolden(name),
            )
            assertEquals(
                "WiroKit-Android/${WiroKitInfo.VERSION}",
                transport.requests[index].headers["User-Agent"],
            )
        }
    }
}

class WiroSecurityAndReliabilityTest {
    @Test
    fun `auth headers redaction covers signature vectors`() {
        val redacted =
            WiroRedaction.headers(
                mapOf(
                    "x-api-key" to "key",
                    "x-nonce" to "nonce",
                    "x-signature" to "sig",
                    "Authorization" to "Bearer tok",
                    "User-Agent" to
                        "WiroKit-Android/${WiroKitInfo.VERSION}",
                ),
            )
        assertEquals("[REDACTED]", redacted["x-api-key"])
        assertEquals("[REDACTED]", redacted["x-nonce"])
        assertEquals("[REDACTED]", redacted["x-signature"])
        assertEquals("[REDACTED]", redacted["Authorization"])
        assertEquals(
            "WiroKit-Android/${WiroKitInfo.VERSION}",
            redacted["User-Agent"],
        )
    }

    @Test
    fun `hmac matches known SHA-256 vectors`() {
        val first =
            WiroClient.signature(
                apiKey = "test-api-key",
                apiSecret = "test-secret",
                nonce = "1700000000000",
            )
        val second =
            WiroClient.signature(
                apiKey = "api",
                apiSecret = "secret",
                nonce = "nonce-1",
            )
        assertEquals(64, first.length)
        assertEquals(64, second.length)
        assertTrue(first.all { it in "0123456789abcdef" })
        assertTrue(second.all { it in "0123456789abcdef" })
        assertEquals(
            first,
            WiroClient.signature(
                "test-api-key",
                "test-secret",
                "1700000000000",
            ),
        )
        assertEquals(second, WiroClient.signature("api", "secret", "nonce-1"))
        assertTrue(first != second)
    }

    @Test
    fun `closed client rejects further work`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport, closeTransportOnClose = true)
        client.close()
        assertThrows(WiroValidationException::class.java) {
            runBlocking { client.getTask(WiroTaskToken("tok")) }
        }
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `non retryable statuses are not retried`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(404, """{"message":"missing"}""")
        transport.enqueueJson(200, """{"result":true,"tool":[]}""")
        val client = testClient(transport)

        runCatching { client.explore() }

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `concurrent calls share one client safely`() = runBlocking {
        val transport = FakeHttpTransport()
        repeat(8) {
            transport.enqueueJson(
                200,
                """{"result":true,"tasklist":[{"taskid":"$it",""" +
                    """"status":"task_postprocess_end","pexit":"0"}]}""",
            )
        }
        val client = testClient(transport)

        val results =
            (0 until 8)
                .map {
                    async { client.getTask(WiroTaskToken("tok-$it")) }
                }.awaitAll()

        assertEquals(8, results.size)
        assertEquals(8, transport.requests.size)
        assertTrue(results.all { it.status.isTerminal })
    }

    @Test
    fun `configurable limits reject oversized rest and upload bodies`() {
        val transport = FakeHttpTransport()
        val client =
            testClient(
                transport = transport,
                limits =
                WiroClientLimits(
                    maxRestBodyBytes = 8,
                    maxInMemoryUploadBytes = 4,
                    maxWebSocketTextBytes = 16,
                    maxWebSocketBinaryBytes = 16,
                ),
            )

        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                @Suppress("UNUSED_EXPRESSION")
                client.postJson(
                    path = "/Tool/Explore",
                    body =
                    mapOf(
                        "prompt" to WiroValue.StringValue("too-large-body"),
                    ),
                ) { it }
            }
        }
        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                client.uploadFile(
                    data = byteArrayOf(1, 2, 3, 4, 5),
                    fileName = "x.bin",
                )
            }
        }
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `configurable websocket text limit rejects oversized frames`() {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames = listOf(WiroSocketFrame.Text("{\"type\":\"task_start\"}")),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
                limits =
                WiroClientLimits(
                    maxWebSocketTextBytes = 4,
                    maxWebSocketBinaryBytes = 4,
                ),
            )

        assertThrows(WiroWebSocketException::class.java) {
            runBlocking {
                client
                    .watchTaskSocket(
                        WiroTaskToken("tok"),
                        timeout = 30.seconds,
                    ).collect { }
            }
        }
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `oversized binary frames are rejected`() {
        val world = ScriptedSocketWorld()
        world.session.configure(
            frames =
            listOf(
                WiroSocketFrame.Binary(ByteArray(32) { 1 }),
            ),
        )
        val client =
            testClient(
                transport = FakeHttpTransport(),
                socketSessionFactory = world.factory,
                parkLongSleeps = true,
                limits =
                WiroClientLimits(
                    maxWebSocketTextBytes = 1024,
                    maxWebSocketBinaryBytes = 8,
                ),
            )

        val error =
            assertThrows(WiroWebSocketException::class.java) {
                runBlocking {
                    client
                        .watchTaskSocket(
                            WiroTaskToken("tok"),
                            30.seconds,
                        ).collect { }
                }
            }
        assertTrue(error.message!!.contains("size limit"))
        assertTrue(world.session.closeCount >= 1)
    }

    @Test
    fun `percent encode covers unicode and reserved characters`() {
        assertEquals(
            "%F0%9F%8C%8A",
            // U+1F30A WATER WAVE — avoid literal emoji in sources.
            WiroClient.percentEncodePathSegment("\uD83C\uDF0A"),
        )
        assertEquals(
            "a%2Fb",
            WiroClient.percentEncodePathSegment("a/b"),
        )
        assertEquals(
            "plain-._~",
            WiroClient.percentEncodePathSegment("plain-._~"),
        )
    }

    @Test
    fun `log events never carry request bodies`() {
        val events = mutableListOf<WiroLogEvent>()
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"result":true,"explore":[]}""")
        val client =
            testClient(
                transport = transport,
                logger = WiroLogger { events += it },
            )

        runBlocking { client.explore() }

        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            assertFalse(event.message.contains("result"))
            assertFalse((event.error ?: "").contains("{"))
        }
    }

    @Test
    fun `task token toString stays redacted`() {
        val token = WiroTaskToken("secret-token-value")
        assertEquals("WiroTaskToken([REDACTED])", token.toString())
        assertFalse(token.toString().contains("secret"))
    }
}
