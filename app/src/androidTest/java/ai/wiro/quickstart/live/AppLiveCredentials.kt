package ai.wiro.quickstart.live

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Resolves short-lived live-test credentials for the example app.
 * Mirrors wirokit live helpers; never logs secret values.
 */
internal object AppLiveCredentials {
    data class Config(
        val apiKey: String?,
        val apiSecret: String?,
        val proxyUrl: String?,
        val useProxy: Boolean,
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
            val fromEnv =
                System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
            if (fromEnv != null) return fromEnv
            return InstrumentationRegistry
                .getArguments()
                .getString(name)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        val useProxy =
            read("WIRO_USE_PROXY")
                ?.equals("true", ignoreCase = true) == true ||
                read("WIRO_USE_PROXY") == "1" ||
                (
                    !read("WIRO_PROXY_URL").isNullOrBlank() &&
                        read("WIRO_API_KEY").isNullOrBlank()
                    )

        return Config(
            apiKey = read("WIRO_API_KEY"),
            apiSecret = read("WIRO_API_SECRET"),
            proxyUrl = read("WIRO_PROXY_URL"),
            useProxy = useProxy,
        )
    }

    fun assumeEnabled() {
        assumeTrue(
            "Live UI tests skipped: set WIRO_API_KEY or WIRO_PROXY_URL.",
            load().isEnabled,
        )
    }
}
