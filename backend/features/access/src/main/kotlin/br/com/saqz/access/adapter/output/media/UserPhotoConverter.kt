package br.com.saqz.access.adapter.output.media

import br.com.saqz.access.application.photo.UserPhotoConversion
import br.com.saqz.access.application.photo.UserPhotoConversionPort
import br.com.saqz.access.application.photo.UserPhotoImage
import br.com.saqz.access.application.photo.UserPhotoRejection
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Aceita os mesmos tipos da foto de grupo, mas nao guarda o que recebeu: o avatar
 * sai daqui sempre como JPEG opaco de no maximo [targetDimension] px no maior lado.
 * Por isso nao existe checagem de tipo declarado contra tipo real nem de animacao —
 * recodificar o pixel decodificado neutraliza o que viesse embutido no arquivo.
 */
class UserPhotoConverter(
    private val maximumBytes: Int = 5 * 1024 * 1024,
    private val maximumSourceDimension: Int = 4096,
    private val targetDimension: Int = 512,
    private val quality: Float = 0.85f,
) : UserPhotoConversionPort {
    override fun convert(declaredContentType: String, input: InputStream): UserPhotoConversion {
        val bytes = readBounded(input) ?: return rejected(UserPhotoRejection.TOO_LARGE)
        if (bytes.isEmpty()) return rejected(UserPhotoRejection.EMPTY)
        val declaredType = declaredContentType.substringBefore(';').trim()
        if (ACCEPTED_TYPES.none { it.equals(declaredType, ignoreCase = true) }) {
            return rejected(UserPhotoRejection.UNSUPPORTED_TYPE)
        }
        return runCatching { shrinkToJpeg(bytes) }.getOrElse { rejected(UserPhotoRejection.INVALID_IMAGE) }
    }

    private fun shrinkToJpeg(bytes: ByteArray): UserPhotoConversion =
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { imageInput ->
            val readers = ImageIO.getImageReaders(imageInput)
            if (!readers.hasNext()) return rejected(UserPhotoRejection.INVALID_IMAGE)
            val reader = readers.next()
            try {
                reader.input = imageInput
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                // Medir antes de decodificar: 5 MB comprimidos cabem uma imagem de
                // dezenas de milhares de pixels que so estoura a heap depois de aberta.
                if (width !in 1..maximumSourceDimension || height !in 1..maximumSourceDimension) {
                    return rejected(UserPhotoRejection.DIMENSIONS_TOO_LARGE)
                }
                val decoded = reader.read(0) ?: return rejected(UserPhotoRejection.INVALID_IMAGE)
                encodeJpeg(shrink(decoded))
            } finally {
                reader.dispose()
            }
        }

    private fun shrink(source: BufferedImage): BufferedImage {
        val scale = minOf(1.0, targetDimension.toDouble() / maxOf(source.width, source.height))
        val width = maxOf(1, Math.round(source.width * scale).toInt())
        val height = maxOf(1, Math.round(source.height * scale).toInt())
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = canvas.createGraphics()
        try {
            // JPEG nao tem canal alfa: sem o fundo branco o PNG transparente sai preto.
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return canvas
    }

    private fun encodeJpeg(image: BufferedImage): UserPhotoConversion {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val parameters = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                }
                writer.write(null, IIOImage(image, null, null), parameters)
            }
        } finally {
            writer.dispose()
        }
        return UserPhotoConversion.Converted(UserPhotoImage(output.toByteArray(), image.width, image.height))
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

    private fun rejected(reason: UserPhotoRejection) = UserPhotoConversion.Rejected(reason)

    private companion object {
        val ACCEPTED_TYPES = listOf("image/jpeg", "image/png", "image/webp")
    }
}
