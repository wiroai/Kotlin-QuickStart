package ai.wiro.quickstart.core.credentials

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialsStoreAndroidTest {
    @Test
    fun keystoreBackedStoreSurvivesNewStoreInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = CredentialsStore(context)
        first.apiKey = "persisted-key"
        first.apiSecret = "persisted-secret"
        first.useProxy = true
        first.proxyUrlString = "https://proxy.example.com/v1"

        val rawValues =
            context
                .getSharedPreferences(
                    "wiro.example.credentials",
                    Context.MODE_PRIVATE,
                ).all.values
                .joinToString()
        assertFalse(rawValues.contains("persisted-key"))
        assertFalse(rawValues.contains("persisted-secret"))

        val second = CredentialsStore(context)
        assertEquals("persisted-key", second.apiKey)
        assertEquals("persisted-secret", second.apiSecret)
        assertTrue(second.useProxy)
        assertEquals("https://proxy.example.com/v1", second.proxyUrlString)
        assertTrue(second.hasCredentials)
    }
}
