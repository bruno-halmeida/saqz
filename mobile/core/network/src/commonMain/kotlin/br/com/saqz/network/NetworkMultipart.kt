package br.com.saqz.network

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlin.random.Random

internal class MediaLimitException : IllegalStateException()

internal class BoundedMultipartContent(
    private val upload: NetworkMediaUpload,
    private val maximumBytes: Long,
) : OutgoingContent.WriteChannelContent() {
    var limitExceeded: Boolean = false
        private set
    private val boundary = "saqz-${Random.nextLong().toString(16)}"
    private val prefix = buildString {
        append("--$boundary\r\n")
        append("Content-Disposition: form-data; name=\"")
        append(upload.fieldName)
        append("\"; filename=\"")
        append(upload.fileName)
        append("\"\r\n")
        append("Content-Type: ${upload.contentType}\r\n")
        append("Content-Length: ${upload.contentLength}\r\n\r\n")
    }.encodeToByteArray()
    private val suffix = "\r\n--$boundary--\r\n".encodeToByteArray()

    override val contentType: ContentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)
    override val contentLength: Long = prefix.size + upload.contentLength + suffix.size

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val source = upload.openChannel()
        var transferred = 0L
        val buffer = ByteArray(8 * 1024)
        try {
            channel.writeFully(prefix)
            while (true) {
                val read = source.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                transferred += read
                if (transferred > maximumBytes || transferred > upload.contentLength) failLimit()
                channel.writeFully(buffer, 0, read)
            }
            if (transferred != upload.contentLength) failLimit()
            channel.writeFully(suffix)
        } catch (failure: Throwable) {
            channel.cancel(failure)
            throw failure
        } finally {
            source.cancel()
        }
    }

    private fun failLimit(): Nothing {
        limitExceeded = true
        throw MediaLimitException()
    }
}
