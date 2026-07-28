package br.com.saqz.access.adapter.output.media

import br.com.saqz.access.application.photo.UserPhotoConversion
import br.com.saqz.access.application.photo.UserPhotoRejection
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
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
    fun `o digest acompanha os bytes guardados`() {
        val photo = converted(converter.convert("image/png", stream(image(64, 64), "png")))
        val outra = converted(converter.convert("image/png", stream(image(64, 64, Color.BLUE), "png")))

        assertContentEquals(MessageDigest.getInstance("SHA-256").digest(photo.bytes), photo.sha256Digest)
        assertTrue(!photo.sha256Digest.contentEquals(outra.sha256Digest), "fotos diferentes precisam de digests diferentes")
    }

    @Test
    fun `orientacao EXIF 6 gira a foto de retrato antes de gravar`() {
        val photo = converted(converter.convert("image/jpeg", ByteArrayInputStream(landscapeJpeg(orientation = 6))))

        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.bytes)))
        assertEquals(40, decoded.width)
        assertEquals(100, decoded.height)
        // O giro de 90 graus horario leva a metade esquerda vermelha para o topo.
        assertTrue(isRed(decoded.getRGB(20, 15)), "esperava vermelho no topo")
        assertTrue(isBlue(decoded.getRGB(20, 85)), "esperava azul embaixo")
    }

    @Test
    fun `orientacao EXIF 8 gira para o outro lado`() {
        val photo = converted(converter.convert("image/jpeg", ByteArrayInputStream(landscapeJpeg(orientation = 8))))

        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.bytes)))
        assertEquals(40, decoded.width)
        assertEquals(100, decoded.height)
        assertTrue(isBlue(decoded.getRGB(20, 15)), "esperava azul no topo")
        assertTrue(isRed(decoded.getRGB(20, 85)), "esperava vermelho embaixo")
    }

    @Test
    fun `orientacao EXIF 3 vira a foto de cabeca para baixo sem trocar os lados`() {
        val photo = converted(converter.convert("image/jpeg", ByteArrayInputStream(landscapeJpeg(orientation = 3))))

        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.bytes)))
        assertEquals(100, decoded.width)
        assertEquals(40, decoded.height)
        assertTrue(isBlue(decoded.getRGB(15, 20)), "esperava azul a esquerda")
        assertTrue(isRed(decoded.getRGB(85, 20)), "esperava vermelho a direita")
    }

    @Test
    fun `orientacoes espelhadas 5 e 7 trocam os eixos e os lados`() {
        val transposta = converted(converter.convert("image/jpeg", ByteArrayInputStream(landscapeJpeg(orientation = 5))))
        val transversa = converted(converter.convert("image/jpeg", ByteArrayInputStream(landscapeJpeg(orientation = 7))))

        val cinco = assertNotNull(ImageIO.read(ByteArrayInputStream(transposta.bytes)))
        assertEquals(40, cinco.width)
        assertEquals(100, cinco.height)
        assertTrue(isRed(cinco.getRGB(20, 15)), "a transposta leva o vermelho para o topo")

        val sete = assertNotNull(ImageIO.read(ByteArrayInputStream(transversa.bytes)))
        assertEquals(40, sete.width)
        assertEquals(100, sete.height)
        assertTrue(isRed(sete.getRGB(20, 85)), "a transversa leva o vermelho para baixo")
    }

    @Test
    fun `orientacao EXIF 1 deixa a foto como veio`() {
        val photo = converted(converter.convert("image/jpeg", ByteArrayInputStream(landscapeJpeg(orientation = 1))))

        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.bytes)))
        assertEquals(100, decoded.width)
        assertEquals(40, decoded.height)
        assertTrue(isRed(decoded.getRGB(15, 20)), "esperava vermelho a esquerda")
    }

    @Test
    fun `JPEG sem EXIF continua passando reto`() {
        val photo = converted(converter.convert("image/jpeg", stream(halves(), "jpg")))

        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.bytes)))
        assertEquals(100, decoded.width)
        assertEquals(40, decoded.height)
        assertTrue(isRed(decoded.getRGB(15, 20)))
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

    private fun image(width: Int, height: Int, color: Color = Color.RED): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }

    /** Paisagem 100x40 com a metade esquerda vermelha e a direita azul. */
    private fun halves(): BufferedImage {
        val image = BufferedImage(100, 40, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, 50, 40)
        graphics.color = Color.BLUE
        graphics.fillRect(50, 0, 50, 40)
        graphics.dispose()
        return image
    }

    /**
     * O JPEG do celular guarda a foto deitada mais um APP1 dizendo como gira-la.
     * ImageIO nao escreve EXIF, entao o segmento entra a mao logo depois do SOI.
     */
    private fun landscapeJpeg(orientation: Int): ByteArray {
        val base = ByteArrayOutputStream().use { output ->
            assertTrue(ImageIO.write(halves(), "jpg", output))
            output.toByteArray()
        }
        val exif = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22, // APP1 de 34 bytes
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00,
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, // TIFF big endian
            0x00, 0x00, 0x00, 0x08, // IFD0 no deslocamento 8
            0x00, 0x01, // uma entrada
            0x01, 0x12, // tag de orientacao
            0x00, 0x03, // tipo SHORT
            0x00, 0x00, 0x00, 0x01, // contagem 1
            0x00, orientation.toByte(), 0x00, 0x00, // valor no proprio campo
            0x00, 0x00, 0x00, 0x00, // sem proxima IFD
        )
        return base.copyOfRange(0, 2) + exif + base.copyOfRange(2, base.size)
    }

    private fun isRed(rgb: Int): Boolean = red(rgb) > 128 && blue(rgb) < 96

    private fun isBlue(rgb: Int): Boolean = blue(rgb) > 128 && red(rgb) < 96

    private fun red(rgb: Int) = (rgb shr 16) and 0xFF

    private fun blue(rgb: Int) = rgb and 0xFF

    private fun stream(image: BufferedImage, format: String): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        assertTrue(ImageIO.write(image, format, output))
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xFF == expected[it] }
}
