package ai.wiro.quickstart.generate

import ai.wiro.quickstart.core.credentials.CredentialsRepository
import ai.wiro.wirokit.WiroClient
import ai.wiro.wirokit.WiroModelRequest
import ai.wiro.wirokit.WiroTaskId
import ai.wiro.wirokit.WiroTaskToken
import ai.wiro.wirokit.WiroTaskUpdate
import ai.wiro.wirokit.WiroValidationException
import ai.wiro.wirokit.subscribeStream
import kotlinx.coroutines.flow.Flow

/**
 * Creates a [WiroSession] from the current credential mode.
 */
public fun interface WiroSessionFactory {
    public fun open(credentials: CredentialsRepository): WiroSession
}

/**
 * Default factory that builds a real [WiroClient] session.
 */
public object DefaultWiroSessionFactory : WiroSessionFactory {
    override fun open(credentials: CredentialsRepository): WiroSession {
        val client = makeClient(credentials)
        return ClientWiroSession(client)
    }

    public fun makeClient(credentials: CredentialsRepository): WiroClient {
        if (credentials.useProxy) {
            val trimmed = credentials.proxyUrlString.trim()
            if (trimmed.isEmpty()) {
                throw WiroValidationException(
                    "Proxy URL is invalid.",
                    statusCode = 0,
                )
            }
            runCatching {
                val uri = java.net.URI(trimmed)
                require(uri.scheme != null && uri.host != null)
            }.getOrElse {
                throw WiroValidationException(
                    "Proxy URL is invalid.",
                    statusCode = 0,
                )
            }
            return WiroClient(proxyUrl = trimmed)
        }

        val key = credentials.apiKey.trim()
        val secret = credentials.apiSecret.trim()
        return WiroClient(
            apiKey = key,
            apiSecret = secret.ifEmpty { null },
        )
    }
}

private class ClientWiroSession(
    private val client: WiroClient,
) : WiroSession {
    override suspend fun subscribeStream(
        request: WiroModelRequest,
    ): Flow<WiroTaskUpdate> = client.subscribeStream(request)

    override suspend fun cancelTask(
        id: WiroTaskId,
    ): Boolean = client.cancelTask(id)

    override suspend fun killTask(
        token: WiroTaskToken,
    ): Boolean = client.killTask(token)

    override suspend fun killTask(id: WiroTaskId): Boolean = client.killTask(id)

    override fun close() {
        client.close()
    }
}
