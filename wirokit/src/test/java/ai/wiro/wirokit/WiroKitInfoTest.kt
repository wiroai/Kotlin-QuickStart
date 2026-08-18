package ai.wiro.wirokit

import org.junit.Assert.assertEquals
import org.junit.Test

public class WiroKitInfoTest {
    @Test
    public fun version_isInitialRelease() {
        assertEquals("0.1.0", WiroKitInfo.VERSION)
    }
}
