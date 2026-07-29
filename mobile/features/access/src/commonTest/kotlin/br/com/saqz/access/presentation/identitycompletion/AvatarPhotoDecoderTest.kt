package br.com.saqz.access.presentation.identitycompletion

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A conta da subamostragem é o que impede o bitmap inteiro de ser alocado, então ela é
 * testada sozinha: os limites dos adapters (4096×4096, VUL-83) contra o alvo do avatar.
 */
class AvatarPhotoDecoderTest {

    @Test fun `the adapter ceiling is sampled down to the avatar`() {
        // 4096 / 8 = 512, o primeiro fator que não passa do dobro do alvo — o bitmap sai
        // com ~1/64 dos pixels, que é a diferença entre uns 64 MiB e uns 1000 KiB.
        assertEquals(8, avatarSampleSize(4096, 4096, AVATAR_TARGET_PX))
    }

    @Test fun `the longest side decides`() {
        assertEquals(8, avatarSampleSize(4096, 512, AVATAR_TARGET_PX))
        assertEquals(8, avatarSampleSize(512, 4096, AVATAR_TARGET_PX))
    }

    // Ampliar gastaria memória para mostrar os mesmos pixels.
    @Test fun `an image already smaller than the target is never enlarged`() {
        assertEquals(1, avatarSampleSize(120, 90, AVATAR_TARGET_PX))
        assertEquals(1, avatarSampleSize(AVATAR_TARGET_PX, AVATAR_TARGET_PX, AVATAR_TARGET_PX))
    }

    @Test fun `a header that says nothing does not divide by zero`() {
        assertEquals(1, avatarSampleSize(0, 0, AVATAR_TARGET_PX))
        assertEquals(1, avatarSampleSize(4096, 4096, 0))
    }

    @Test fun `bytes that are not an image decode to nothing`() = runTest {
        assertNull(decodeAvatarPhoto(byteArrayOf(1, 2, 3), AVATAR_TARGET_PX))
        assertNull(decodeAvatarPhoto(byteArrayOf(), AVATAR_TARGET_PX))
    }

    @Test fun `a real image decodes`() = runTest {
        val decoded = decodeAvatarPhoto(PNG_PIXEL, AVATAR_TARGET_PX)

        assertEquals(1, decoded?.width)
        assertEquals(1, decoded?.height)
    }

    private companion object {
        /** PNG 1×1 válido — o menor arquivo que os dois decodificadores aceitam. */
        val PNG_PIXEL = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte(),
        )
    }
}
