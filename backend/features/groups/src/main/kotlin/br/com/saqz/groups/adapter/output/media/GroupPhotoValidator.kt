package br.com.saqz.groups.adapter.output.media

import br.com.saqz.groups.application.photo.GroupPhotoMediaType
import br.com.saqz.groups.application.photo.GroupPhotoRejection
import br.com.saqz.groups.application.photo.GroupPhotoValidationResult
import br.com.saqz.groups.application.photo.ValidatedGroupPhoto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.imageio.ImageIO

class GroupPhotoValidator(
    private val maximumBytes: Int = 5 * 1024 * 1024,
    private val maximumDimension: Int = 4096,
) {
    fun validate(declaredContentType: String, input: InputStream): GroupPhotoValidationResult {
        val bytes = readBounded(input) ?: return rejected(GroupPhotoRejection.TOO_LARGE)
        if (bytes.isEmpty()) return rejected(GroupPhotoRejection.EMPTY)
        val actualType = detectType(bytes) ?: return rejected(GroupPhotoRejection.UNSUPPORTED_TYPE)
        val declaredType = GroupPhotoMediaType.entries.firstOrNull { it.value.equals(declaredContentType, ignoreCase = true) }
            ?: return rejected(GroupPhotoRejection.UNSUPPORTED_TYPE)
        if (declaredType != actualType) return rejected(GroupPhotoRejection.DECLARED_TYPE_MISMATCH)
        if (isAnimated(actualType, bytes)) return rejected(GroupPhotoRejection.ANIMATED)

        return runCatching {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { imageInput ->
                val readers = ImageIO.getImageReaders(imageInput)
                if (!readers.hasNext()) return rejected(GroupPhotoRejection.INVALID_IMAGE)
                val reader = readers.next()
                try {
                    reader.input = imageInput
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width !in 1..maximumDimension || height !in 1..maximumDimension) {
                        return rejected(GroupPhotoRejection.DIMENSIONS_TOO_LARGE)
                    }
                    val decoded = reader.read(0) ?: return rejected(GroupPhotoRejection.INVALID_IMAGE)
                    if (decoded.width != width || decoded.height != height) return rejected(GroupPhotoRejection.INVALID_IMAGE)
                    GroupPhotoValidationResult.Valid(
                        ValidatedGroupPhoto(
                            bytes = bytes,
                            mediaType = actualType,
                            width = width,
                            height = height,
                            sha256Digest = MessageDigest.getInstance("SHA-256").digest(bytes),
                        ),
                    )
                } finally {
                    reader.dispose()
                }
            }
        }.getOrElse { rejected(GroupPhotoRejection.INVALID_IMAGE) }
    }

    private fun readBounded(input: InputStream): ByteArray? = input.use { source ->
        val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            if (total > maximumBytes) return null
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun detectType(bytes: ByteArray): GroupPhotoMediaType? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> GroupPhotoMediaType.JPEG
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> GroupPhotoMediaType.PNG
        bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" -> GroupPhotoMediaType.WEBP
        else -> null
    }

    private fun isAnimated(type: GroupPhotoMediaType, bytes: ByteArray): Boolean = when (type) {
        GroupPhotoMediaType.JPEG -> false
        GroupPhotoMediaType.PNG -> pngChunks(bytes).any { it == "acTL" }
        GroupPhotoMediaType.WEBP -> webpChunks(bytes).any { it == "ANIM" || it == "ANMF" }
    }

    private fun pngChunks(bytes: ByteArray): Sequence<String> = sequence {
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = bytes.bigEndianInt(offset)
            if (length < 0 || offset + 12L + length > bytes.size) return@sequence
            yield(bytes.ascii(offset + 4, 4))
            offset += 12 + length
        }
    }

    private fun webpChunks(bytes: ByteArray): Sequence<String> = sequence {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val length = bytes.littleEndianInt(offset + 4)
            if (length < 0 || offset + 8L + length > bytes.size) return@sequence
            yield(bytes.ascii(offset, 4))
            offset += 8 + length + (length and 1)
        }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xFF == expected[it] }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun ByteArray.bigEndianInt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun rejected(reason: GroupPhotoRejection) = GroupPhotoValidationResult.Rejected(reason)
}
