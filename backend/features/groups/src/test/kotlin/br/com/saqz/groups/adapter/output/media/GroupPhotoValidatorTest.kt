package br.com.saqz.groups.adapter.output.media

import br.com.saqz.groups.application.photo.GroupPhotoMediaType
import br.com.saqz.groups.application.photo.GroupPhotoRejection
import br.com.saqz.groups.application.photo.GroupPhotoValidationResult
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupPhotoValidatorTest {
    private val validator = GroupPhotoValidator()

    @Test fun `empty input is rejected`() {
        assertRejected(GroupPhotoRejection.EMPTY, validator.validate("image/png", ByteArrayInputStream(byteArrayOf())))
    }

    @Test fun `stream exceeding byte limit is rejected before decode`() {
        val result = GroupPhotoValidator(maximumBytes = 8).validate("image/png", ByteArrayInputStream(ByteArray(9)))
        assertRejected(GroupPhotoRejection.TOO_LARGE, result)
    }

    @Test fun `unsupported GIF is rejected`() {
        assertRejected(
            GroupPhotoRejection.UNSUPPORTED_TYPE,
            validator.validate("image/gif", ByteArrayInputStream("GIF89a".encodeToByteArray())),
        )
    }

    @Test fun `declared and actual type mismatch is rejected`() {
        assertRejected(
            GroupPhotoRejection.DECLARED_TYPE_MISMATCH,
            validator.validate("image/jpeg", ByteArrayInputStream(png())),
        )
    }

    @Test fun `valid PNG records decoded metadata and digest`() {
        val bytes = png(3, 2)
        val photo = assertIs<GroupPhotoValidationResult.Valid>(
            validator.validate("image/png", ByteArrayInputStream(bytes)),
        ).photo
        assertEquals(GroupPhotoMediaType.PNG, photo.mediaType)
        assertEquals(3, photo.width)
        assertEquals(2, photo.height)
        assertContentEquals(MessageDigest.getInstance("SHA-256").digest(bytes), photo.sha256Digest)
    }

    @Test fun `PNG animation control chunk is rejected before decode`() {
        assertRejected(
            GroupPhotoRejection.ANIMATED,
            validator.validate("image/png", ByteArrayInputStream(pngWithChunk("acTL"))),
        )
    }

    @Test fun `valid JPEG is decoded`() {
        val photo = assertIs<GroupPhotoValidationResult.Valid>(
            validator.validate("image/jpeg", ByteArrayInputStream(jpeg(4, 3))),
        ).photo
        assertEquals(GroupPhotoMediaType.JPEG, photo.mediaType)
        assertEquals(4 to 3, photo.width to photo.height)
    }

    @Test fun `corrupt JPEG is rejected`() {
        assertRejected(
            GroupPhotoRejection.INVALID_IMAGE,
            validator.validate("image/jpeg", ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0))),
        )
    }

    @Test fun `valid WebP is decoded by registered reader`() {
        val photo = assertIs<GroupPhotoValidationResult.Valid>(
            validator.validate("image/webp", ByteArrayInputStream(webp())),
        ).photo
        assertEquals(GroupPhotoMediaType.WEBP, photo.mediaType)
        assertEquals(1 to 1, photo.width to photo.height)
    }

    @Test fun `WebP ANIM chunk is rejected`() {
        assertRejected(GroupPhotoRejection.ANIMATED, validator.validate("image/webp", ByteArrayInputStream(webpChunk("ANIM"))))
    }

    @Test fun `WebP ANMF chunk is rejected`() {
        assertRejected(GroupPhotoRejection.ANIMATED, validator.validate("image/webp", ByteArrayInputStream(webpChunk("ANMF"))))
    }

    @Test fun `corrupt PNG is rejected`() {
        val corrupt = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        assertRejected(GroupPhotoRejection.INVALID_IMAGE, validator.validate("image/png", ByteArrayInputStream(corrupt)))
    }

    @Test fun `dimension over decoded limit is rejected before raster read`() {
        val result = validator.validate("image/png", ByteArrayInputStream(png(4097, 1)))
        assertRejected(GroupPhotoRejection.DIMENSIONS_TOO_LARGE, result)
    }

    @Test fun `exact byte limit remains accepted`() {
        val bytes = png()
        val result = GroupPhotoValidator(maximumBytes = bytes.size).validate("image/png", ByteArrayInputStream(bytes))
        assertIs<GroupPhotoValidationResult.Valid>(result)
    }

    @Test fun `validator closes owned stream on rejection`() {
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(9)) {
            override fun close() { closed = true; super.close() }
        }
        GroupPhotoValidator(maximumBytes = 8).validate("image/png", input)
        assertTrue(closed)
    }

    private fun assertRejected(reason: GroupPhotoRejection, result: GroupPhotoValidationResult) {
        assertEquals(reason, assertIs<GroupPhotoValidationResult.Rejected>(result).reason)
    }

    private fun png(width: Int = 2, height: Int = 2): ByteArray = image("png", width, height)
    private fun jpeg(width: Int = 2, height: Int = 2): ByteArray = image("jpeg", width, height)

    private fun image(format: String, width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        assertTrue(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output))
        output.toByteArray()
    }

    private fun pngWithChunk(type: String): ByteArray {
        val original = png()
        val ihdrEnd = 8 + 12 + 13
        val chunk = byteArrayOf(0, 0, 0, 0) + type.encodeToByteArray() + byteArrayOf(0, 0, 0, 0)
        return original.copyOfRange(0, ihdrEnd) + chunk + original.copyOfRange(ihdrEnd, original.size)
    }

    private fun webp(): ByteArray = Base64.getDecoder().decode(
        "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/vuUAAA=",
    )

    private fun webpChunk(type: String): ByteArray =
        "RIFF".encodeToByteArray() + byteArrayOf(4, 0, 0, 0) + "WEBP".encodeToByteArray() +
            type.encodeToByteArray() + byteArrayOf(0, 0, 0, 0)
}
