package ai.wiro.wirokit

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WiroIdentifiersTest {
    @Test
    fun `model id accepts valid segments and serializes as slug`() {
        val id =
            WiroModelId(
                owner = "black-forest-labs",
                project = "flux-2-pro",
            )

        assertEquals("black-forest-labs/flux-2-pro", id.slug)
        assertEquals(
            "\"black-forest-labs/flux-2-pro\"",
            Json.encodeToString(id),
        )
        assertEquals(id, Json.decodeFromString<WiroModelId>("\"${id.slug}\""))
    }

    @Test
    fun `model id rejects malformed segments`() {
        listOf(
            "" to "model",
            "-owner" to "model",
            "bad owner" to "model",
            "owner" to "bad/model",
            "ünicode" to "model",
        ).forEach { (owner, project) ->
            val error =
                assertThrows(WiroValidationException::class.java) {
                    WiroModelId(owner, project)
                }
            assertTrue(error.message.orEmpty().contains("Invalid model"))
            assertEquals(0, error.statusCode)
        }
    }

    @Test
    fun `model id parser trims and rejects malformed slugs`() {
        assertEquals(
            "openai/gpt-image-2",
            WiroModelId.parse(" openai/gpt-image-2 ")?.slug,
        )
        assertNull(WiroModelId.parse(""))
        assertNull(WiroModelId.parse("owner"))
        assertNull(WiroModelId.parse("owner/project/extra"))
        assertNull(WiroModelId.parse("/project"))
        assertNull(WiroModelId.parse("owner/"))
    }

    @Test
    fun `task identifiers trim values and reject blanks`() {
        assertEquals("42", WiroTaskId(" 42 ").rawValue)
        assertEquals("token", WiroTaskToken(" token ").rawValue)
        assertNull(WiroTaskId.parse(" \n "))
        assertNull(WiroTaskToken.parse(""))
    }

    @Test
    fun `task identifiers serialize as strings without rendering tokens`() {
        val id = WiroTaskId("42")
        val token = WiroTaskToken("secret-token")

        assertEquals("\"42\"", Json.encodeToString(id))
        assertEquals(id, Json.decodeFromString<WiroTaskId>("\"42\""))
        assertEquals(
            token,
            Json.decodeFromString<WiroTaskToken>(
                "\"secret-token\"",
            ),
        )
        assertTrue(token.toString().contains("[REDACTED]"))
        assertTrue(!token.toString().contains(token.rawValue))
    }
}
