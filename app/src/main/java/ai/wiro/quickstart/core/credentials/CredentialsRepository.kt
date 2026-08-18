package ai.wiro.quickstart.core.credentials

/**
 * Development credentials for the example app.
 *
 * Production apps should prefer a backend proxy so long-lived API secrets
 * never embed in the binary.
 */
public interface CredentialsRepository {
    public val allowsDirectCredentials: Boolean
    public var apiKey: String
    public var apiSecret: String
    public var proxyUrlString: String
    public var useProxy: Boolean

    public val hasCredentials: Boolean
        get() {
            if (!allowsDirectCredentials || useProxy) {
                val trimmed = proxyUrlString.trim()
                if (trimmed.isEmpty()) {
                    return false
                }
                return runCatching {
                    val uri = java.net.URI(trimmed)
                    uri.scheme != null && uri.host != null
                }.getOrDefault(false)
            }
            return apiKey.trim().isNotEmpty()
        }
}
