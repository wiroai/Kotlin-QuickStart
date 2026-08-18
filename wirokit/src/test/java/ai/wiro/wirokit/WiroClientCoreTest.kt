package ai.wiro.wirokit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class WiroClientAuthTest {
    @Test
    fun `hmac signature matches the published golden vector`() {
        val signature =
            WiroClient.signature(
                apiKey = "test-api-key",
                apiSecret = "test-secret",
                nonce = "1700000000000",
            )

        assertEquals(
            "2d99fa1b6934f66a712785d1b402997e1b13d9d7cd5e0085211dac133ae4a8ef",
            signature,
        )
        assertEquals(signature, signature.lowercase())
    }

    @Test
    fun `api key mode sends key and android user agent`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"ok":true}""")
        val client = testClient(transport)

        client.postJson("/Tool/List") { it }

        val headers = transport.requests.single().headers
        assertEquals("test-api-key", headers["x-api-key"])
        assertEquals(
            "WiroKit-Android/${WiroKitInfo.VERSION}",
            headers["User-Agent"],
        )
        assertEquals("application/json", headers["Content-Type"])
        assertNull(headers["x-nonce"])
        assertNull(headers["x-signature"])
    }

    @Test
    fun `signature mode sends nonce and signature`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"ok":true}""")
        val client = testClient(transport, apiSecret = "test-secret")

        client.postJson("/Tool/List") { it }

        val headers = transport.requests.single().headers
        assertEquals("test-api-key", headers["x-api-key"])
        assertEquals("1700000000000", headers["x-nonce"])
        assertEquals(
            "2d99fa1b6934f66a712785d1b402997e1b13d9d7cd5e0085211dac133ae4a8ef",
            headers["x-signature"],
        )
        assertEquals(
            "WiroKit-Android/${WiroKitInfo.VERSION}",
            headers["User-Agent"],
        )
    }

    @Test
    fun `proxy mode stores no credentials and keeps sdk headers`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"ok":true}""")
        val client =
            testProxyClient(
                transport = transport,
                headers =
                mapOf(
                    "Authorization" to "Bearer tok",
                    "User-Agent" to "spoofed",
                    "Content-Type" to "text/plain",
                ),
            )

        client.postJson("/Tool/List") { it }

        val headers = transport.requests.single().headers
        assertEquals("Bearer tok", headers["Authorization"])
        assertEquals(
            "WiroKit-Android/${WiroKitInfo.VERSION}",
            headers["User-Agent"],
        )
        assertEquals("application/json", headers["Content-Type"])
        assertNull(headers["x-api-key"])
        assertEquals(WiroAuthType.PROXY, client.authType)
    }

    @Test
    fun `invalid credentials and urls are rejected`() {
        assertThrows(WiroValidationException::class.java) {
            WiroClient(apiKey = "  ")
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient(apiKey = "key", apiSecret = "  ")
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient(
                apiKey = "key",
                baseUrl = "ftp://api.wiro.ai/v1",
            )
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient(
                apiKey = "key",
                socketUrl = "https://socket.wiro.ai/v1",
            )
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient(
                proxyUrl = "https://proxy.example.com/v1",
                headers = mapOf("X-Test" to "bad\nvalue"),
            )
        }
    }
}

class WiroClientEnvelopeTest {
    @Test
    fun `envelope maps status codes to typed exceptions`() = runBlocking {
        assertMapped(
            status = 200,
            body = """{"result":false,"errors":[{"message":"nope","code":"E1"}]}""",
            type = WiroApiResultException::class.java,
        ) { error ->
            error as WiroApiResultException
            assertEquals("nope", error.message)
            assertEquals("E1", error.code)
            assertEquals(200, error.statusCode)
        }

        assertMapped(
            status = 401,
            body = """{"message":"denied"}""",
            type = WiroAuthenticationException::class.java,
        )
        assertMapped(
            status = 400,
            body = """{"errors":[{"message":"bad"}]}""",
            type = WiroValidationException::class.java,
        )
        assertMapped(
            status = 429,
            body = """{"message":"slow"}""",
            headers = mapOf("Retry-After" to "7"),
            type = WiroRateLimitException::class.java,
        ) { error ->
            error as WiroRateLimitException
            assertEquals(7.seconds, error.retryAfter)
        }
        assertMapped(
            status = 500,
            body = "",
            type = WiroUnknownApiException::class.java,
        ) { error ->
            assertEquals("Wiro API request failed.", error.message)
        }
    }

    @Test
    fun `success without result key returns object`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"tool":[]}""")
        val client = testClient(transport)

        val result = client.postJson("/Tool/List") { it }

        assertTrue(result.containsKey("tool"))
    }

    @Test
    fun `non object and invalid json stay inspectable`() = runBlocking {
        assertMapped(
            status = 200,
            body = "[1,2,3]",
            type = WiroUnknownApiException::class.java,
        ) { error ->
            assertTrue(
                error.message.orEmpty().contains("non-object"),
            )
            assertEquals("[1,2,3]", error.rawResponseBody)
            assertFalse(error.toString().contains("[1,2,3]"))
        }

        assertMapped(
            status = 502,
            body = "bad gateway",
            type = WiroUnknownApiException::class.java,
        ) { error ->
            assertEquals("bad gateway", error.message)
            assertEquals("bad gateway", error.rawResponseBody)
        }
    }

    private suspend fun assertMapped(
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
        type: Class<out WiroException>,
        assertBlock: (WiroException) -> Unit = {},
    ) {
        val transport = FakeHttpTransport()
        transport.enqueueJson(status, body, headers)
        val client =
            testClient(
                transport = transport,
                retryPolicy = WiroRetryPolicy.None,
            )
        val error =
            runCatching {
                client.postJson("/Tool/List") { it }
            }.exceptionOrNull()
        assertTrue(
            "expected ${type.simpleName} but was $error",
            type.isInstance(error),
        )
        assertBlock(error as WiroException)
    }
}

class WiroClientRetryTest {
    @Test
    fun `retryable statuses are retried until success`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(503, """{"message":"busy"}""")
        transport.enqueueJson(200, """{"ok":true}""")
        val client = testClient(transport)

        client.postJson("/Tool/List") { it }

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `retry exhaustion performs initial plus max retries`() = runBlocking {
        val transport = FakeHttpTransport()
        repeat(3) {
            transport.enqueueJson(503, """{"message":"busy"}""")
        }
        val client = testClient(transport)

        val error =
            runCatching {
                client.postJson("/Tool/List") { it }
            }.exceptionOrNull()

        assertTrue(error is WiroUnknownApiException)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `run and upload paths never retry`() = runBlocking {
        for (path in listOf("/Run/owner/project", "/File/Upload")) {
            val transport = FakeHttpTransport()
            transport.enqueueJson(503, """{"message":"busy"}""")
            val client = testClient(transport)

            runCatching { client.postJson(path) { it } }

            assertEquals(path, 1, transport.requests.size)
        }
        assertFalse(WiroClient.isRetryablePath("/Run/a/b"))
        assertFalse(WiroClient.isRetryablePath("File/Upload"))
        assertTrue(WiroClient.isRetryablePath("/Tool/List"))
    }

    @Test
    fun `retry after sets minimum delay`() = runBlocking {
        val delays = mutableListOf<kotlin.time.Duration>()
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            statusCode = 429,
            body = """{"message":"slow"}""",
            headers = mapOf("Retry-After" to "3"),
        )
        transport.enqueueJson(200, """{"ok":true}""")
        val client = testClient(transport, delays = delays)

        client.postJson("/Tool/List") { it }

        assertEquals(listOf(3.seconds), delays)
    }

    @Test
    fun `cancellation is never retried or wrapped`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueue { throw CancellationException("stop") }
        val client = testClient(transport)

        val thrown =
            runCatching {
                client.postJson("/Tool/List") { it }
            }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertTrue(thrown !is WiroException)
        assertEquals(1, transport.requests.size)
    }
}

class WiroClientCoreTest {
    @Test
    fun `url joining trims base and accepts paths without slash`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"ok":true}""")
        val client =
            WiroClient.createForTests(
                transport = transport,
                baseUrl = "https://api.wiro.ai/v1///",
            )

        client.postJson("Tool/List") { it }

        assertEquals(
            "https://api.wiro.ai/v1/Tool/List",
            transport.requests.single().url,
        )
    }

    @Test
    fun `owned transport closes with client`() {
        val transport = FakeHttpTransport()
        val client =
            testClient(
                transport = transport,
                closeTransportOnClose = true,
            )

        client.close()

        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `caller owned transport remains open`() {
        val transport = FakeHttpTransport()
        val client =
            testClient(
                transport = transport,
                closeTransportOnClose = false,
            )

        client.close()

        assertEquals(0, transport.closeCount)
    }

    @Test
    fun `logging emits safe request lifecycle events`() = runBlocking {
        val events = mutableListOf<WiroLogEvent>()
        val transport = FakeHttpTransport()
        transport.enqueueJson(503, """{"message":"busy"}""")
        transport.enqueueJson(200, """{"ok":true}""")
        val client =
            testClient(
                transport = transport,
                logger = WiroLogger { events += it },
            )

        client.postJson("/Tool/List") { it }

        assertEquals(WiroLogLevel.DEBUG, events[0].level)
        assertEquals("Starting request.", events[0].message)
        assertEquals(WiroLogLevel.INFO, events[1].level)
        assertEquals(WiroLogLevel.WARNING, events[2].level)
        assertEquals(
            "Retrying request after transient failure.",
            events[2].message,
        )
        assertTrue(events.none { it.message.contains("test-api-key") })
        assertTrue(
            events.none {
                it.toString().contains("x-api-key") ||
                    it.toString().contains("x-signature")
            },
        )
    }

    @Test
    fun `transport error mapping preserves cancellation`() {
        val cancellation = CancellationException("stop")
        val thrown =
            runCatching {
                throw cancellation
            }.exceptionOrNull()

        assertSame(cancellation, thrown)
        val mapped =
            WiroKtorHttpTransport.mapTransportError(
                IllegalStateException("boom"),
            )
        assertTrue(mapped is WiroNetworkException)
        assertEquals(
            "IllegalStateException",
            (mapped as WiroNetworkException).underlyingType,
        )
    }
}
