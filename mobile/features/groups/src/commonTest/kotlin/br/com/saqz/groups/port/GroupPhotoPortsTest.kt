package br.com.saqz.groups.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupPhotoPortsTest {
    @Test fun `source and preview handles reject empty provider values`() {
        assertFailsWith<IllegalArgumentException> { GroupPhotoSourceHandle(" ") }
        assertFailsWith<IllegalArgumentException> { GroupPhotoPreviewHandle("") }
    }

    @Test fun `crop rejects centers and zoom outside shared bounds`() {
        assertFailsWith<IllegalArgumentException> { GroupPhotoCrop(centerX = 1.1f) }
        assertFailsWith<IllegalArgumentException> { GroupPhotoCrop(centerY = -0.1f) }
        assertFailsWith<IllegalArgumentException> { GroupPhotoCrop(zoom = 0.9f) }
    }

    @Test fun `encoded payload accepts only bounded nonempty content`() {
        val source = GroupPhotoByteSource { byteArrayOf(1) }
        assertFailsWith<IllegalArgumentException> { EncodedGroupPhoto(GroupPhotoMediaType.PNG, 0, source) }
        assertFailsWith<IllegalArgumentException> {
            EncodedGroupPhoto(GroupPhotoMediaType.PNG, EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES + 1, source)
        }
        assertEquals(1, EncodedGroupPhoto(GroupPhotoMediaType.PNG, 1, source).contentLength)
    }

    @Test fun `cached photo keeps an opaque preview separate from its photo etag`() {
        val cached = CachedGroupPhoto(GroupPhotoPreviewHandle("cache-preview"), "\"photo-7\"")

        assertEquals("cache-preview", cached.preview.value)
        assertEquals("\"photo-7\"", cached.photoEtag)
        assertFailsWith<IllegalArgumentException> {
            CachedGroupPhoto(GroupPhotoPreviewHandle("cache-preview"), " ")
        }
    }
}
