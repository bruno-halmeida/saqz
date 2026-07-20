package br.com.saqz.groups.port

import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupPhotoPortsTest {
    @Test fun `source and preview handles reject empty provider values`() {
        assertFailsWith<IllegalArgumentException> { GroupPhotoSourceHandle(" ") }
        assertFailsWith<IllegalArgumentException> { GroupPhotoPreviewHandle("") }
    }

    @Test fun `crop rejects coordinates outside normalized square`() {
        assertFailsWith<IllegalArgumentException> { GroupPhotoCrop(left = 0.5f, size = 0.6f) }
        assertFailsWith<IllegalArgumentException> { GroupPhotoCrop(top = -0.1f) }
    }

    @Test fun `encoded payload accepts only bounded nonempty content`() {
        val source = GroupPhotoByteSource { ByteReadChannel(byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { EncodedGroupPhoto(GroupPhotoMediaType.PNG, 0, source) }
        assertFailsWith<IllegalArgumentException> {
            EncodedGroupPhoto(GroupPhotoMediaType.PNG, EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES + 1, source)
        }
        assertEquals(1, EncodedGroupPhoto(GroupPhotoMediaType.PNG, 1, source).contentLength)
    }
}
