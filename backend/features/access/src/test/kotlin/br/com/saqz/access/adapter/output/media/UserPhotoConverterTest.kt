package br.com.saqz.access.adapter.output.media

import br.com.saqz.access.application.photo.UserPhotoConversion
import br.com.saqz.access.application.photo.UserPhotoRejection
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class UserPhotoConverterTest {
    private val converter = UserPhotoConverter()

    @Test
    fun `recomprime o envio grande para JPEG dentro do lado maximo`() {
        val photo = converted(converter.convert("image/png", stream(image(1000, 600), "png")))

        assertEquals(512, photo.width)
        assertEquals(307, photo.height)
        assertTrue(photo.bytes.startsWith(0xFF, 0xD8, 0xFF), "a foto guardada precisa ser JPEG")
        assertEquals(photo.bytes.size.toLong(), photo.byteSize)
    }

    @Test
    fun `foto pequena mantem as dimensoes de origem`() {
        val photo = converted(converter.convert("image/jpeg", stream(image(100, 50), "jpg")))

        assertEquals(100, photo.width)
        assertEquals(50, photo.height)
    }

    @Test
    fun `PNG com transparencia vira JPEG opaco decodificavel`() {
        val transparent = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)

        val photo = converted(converter.convert("image/png", stream(transparent, "png")))

        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.bytes)))
        assertEquals(40, decoded.width)
        assertEquals(Color.WHITE.rgb, decoded.getRGB(0, 0))
    }

    @Test
    fun `tipo declarado fora da lista aceita e recusado`() {
        assertEquals(
            UserPhotoRejection.UNSUPPORTED_TYPE,
            rejected(converter.convert("image/gif", stream(image(10, 10), "png"))),
        )
    }

    @Test
    fun `tipo declarado com charset continua aceito`() {
        assertNotNull(converted(converter.convert("image/png; charset=binary", stream(image(10, 10), "png"))))
    }

    @Test
    fun `envio vazio e recusado`() {
        assertEquals(
            UserPhotoRejection.EMPTY,
            rejected(converter.convert("image/png", ByteArrayInputStream(ByteArray(0)))),
        )
    }

    @Test
    fun `envio acima do limite de bytes e recusado antes de decodificar`() {
        val small = UserPhotoConverter(maximumBytes = 128)

        assertEquals(
            UserPhotoRejection.TOO_LARGE,
            rejected(small.convert("image/png", stream(image(200, 200), "png"))),
        )
    }

    @Test
    fun `bytes corrompidos sao recusados como imagem invalida`() {
        val corrupt = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01)

        assertEquals(
            UserPhotoRejection.INVALID_IMAGE,
            rejected(converter.convert("image/png", ByteArrayInputStream(corrupt))),
        )
    }

    @Test
    fun `origem acima do limite de dimensao e recusada`() {
        val narrow = UserPhotoConverter(maximumSourceDimension = 64)

        assertEquals(
            UserPhotoRejection.DIMENSIONS_TOO_LARGE,
            rejected(narrow.convert("image/png", stream(image(100, 100), "png"))),
        )
    }

    private fun converted(conversion: UserPhotoConversion) =
        assertNotNull(conversion as? UserPhotoConversion.Converted, "esperava conversao: $conversion").photo

    private fun rejected(conversion: UserPhotoConversion) =
        assertNotNull(conversion as? UserPhotoConversion.Rejected, "esperava recusa: $conversion").reason

    private fun image(width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }

    private fun stream(image: BufferedImage, format: String): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        assertTrue(ImageIO.write(image, format, output))
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xFF == expected[it] }
}
