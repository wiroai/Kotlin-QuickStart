package ai.wiro.wirokit

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import kotlin.coroutines.cancellation.CancellationException

class WiroUploadTest {
    @Test
    fun `multipart framing matches fixed boundary fixture`() {
        val body =
            MultipartFormData.buildFilePart(
                data = "hello-bytes".toByteArray(Charsets.UTF_8),
                fileName = "photo.png",
                boundary = "Boundary-TESTFIXED0001",
            )
        val text = body.data.toString(Charsets.UTF_8)

        assertEquals(
            "multipart/form-data; boundary=Boundary-TESTFIXED0001",
            body.contentType,
        )
        assertTrue(text.contains("name=\"file\""))
        assertTrue(text.contains("filename=\"photo.png\""))
        assertTrue(text.contains("Content-Type: application/octet-stream"))
        assertTrue(text.contains("hello-bytes"))
        assertTrue(text.startsWith("--Boundary-TESTFIXED0001\r\n"))
        assertTrue(text.endsWith("--Boundary-TESTFIXED0001--\r\n"))
    }

    @Test
    fun `upload file name validation rejects unsafe values`() {
        assertThrows(WiroValidationException::class.java) {
            WiroClient.validatedUploadFileName("  ")
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient.validatedUploadFileName("a/b.png")
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient.validatedUploadFileName("a\\b.png")
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient.validatedUploadFileName("a\nb.png")
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClient.validatedUploadFileName("a".repeat(256))
        }
        assertEquals("shot.png", WiroClient.validatedUploadFileName(" shot.png "))
    }

    @Test
    fun `uploadFile posts multipart without json content type`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """
                {
                  "result": true,
                  "list": [{
                    "id": "f1",
                    "name": "shot.png",
                    "contenttype": "image/png",
                    "size": "4",
                    "url": "https://cdn.wiro.ai/shot.png"
                  }]
                }
            """.trimIndent(),
        )
        val client = testClient(transport)

        val result =
            client.uploadFile(
                data = byteArrayOf(1, 2, 3, 4),
                fileName = "shot.png",
            )

        val request = transport.requests.single()
        assertEquals("https://api.wiro.ai/v1/File/Upload", request.url)
        assertEquals("test-api-key", request.headers["x-api-key"])
        assertEquals(
            "WiroKit-Android/${WiroKitInfo.VERSION}",
            request.headers["User-Agent"],
        )
        assertTrue(
            request.headers["Content-Type"]
                ?.startsWith("multipart/form-data; boundary=") == true,
        )
        assertFalse(
            request.headers["Content-Type"]
                ?.startsWith("application/json") == true,
        )
        val body = request.body!!.toString(Charsets.UTF_8)
        assertTrue(body.contains("filename=\"shot.png\""))
        assertTrue(result.isSuccess)
        assertEquals(
            "https://cdn.wiro.ai/shot.png",
            result.files
                .single()
                .url
                ?.toASCIIString(),
        )
        assertEquals(4, result.files.single().size)
    }

    @Test
    fun `upload never retries`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(503, """{"message":"busy"}""")
        transport.enqueueJson(200, """{"result":true,"list":[]}""")
        val client = testClient(transport)

        val error =
            runCatching {
                client.uploadFile(byteArrayOf(1), "a.png")
            }.exceptionOrNull()

        assertTrue(error is WiroUnknownApiException)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `url file inputs skip upload while bytes are uploaded once each`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"list":[{"url":"https://cdn.wiro.ai/a.png"}]}""",
        )
        transport.enqueueJson(
            200,
            """{"result":true,"list":[{"url":"https://cdn.wiro.ai/b.png"}]}""",
        )
        transport.enqueueJson(
            200,
            """{"result":true,"taskid":"1","socketaccesstoken":"tok"}""",
        )
        val client = testClient(transport)

        client.runModel(
            modelId = WiroModelId("openai", "gpt-image-2"),
            parameters =
            mapOf(
                "payload" to
                    WiroValue.ObjectValue(
                        mapOf(
                            "items" to
                                WiroValue.ArrayValue(
                                    listOf(
                                        WiroValue.ObjectValue(
                                            mapOf(
                                                "image" to
                                                    WiroValue.FileInputValue(
                                                        WiroFileInput.Bytes(
                                                            byteArrayOf(1),
                                                            "a.png",
                                                        ),
                                                    ),
                                                "ref" to
                                                    WiroValue.FileInputValue(
                                                        WiroFileInput.Url(
                                                            java.net.URI(
                                                                "https://cdn.example.com/ref.png",
                                                            ),
                                                        ),
                                                    ),
                                            ),
                                        ),
                                        WiroValue.FileInputValue(
                                            WiroFileInput.Bytes(
                                                byteArrayOf(2),
                                                "b.png",
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
            ),
        )

        assertEquals(3, transport.requests.size)
        assertTrue(transport.requests[0].url.endsWith("/File/Upload"))
        assertTrue(transport.requests[1].url.endsWith("/File/Upload"))
        assertTrue(transport.requests[2].url.contains("/Run/"))
        val runBody =
            transport.requests[2]
                .body!!
                .toString(Charsets.UTF_8)
        assertTrue(runBody.contains("https://cdn.wiro.ai/a.png"))
        assertTrue(runBody.contains("https://cdn.example.com/ref.png"))
        assertTrue(runBody.contains("https://cdn.wiro.ai/b.png"))
        assertFalse(runBody.contains("FileInput"))
    }

    @Test
    fun `missing upload url prevents model run`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"list":[{"name":"x"}]}""",
        )
        val client =
            testClient(
                transport = transport,
                retryPolicy = WiroRetryPolicy.None,
            )

        val error =
            runCatching {
                client.runModel(
                    modelId = WiroModelId("openai", "gpt-image-2"),
                    parameters =
                    mapOf(
                        "image" to
                            WiroValue.FileInputValue(
                                WiroFileInput.Bytes(byteArrayOf(1), "x.png"),
                            ),
                    ),
                )
            }.exceptionOrNull()

        assertTrue(error is WiroUnknownApiException)
        assertTrue(
            error?.message.orEmpty().contains("did not return a file URL"),
        )
        assertEquals(1, transport.requests.size)
        assertTrue(
            transport.requests
                .single()
                .url
                .endsWith("/File/Upload"),
        )
    }

    @Test
    fun `content uri streams through content source`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"list":[{"url":"https://cdn.wiro.ai/uri.png"}]}""",
        )
        val client = testClient(transport)
        val uri = Mockito.mock(Uri::class.java)
        val source =
            WiroUriContentSource {
                ByteArrayInputStream("stream-me".toByteArray())
            }

        val result =
            client.uploadFile(
                input =
                WiroFileInput.ContentUri(
                    uri = uri,
                    fileName = "note.txt",
                ),
                contentSource = source,
            )

        assertEquals(
            "https://cdn.wiro.ai/uri.png",
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
        assertTrue(body.contains("filename=\"note.txt\""))
        assertTrue(body.contains("stream-me"))
    }

    @Test
    fun `content uri permission failures stay redacted`() = runBlocking {
        val transport = FakeHttpTransport()
        val client = testClient(transport)
        val uri = Mockito.mock(Uri::class.java)
        Mockito
            .`when`(uri.toString())
            .thenReturn("content://secret/provider/doc")

        val error =
            runCatching {
                client.uploadFile(
                    input = WiroFileInput.ContentUri(uri = uri),
                    contentSource = WiroUriContentSource { null },
                )
            }.exceptionOrNull()

        assertTrue(error is WiroValidationException)
        assertFalse(error.toString().contains("content://"))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `cancellation stops upload before run`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueue { throw CancellationException("stop") }
        val client = testClient(transport)

        val thrown =
            runCatching {
                client.runModel(
                    modelId = WiroModelId("openai", "gpt-image-2"),
                    parameters =
                    mapOf(
                        "image" to
                            WiroValue.FileInputValue(
                                WiroFileInput.Bytes(byteArrayOf(1), "a.png"),
                            ),
                    ),
                )
            }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(1, transport.requests.size)
        assertTrue(
            transport.requests
                .single()
                .url
                .endsWith("/File/Upload"),
        )
    }

    @Test
    fun `plain parameters skip upload entirely`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"taskid":"1","socketaccesstoken":"tok"}""",
        )
        val client = testClient(transport)

        client.runModel(
            modelId = WiroModelId("openai", "gpt-image-2"),
            parameters =
            mapOf(
                "prompt" to WiroValue.StringValue("lake"),
            ),
        )

        assertEquals(1, transport.requests.size)
        assertTrue(
            transport.requests
                .single()
                .url
                .contains("/Run/"),
        )
    }

    @Test
    fun `upload result toString redacts urls`() {
        val file =
            WiroUploadedFile(
                id = "f1",
                url = java.net.URI("https://cdn.wiro.ai/secret.png"),
                raw = emptyMap(),
            )

        assertFalse(file.toString().contains("secret.png"))
        assertNull(WiroFileInput.Bytes(byteArrayOf(1), "a.png").wireValue)
    }
}
