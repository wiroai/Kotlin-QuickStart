package ai.wiro.wirokit

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class WiroValueTest {
    @Test
    fun `json round trip preserves nested structure`() {
        val source =
            """
            {
              "decimal": 1.25,
              "large": 1234567890,
              "items": [true, null, "text", -0.25]
            }
            """.trimIndent()

        val value = WiroValue.fromJsonElement(Json.parseToJsonElement(source))
        val encoded = Json.encodeToString<WiroValue>(value)
        val restored = Json.decodeFromString<WiroValue>(encoded)

        assertEquals(value, restored)
    }

    @Test
    fun `json element conversion preserves number lexemes`() {
        listOf(
            "1.2300e+4",
            "123456789012345678901234567890",
            "-0.00000000000000000001",
        ).forEach { lexeme ->
            val number = WiroValue.NumberValue(lexeme)
            val element = number.toJsonElement()
            val restored = WiroValue.fromJsonElement(element)

            assertEquals(lexeme, element.toString())
            assertEquals(number, restored)
        }
    }

    @Test
    fun `accessors coerce only compatible scalar values`() {
        assertEquals("hello", WiroValue.StringValue("hello").stringValue)
        assertEquals(42, WiroValue.NumberValue("42").intValue)
        assertEquals(7, WiroValue.StringValue(" 7 ").intValue)
        assertNull(WiroValue.NumberValue("3.5").intValue)
        assertEquals(2.5, WiroValue.StringValue("2.5").doubleValue)
        assertEquals(true, WiroValue.BooleanValue(true).booleanValue)
        assertTrue(WiroValue.NullValue.isNull)
        assertFalse(WiroValue.StringValue("null").isNull)
    }

    @Test
    fun `invalid number lexemes are rejected`() {
        listOf("", "01", "+1", "1.", ".5", "NaN", "Infinity", "true")
            .forEach { raw ->
                assertThrows(WiroValidationException::class.java) {
                    WiroValue.NumberValue(raw)
                }
            }
    }

    @Test
    fun `objects and arrays defensively copy their inputs`() {
        val sourceMap =
            mutableMapOf<String, WiroValue>(
                "key" to WiroValue.StringValue("original"),
            )
        val sourceList =
            mutableListOf<WiroValue>(
                WiroValue.BooleanValue(true),
            )
        val objectValue = WiroValue.ObjectValue(sourceMap)
        val arrayValue = WiroValue.ArrayValue(sourceList)

        sourceMap["key"] = WiroValue.StringValue("changed")
        sourceList.clear()

        assertEquals("original", objectValue.value["key"]?.stringValue)
        assertEquals(1, arrayValue.value.size)
    }

    @Test
    fun `unresolved file inputs cannot be serialized`() {
        val input = WiroFileInput.Url(URI("https://example.com/image.png"))
        val value = WiroValue.FileInputValue(input)

        val error =
            assertThrows(WiroValidationException::class.java) {
                value.toJsonElement()
            }

        assertTrue(error.message.orEmpty().contains("unresolved"))
        assertFalse(error.toString().contains("example.com"))
    }

    @Test
    fun `remote file inputs validate URLs and preserve signed queries`() {
        val input =
            WiroFileInput.Url(
                URI("https://example.com/image.png?signature=value"),
            )

        assertEquals(
            "https://example.com/image.png?signature=value",
            input.wireValue,
        )
        assertFalse(input.toString().contains("signature"))
        assertThrows(WiroValidationException::class.java) {
            WiroFileInput.Url(URI("ftp://example.com/image.png"))
        }
        assertThrows(WiroValidationException::class.java) {
            WiroFileInput.Url(URI("/relative/image.png"))
        }
    }

    @Test
    fun `byte input has content equality and defensive copies`() {
        val original = byteArrayOf(1, 2, 3)
        val input = WiroFileInput.Bytes(original, "image.png", "image/png")
        val equal =
            WiroFileInput.Bytes(
                byteArrayOf(1, 2, 3),
                "image.png",
                "image/png",
            )

        original[0] = 9
        val exposed = input.bytes
        exposed[1] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), input.bytes)
        assertEquals(input, equal)
        assertEquals(input.hashCode(), equal.hashCode())
        assertFalse(input.toString().contains("[1, 2, 3]"))
    }

    @Test
    fun `content URI input retains metadata and redacts URI`() {
        val uri = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito
            .`when`(uri.toString())
            .thenReturn("content://ai.wiro.provider/doc/1")

        val input =
            WiroFileInput.ContentUri(
                uri = uri,
                fileName = "photo.png",
                mediaType = "image/png",
                sizeBytes = 128L,
            )
        val equal =
            WiroFileInput.ContentUri(
                uri = uri,
                fileName = "photo.png",
                mediaType = "image/png",
                sizeBytes = 128L,
            )

        assertNull(input.wireValue)
        assertEquals(input, equal)
        assertEquals(input.hashCode(), equal.hashCode())
        assertFalse(input.toString().contains("content://"))
        assertFalse(input.toString().contains("photo.png"))
    }
}
