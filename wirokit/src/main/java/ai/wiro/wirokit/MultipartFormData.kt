package ai.wiro.wirokit

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Builds `multipart/form-data` bodies for `/File/Upload`.
 */
internal object MultipartFormData {
    const val PART_NAME: String = "file"
    private const val CHUNK_SIZE: Int = 1024 * 1024

    class Body(
        public val boundary: String,
        bytes: ByteArray,
    ) {
        private val stored: ByteArray = bytes.copyOf()

        public val data: ByteArray
            get() = stored.copyOf()

        public val contentType: String
            get() = "multipart/form-data; boundary=$boundary"
    }

    fun buildFilePart(
        data: ByteArray,
        fileName: String,
        boundary: String = makeBoundary(),
    ): Body {
        val preamble = preamble(boundary, fileName).toByteArray(Charsets.UTF_8)
        val epilogue = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val body = ByteArray(preamble.size + data.size + epilogue.size)
        System.arraycopy(preamble, 0, body, 0, preamble.size)
        System.arraycopy(data, 0, body, preamble.size, data.size)
        System.arraycopy(
            epilogue,
            0,
            body,
            preamble.size + data.size,
            epilogue.size,
        )
        return Body(boundary = boundary, bytes = body)
    }

    fun writeFilePart(
        source: InputStream,
        fileName: String,
        destination: File,
        boundary: String = makeBoundary(),
    ): String {
        destination.outputStream().use { output ->
            output.write(preamble(boundary, fileName).toByteArray(Charsets.UTF_8))
            copy(source, output)
            output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }
        return boundary
    }

    fun makeBoundary(): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        return "Boundary-$token"
    }

    fun escapeFileName(fileName: String): String = fileName
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun preamble(
        boundary: String,
        fileName: String,
    ): String = "--$boundary\r\n" +
        "Content-Disposition: form-data; name=\"$PART_NAME\"; " +
        "filename=\"${escapeFileName(fileName)}\"\r\n" +
        "Content-Type: application/octet-stream\r\n" +
        "\r\n"

    private fun copy(
        source: InputStream,
        destination: OutputStream,
    ) {
        val buffer = ByteArray(CHUNK_SIZE)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) {
                break
            }
            if (read > 0) {
                destination.write(buffer, 0, read)
            }
        }
    }
}
