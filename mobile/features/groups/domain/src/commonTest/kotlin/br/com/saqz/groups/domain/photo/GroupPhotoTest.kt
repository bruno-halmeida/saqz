package br.com.saqz.groups.domain.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GroupPhotoTest {
    @Test
    fun `all media types preserve content type and extension`() {
        assertEquals(
            listOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp"),
            GroupPhotoMediaType.entries.map { it.value to it.extension },
        )
    }

    @Test
    fun `source handle rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { GroupPhotoSourceHandle(" ") }
    }

    @Test
    fun `preview handle preserves provider neutral value`() {
        assertEquals("preview-1", GroupPhotoPreviewHandle("preview-1").value)
    }

    @Test
    fun `selection preserves source preview and dimensions`() {
        assertEquals(1200 to 900, selection().let { it.width to it.height })
    }

    @Test
    fun `selection rejects invalid dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            GroupPhotoSelection(source(), preview(), 0, 100)
        }
    }

    @Test
    fun `crop preserves center and zoom`() {
        assertEquals(Triple(0.4f, 0.6f, 2f), GroupPhotoCrop(0.4f, 0.6f, 2f).let {
            Triple(it.centerX, it.centerY, it.zoom)
        })
    }

    @Test
    fun `crop rejects out of range zoom`() {
        assertFailsWith<IllegalArgumentException> { GroupPhotoCrop(zoom = 9f) }
    }

    @Test
    fun `encoded photo preserves bytes media and length`() {
        val encoded = encoded()

        assertEquals(GroupPhotoMediaType.JPEG to 3L, encoded.mediaType to encoded.contentLength)
        assertContentEquals(byteArrayOf(1, 2, 3), encoded.source.read())
    }

    @Test
    fun `encoded photo enforces media limit`() {
        assertFailsWith<IllegalArgumentException> {
            EncodedGroupPhoto(
                GroupPhotoMediaType.JPEG,
                EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES + 1,
            ) { byteArrayOf(1) }
        }
    }

    @Test
    fun `selection outcomes are exhaustive`() {
        val outcomes = listOf(
            GroupPhotoSelectionResult.Selected(selection()),
            GroupPhotoSelectionResult.Cancelled,
            GroupPhotoSelectionResult.Failed,
        )

        assertEquals(3, outcomes.size)
    }

    @Test
    fun `encoding outcomes preserve encoded value and failure`() {
        val encoded = encoded()

        assertEquals(
            encoded,
            assertIs<GroupPhotoEncodingResult.Encoded>(
                GroupPhotoEncodingResult.Encoded(encoded),
            ).value,
        )
        assertIs<GroupPhotoEncodingResult.Failed>(GroupPhotoEncodingResult.Failed)
    }

    @Test
    fun `available photo preserves bytes content type and version`() {
        val result = GroupPhotoReadResult.Available(
            byteArrayOf(4, 5),
            "image/jpeg",
            GroupPhotoVersionToken("photo-v1"),
        )

        assertContentEquals(byteArrayOf(4, 5), result.bytes)
        assertEquals("image/jpeg" to "photo-v1", result.contentType to result.version.value)
    }

    @Test
    fun `not modified remains explicit`() {
        assertIs<GroupPhotoReadResult.NotModified>(GroupPhotoReadResult.NotModified)
    }

    @Test
    fun `receipt preserves opaque version`() {
        assertEquals("group-v2", GroupPhotoReceipt(GroupPhotoVersionToken("group-v2")).version.value)
    }

    @Test
    fun `upload command preserves group version and photo`() {
        val command = GroupPhotoUploadCommand(
            GroupId("group-1"),
            GroupPhotoVersionToken("group-v1"),
            encoded(),
        )

        assertEquals(GroupId("group-1") to "group-v1", command.groupId to command.groupVersion.value)
        assertEquals(GroupPhotoMediaType.JPEG to 3L, command.photo.let {
            it.mediaType to it.contentLength
        })
    }

    @Test
    fun `photo errors remain exhaustive and transport independent`() {
        val errors = listOf(
            GroupPhotoError.MediaLimit,
            GroupPhotoError.StaleVersion,
            GroupPhotoError.NotFound,
            GroupPhotoError.DataFailure(DataError.Connectivity),
        )

        assertEquals(4, errors.size)
        assertEquals(
            DataError.Connectivity,
            assertIs<GroupPhotoError.DataFailure>(errors.last()).error,
        )
    }

    @Test
    fun `cached photo preserves preview and version`() {
        val cached = CachedGroupPhoto(preview(), GroupPhotoVersionToken("photo-v1"))

        assertEquals("preview-1" to "photo-v1", cached.preview.value to cached.version.value)
    }

    private fun source() = GroupPhotoSourceHandle("source-1")
    private fun preview() = GroupPhotoPreviewHandle("preview-1")
    private fun selection() = GroupPhotoSelection(source(), preview(), 1200, 900)
    private fun encoded() = EncodedGroupPhoto(GroupPhotoMediaType.JPEG, 3) { byteArrayOf(1, 2, 3) }
}
