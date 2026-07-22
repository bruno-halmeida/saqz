package br.com.saqz.composeapp.navigation

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoilGroupPhotoCacheTest {
    private val directory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "saqz-coil-group-photo-test-${Random.nextLong().toULong()}"
    private var cache = CoilGroupPhotoCache(directory)

    @AfterTest
    fun cleanup() {
        cache.clearAll()
        cache.shutdown()
    }

    @Test
    fun `writes and reads private preview with separate photo etag`() {
        val bytes = byteArrayOf(1, 2, 3)

        val written = cache.write(GroupId("group-1"), bytes, GroupPhotoVersionToken("photo-etag"))
        val cached = cache.read(GroupId("group-1"))

        assertNotNull(written)
        assertEquals("photo-etag", cached?.version?.value)
        assertEquals(written.preview, cached?.preview)
        assertContentEquals(bytes, cache.read(written.preview))
        assertNotEquals("group-1", written.preview.value)
    }

    @Test
    fun `replacement atomically exposes only the latest bytes and etag`() {
        val old = cache.write(GroupId("group-1"), byteArrayOf(1), GroupPhotoVersionToken("old"))
        val latest = cache.write(GroupId("group-1"), byteArrayOf(9, 8), GroupPhotoVersionToken("latest"))

        assertNotNull(old)
        assertNotNull(latest)
        assertNotEquals(latest.preview, old.preview)
        assertNull(cache.read(old.preview))
        assertEquals("latest", cache.read(GroupId("group-1"))?.version?.value)
        assertContentEquals(byteArrayOf(9, 8), cache.read(latest.preview))
    }

    @Test
    fun `eviction removes exactly the requested group`() {
        val first = cache.write(GroupId("group-1"), byteArrayOf(1), GroupPhotoVersionToken("one"))
        val second = cache.write(GroupId("group-2"), byteArrayOf(2), GroupPhotoVersionToken("two"))

        cache.evict(GroupId("group-1"))

        assertNull(cache.read(GroupId("group-1")))
        assertNull(first?.preview?.let { cache.read(it) })
        assertEquals("two", cache.read(GroupId("group-2"))?.version?.value)
        assertContentEquals(byteArrayOf(2), second?.preview?.let { cache.read(it) })
    }

    @Test
    fun `rejects invalid payload bounds metadata and forged handles`() {
        assertNull(cache.write(GroupId("group-1"), byteArrayOf(), GroupPhotoVersionToken("etag")))
        assertNull(
            cache.write(
                GroupId("group-1"),
                ByteArray(EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES.toInt() + 1),
                GroupPhotoVersionToken("etag"),
            ),
        )
        assertNull(cache.read(GroupPhotoPreviewHandle("../group-photo-secret")))
        assertNull(cache.read(GroupPhotoPreviewHandle("coil-group-photo:../secret")))
    }

    @Test
    fun `new process cache clears prior session entries`() {
        assertNotNull(cache.write(GroupId("group-1"), byteArrayOf(4), GroupPhotoVersionToken("etag")))
        cache.shutdown()

        cache = CoilGroupPhotoCache(directory)

        assertNull(cache.read(GroupId("group-1")))
    }
}
