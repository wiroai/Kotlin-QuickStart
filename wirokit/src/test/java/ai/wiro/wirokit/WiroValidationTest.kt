package ai.wiro.wirokit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WiroValidationTest {
    @Test
    fun `http and websocket URLs enforce safe components`() {
        val http =
            WiroValidation.validateUrl(
                value = "https://api.wiro.ai/v1",
                kind = WiroUrlKind.HTTP,
                label = "baseUrl",
            )
        val socket =
            WiroValidation.validateUrl(
                value = "wss://socket.wiro.ai/v1",
                kind = WiroUrlKind.WEB_SOCKET,
                label = "socketUrl",
            )

        assertEquals("api.wiro.ai", http.host)
        assertEquals("socket.wiro.ai", socket.host)

        listOf(
            "ftp://api.wiro.ai/v1",
            "https://user:pass@api.wiro.ai/v1",
            "https://api.wiro.ai/v1?token=secret",
            "https://api.wiro.ai/v1#fragment",
            "https:///missing-host",
        ).forEach { value ->
            val error =
                assertThrows(WiroValidationException::class.java) {
                    WiroValidation.validateUrl(
                        value = value,
                        kind = WiroUrlKind.HTTP,
                        label = "baseUrl",
                    )
                }
            assertTrue(!error.message.orEmpty().contains("secret"))
        }
    }

    @Test
    fun `trailing URL slashes are removed`() {
        val value =
            WiroValidation.trimTrailingSlashes(
                URI("https://api.wiro.ai/v1///"),
            )

        assertEquals("https://api.wiro.ai/v1", value.toASCIIString())
    }

    @Test
    fun `duration validation rejects invalid boundaries`() {
        WiroValidation.requirePositiveDuration(
            1.milliseconds,
            "timeout",
        )
        WiroValidation.requireNonNegativeDuration(
            Duration.ZERO,
            "delay",
        )

        assertThrows(WiroValidationException::class.java) {
            WiroValidation.requirePositiveDuration(
                Duration.ZERO,
                "timeout",
            )
        }
        assertThrows(WiroValidationException::class.java) {
            WiroValidation.requireNonNegativeDuration(
                (-1).milliseconds,
                "delay",
            )
        }
        assertThrows(WiroValidationException::class.java) {
            WiroValidation.requirePositiveDuration(
                Duration.INFINITE,
                "timeout",
            )
        }
    }

    @Test
    fun `header validation rejects injection characters`() {
        WiroValidation.validateHeader("X-Request-ID", "safe-value")

        listOf("Bad Header", "X-Test\r\nInjected", "").forEach { name ->
            assertThrows(WiroValidationException::class.java) {
                WiroValidation.validateHeader(name, "safe")
            }
        }
        listOf("value\rnext", "value\nnext", "value\u0000next")
            .forEach { value ->
                assertThrows(WiroValidationException::class.java) {
                    WiroValidation.validateHeader("X-Test", value)
                }
            }
    }

    @Test
    fun `nested unresolved file input fails JSON validation`() {
        val value =
            WiroValue.ObjectValue(
                mapOf(
                    "nested" to
                        WiroValue.ArrayValue(
                            listOf(
                                WiroValue.FileInputValue(
                                    WiroFileInput.Bytes(
                                        byteArrayOf(1, 2),
                                        "private.png",
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        val error =
            assertThrows(WiroValidationException::class.java) {
                WiroValidation.requireResolvedJson(value)
            }

        assertTrue(!error.toString().contains("private.png"))
    }
}
