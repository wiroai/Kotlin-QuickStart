package ai.wiro.wirokit

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Client for the Wiro AI REST API.
 *
 * Construct with an API key (and optional secret for signature auth) or in
 * proxy mode with static headers. Networking goes through an injectable
 * [WiroHttpTransport] so unit tests never hit the network.
 */
public class WiroClient private constructor(
    public val authType: WiroAuthType,
    public val baseUrl: URI,
    public val socketUrl: URI,
    public val pollInterval: Duration,
    public val requestTimeout: Duration,
    public val retryPolicy: WiroRetryPolicy,
    public val limits: WiroClientLimits,
    private val apiKey: String?,
    private val apiSecret: String?,
    proxyHeaders: Map<String, String>,
    private val transport: WiroHttpTransport,
    private val ownsTransport: Boolean,
    private val logger: WiroLogger?,
    private val clock: WiroClock,
    private val nonceProvider: WiroNonceProvider,
    private val delay: WiroDelay,
    private val jitterProvider: WiroJitterProvider,
    private val monotonicClock: WiroMonotonicClock,
    private val socketSessionFactory: WiroSocketSessionFactory,
) : Closeable {
    private val proxyHeaders: Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(proxyHeaders))
    private val closed = AtomicBoolean(false)

    /**
     * Creates a client that authenticates with a Wiro API key.
     *
     * When [apiSecret] is provided, requests use signature auth
     * (`x-nonce` + `x-signature`). Otherwise only `x-api-key` is sent.
     */
    public constructor(
        apiKey: String,
        apiSecret: String? = null,
        baseUrl: String = DEFAULT_BASE_URL,
        socketUrl: String = DEFAULT_SOCKET_URL,
        transport: WiroHttpTransport? = null,
        closeTransportOnClose: Boolean = false,
        okHttpClient: OkHttpClient? = null,
        closeOkHttpClientOnClose: Boolean = false,
        pollInterval: Duration = 3.seconds,
        requestTimeout: Duration = 30.seconds,
        retryPolicy: WiroRetryPolicy = WiroRetryPolicy.Default,
        limits: WiroClientLimits = WiroClientLimits.Default,
        logger: WiroLogger? = null,
    ) : this(
        apiKey = apiKey,
        apiSecret = apiSecret,
        proxyHeaders = emptyMap(),
        authType = resolveAuthType(apiSecret),
        baseUrl = baseUrl,
        socketUrl = socketUrl,
        transport = transport,
        closeTransportOnClose = closeTransportOnClose,
        okHttpClient = okHttpClient,
        closeOkHttpClientOnClose = closeOkHttpClientOnClose,
        pollInterval = pollInterval,
        requestTimeout = requestTimeout,
        retryPolicy = retryPolicy,
        limits = limits,
        logger = logger,
        clock = WiroRuntimeDefaults.clock,
        nonceProvider = WiroRuntimeDefaults.nonceProvider,
        delay = WiroRuntimeDefaults.delay,
        jitterProvider = WiroRuntimeDefaults.jitterProvider,
        monotonicClock = WiroRuntimeDefaults.monotonicClock,
        socketSessionFactory = WiroDefaultSocketSessionFactory,
    )

    /**
     * Creates a client that sends REST requests through a proxy.
     *
     * No Wiro credentials are stored. [headers] are attached to every REST
     * request. Prefer proxy mode in shipped apps so long-lived API secrets
     * never embed in the binary.
     */
    public constructor(
        proxyUrl: String,
        headers: Map<String, String> = emptyMap(),
        socketUrl: String = DEFAULT_SOCKET_URL,
        transport: WiroHttpTransport? = null,
        closeTransportOnClose: Boolean = false,
        okHttpClient: OkHttpClient? = null,
        closeOkHttpClientOnClose: Boolean = false,
        pollInterval: Duration = 3.seconds,
        requestTimeout: Duration = 30.seconds,
        retryPolicy: WiroRetryPolicy = WiroRetryPolicy.Default,
        limits: WiroClientLimits = WiroClientLimits.Default,
        logger: WiroLogger? = null,
    ) : this(
        apiKey = null,
        apiSecret = null,
        proxyHeaders = headers,
        authType = WiroAuthType.PROXY,
        baseUrl = proxyUrl,
        socketUrl = socketUrl,
        transport = transport,
        closeTransportOnClose = closeTransportOnClose,
        okHttpClient = okHttpClient,
        closeOkHttpClientOnClose = closeOkHttpClientOnClose,
        pollInterval = pollInterval,
        requestTimeout = requestTimeout,
        retryPolicy = retryPolicy,
        limits = limits,
        logger = logger,
        clock = WiroRuntimeDefaults.clock,
        nonceProvider = WiroRuntimeDefaults.nonceProvider,
        delay = WiroRuntimeDefaults.delay,
        jitterProvider = WiroRuntimeDefaults.jitterProvider,
        monotonicClock = WiroRuntimeDefaults.monotonicClock,
        socketSessionFactory = WiroDefaultSocketSessionFactory,
    )

    private constructor(
        apiKey: String?,
        apiSecret: String?,
        proxyHeaders: Map<String, String>,
        authType: WiroAuthType,
        baseUrl: String,
        socketUrl: String,
        transport: WiroHttpTransport?,
        closeTransportOnClose: Boolean,
        okHttpClient: OkHttpClient?,
        closeOkHttpClientOnClose: Boolean,
        pollInterval: Duration,
        requestTimeout: Duration,
        retryPolicy: WiroRetryPolicy,
        limits: WiroClientLimits,
        logger: WiroLogger?,
        clock: WiroClock,
        nonceProvider: WiroNonceProvider,
        delay: WiroDelay,
        jitterProvider: WiroJitterProvider,
        monotonicClock: WiroMonotonicClock,
        socketSessionFactory: WiroSocketSessionFactory,
    ) : this(
        authType = authType,
        baseUrl = validateAndTrimBaseUrl(baseUrl),
        socketUrl = validateSocketUrl(socketUrl),
        pollInterval =
        pollInterval.also {
            WiroValidation.requirePositiveDuration(it, "pollInterval")
        },
        requestTimeout =
        requestTimeout.also {
            WiroValidation.requirePositiveDuration(it, "requestTimeout")
        },
        retryPolicy = retryPolicy,
        limits = limits,
        apiKey = resolveApiKey(authType, apiKey),
        apiSecret = resolveApiSecret(authType, apiSecret),
        proxyHeaders = validateProxyHeaders(proxyHeaders),
        transport =
        transport ?: createOwnedKtorTransport(
            okHttpClient = okHttpClient,
            closeOkHttpClientOnClose = closeOkHttpClientOnClose,
        ),
        ownsTransport =
        when {
            transport == null -> true
            else -> closeTransportOnClose
        },
        logger = logger,
        clock = clock,
        nonceProvider = nonceProvider,
        delay = delay,
        jitterProvider = jitterProvider,
        monotonicClock = monotonicClock,
        socketSessionFactory = socketSessionFactory,
    )

    /**
     * Performs an authenticated JSON POST and parses the API envelope.
     *
     * Paths under `/Run/` and `/File/Upload` are never retried.
     */
    internal suspend fun <T> postJson(
        path: String,
        body: WiroJson = emptyMap(),
        retryable: Boolean = true,
        parse: (WiroJson) -> T,
    ): T {
        ensureOpen()
        val url = makeUrl(path)
        val effectiveRetryable = retryable && isRetryablePath(path)
        val bodyBytes = encodeBody(body)
        var attempt = 0

        while (true) {
            ensureActiveCoroutine()

            val request =
                WiroHttpRequest(
                    method = "POST",
                    url = url,
                    headers = authHeaders(includeContentType = true),
                    body = bodyBytes,
                    timeout = requestTimeout,
                )

            log(
                WiroLogEvent(
                    level = WiroLogLevel.DEBUG,
                    message = "Starting request.",
                    method = "POST",
                    url = url,
                    retryCount = attempt,
                ),
            )

            val started = clock.epochMilliseconds()
            val response =
                try {
                    transport.perform(request)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: WiroException) {
                    retryOrThrow(
                        error = error,
                        attempt = attempt,
                        retryable = effectiveRetryable,
                        headerRetryAfter = null,
                        url = url,
                    )
                    attempt += 1
                    continue
                } catch (error: Throwable) {
                    retryOrThrow(
                        error =
                        WiroNetworkException(
                            message = "The network request failed.",
                            underlyingType = WiroRedaction.throwableType(error),
                        ),
                        attempt = attempt,
                        retryable = effectiveRetryable,
                        headerRetryAfter = null,
                        url = url,
                    )
                    attempt += 1
                    continue
                }

            val duration = durationSince(started)
            log(
                WiroLogEvent(
                    level = WiroLogLevel.INFO,
                    message = "Request completed.",
                    method = "POST",
                    url = url,
                    statusCode = response.statusCode,
                    duration = duration,
                    retryCount = attempt,
                ),
            )

            val retryAfter = WiroResponseEnvelope.retryAfterInterval(response)
            try {
                val envelope =
                    WiroResponseEnvelope.decodeSuccessObject(
                        body = response.body,
                        statusCode = response.statusCode,
                        retryAfter = retryAfter,
                    )
                return parse(envelope)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: WiroException) {
                retryOrThrow(
                    error = error,
                    attempt = attempt,
                    retryable = effectiveRetryable,
                    headerRetryAfter = retryAfter,
                    url = url,
                )
                attempt += 1
            }
        }
    }

    /**
     * Searches and lists models available on Wiro.
     *
     * @throws WiroValidationException when [start] or [limit] are out of range.
     */
    public suspend fun searchModels(
        search: String = "",
        categories: List<String> = emptyList(),
        start: Int = 0,
        limit: Int = 20,
        sort: WiroModelSort = WiroModelSort.RELEVANCE,
        owner: String? = null,
        order: WiroSortOrder? = null,
    ): WiroPaginatedResult<WiroModel> {
        if (start < 0) {
            throw WiroValidationException(
                "start cannot be negative.",
                statusCode = 0,
            )
        }
        if (limit !in 1..100) {
            throw WiroValidationException(
                "limit must be between 1 and 100.",
                statusCode = 0,
            )
        }

        val body = LinkedHashMap<String, WiroValue>()
        body["start"] = WiroValue.StringValue(start.toString())
        body["limit"] = WiroValue.StringValue(limit.toString())
        body["search"] = WiroValue.StringValue(search)
        body["categories"] =
            WiroValue.ArrayValue(
                categories.map { WiroValue.StringValue(it) },
            )
        body["sort"] = WiroValue.StringValue(sort.apiValue)
        body["hideworkflows"] = WiroValue.BooleanValue(true)
        body["summary"] = WiroValue.BooleanValue(true)
        if (owner != null) {
            body["slugowner"] = WiroValue.StringValue(owner)
        }
        if (order != null) {
            body["order"] = WiroValue.StringValue(order.apiValue)
        }

        val handler = malformedJsonHandler()
        return postJson("/Tool/List", body) { json ->
            WiroPaginatedResult.parse(
                json = json,
                itemsKey = "tool",
                onMalformedJson = handler,
                itemFromJson = { WiroModel.parse(it, handler) },
            )
        }
    }

    /**
     * Returns curated model categories from `/Tool/Explore`.
     */
    public suspend fun explore(): List<WiroExploreCategory> {
        val handler = malformedJsonHandler()
        return postJson("/Tool/Explore", emptyMap()) { json ->
            WiroJsonReader.objects(json, "explore", handler).map {
                WiroExploreCategory.parse(it, handler)
            }
        }
    }

    /**
     * Returns the input schema for [modelId] from `/Tool/Detail`.
     *
     * @throws WiroUnknownApiException when the `tool` array is missing or empty.
     */
    public suspend fun getModelSchema(modelId: WiroModelId): WiroModelSchema {
        val handler = malformedJsonHandler()
        return postJson(
            path = "/Tool/Detail",
            body =
            mapOf(
                "slugowner" to WiroValue.StringValue(modelId.owner),
                "slugproject" to WiroValue.StringValue(modelId.project),
            ),
        ) { json ->
            val tools = WiroJsonReader.objects(json, "tool", handler)
            val first =
                tools.firstOrNull()
                    ?: throw WiroUnknownApiException(
                        message =
                        "The model schema response did not contain a model.",
                        statusCode = 200,
                        rawResponseBody = null,
                    )
            WiroModelSchema.parse(first, handler)
        }
    }

    /**
     * Starts [modelId] with the supplied dynamic [parameters].
     *
     * This billable operation is never retried automatically.
     * Unresolved file inputs are uploaded before `/Run` starts.
     */
    public suspend fun runModel(
        modelId: WiroModelId,
        parameters: WiroJson = emptyMap(),
        callbackUrl: String? = null,
        contentSource: WiroUriContentSource? = null,
    ): WiroRunResult {
        val callback = callbackUrl?.let(::validateCallbackUrl)
        var body = parameters
        if (containsFileInput(body)) {
            body = resolveFileInputs(body, contentSource)
        }
        val requestBody = LinkedHashMap(body)
        if (callback != null) {
            requestBody["callbackUrl"] = WiroValue.StringValue(callback)
        }

        val owner = percentEncodePathSegment(modelId.owner)
        val project = percentEncodePathSegment(modelId.project)
        val path = "/Run/$owner/$project"
        val handler = malformedJsonHandler()

        return postJson(
            path = path,
            body = requestBody,
            retryable = false,
        ) { json ->
            WiroRunResult.parse(json, handler)
        }
    }

    /**
     * Uploads [data] to Wiro as a multipart file part named `"file"`.
     *
     * This billable operation is never retried automatically.
     */
    public suspend fun uploadFile(
        data: ByteArray,
        fileName: String,
    ): WiroUploadResult {
        if (data.size > limits.maxInMemoryUploadBytes) {
            throw WiroValidationException(
                "In-memory upload exceeds the configured size limit.",
                statusCode = 0,
            )
        }
        val trimmedName = validatedUploadFileName(fileName)
        val multipart =
            MultipartFormData.buildFilePart(
                data = data,
                fileName = trimmedName,
            )
        return sendUpload(
            bodyData = multipart.data,
            contentType = multipart.contentType,
            filePath = null,
        )
    }

    /**
     * Uploads an in-memory [WiroFileInput.Bytes] value.
     */
    public suspend fun uploadFile(
        input: WiroFileInput.Bytes,
    ): WiroUploadResult = uploadFile(input.bytes, input.fileName)

    /**
     * Uploads a [WiroFileInput.ContentUri] by streaming through [contentSource].
     *
     * Large URI-backed files are streamed into a temporary multipart file and
     * are not fully buffered in memory.
     */
    public suspend fun uploadFile(
        input: WiroFileInput.ContentUri,
        contentSource: WiroUriContentSource,
    ): WiroUploadResult {
        val name =
            validatedUploadFileName(
                input.fileName ?: "upload.bin",
            )
        val stream =
            try {
                contentSource.openInputStream(input.uri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SecurityException) {
                throw WiroValidationException(
                    "The content URI could not be opened.",
                    statusCode = 0,
                ).apply { initCause(error) }
            } catch (error: Throwable) {
                throw WiroValidationException(
                    "The content URI could not be opened.",
                    statusCode = 0,
                ).apply { initCause(error) }
            } ?: throw WiroValidationException(
                "The content URI could not be opened.",
                statusCode = 0,
            )

        val tempFile = File.createTempFile("wiro-upload-", ".multipart")
        try {
            stream.use { inputStream ->
                ensureActiveCoroutine()
                val boundary =
                    MultipartFormData.writeFilePart(
                        source = inputStream,
                        fileName = name,
                        destination = tempFile,
                    )
                return sendUpload(
                    bodyData = null,
                    contentType =
                    "multipart/form-data; boundary=$boundary",
                    filePath = tempFile.absolutePath,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Deep-walks [parameters], uploading every unresolved file input and
     * replacing file inputs with hosted URL strings.
     */
    public suspend fun resolveFileInputs(
        parameters: WiroJson,
        contentSource: WiroUriContentSource? = null,
    ): WiroJson {
        val resolved = LinkedHashMap<String, WiroValue>(parameters.size)
        for ((key, value) in parameters) {
            resolved[key] = resolveFileValue(value, contentSource)
        }
        return resolved
    }

    /**
     * Returns task details using a task access token.
     */
    public suspend fun getTask(token: WiroTaskToken): WiroTask {
        val handler = malformedJsonHandler()
        return postJson(
            path = "/Task/Detail",
            body =
            mapOf(
                "tasktoken" to WiroValue.StringValue(token.rawValue),
            ),
        ) { json ->
            taskFromResponse(json, handler)
        }
    }

    /**
     * Returns task details using the server-side task id.
     */
    public suspend fun getTaskById(id: WiroTaskId): WiroTask {
        val handler = malformedJsonHandler()
        return postJson(
            path = "/Task/Detail",
            body =
            mapOf(
                "taskid" to WiroValue.StringValue(id.rawValue),
            ),
        ) { json ->
            taskFromResponse(json, handler)
        }
    }

    /**
     * Requests cancellation of a queued task.
     */
    public suspend fun cancelTask(id: WiroTaskId): Boolean = postJson(
        path = "/Task/Cancel",
        body =
        mapOf(
            "taskid" to WiroValue.StringValue(id.rawValue),
        ),
    ) { json ->
        WiroJsonReader.boolean(json, "result", fallback = false) ?: false
    }

    /**
     * Stops a running task using its access token.
     */
    public suspend fun killTask(token: WiroTaskToken): Boolean = postJson(
        path = "/Task/Kill",
        body =
        mapOf(
            "socketaccesstoken" to
                WiroValue.StringValue(token.rawValue),
        ),
    ) { json ->
        WiroJsonReader.boolean(json, "result", fallback = false) ?: false
    }

    /**
     * Stops a running task using its server-side task id.
     */
    public suspend fun killTask(id: WiroTaskId): Boolean = postJson(
        path = "/Task/Kill",
        body =
        mapOf(
            "taskid" to WiroValue.StringValue(id.rawValue),
        ),
    ) { json ->
        WiroJsonReader.boolean(json, "result", fallback = false) ?: false
    }

    /**
     * Builds authentication headers for a REST request.
     *
     * SDK-owned `User-Agent` and `Content-Type` values always win over
     * caller-supplied proxy headers.
     */
    internal fun authHeaders(includeContentType: Boolean = true): Map<String, String> {
        val headers = LinkedHashMap<String, String>()

        when (authType) {
            WiroAuthType.API_KEY -> {
                val key = apiKey
                if (key != null) {
                    headers["x-api-key"] = key
                }
            }

            WiroAuthType.SIGNATURE -> {
                val key = apiKey
                val secret = apiSecret
                if (key != null && secret != null) {
                    val nonce = nonceProvider.nextNonce()
                    headers["x-api-key"] = key
                    headers["x-nonce"] = nonce
                    headers["x-signature"] =
                        signature(
                            apiKey = key,
                            apiSecret = secret,
                            nonce = nonce,
                        )
                }
            }

            WiroAuthType.PROXY -> {
                proxyHeaders.forEach { (name, value) ->
                    if (!isSdkOwnedHeader(name)) {
                        headers[name] = value
                    }
                }
            }
        }

        headers["User-Agent"] = userAgent()
        if (includeContentType) {
            headers["Content-Type"] = "application/json"
        }
        return headers
    }

    internal fun makeUrl(path: String): String {
        val trimmedPath = if (path.startsWith("/")) path else "/$path"
        return try {
            URI(baseUrl.toASCIIString() + trimmedPath).toASCIIString()
        } catch (_: Throwable) {
            throw WiroValidationException(
                "Could not build request URL for path $path.",
                statusCode = 0,
            )
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        if (ownsTransport) {
            transport.close()
        }
    }

    private suspend fun retryOrThrow(
        error: WiroException,
        attempt: Int,
        retryable: Boolean,
        headerRetryAfter: Duration?,
        url: String,
    ) {
        if (!retryable || attempt >= retryPolicy.maxRetries) {
            logFailure(error, url, attempt)
            throw error
        }

        val delayDuration =
            retryDelay(
                error = error,
                attempt = attempt,
                headerRetryAfter = headerRetryAfter,
            )
        if (delayDuration == null) {
            logFailure(error, url, attempt)
            throw error
        }

        log(
            WiroLogEvent(
                level = WiroLogLevel.WARNING,
                message = "Retrying request after transient failure.",
                retryCount = attempt + 1,
                error = error.message,
            ),
        )

        ensureActiveCoroutine()
        try {
            delay.sleep(delayDuration)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
        ensureActiveCoroutine()
    }

    private fun retryDelay(
        error: WiroException,
        attempt: Int,
        headerRetryAfter: Duration?,
    ): Duration? {
        val policyDelay = retryPolicy.delayForRetry(attempt, jitterProvider)
        return when (error) {
            is WiroRateLimitException -> {
                val minimum = error.retryAfter ?: headerRetryAfter
                if (minimum != null) {
                    maxOf(policyDelay, minimum)
                } else {
                    policyDelay
                }
            }

            is WiroUnknownApiException -> {
                val status = error.statusCode ?: return null
                if (!retryPolicy.shouldRetry(status)) {
                    null
                } else {
                    policyDelay
                }
            }

            is WiroNetworkException, is WiroTimeoutException -> {
                policyDelay
            }

            else -> {
                null
            }
        }
    }

    private fun encodeBody(body: WiroJson): ByteArray {
        val bytes =
            try {
                bodyJson
                    .encodeToString(
                        WiroValue.serializer(),
                        WiroValue.ObjectValue(body),
                    ).toByteArray(Charsets.UTF_8)
            } catch (_: Throwable) {
                throw WiroValidationException(
                    "Could not encode request body as JSON.",
                    statusCode = 0,
                )
            }
        if (bytes.size > limits.maxRestBodyBytes) {
            throw WiroValidationException(
                "Request body exceeds the configured REST payload limit.",
                statusCode = 0,
            )
        }
        return bytes
    }

    private fun durationSince(startedEpochMs: Long): Duration {
        val elapsed =
            (clock.epochMilliseconds() - startedEpochMs)
                .coerceAtLeast(0L)
        return elapsed.milliseconds
    }

    private suspend fun sendUpload(
        bodyData: ByteArray?,
        contentType: String,
        filePath: String?,
    ): WiroUploadResult {
        ensureOpen()
        val url = makeUrl("/File/Upload")
        val headers = LinkedHashMap(authHeaders(includeContentType = false))
        headers["Content-Type"] = contentType
        val request =
            WiroHttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = bodyData,
                timeout = requestTimeout,
            )
        val handler = malformedJsonHandler()
        return executeRequest(
            request = request,
            url = url,
            retryable = false,
            transportCall = { current ->
                if (filePath != null) {
                    transport.upload(current, filePath)
                } else {
                    transport.perform(current)
                }
            },
            parse = { json ->
                WiroUploadResult.parse(json, handler)
            },
        )
    }

    private suspend fun resolveFileValue(
        value: WiroValue,
        contentSource: WiroUriContentSource?,
    ): WiroValue = when (value) {
        is WiroValue.FileInputValue -> {
            when (val input = value.value) {
                is WiroFileInput.Url -> {
                    WiroValue.StringValue(input.wireValue)
                }

                is WiroFileInput.Bytes -> {
                    val upload = uploadFile(input)
                    val hosted =
                        upload.files.firstOrNull()?.url
                            ?: throw WiroUnknownApiException(
                                message =
                                "The upload for \"${input.fileName}\" " +
                                    "did not return a file URL.",
                                statusCode = 200,
                                rawResponseBody = null,
                            )
                    WiroValue.StringValue(hosted.toASCIIString())
                }

                is WiroFileInput.ContentUri -> {
                    val source =
                        contentSource
                            ?: throw WiroValidationException(
                                "A content source is required to upload " +
                                    "content URI file inputs.",
                                statusCode = 0,
                            )
                    val upload = uploadFile(input, source)
                    val hosted =
                        upload.files.firstOrNull()?.url
                            ?: throw WiroUnknownApiException(
                                message =
                                "The upload did not return a file URL.",
                                statusCode = 200,
                                rawResponseBody = null,
                            )
                    WiroValue.StringValue(hosted.toASCIIString())
                }
            }
        }

        is WiroValue.ObjectValue -> {
            val nested = LinkedHashMap<String, WiroValue>()
            for ((key, child) in value.value) {
                nested[key] = resolveFileValue(child, contentSource)
            }
            WiroValue.ObjectValue(nested)
        }

        is WiroValue.ArrayValue -> {
            val items =
                value.value.map {
                    resolveFileValue(it, contentSource)
                }
            WiroValue.ArrayValue(items)
        }

        else -> {
            value
        }
    }

    private suspend fun <T> executeRequest(
        request: WiroHttpRequest,
        url: String,
        retryable: Boolean,
        transportCall: suspend (WiroHttpRequest) -> WiroHttpResponse,
        parse: (WiroJson) -> T,
    ): T {
        val effectiveRetryable =
            retryable &&
                isRetryablePath(
                    url.removePrefix(baseUrl.toASCIIString()),
                )
        var attempt = 0
        var currentRequest = request

        while (true) {
            ensureActiveCoroutine()
            log(
                WiroLogEvent(
                    level = WiroLogLevel.DEBUG,
                    message = "Starting request.",
                    method = currentRequest.method,
                    url = url,
                    retryCount = attempt,
                ),
            )

            val auth =
                authHeaders(
                    includeContentType =
                    currentRequest.headers["Content-Type"]
                        ?.startsWith("application/json") == true,
                )
            val merged = LinkedHashMap(currentRequest.headers)
            auth.forEach { (name, value) ->
                val existing =
                    merged.entries
                        .firstOrNull {
                            it.key.equals(name, ignoreCase = true)
                        }?.value
                if (
                    name.equals("Content-Type", ignoreCase = true) &&
                    existing != null &&
                    !existing.startsWith("application/json")
                ) {
                    return@forEach
                }
                merged.keys
                    .filter {
                        it.equals(name, ignoreCase = true)
                    }.forEach(merged::remove)
                merged[name] = value
            }
            currentRequest =
                WiroHttpRequest(
                    method = currentRequest.method,
                    url = currentRequest.url,
                    headers = merged,
                    body = currentRequest.body,
                    timeout = currentRequest.timeout,
                )

            val started = clock.epochMilliseconds()
            val response =
                try {
                    transportCall(currentRequest)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: WiroException) {
                    retryOrThrow(
                        error = error,
                        attempt = attempt,
                        retryable = effectiveRetryable,
                        headerRetryAfter = null,
                        url = url,
                    )
                    attempt += 1
                    continue
                } catch (error: Throwable) {
                    retryOrThrow(
                        error =
                        WiroNetworkException(
                            message = "The network request failed.",
                            underlyingType = WiroRedaction.throwableType(error),
                        ),
                        attempt = attempt,
                        retryable = effectiveRetryable,
                        headerRetryAfter = null,
                        url = url,
                    )
                    attempt += 1
                    continue
                }

            log(
                WiroLogEvent(
                    level = WiroLogLevel.INFO,
                    message = "Request completed.",
                    method = currentRequest.method,
                    url = url,
                    statusCode = response.statusCode,
                    duration = durationSince(started),
                    retryCount = attempt,
                ),
            )

            val retryAfter = WiroResponseEnvelope.retryAfterInterval(response)
            try {
                val envelope =
                    WiroResponseEnvelope.decodeSuccessObject(
                        body = response.body,
                        statusCode = response.statusCode,
                        retryAfter = retryAfter,
                    )
                return parse(envelope)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: WiroException) {
                retryOrThrow(
                    error = error,
                    attempt = attempt,
                    retryable = effectiveRetryable,
                    headerRetryAfter = retryAfter,
                    url = url,
                )
                attempt += 1
            }
        }
    }

    private fun log(event: WiroLogEvent) {
        logger?.log(event)
    }

    private fun logFailure(
        error: WiroException,
        url: String,
        attempt: Int,
    ) {
        log(
            WiroLogEvent(
                level = WiroLogLevel.ERROR,
                message = "Request failed.",
                method = "POST",
                url = url,
                retryCount = attempt,
                error = error.message,
            ),
        )
    }

    /** Sleep source shared by retries and task tracking. */
    internal val trackingDelay: WiroDelay
        get() = delay

    /** Monotonic time source used for tracking deadlines. */
    internal val trackingClock: WiroMonotonicClock
        get() = monotonicClock

    /** Factory used to open task-tracking WebSocket sessions. */
    internal val trackingSocketFactory: WiroSocketSessionFactory
        get() = socketSessionFactory

    internal fun malformedJsonHandler(): WiroJsonReader.MalformedJsonHandler {
        val handler = WiroJsonReader.MalformedJsonHandler { raw ->
            log(
                WiroLogEvent(
                    level = WiroLogLevel.DEBUG,
                    message =
                    "Ignored malformed nested JSON string " +
                        "(length ${raw.length}).",
                ),
            )
        }
        return handler
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw WiroValidationException(
                "WiroClient is closed.",
                statusCode = 0,
            )
        }
    }

    private suspend fun ensureActiveCoroutine() {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.wiro.ai/v1"
        public const val DEFAULT_SOCKET_URL: String =
            "wss://socket.wiro.ai/v1"

        internal fun userAgent(): String = "WiroKit-Android/${WiroKitInfo.VERSION}"

        internal fun signature(
            apiKey: String,
            apiSecret: String,
            nonce: String,
        ): String = WiroSignature.sign(apiKey, apiSecret, nonce)

        internal fun isRetryablePath(path: String): Boolean {
            val normalized = if (path.startsWith("/")) path else "/$path"
            if (normalized.equals("/File/Upload", ignoreCase = false)) {
                return false
            }
            if (normalized.startsWith("/Run/")) {
                return false
            }
            return true
        }

        internal fun percentEncodePathSegment(value: String): String {
            val builder = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                if (charCount == 1) {
                    val char = value[index]
                    if (char.isLetterOrDigit() ||
                        char == '-' ||
                        char == '.' ||
                        char == '_' ||
                        char == '~'
                    ) {
                        builder.append(char)
                        index += 1
                        continue
                    }
                }
                val bytes =
                    value
                        .substring(index, index + charCount)
                        .toByteArray(Charsets.UTF_8)
                bytes.forEach { byte ->
                    builder.append('%')
                    val hex =
                        (byte.toInt() and 0xFF)
                            .toString(16)
                            .uppercase()
                            .padStart(2, '0')
                    builder.append(hex)
                }
                index += charCount
            }
            return builder.toString()
        }

        internal fun validateCallbackUrl(value: String): String {
            val uri =
                try {
                    WiroValidation.validateUrl(
                        value = value,
                        kind = WiroUrlKind.HTTP,
                        label = "callbackUrl",
                        allowQuery = true,
                        allowFragment = false,
                    )
                } catch (_: WiroValidationException) {
                    throw WiroValidationException(
                        "callbackURL must be an HTTP(S) URL without " +
                            "credentials or a fragment.",
                        statusCode = 0,
                    )
                }
            return uri.toASCIIString()
        }

        internal fun taskFromResponse(
            json: WiroJson,
            onMalformedJson: WiroJsonReader.MalformedJsonHandler?,
        ): WiroTask {
            val tasks =
                WiroJsonReader.objects(
                    json,
                    "tasklist",
                    onMalformedJson,
                )
            val first =
                tasks.firstOrNull()
                    ?: throw WiroUnknownApiException(
                        message = "The task response did not contain a task.",
                        statusCode = 200,
                        rawResponseBody = null,
                    )
            return WiroTask.parse(first, onMalformedJson)
        }

        internal fun containsFileInput(value: WiroValue): Boolean = when (value) {
            is WiroValue.FileInputValue -> {
                true
            }

            is WiroValue.ObjectValue -> {
                value.value.values.any(::containsFileInput)
            }

            is WiroValue.ArrayValue -> {
                value.value.any(::containsFileInput)
            }

            else -> {
                false
            }
        }

        internal fun containsFileInput(json: WiroJson): Boolean = json.values.any(::containsFileInput)

        internal const val MAX_UPLOAD_FILE_NAME_LENGTH: Int = 255

        internal fun validatedUploadFileName(fileName: String): String {
            val trimmed = fileName.trim()
            if (trimmed.isEmpty()) {
                throw WiroValidationException(
                    "fileName must be a non-empty string.",
                    statusCode = 0,
                )
            }
            if (trimmed.length > MAX_UPLOAD_FILE_NAME_LENGTH) {
                throw WiroValidationException(
                    "fileName exceeds the maximum allowed length.",
                    statusCode = 0,
                )
            }
            if (
                trimmed.any {
                    it == '\r' ||
                        it == '\n' ||
                        it == '\u0000' ||
                        it == '/' ||
                        it == '\\'
                }
            ) {
                throw WiroValidationException(
                    "fileName contains invalid characters.",
                    statusCode = 0,
                )
            }
            return trimmed
        }

        internal fun createForTests(
            apiKey: String? = "test-api-key",
            apiSecret: String? = null,
            proxyHeaders: Map<String, String> = emptyMap(),
            authType: WiroAuthType = resolveAuthType(apiSecret),
            baseUrl: String = DEFAULT_BASE_URL,
            socketUrl: String = DEFAULT_SOCKET_URL,
            transport: WiroHttpTransport,
            closeTransportOnClose: Boolean = false,
            pollInterval: Duration = 3.seconds,
            requestTimeout: Duration = 30.seconds,
            retryPolicy: WiroRetryPolicy = WiroRetryPolicy.Default,
            limits: WiroClientLimits = WiroClientLimits.Default,
            logger: WiroLogger? = null,
            clock: WiroClock = WiroClock { 1_700_000_000_000L },
            nonceProvider: WiroNonceProvider =
                WiroNonceProvider { "1700000000000" },
            delay: WiroDelay = WiroDelay { },
            jitterProvider: WiroJitterProvider = WiroJitterProvider { 1.0 },
            monotonicClock: WiroMonotonicClock = WiroMonotonicClock { 0L },
            socketSessionFactory: WiroSocketSessionFactory =
                WiroDefaultSocketSessionFactory,
        ): WiroClient = WiroClient(
            apiKey = apiKey,
            apiSecret = apiSecret,
            proxyHeaders = proxyHeaders,
            authType = authType,
            baseUrl = baseUrl,
            socketUrl = socketUrl,
            transport = transport,
            closeTransportOnClose = closeTransportOnClose,
            okHttpClient = null,
            closeOkHttpClientOnClose = false,
            pollInterval = pollInterval,
            requestTimeout = requestTimeout,
            retryPolicy = retryPolicy,
            limits = limits,
            logger = logger,
            clock = clock,
            nonceProvider = nonceProvider,
            delay = delay,
            jitterProvider = jitterProvider,
            monotonicClock = monotonicClock,
            socketSessionFactory = socketSessionFactory,
        )

        private fun resolveAuthType(apiSecret: String?): WiroAuthType {
            if (apiSecret == null) {
                return WiroAuthType.API_KEY
            }
            val trimmed = apiSecret.trim()
            return if (trimmed.isEmpty()) {
                WiroAuthType.API_KEY
            } else {
                WiroAuthType.SIGNATURE
            }
        }

        private fun resolveApiKey(
            authType: WiroAuthType,
            apiKey: String?,
        ): String? {
            if (authType == WiroAuthType.PROXY) {
                return null
            }
            val trimmed = apiKey?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                throw WiroValidationException(
                    "apiKey must be a non-empty string.",
                    statusCode = 0,
                )
            }
            return trimmed
        }

        private fun resolveApiSecret(
            authType: WiroAuthType,
            apiSecret: String?,
        ): String? {
            if (authType == WiroAuthType.PROXY || apiSecret == null) {
                return null
            }
            val trimmed = apiSecret.trim()
            if (trimmed.isEmpty()) {
                throw WiroValidationException(
                    "apiSecret must be a non-empty string when provided.",
                    statusCode = 0,
                )
            }
            return trimmed
        }

        private fun validateAndTrimBaseUrl(baseUrl: String): URI {
            val uri =
                WiroValidation.validateUrl(
                    value = baseUrl,
                    kind = WiroUrlKind.HTTP,
                    label = "baseUrl",
                )
            return WiroValidation.trimTrailingSlashes(uri)
        }

        private fun validateSocketUrl(socketUrl: String): URI = WiroValidation.validateUrl(
            value = socketUrl,
            kind = WiroUrlKind.WEB_SOCKET,
            label = "socketUrl",
        )

        private fun validateProxyHeaders(headers: Map<String, String>): Map<String, String> {
            headers.forEach { (name, value) ->
                WiroValidation.validateHeader(name, value)
            }
            return headers
        }

        private fun isSdkOwnedHeader(name: String): Boolean {
            val lowered = name.lowercase(Locale.ROOT)
            return lowered == "user-agent" || lowered == "content-type"
        }

        private val bodyJson =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
    }
}
