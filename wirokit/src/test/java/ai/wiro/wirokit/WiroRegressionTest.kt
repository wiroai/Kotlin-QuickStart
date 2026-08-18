package ai.wiro.wirokit

import android.net.Uri
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Regression tests for model equality, JSON helpers, typed tracking,
 * ContentUri resolution, and transport retries.
 */
class WiroRegressionTest {
    @Test
    fun `every known task status parses and exposes apiValue`() {
        val wire =
            listOf(
                "task_queue",
                "task_accept",
                "task_preprocess_start",
                "task_preprocess_end",
                "task_assign",
                "task_start",
                "task_output",
                "task_output_full",
                "task_error",
                "task_error_full",
                "task_end",
                "task_postprocess_start",
                "task_postprocess_end",
                "task_cancel",
                "task_stream_ready",
                "task_stream_end",
            )
        wire.forEach { raw ->
            val status = WiroTaskStatus.parse(raw)
            assertEquals(raw, status.apiValue)
            assertEquals(
                status is WiroTaskStatus.Completed ||
                    status is WiroTaskStatus.Cancelled,
                status.isTerminal,
            )
        }
        val unknown = WiroTaskStatus.parse("task_future")
        assertTrue(unknown is WiroTaskStatus.Unknown)
        assertEquals(unknown, WiroTaskStatus.Unknown("task_future"))
        assertEquals(
            unknown.hashCode(),
            WiroTaskStatus.Unknown("task_future").hashCode(),
        )
    }

    @Test
    fun `task and model equality cover all fields`() {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val taskA =
            WiroTask(
                id = WiroTaskId("1"),
                taskToken = WiroTaskToken("tok"),
                parameters = mapOf("p" to WiroValue.StringValue("v")),
                status = WiroTaskStatus.Completed,
                statusRawValue = "task_postprocess_end",
                exitCode = 0,
                debugOutput = "dbg",
                startTime = now,
                endTime = now,
                elapsed = 1.5.seconds,
                totalCost = 0.25,
                outputs = emptyList(),
                modelDescription = "desc",
                modelOwner = "owner",
                modelSlug = "slug",
                raw = mapOf("id" to WiroValue.StringValue("1")),
            )
        val taskB =
            WiroTask(
                id = WiroTaskId("1"),
                taskToken = WiroTaskToken("tok"),
                parameters = mapOf("p" to WiroValue.StringValue("v")),
                status = WiroTaskStatus.Completed,
                statusRawValue = "task_postprocess_end",
                exitCode = 0,
                debugOutput = "dbg",
                startTime = now,
                endTime = now,
                elapsed = 1.5.seconds,
                totalCost = 0.25,
                outputs = emptyList(),
                modelDescription = "desc",
                modelOwner = "owner",
                modelSlug = "slug",
                raw = mapOf("id" to WiroValue.StringValue("1")),
            )
        assertEquals(taskA, taskB)
        assertEquals(taskA.hashCode(), taskB.hashCode())
        assertTrue(taskA.isFinished)
        assertTrue(taskA.isSuccessful)
        assertNotEquals(
            taskA,
            taskA.copyish(exitCode = 1),
        )

        val stats = WiroModelTaskStats(1, 1, 0, now)
        assertEquals(stats, WiroModelTaskStats(1, 1, 0, now))
        assertEquals(
            stats.hashCode(),
            WiroModelTaskStats(1, 1, 0, now).hashCode(),
        )

        val modelA =
            WiroModel(
                id = "m1",
                owner = "owner",
                slug = "slug",
                title = "t",
                description = "d",
                seoDescription = "seo",
                imageUrl = java.net.URI("https://cdn.example/i.png"),
                categories = listOf("c"),
                tags = listOf("tag"),
                samples = listOf("s"),
                computingTime = "1s",
                approximateCost = "0.1",
                dynamicPrice = "dyn",
                cps = "cps",
                taskStats = stats,
                raw = mapOf("id" to WiroValue.StringValue("m1")),
            )
        val modelB =
            WiroModel(
                id = "m1",
                owner = "owner",
                slug = "slug",
                title = "t",
                description = "d",
                seoDescription = "seo",
                imageUrl = java.net.URI("https://cdn.example/i.png"),
                categories = listOf("c"),
                tags = listOf("tag"),
                samples = listOf("s"),
                computingTime = "1s",
                approximateCost = "0.1",
                dynamicPrice = "dyn",
                cps = "cps",
                taskStats = stats,
                raw = mapOf("id" to WiroValue.StringValue("m1")),
            )
        assertEquals(modelA, modelB)
        assertEquals(modelA.hashCode(), modelB.hashCode())
        assertEquals(WiroModelId("owner", "slug"), modelA.modelId)
    }

    @Test
    fun `json reader covers coercion and nested edge cases`() {
        assertEquals(
            "2.5",
            WiroJsonReader.string(WiroValue.NumberValue("2.5")),
        )
        assertEquals(
            "true",
            WiroJsonReader.string(WiroValue.BooleanValue(true)),
        )
        assertEquals(
            "false",
            WiroJsonReader.string(WiroValue.BooleanValue(false)),
        )
        assertTrue(
            WiroJsonReader.boolean(WiroValue.StringValue("1")) == true,
        )
        assertFalse(
            WiroJsonReader.boolean(WiroValue.StringValue("0")) == true,
        )
        assertTrue(
            WiroJsonReader.boolean(WiroValue.NumberValue("1")) == true,
        )
        assertFalse(
            WiroJsonReader.boolean(WiroValue.NumberValue("0")) == true,
        )
        assertNull(
            WiroJsonReader.boolean(WiroValue.StringValue("maybe")),
        )
        assertTrue(
            WiroJsonReader.boolean(
                WiroValue.ArrayValue(emptyList()),
                fallback = true,
            ) == true,
        )
        assertTrue(
            WiroJsonReader
                .values(
                    mapOf("k" to WiroValue.ArrayValue(listOf(WiroValue.StringValue("a")))),
                    "k",
                ).isNotEmpty(),
        )
        assertNull(WiroJsonReader.url(WiroValue.StringValue("   ")))
        assertNull(WiroJsonReader.url(WiroValue.StringValue(":// bad")))
        assertNotNull(
            WiroJsonReader.date(WiroValue.StringValue("1700000000000")),
        )
        assertNull(WiroJsonReader.date(WiroValue.NumberValue("1e999")))

        val malformed = mutableListOf<String>()
        val handler =
            WiroJsonReader.MalformedJsonHandler {
                malformed += it
            }
        assertTrue(
            WiroJsonReader
                .map(
                    WiroValue.StringValue(""),
                    handler,
                )!!
                .isEmpty(),
        )
        assertTrue(
            WiroJsonReader
                .map(
                    WiroValue.StringValue("[]"),
                    handler,
                )!!
                .isEmpty(),
        )
        assertTrue(
            WiroJsonReader
                .map(
                    WiroValue.StringValue("{not-json"),
                    handler,
                )!!
                .isEmpty(),
        )
        assertTrue(malformed.size >= 3)
        assertTrue(
            WiroJsonReader
                .objects(
                    WiroValue.ArrayValue(
                        listOf(
                            WiroValue.ObjectValue(emptyMap()),
                            WiroValue.StringValue("x"),
                        ),
                    ),
                ).isEmpty(),
        )
    }

    @Test
    fun `typed subscribe and subscribeStream overloads work`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":true,"taskid":"1","socketaccesstoken":"tok"}""",
        )
        transport.enqueueJson(
            200,
            """{"tasklist":[{"taskid":"1","status":"task_postprocess_end","pexit":"0"}]}""",
        )
        val client = testClient(transport)
        val request = Wiro.flux2Pro(prompt = "lake", width = 1024)

        val result = client.subscribe(request, timeout = 60.seconds)
        assertTrue(result is WiroTaskResult.Success)

        transport.enqueueJson(
            200,
            """{"result":true,"taskid":"2","socketaccesstoken":"tok2"}""",
        )
        transport.enqueueJson(
            200,
            """{"tasklist":[{"taskid":"2","status":"task_postprocess_end","pexit":"0"}]}""",
        )
        val updates = client.subscribeStream(request, timeout = 60.seconds)
        assertTrue(updates.toList().isNotEmpty())
    }

    @Test
    fun `content uri resolution and upload error paths`() {
        runBlocking {
            val transport = FakeHttpTransport()
            val client = testClient(transport)
            val uri = Mockito.mock(Uri::class.java)

            assertThrows(WiroValidationException::class.java) {
                runBlocking {
                    client.resolveFileInputs(
                        mapOf(
                            "image" to
                                WiroValue.FileInputValue(
                                    WiroFileInput.ContentUri(uri = uri),
                                ),
                        ),
                        contentSource = null,
                    )
                }
            }

            assertThrows(WiroValidationException::class.java) {
                runBlocking {
                    client.uploadFile(
                        input = WiroFileInput.ContentUri(uri = uri),
                        contentSource =
                        WiroUriContentSource {
                            throw SecurityException("denied")
                        },
                    )
                }
            }
            assertThrows(WiroValidationException::class.java) {
                runBlocking {
                    client.uploadFile(
                        input = WiroFileInput.ContentUri(uri = uri),
                        contentSource =
                        WiroUriContentSource {
                            throw IllegalStateException("boom")
                        },
                    )
                }
            }

            transport.enqueueJson(
                200,
                """{"result":true,"list":[{"url":"https://cdn.wiro.ai/a.png"}]}""",
            )
            transport.enqueueJson(
                200,
                """{"result":true,"list":[]}""",
            )
            val source =
                WiroUriContentSource {
                    ByteArrayInputStream(byteArrayOf(1, 2, 3))
                }
            val nested =
                client.resolveFileInputs(
                    mapOf(
                        "wrap" to
                            WiroValue.ObjectValue(
                                mapOf(
                                    "image" to
                                        WiroValue.FileInputValue(
                                            WiroFileInput.ContentUri(
                                                uri = uri,
                                                fileName = "a.png",
                                            ),
                                        ),
                                ),
                            ),
                        "list" to
                            WiroValue.ArrayValue(
                                listOf(WiroValue.StringValue("plain")),
                            ),
                    ),
                    contentSource = source,
                )
            assertTrue(nested["wrap"] is WiroValue.ObjectValue)

            assertThrows(WiroUnknownApiException::class.java) {
                runBlocking {
                    client.resolveFileInputs(
                        mapOf(
                            "image" to
                                WiroValue.FileInputValue(
                                    WiroFileInput.ContentUri(
                                        uri = uri,
                                        fileName = "b.png",
                                    ),
                                ),
                        ),
                        contentSource = source,
                    )
                }
            }
        }
    }

    @Test
    fun `transport throws are retried then succeed`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueue {
            throw WiroTimeoutException("slow", timeout = 1.seconds)
        }
        transport.enqueue { throw RuntimeException("wire cut") }
        transport.enqueueJson(
            200,
            """{"result":true,"list":[]}""",
        )
        val delays = mutableListOf<kotlin.time.Duration>()
        val client =
            testClient(
                transport = transport,
                delays = delays,
                retryPolicy = WiroRetryPolicy.Default,
            )

        val models = client.searchModels("x")
        assertNotNull(models)
        assertTrue(delays.isNotEmpty())
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `upload transport failure surfaces without retry`() {
        val transport = FakeHttpTransport()
        // /File/Upload is not retryable — first throw should surface.
        transport.enqueue {
            throw WiroTimeoutException("slow-upload", timeout = 1.seconds)
        }
        val client = testClient(transport)
        assertThrows(WiroTimeoutException::class.java) {
            runBlocking {
                client.uploadFile(byteArrayOf(1), "a.png")
            }
        }
    }

    @Test
    fun `value objects preserve equality and output type semantics`() {
        val contentA =
            WiroTaskOutputContent(
                prompt = "p",
                rawText = "r",
                thinking = listOf("t"),
                answers = listOf("a"),
            )
        val contentB =
            WiroTaskOutputContent.parse(
                mapOf(
                    "prompt" to WiroValue.StringValue("p"),
                    "raw" to WiroValue.StringValue("r"),
                    "thinking" to
                        WiroValue.ArrayValue(
                            listOf(WiroValue.StringValue("t")),
                        ),
                    "answer" to
                        WiroValue.ArrayValue(
                            listOf(WiroValue.StringValue("a")),
                        ),
                ),
            )
        assertEquals(contentA, contentB)
        assertEquals(contentA.hashCode(), contentB.hashCode())

        val output =
            WiroTaskOutput.parse(
                mapOf(
                    "name" to WiroValue.StringValue("out.png"),
                    "contenttype" to WiroValue.StringValue("image/png"),
                    "size" to WiroValue.NumberValue("10"),
                    "url" to WiroValue.StringValue("https://cdn.example/o.png"),
                    "content" to
                        WiroValue.ObjectValue(
                            mapOf("prompt" to WiroValue.StringValue("p")),
                        ),
                ),
            )
        assertTrue(output.isImage)
        assertFalse(output.isVideo)
        assertFalse(output.isAudio)
        assertFalse(output.isText)
        assertEquals(
            output,
            WiroTaskOutput(
                name = output.name,
                contentType = output.contentType,
                size = output.size,
                url = output.url,
                content = output.content,
                raw = output.raw,
            ),
        )
        assertEquals(
            output.hashCode(),
            WiroTaskOutput(
                name = output.name,
                contentType = output.contentType,
                size = output.size,
                url = output.url,
                content = output.content,
                raw = output.raw,
            ).hashCode(),
        )
        assertTrue(
            WiroTaskOutput(
                contentType = "text/plain",
                raw = emptyMap(),
            ).isText,
        )
        assertTrue(
            WiroTaskOutput(
                contentType = "video/mp4",
                raw = emptyMap(),
            ).isVideo,
        )
        assertTrue(
            WiroTaskOutput(
                contentType = "audio/mpeg",
                raw = emptyMap(),
            ).isAudio,
        )

        val run =
            WiroRunResult.parse(
                mapOf(
                    "result" to WiroValue.BooleanValue(true),
                    "taskid" to WiroValue.StringValue("1"),
                    "socketaccesstoken" to WiroValue.StringValue("tok"),
                ),
            )
        assertEquals(
            run,
            WiroRunResult(
                isSuccess = true,
                taskId = WiroTaskId("1"),
                taskToken = WiroTaskToken("tok"),
                errors = emptyList(),
                raw = run.raw,
            ),
        )
        assertEquals(
            run.hashCode(),
            WiroRunResult(
                isSuccess = true,
                taskId = WiroTaskId("1"),
                taskToken = WiroTaskToken("tok"),
                errors = emptyList(),
                raw = run.raw,
            ).hashCode(),
        )

        val uploaded =
            WiroUploadedFile.parse(
                mapOf(
                    "id" to WiroValue.StringValue("f1"),
                    "name" to WiroValue.StringValue("a.png"),
                    "contenttype" to WiroValue.StringValue("image/png"),
                    "size" to WiroValue.NumberValue("3"),
                    "url" to WiroValue.StringValue("https://cdn.example/a.png"),
                ),
            )
        assertEquals(
            uploaded,
            WiroUploadedFile(
                id = "f1",
                name = "a.png",
                contentType = "image/png",
                size = 3,
                url = uploaded.url,
                raw = uploaded.raw,
            ),
        )
        assertEquals(
            uploaded.hashCode(),
            WiroUploadedFile(
                id = "f1",
                name = "a.png",
                contentType = "image/png",
                size = 3,
                url = uploaded.url,
                raw = uploaded.raw,
            ).hashCode(),
        )
        assertTrue(uploaded.toString().contains("f1"))

        val upload =
            WiroUploadResult.parse(
                mapOf(
                    "result" to WiroValue.BooleanValue(true),
                    "list" to
                        WiroValue.ArrayValue(
                            listOf(WiroValue.ObjectValue(uploaded.raw)),
                        ),
                ),
            )
        assertEquals(
            upload,
            WiroUploadResult(
                isSuccess = true,
                files = upload.files,
                errors = emptyList(),
                raw = upload.raw,
            ),
        )
        assertEquals(
            upload.hashCode(),
            WiroUploadResult(
                isSuccess = true,
                files = upload.files,
                errors = emptyList(),
                raw = upload.raw,
            ).hashCode(),
        )

        val category =
            WiroExploreCategory.parse(
                mapOf(
                    "id" to WiroValue.StringValue("c1"),
                    "name" to WiroValue.StringValue("Cats"),
                    "total" to WiroValue.NumberValue("0"),
                    "url" to WiroValue.StringValue("https://wiro.ai/c"),
                    "tools" to WiroValue.ArrayValue(emptyList()),
                ),
            )
        assertEquals(
            category,
            WiroExploreCategory(
                id = "c1",
                title = "Cats",
                models = emptyList(),
                total = 0,
                url = category.url,
                raw = category.raw,
            ),
        )
        assertEquals(
            category.hashCode(),
            WiroExploreCategory(
                id = "c1",
                title = "Cats",
                models = emptyList(),
                total = 0,
                url = category.url,
                raw = category.raw,
            ).hashCode(),
        )

        val info =
            WiroModelParameterInfo(
                name = "prompt",
                label = "Prompt",
                description = "d",
                isRequired = true,
                placeholder = "ph",
                note = "n",
                raw = mapOf("name" to WiroValue.StringValue("prompt")),
            )
        assertEquals(
            info,
            WiroModelParameterInfo(
                name = "prompt",
                label = "Prompt",
                description = "d",
                isRequired = true,
                placeholder = "ph",
                note = "n",
                raw = info.raw,
            ),
        )
        assertEquals(
            info.hashCode(),
            WiroModelParameterInfo(
                name = "prompt",
                label = "Prompt",
                description = "d",
                isRequired = true,
                placeholder = "ph",
                note = "n",
                raw = info.raw,
            ).hashCode(),
        )
        val option =
            WiroModelParameterOption.parse(
                mapOf(
                    "label" to WiroValue.StringValue("L"),
                    "value" to WiroValue.StringValue("v"),
                ),
            )
        assertEquals(option, WiroModelParameterOption("L", "v"))
        assertEquals(option.hashCode(), WiroModelParameterOption("L", "v").hashCode())

        val select =
            WiroModelParameter.Select(
                info = info,
                options = listOf(option),
                defaultValue = "v",
            )
        assertEquals(
            select,
            WiroModelParameter.Select(info, listOf(option), "v"),
        )
        assertEquals(
            select.hashCode(),
            WiroModelParameter.Select(info, listOf(option), "v").hashCode(),
        )
        val number =
            WiroModelParameter.Number(
                info = info,
                defaultValue = 1.0,
                minimum = 0.0,
                maximum = 2.0,
                step = 0.5,
            )
        assertEquals(
            number,
            WiroModelParameter.Number(info, 1.0, 0.0, 2.0, 0.5),
        )
        assertEquals(
            number.hashCode(),
            WiroModelParameter.Number(info, 1.0, 0.0, 2.0, 0.5).hashCode(),
        )

        val failedTask =
            WiroTask(
                status = WiroTaskStatus.Completed,
                statusRawValue = "task_postprocess_end",
                exitCode = 2,
                raw = emptyMap(),
            )
        val failure = WiroTaskResult.from(failedTask)
        assertTrue(failure is WiroTaskResult.Failure)
        assertEquals(
            failure,
            WiroTaskResult.Failure(
                failedTask,
                WiroTaskFailureReason.NON_ZERO_EXIT,
            ),
        )
        assertEquals(
            failure.hashCode(),
            WiroTaskResult
                .Failure(
                    failedTask,
                    WiroTaskFailureReason.NON_ZERO_EXIT,
                ).hashCode(),
        )
        val cancelled =
            WiroTaskResult.from(
                WiroTask(
                    status = WiroTaskStatus.Cancelled,
                    statusRawValue = "task_cancel",
                    raw = emptyMap(),
                ),
            )
        assertEquals(
            WiroTaskFailureReason.CANCELLED,
            (cancelled as WiroTaskResult.Failure).reason,
        )
    }

    @Test
    fun `limits equality and owned transport close`() {
        assertEquals(WiroClientLimits.Default, WiroClientLimits())
        assertEquals(
            WiroClientLimits.Default.hashCode(),
            WiroClientLimits().hashCode(),
        )
        assertThrows(WiroValidationException::class.java) {
            WiroClientLimits(maxRestBodyBytes = 0)
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClientLimits(maxWebSocketTextBytes = -1)
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClientLimits(maxWebSocketBinaryBytes = 0)
        }
        assertThrows(WiroValidationException::class.java) {
            WiroClientLimits(maxInMemoryUploadBytes = 0)
        }

        val owned = WiroClient(apiKey = "k")
        owned.close()
        assertThrows(WiroValidationException::class.java) {
            runBlocking {
                owned.searchModels()
            }
        }

        val proxy =
            WiroClient(
                proxyUrl = "https://proxy.example.com/v1",
                headers = mapOf("Authorization" to "Bearer x"),
            )
        proxy.close()
    }

    @Test
    fun `task parse accepts nonfinite elapsed as null`() {
        val task =
            WiroTask.parse(
                mapOf(
                    "status" to WiroValue.StringValue("task_start"),
                    "elapsedseconds" to WiroValue.NumberValue("1e999"),
                ),
            )
        assertNull(task.elapsed)
    }

    @Test
    fun `model parse falls back to slug fields`() {
        val model =
            WiroModel.parse(
                mapOf(
                    "id" to WiroValue.StringValue("1"),
                    "slugowner" to WiroValue.StringValue("owner"),
                    "slugproject" to WiroValue.StringValue("project"),
                    "taskstat" to
                        WiroValue.ObjectValue(
                            mapOf(
                                "runcount" to WiroValue.NumberValue("2"),
                                "successcount" to WiroValue.NumberValue("1"),
                                "errorcount" to WiroValue.NumberValue("1"),
                            ),
                        ),
                ),
            )
        assertEquals("owner", model.owner)
        assertEquals("project", model.slug)
        assertEquals(2, model.taskStats?.runCount)
    }

    private fun WiroTask.copyish(exitCode: Int): WiroTask = WiroTask(
        id = id,
        taskToken = taskToken,
        parameters = parameters,
        status = status,
        statusRawValue = statusRawValue,
        exitCode = exitCode,
        debugOutput = debugOutput,
        startTime = startTime,
        endTime = endTime,
        elapsed = elapsed,
        totalCost = totalCost,
        outputs = outputs,
        modelDescription = modelDescription,
        modelOwner = modelOwner,
        modelSlug = modelSlug,
        raw = raw,
    )
}
