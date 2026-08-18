package ai.wiro.consumer

import ai.wiro.wirokit.Wiro
import ai.wiro.wirokit.WiroClient
import ai.wiro.wirokit.WiroTaskTrackingMode
import ai.wiro.wirokit.WiroValidationException
import ai.wiro.wirokit.subscribeStream
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * Clean consumer that depends only on the published `ai.wiro:wirokit`
 * Maven artifact (resolved from `mavenLocal()` after
 * `./gradlew :wirokit:publishToMavenLocal`).
 */
class ConsumerFixtureTest {
    @Test
    fun `published artifact constructs a client`() {
        val client = WiroClient(apiKey = "fixture-api-key")
        client.close()
    }

    @Test
    fun `validation errors are typed and catchable`() {
        assertThrows(WiroValidationException::class.java) {
            WiroClient(apiKey = "   ")
        }
    }

    @Test
    fun `flow collection can be cancelled cooperatively`() = runTest {
        val client = WiroClient(apiKey = "fixture-api-key")
        try {
            val request =
                Wiro.flux2Pro(
                    prompt = "consumer fixture",
                    width = 1024,
                    height = 1024,
                )
            val job =
                launch {
                    try {
                        client
                            .subscribeStream(
                                request = request,
                                trackingMode = WiroTaskTrackingMode.POLLING,
                            ).catch { error ->
                                if (error is CancellationException) throw error
                                // Network/auth failures are expected without a
                                // live backend; the fixture only proves the API
                                // surface resolves and cancellation works.
                            }.collect { }
                    } catch (_: CancellationException) {
                        // Expected when the parent job is cancelled.
                    } catch (_: Throwable) {
                        // Expected without live credentials/network.
                    }
                }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        } finally {
            client.close()
        }
    }

    @Test
    fun `async work is cancelled without leaking the client`() = runTest {
        val client = WiroClient(apiKey = "fixture-api-key")
        val deferred =
            async {
                try {
                    client
                        .subscribeStream(
                            Wiro.flux2Pro(
                                prompt = "cancel me",
                                width = 1024,
                                height = 1024,
                            ),
                        ).collect { }
                } catch (_: CancellationException) {
                    throw CancellationException("cancelled by fixture")
                } catch (_: Throwable) {
                    // ignore transport failures
                }
            }
        deferred.cancel()
        try {
            deferred.await()
        } catch (_: CancellationException) {
            // ok
        }
        client.close()
        assertTrue(deferred.isCancelled)
    }
}
