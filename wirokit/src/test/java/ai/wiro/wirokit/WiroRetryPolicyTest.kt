package ai.wiro.wirokit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WiroRetryPolicyTest {
    @Test
    fun `default policy uses documented retry values`() {
        val policy = WiroRetryPolicy.Default

        assertEquals(2, policy.maxRetries)
        assertEquals(500.milliseconds, policy.initialDelay)
        assertEquals(4.seconds, policy.maximumDelay)
        assertEquals(2.0, policy.multiplier, 0.0)
        assertEquals(
            setOf(408, 429, 500, 502, 503, 504),
            policy
                .retryableStatusCodes,
        )
    }

    @Test
    fun `retry delays are deterministic and capped`() {
        val policy = WiroRetryPolicy.Default

        assertEquals(
            500.milliseconds,
            policy.delayForRetry(0, jitterFactor = 1.0),
        )
        assertEquals(
            1.seconds,
            policy.delayForRetry(1, jitterFactor = 1.0),
        )
        assertEquals(
            4.seconds,
            policy.delayForRetry(10, jitterFactor = 1.0),
        )
        assertEquals(
            400.milliseconds,
            policy.delayForRetry(0, jitterFactor = 0.1),
        )
        assertEquals(
            600.milliseconds,
            policy.delayForRetry(0, jitterFactor = 2.0),
        )
        assertEquals(
            500.milliseconds,
            policy.delayForRetry(-1, jitterFactor = 1.0),
        )
    }

    @Test
    fun `retry status lookup and none policy are stable`() {
        assertTrue(WiroRetryPolicy.Default.shouldRetry(429))
        assertFalse(WiroRetryPolicy.Default.shouldRetry(404))
        assertEquals(0, WiroRetryPolicy.None.maxRetries)
        assertEquals(Duration.ZERO, WiroRetryPolicy.None.initialDelay)
        assertTrue(WiroRetryPolicy.None.retryableStatusCodes.isEmpty())
    }

    @Test
    fun `injected jitter provider drives retry delay`() {
        val policy = WiroRetryPolicy.Default
        val fixed = WiroJitterProvider { 1.0 }

        assertEquals(
            1.seconds,
            policy.delayForRetry(1, fixed),
        )
    }

    @Test
    fun `invalid policy values are rejected`() {
        assertThrows(WiroValidationException::class.java) {
            policy(maxRetries = -1)
        }
        assertThrows(WiroValidationException::class.java) {
            policy(initialDelay = (-1).milliseconds)
        }
        assertThrows(WiroValidationException::class.java) {
            policy(multiplier = Double.NaN)
        }
        assertThrows(WiroValidationException::class.java) {
            policy(minimumJitterFactor = 1.3)
        }
    }

    private fun policy(
        maxRetries: Int = 1,
        initialDelay: Duration = 1.seconds,
        multiplier: Double = 2.0,
        minimumJitterFactor: Double = 0.8,
    ): WiroRetryPolicy = WiroRetryPolicy(
        maxRetries = maxRetries,
        initialDelay = initialDelay,
        maximumDelay = 4.seconds,
        multiplier = multiplier,
        retryableStatusCodes = setOf(500),
        minimumJitterFactor = minimumJitterFactor,
        maximumJitterFactor = 1.2,
    )
}
