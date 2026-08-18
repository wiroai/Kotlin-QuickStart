package ai.wiro.quickstart.core.credentials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsStoreTest {
    @Test
    fun `in-memory store persists field updates`() {
        val store = InMemoryCredentialsStore()
        assertFalse(store.hasCredentials)

        store.apiKey = " key "
        assertTrue(store.hasCredentials)

        store.useProxy = true
        assertFalse(store.hasCredentials)

        store.proxyUrlString = "https://proxy.example.com/v1"
        assertTrue(store.hasCredentials)
        assertEquals("https://proxy.example.com/v1", store.proxyUrlString)
    }

    @Test
    fun `direct and proxy mode switching clears the other gate`() {
        val store =
            InMemoryCredentialsStore(
                apiKey = "key",
                useProxy = false,
            )
        assertTrue(store.hasCredentials)

        store.useProxy = true
        store.proxyUrlString = "not-a-url"
        assertFalse(store.hasCredentials)

        store.proxyUrlString = "https://ok.example/v1"
        assertTrue(store.hasCredentials)

        store.useProxy = false
        assertTrue(store.hasCredentials)
    }

    @Test
    fun `proxy only repository ignores direct credentials`() {
        val store =
            InMemoryCredentialsStore(
                allowsDirectCredentials = false,
                apiKey = "key",
            )

        assertFalse(store.hasCredentials)
        store.proxyUrlString = "https://proxy.example.com/v1"
        assertTrue(store.hasCredentials)
    }
}
