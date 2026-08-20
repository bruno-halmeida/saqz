package br.com.saqz.groups.presentation.photo

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupPhotoDecoderTest {

    @Test fun `the adapter ceiling is sampled down to the thumb`() {
        assertEquals(8, groupPhotoSampleSize(4096, 4096, GROUP_PHOTO_THUMB_PX))
    }

    @Test fun `an image already smaller than the target is never enlarged`() {
        assertEquals(1, groupPhotoSampleSize(120, 90, GROUP_PHOTO_THUMB_PX))
        assertEquals(1, groupPhotoSampleSize(GROUP_PHOTO_THUMB_PX, GROUP_PHOTO_THUMB_PX, GROUP_PHOTO_THUMB_PX))
    }

    @Test fun `a header that says nothing does not divide by zero`() {
        assertEquals(1, groupPhotoSampleSize(0, 0, GROUP_PHOTO_THUMB_PX))
        assertEquals(1, groupPhotoSampleSize(4096, 4096, 0))
    }

    @Test fun `bytes that are not an image decode to nothing`() = runTest {
        assertNull(decodeGroupPhoto(byteArrayOf(1, 2, 3), GROUP_PHOTO_THUMB_PX))
        assertNull(decodeGroupPhoto(byteArrayOf(), GROUP_PHOTO_THUMB_PX))
    }

    @Test fun `a real image decodes`() = runTest {
        val decoded = decodeGroupPhoto(GROUP_PHOTO_PNG_PIXEL, GROUP_PHOTO_THUMB_PX)

        assertEquals(1, decoded?.width)
        assertEquals(1, decoded?.height)
    }
}
