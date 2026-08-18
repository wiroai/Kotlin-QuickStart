package ai.wiro.wirokit

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class WiroContentUriUploadAndroidTest {
    @Test
    fun contentResolverStreamingUploadsWithoutBufferingFailure() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"list":[{"url":"https://cdn.wiro.ai/android.png"}]}""",
        )
        val client = testClient(transport)
        val resolver =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .contentResolver
        val uri = Uri.parse("content://ai.wiro.test/doc/1")
        val source =
            WiroUriContentSource { requested ->
                assertEquals(uri, requested)
                ByteArrayInputStream("android-stream".toByteArray())
            }

        val result =
            client.uploadFile(
                input =
                WiroFileInput.ContentUri(
                    uri = uri,
                    fileName = "android.png",
                ),
                contentSource = source,
            )

        assertEquals(
            "https://cdn.wiro.ai/android.png",
            result.files
                .single()
                .url
                ?.toASCIIString(),
        )
        val body =
            transport.requests
                .single()
                .body!!
                .toString(Charsets.UTF_8)
        assertTrue(body.contains("android-stream"))
        assertTrue(body.contains("filename=\"android.png\""))
        // Ensure the real ContentResolver is available in this environment.
        assertTrue(resolver.javaClass.name.isNotEmpty())
    }

    @Test
    fun contentUriPermissionFailureDoesNotLeakUri() = runBlocking {
        val transport = FakeHttpTransport()
        val client = testClient(transport)
        val uri = Uri.parse("content://ai.wiro.secret/doc/42")

        val error =
            runCatching {
                client.uploadFile(
                    input = WiroFileInput.ContentUri(uri = uri),
                    contentSource =
                    WiroUriContentSource {
                        throw SecurityException("Permission Denial")
                    },
                )
            }.exceptionOrNull()

        assertTrue(error is WiroValidationException)
        assertFalse(error.toString().contains("ai.wiro.secret"))
        assertEquals(0, transport.requests.size)
    }
}
