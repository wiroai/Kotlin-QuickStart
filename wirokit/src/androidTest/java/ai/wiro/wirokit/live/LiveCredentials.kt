package ai.wiro.wirokit.live

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Resolves short-lived live-test credentials from process environment or
 * instrumentation arguments. Never logs secret values.
 */
internal object LiveCredentials {
    const val ENV_API_KEY: String = "WIRO_API_KEY"
    const val ENV_API_SECRET: String = "WIRO_API_SECRET"
    const val ENV_PROXY_URL: String = "WIRO_PROXY_URL"
    const val ENV_USE_PROXY: String = "WIRO_USE_PROXY"
    const val ENV_BASE_URL: String = "WIRO_BASE_URL"
    const val ENV_SOCKET_URL: String = "WIRO_SOCKET_URL"

    data class Config(
        val apiKey: String?,
        val apiSecret: String?,
        val proxyUrl: String?,
        val useProxy: Boolean,
        val baseUrl: String?,
        val socketUrl: String?,
    ) {
        val isEnabled: Boolean
            get() =
                if (useProxy) {
                    !proxyUrl.isNullOrBlank()
                } else {
                    !apiKey.isNullOrBlank()
                }
    }

    fun load(): Config {
        fun read(name: String): String? {
            val fromEnv = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
            if (fromEnv != null) return fromEnv
            return InstrumentationRegistry
                .getArguments()
                .getString(name)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        val useProxy =
            read(ENV_USE_PROXY)
                ?.equals("true", ignoreCase = true) == true ||
                read(ENV_USE_PROXY) == "1" ||
                (
                    !read(ENV_PROXY_URL).isNullOrBlank() &&
                        read(ENV_API_KEY).isNullOrBlank()
                    )

        return Config(
            apiKey = read(ENV_API_KEY),
            apiSecret = read(ENV_API_SECRET),
            proxyUrl = read(ENV_PROXY_URL),
            useProxy = useProxy,
            baseUrl = read(ENV_BASE_URL),
            socketUrl = read(ENV_SOCKET_URL),
        )
    }

    fun assumeEnabled() {
        val config = load()
        assumeTrue(
            "Live tests skipped: set WIRO_API_KEY (+ optional " +
                "WIRO_API_SECRET) or WIRO_PROXY_URL.",
            config.isEnabled,
        )
    }
}
