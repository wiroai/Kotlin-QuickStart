package ai.wiro.quickstart.core.error

import ai.wiro.wirokit.WiroAuthenticationException
import ai.wiro.wirokit.WiroRateLimitException
import ai.wiro.wirokit.WiroSchemaValidationException
import ai.wiro.wirokit.WiroValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class FriendlyMessagesTest {
    @Test
    fun `maps known exceptions without leaking bodies`() {
        val auth =
            WiroAuthenticationException(
                message = "bad key",
                rawResponseBody = "secret-body",
            )
        val message = auth.toFriendlyMessage()
        assertTrue(message.contains("Authentication failed"))
        assertFalse(message.contains("secret-body"))

        assertEquals(
            "Invalid request: bad prompt",
            WiroValidationException("bad prompt", statusCode = 0)
                .toFriendlyMessage(),
        )
        assertEquals(
            "Rate limited. Try again in 5s.",
            WiroRateLimitException(
                message = "slow down",
                retryAfter = 5.seconds,
            ).toFriendlyMessage(),
        )
        assertEquals(
            "Schema validation failed: a; b",
            WiroSchemaValidationException(listOf("a", "b"))
                .toFriendlyMessage(),
        )
        assertEquals(
            CANCELLED_MESSAGE,
            CancellationException("stop").toFriendlyMessage(),
        )
    }
}
