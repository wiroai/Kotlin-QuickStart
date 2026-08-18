package ai.wiro.wirokit

import kotlinx.coroutines.delay
import java.util.Collections
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

public enum class WiroAuthType {
    API_KEY,
    SIGNATURE,
    PROXY,
}

public class WiroRetryPolicy(
    public val maxRetries: Int,
    public val initialDelay: Duration,
    public val maximumDelay: Duration,
    public val multiplier: Double,
    retryableStatusCodes: Set<Int>,
    public val minimumJitterFactor: Double = 0.8,
    public val maximumJitterFactor: Double = 1.2,
) {
    public val retryableStatusCodes: Set<Int> =
        Collections.unmodifiableSet(retryableStatusCodes.toSet())

    init {
        if (maxRetries < 0) {
            throw WiroValidationException(
                "maxRetries must not be negative.",
                statusCode = 0,
            )
        }
        WiroValidation.requireNonNegativeDuration(
            initialDelay,
            "initialDelay",
        )
        WiroValidation.requireNonNegativeDuration(
            maximumDelay,
            "maximumDelay",
        )
        if (!multiplier.isFinite() || multiplier <= 0.0) {
            throw WiroValidationException(
                "multiplier must be finite and greater than zero.",
                statusCode = 0,
            )
        }
        if (
            !minimumJitterFactor.isFinite() ||
            !maximumJitterFactor.isFinite() ||
            minimumJitterFactor < 0.0 ||
            minimumJitterFactor > maximumJitterFactor
        ) {
            throw WiroValidationException(
                "Invalid jitter factor range.",
                statusCode = 0,
            )
        }
    }

    public fun delayForRetry(
        retryIndex: Int,
        jitterFactor: Double,
    ): Duration {
        val exponent = retryIndex.coerceAtLeast(0)
        val baseNanos =
            initialDelay.inWholeNanoseconds.toDouble() *
                multiplier.pow(exponent)
        val cappedNanos =
            minOf(
                baseNanos,
                maximumDelay.inWholeNanoseconds.toDouble(),
            )
        val jitter =
            jitterFactor.coerceIn(
                minimumJitterFactor,
                maximumJitterFactor,
            )
        return (cappedNanos * jitter)
            .coerceAtLeast(0.0)
            .roundToLong()
            .nanoseconds
    }

    public fun shouldRetry(statusCode: Int): Boolean = statusCode in retryableStatusCodes

    override fun equals(other: Any?): Boolean = other is WiroRetryPolicy &&
        maxRetries == other.maxRetries &&
        initialDelay == other.initialDelay &&
        maximumDelay == other.maximumDelay &&
        multiplier == other.multiplier &&
        retryableStatusCodes == other.retryableStatusCodes &&
        minimumJitterFactor == other.minimumJitterFactor &&
        maximumJitterFactor == other.maximumJitterFactor

    override fun hashCode(): Int {
        var result = maxRetries
        result = 31 * result + initialDelay.hashCode()
        result = 31 * result + maximumDelay.hashCode()
        result = 31 * result + multiplier.hashCode()
        result = 31 * result + retryableStatusCodes.hashCode()
        result = 31 * result + minimumJitterFactor.hashCode()
        result = 31 * result + maximumJitterFactor.hashCode()
        return result
    }

    internal fun delayForRetry(
        retryIndex: Int,
        jitterProvider: WiroJitterProvider,
    ): Duration = delayForRetry(retryIndex, jitterProvider.nextFactor())

    public companion object {
        public val Default: WiroRetryPolicy =
            WiroRetryPolicy(
                maxRetries = 2,
                initialDelay = 500.milliseconds,
                maximumDelay = 4_000.milliseconds,
                multiplier = 2.0,
                retryableStatusCodes = setOf(408, 429, 500, 502, 503, 504),
            )

        public val None: WiroRetryPolicy =
            WiroRetryPolicy(
                maxRetries = 0,
                initialDelay = Duration.ZERO,
                maximumDelay = Duration.ZERO,
                multiplier = 1.0,
                retryableStatusCodes = emptySet(),
                minimumJitterFactor = 1.0,
                maximumJitterFactor = 1.0,
            )
    }
}

internal fun interface WiroClock {
    fun epochMilliseconds(): Long
}

internal fun interface WiroNonceProvider {
    fun nextNonce(): String
}

/**
 * Monotonic time source used for tracking deadlines.
 *
 * Wall-clock adjustments must never shorten or extend a timeout, so
 * deadlines are computed from this source instead of [WiroClock].
 */
internal fun interface WiroMonotonicClock {
    fun nanoTime(): Long
}

internal fun interface WiroDelay {
    suspend fun sleep(duration: Duration)
}

internal fun interface WiroJitterProvider {
    fun nextFactor(): Double
}

internal object WiroRuntimeDefaults {
    val clock: WiroClock = WiroClock(System::currentTimeMillis)
    val monotonicClock: WiroMonotonicClock =
        WiroMonotonicClock(System::nanoTime)
    val nonceProvider: WiroNonceProvider =
        WiroNonceProvider { clock.epochMilliseconds().toString() }
    val delay: WiroDelay = WiroDelay { duration -> delay(duration) }
    val jitterProvider: WiroJitterProvider =
        WiroJitterProvider {
            Random.nextDouble(0.8, 1.2)
        }
}
