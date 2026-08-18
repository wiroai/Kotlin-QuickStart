package ai.wiro.wirokit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WiroDiscoveryParseTest {
    @Test
    fun `model parses stringified stats and clean slug fallbacks`() {
        val json =
            mapOf(
                "id" to WiroValue.StringValue("42"),
                "cleanslugowner" to WiroValue.StringValue("openai"),
                "cleanslugproject" to WiroValue.StringValue("gpt-image-2"),
                "title" to WiroValue.StringValue("GPT Image 2"),
                "image" to WiroValue.StringValue("https://cdn.example.com/a.png"),
                "categories" to
                    WiroValue.ArrayValue(
                        listOf(WiroValue.StringValue("image")),
                    ),
                "tags" to
                    WiroValue.ArrayValue(
                        listOf(WiroValue.StringValue("openai")),
                    ),
                "samples" to
                    WiroValue.ArrayValue(
                        listOf(WiroValue.StringValue("https://cdn.example.com/s.png")),
                    ),
                "taskstat" to
                    WiroValue.ObjectValue(
                        mapOf(
                            "runcount" to WiroValue.StringValue("100"),
                            "successcount" to WiroValue.NumberValue("90"),
                            "errorcount" to WiroValue.NumberValue("10"),
                            "lastruntime" to WiroValue.NumberValue("1700000000"),
                        ),
                    ),
            )

        val model = WiroModel.parse(json)

        assertEquals("42", model.id)
        assertEquals("openai", model.owner)
        assertEquals("gpt-image-2", model.slug)
        assertEquals(
            WiroModelId("openai", "gpt-image-2"),
            model.modelId,
        )
        assertEquals(
            Instant.ofEpochSecond(1_700_000_000L),
            model.taskStats?.lastRunTime,
        )
        assertEquals(100, model.taskStats?.runCount)
        assertEquals(90, model.taskStats?.successCount)
        assertTrue(model.raw.containsKey("taskstat"))
    }

    @Test
    fun `explore category accepts string total and name fallback`() {
        val json =
            mapOf(
                "id" to WiroValue.StringValue("cat-1"),
                "name" to WiroValue.StringValue("Featured"),
                "total" to WiroValue.StringValue("2"),
                "url" to WiroValue.StringValue("https://wiro.ai/explore"),
                "tools" to
                    WiroValue.ArrayValue(
                        listOf(
                            WiroValue.ObjectValue(
                                mapOf(
                                    "id" to WiroValue.StringValue("1"),
                                    "slugowner" to WiroValue.StringValue("openai"),
                                    "slugproject" to
                                        WiroValue.StringValue("gpt-image-2"),
                                ),
                            ),
                        ),
                    ),
            )

        val category = WiroExploreCategory.parse(json)

        assertEquals("Featured", category.title)
        assertEquals(2, category.total)
        assertEquals(1, category.models.size)
        assertEquals(
            "https://wiro.ai/explore",
            category.url?.toASCIIString(),
        )
    }

    @Test
    fun `schema parses known and unknown parameter kinds`() {
        val schema = WiroModelSchema.parse(schemaFixture())

        assertEquals(5, schema.parameters.size)
        assertTrue(schema.parameters[0] is WiroModelParameter.Text)
        assertTrue(schema.parameters[1] is WiroModelParameter.Select)
        assertTrue(schema.parameters[2] is WiroModelParameter.Number)
        assertTrue(schema.parameters[3] is WiroModelParameter.File)
        val unknown = schema.parameters[4] as WiroModelParameter.Unknown
        assertEquals("futureType", unknown.type)
        assertNotNull(unknown.defaultValue)
        assertTrue(unknown.defaultValue is WiroValue.ObjectValue)
    }

    @Test
    fun `schema validate covers required select and number boundaries`() {
        val schema = WiroModelSchema.parse(schemaFixture())

        assertEquals(
            emptyList<String>(),
            schema.validate(
                mapOf(
                    "prompt" to WiroValue.StringValue("a cat"),
                    "outputFormat" to WiroValue.StringValue("png"),
                    "width" to WiroValue.NumberValue("1024"),
                ),
            ),
        )
        assertTrue(
            schema
                .validate(emptyMap())
                .any { it.contains("prompt is required") },
        )
        assertTrue(
            schema
                .validate(
                    mapOf(
                        "prompt" to WiroValue.StringValue("x"),
                        "outputFormat" to WiroValue.StringValue("gif"),
                    ),
                ).any { it.contains("outputFormat must be one of") },
        )
        assertTrue(
            schema
                .validate(
                    mapOf(
                        "prompt" to WiroValue.StringValue("x"),
                        "width" to WiroValue.StringValue("wide"),
                    ),
                ).any { it.contains("width must be numeric") },
        )
        assertTrue(
            schema
                .validate(
                    mapOf(
                        "prompt" to WiroValue.StringValue("x"),
                        "width" to WiroValue.NumberValue("32"),
                    ),
                ).any { it.contains("width must be at least 64") },
        )
        assertTrue(
            schema
                .validate(
                    mapOf(
                        "prompt" to WiroValue.StringValue("x"),
                        "width" to WiroValue.NumberValue("4096"),
                    ),
                ).any { it.contains("width must be at most 2048") },
        )
        assertEquals(
            emptyList<String>(),
            schema.validate(
                mapOf(
                    "prompt" to WiroValue.StringValue("ok"),
                    "extra" to WiroValue.StringValue("ignored"),
                ),
            ),
        )
    }

    @Test
    fun `paginated result preserves errors and raw payload`() {
        val json =
            mapOf(
                "result" to WiroValue.BooleanValue(false),
                "total" to WiroValue.StringValue("0"),
                "tool" to WiroValue.ArrayValue(emptyList()),
                "errors" to
                    WiroValue.ArrayValue(
                        listOf(
                            WiroValue.ObjectValue(
                                mapOf(
                                    "code" to WiroValue.NumberValue("42"),
                                    "message" to WiroValue.StringValue("nope"),
                                ),
                            ),
                        ),
                    ),
            )

        val page =
            WiroPaginatedResult.parse(json, "tool") {
                WiroModel.parse(it)
            }

        assertFalse(page.isSuccess)
        assertEquals(0, page.total)
        assertEquals(1, page.errors.size)
        assertEquals("42", page.errors[0].code)
        assertEquals("nope", page.errors[0].message)
    }
}

class WiroDiscoveryClientTest {
    @Test
    fun `searchModels sends defaults and omits optionals`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"result":true,"total":0,"tool":[]}""")
        val client = testClient(transport)

        client.searchModels()

        val body = decodeBody(transport.requests.single().body!!)
        assertEquals("0", body.string("start"))
        assertEquals("20", body.string("limit"))
        assertEquals("", body.string("search"))
        assertEquals("relevance", body.string("sort"))
        assertEquals(true, body.boolean("hideworkflows"))
        assertEquals(true, body.boolean("summary"))
        assertNull(body["slugowner"])
        assertNull(body["order"])
        assertEquals(
            "https://api.wiro.ai/v1/Tool/List",
            transport.requests.single().url,
        )
    }

    @Test
    fun `searchModels includes owner order and categories`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"result":true,"total":0,"tool":[]}""")
        val client = testClient(transport)

        client.searchModels(
            search = "flux",
            categories = listOf("image"),
            start = 10,
            limit = 5,
            sort = WiroModelSort.TIME,
            owner = "black-forest-labs",
            order = WiroSortOrder.DESCENDING,
        )

        val body = decodeBody(transport.requests.single().body!!)
        assertEquals("10", body.string("start"))
        assertEquals("5", body.string("limit"))
        assertEquals("flux", body.string("search"))
        assertEquals("time", body.string("sort"))
        assertEquals("black-forest-labs", body.string("slugowner"))
        assertEquals("DESC", body.string("order"))
        assertEquals(
            listOf("image"),
            WiroJsonReader.stringList(body, "categories"),
        )
    }

    @Test
    fun `searchModels rejects invalid pagination`() {
        val transport = FakeHttpTransport()
        val client = testClient(transport)

        assertThrows(WiroValidationException::class.java) {
            runBlocking { client.searchModels(start = -1) }
        }
        assertThrows(WiroValidationException::class.java) {
            runBlocking { client.searchModels(limit = 0) }
        }
        assertThrows(WiroValidationException::class.java) {
            runBlocking { client.searchModels(limit = 101) }
        }
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `explore posts empty body and parses categories`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """
                {
                  "explore": [
                    {
                      "id": "1",
                      "title": "Popular",
                      "total": 1,
                      "tools": [
                        {
                          "id": "m1",
                          "slugowner": "openai",
                          "slugproject": "gpt-image-2"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        )
        val client = testClient(transport)

        val categories = client.explore()

        assertEquals("{}", String(transport.requests.single().body!!))
        assertEquals(1, categories.size)
        assertEquals("Popular", categories[0].title)
        assertEquals("openai", categories[0].models[0].owner)
    }

    @Test
    fun `getModelSchema posts owner project and parses schema`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """
                {
                  "tool": [
                    {
                      "id": "1",
                      "slugowner": "black-forest-labs",
                      "slugproject": "flux-2-pro",
                      "parameters": [
                        {
                          "title": "Inputs",
                          "items": [
                            {
                              "id": "prompt",
                              "type": "textarea",
                              "label": "Prompt",
                              "required": true
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        )
        val client = testClient(transport)

        val schema =
            client.getModelSchema(
                WiroModelId("black-forest-labs", "flux-2-pro"),
            )

        val body = decodeBody(transport.requests.single().body!!)
        assertEquals("black-forest-labs", body.string("slugowner"))
        assertEquals("flux-2-pro", body.string("slugproject"))
        assertEquals(
            "https://api.wiro.ai/v1/Tool/Detail",
            transport.requests.single().url,
        )
        assertEquals("prompt", schema.parameters.single().name)
    }

    @Test
    fun `getModelSchema fails when tool array is empty`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(200, """{"tool":[]}""")
        val client =
            testClient(
                transport = transport,
                retryPolicy = WiroRetryPolicy.None,
            )

        val error =
            runCatching {
                client.getModelSchema(WiroModelId("openai", "gpt-image-2"))
            }.exceptionOrNull()

        assertTrue(error is WiroUnknownApiException)
        assertTrue(
            error?.message.orEmpty().contains("did not contain a model"),
        )
    }

    @Test
    fun `discovery apiResult failures use typed exception`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.enqueueJson(
            200,
            """{"result":false,"errors":[{"code":"E1","message":"nope"}]}""",
        )
        val client =
            testClient(
                transport = transport,
                retryPolicy = WiroRetryPolicy.None,
            )

        val error = runCatching { client.searchModels() }.exceptionOrNull()

        assertTrue(error is WiroApiResultException)
        error as WiroApiResultException
        assertEquals("nope", error.message)
        assertEquals("E1", error.code)
    }

    @Test
    fun `sort and order expose stable wire values`() {
        assertEquals("relevance", WiroModelSort.RELEVANCE.apiValue)
        assertEquals("ratedusercount", WiroModelSort.RATED_USER_COUNT.apiValue)
        assertEquals("ASC", WiroSortOrder.ASCENDING.apiValue)
        assertEquals("DESC", WiroSortOrder.DESCENDING.apiValue)
    }
}

private fun decodeBody(bytes: ByteArray): WiroJson {
    val value =
        WiroValue.fromJsonElement(
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)),
        )
    return value.objectValue
        ?: error("expected JSON object body")
}

private fun WiroJson.string(key: String): String? = WiroJsonReader.string(this, key)

private fun WiroJson.boolean(key: String): Boolean? = WiroJsonReader.boolean(this, key)

private fun schemaFixture(): WiroJson = mapOf(
    "id" to WiroValue.StringValue("1"),
    "slugowner" to WiroValue.StringValue("black-forest-labs"),
    "slugproject" to WiroValue.StringValue("flux-2-pro"),
    "parameters" to
        WiroValue.ArrayValue(
            listOf(
                WiroValue.ObjectValue(
                    mapOf(
                        "title" to WiroValue.StringValue("Inputs"),
                        "items" to
                            WiroValue.ArrayValue(
                                listOf(
                                    param(
                                        id = "prompt",
                                        type = "textarea",
                                        required = true,
                                        default = WiroValue.StringValue("hello"),
                                    ),
                                    param(
                                        id = "outputFormat",
                                        type = "select",
                                        required = false,
                                        default = WiroValue.StringValue("png"),
                                        options = listOf("jpeg", "png"),
                                    ),
                                    param(
                                        id = "width",
                                        type = "number",
                                        required = false,
                                        default = WiroValue.NumberValue("1024"),
                                        min = 64.0,
                                        max = 2048.0,
                                        step = 16.0,
                                    ),
                                    param(
                                        id = "inputImage",
                                        type = "fileinput",
                                        required = false,
                                    ),
                                    param(
                                        id = "magic",
                                        type = "futureType",
                                        required = false,
                                        default =
                                        WiroValue.ObjectValue(
                                            mapOf(
                                                "x" to
                                                    WiroValue.NumberValue("1"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            ),
        ),
)

private fun param(
    id: String,
    type: String,
    required: Boolean,
    default: WiroValue? = null,
    options: List<String> = emptyList(),
    min: Double? = null,
    max: Double? = null,
    step: Double? = null,
): WiroValue {
    val json = LinkedHashMap<String, WiroValue>()
    json["id"] = WiroValue.StringValue(id)
    json["type"] = WiroValue.StringValue(type)
    json["label"] = WiroValue.StringValue(id)
    json["required"] = WiroValue.BooleanValue(required)
    if (default != null) {
        json["default"] = default
    }
    if (options.isNotEmpty()) {
        json["options"] =
            WiroValue.ArrayValue(
                options.map { value ->
                    WiroValue.ObjectValue(
                        mapOf(
                            "label" to WiroValue.StringValue(value),
                            "value" to WiroValue.StringValue(value),
                        ),
                    )
                },
            )
    }
    if (min != null) {
        json["min"] = WiroValue.NumberValue(min.toString())
    }
    if (max != null) {
        json["max"] = WiroValue.NumberValue(max.toString())
    }
    if (step != null) {
        json["step"] = WiroValue.NumberValue(step.toString())
    }
    return WiroValue.ObjectValue(json)
}
