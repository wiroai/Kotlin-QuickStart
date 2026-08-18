package ai.wiro.wirokit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

class WiroExceptionTest {
    @Test
    fun `response body remains inspectable but never renders`() {
        val rawBody = """{"apiKey":"sk-secret","payload":"private"}"""
        val error =
            WiroApiResultException(
                message = "Application failed.",
                code = "E1",
                statusCode = 200,
                rawResponseBody = rawBody,
            )

        assertEquals(rawBody, error.rawResponseBody)
        assertEquals("Application failed.", error.message)
        assertFalse(error.toString().contains("sk-secret"))
        assertFalse(error.stackTraceToString().contains("sk-secret"))
    }

    @Test
    fun `exception hierarchy exposes typed metadata`() {
        val auth = WiroAuthenticationException("Unauthorized", 401, "{}")
        val validation = WiroValidationException("Invalid", 422, "{}")
        val rateLimit =
            WiroRateLimitException(
                message = "Slow down",
                retryAfter = 2.seconds,
                rawResponseBody = "{}",
            )
        val unknown = WiroUnknownApiException("Unexpected", 503, "{}")
        val network = WiroNetworkException("Offline", "IOException")
        val socket = WiroWebSocketException("Closed", "SocketException")
        val timeout = WiroTimeoutException("Deadline", 10.seconds)

        assertEquals(401, auth.statusCode)
        assertEquals(422, validation.statusCode)
        assertEquals(429, rateLimit.statusCode)
        assertEquals(2.seconds, rateLimit.retryAfter)
        assertEquals(503, unknown.statusCode)
        assertEquals("IOException", network.underlyingType)
        assertEquals("SocketException", socket.underlyingType)
        assertEquals(10.seconds, timeout.timeout)
        assertNull(network.rawResponseBody)
    }

    @Test
    fun `schema messages are immutable and safely rendered`() {
        val source = mutableListOf("prompt is required", "width is invalid")
        val error = WiroSchemaValidationException(source)
        source.clear()

        assertEquals(2, error.messages.size)
        assertEquals(
            "prompt is required; width is invalid",
            error.message,
        )
        assertEquals(
            "Schema validation failed.",
            WiroSchemaValidationException(emptyList()).message,
        )
    }

    @Test
    fun `coroutine cancellation remains native`() {
        val cancellation = CancellationException("stop")
        val thrown = runCatching { throw cancellation }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertTrue(thrown !is WiroException)
    }
}
