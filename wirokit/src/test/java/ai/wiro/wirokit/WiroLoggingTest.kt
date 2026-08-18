package ai.wiro.wirokit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

class WiroLoggingTest {
    @Test
    fun `log levels retain severity ordering`() {
        assertTrue(WiroLogLevel.DEBUG < WiroLogLevel.INFO)
        assertTrue(WiroLogLevel.INFO < WiroLogLevel.WARNING)
        assertTrue(WiroLogLevel.WARNING < WiroLogLevel.ERROR)
    }

    @Test
    fun `logger receives structured safe event`() {
        var received: WiroLogEvent? = null
        val logger = WiroLogger { received = it }
        val event =
            WiroLogEvent(
                level = WiroLogLevel.WARNING,
                message = "Retrying request after transient failure.",
                method = "POST",
                url = "https://api.wiro.ai/v1/Tool/List",
                statusCode = 503,
                duration = 25.milliseconds,
                retryCount = 1,
                error = "Service unavailable.",
            )

        logger.log(event)

        assertEquals(event, received)
        assertFalse(event.toString().contains("x-api-key"))
    }

    @Test
    fun `sensitive headers and URL components are redacted`() {
        val redactedHeaders =
            WiroRedaction.headers(
                mapOf(
                    "Authorization" to "Bearer secret",
                    "x-api-key" to "key",
                    "x-signature" to "signature",
                    "Accept" to "application/json",
                ),
            )
        val safeUrl =
            WiroRedaction.url(
                URI("https://user:pass@example.com/path?token=secret#fragment"),
            )

        assertEquals("[REDACTED]", redactedHeaders["Authorization"])
        assertEquals("[REDACTED]", redactedHeaders["x-api-key"])
        assertEquals("application/json", redactedHeaders["Accept"])
        assertFalse(redactedHeaders.toString().contains("secret"))
        assertEquals("https://example.com/path", safeUrl)
    }

    @Test
    fun `throwable redaction retains type only`() {
        val type =
            WiroRedaction.throwableType(
                IllegalStateException("apiKey=secret"),
            )

        assertEquals("IllegalStateException", type)
        assertFalse(type.contains("secret"))
    }

    @Test
    fun `auth modes expose exact supported variants`() {
        assertEquals(
            setOf(
                WiroAuthType.API_KEY,
                WiroAuthType.SIGNATURE,
                WiroAuthType.PROXY,
            ),
            WiroAuthType.entries.toSet(),
        )
    }
}
