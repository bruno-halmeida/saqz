package br.com.saqz.access.adapter.output.media

import br.com.saqz.access.application.photo.UserPhotoConversion
import br.com.saqz.access.application.photo.UserPhotoConversionPort
import br.com.saqz.access.application.photo.UserPhotoImage
import br.com.saqz.access.application.photo.UserPhotoRejection
import java.awt.Color
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
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
                encodeJpeg(shrink(orient(decoded, jpegExifOrientation(bytes))))
            } finally {
                reader.dispose()
            }
        }

    /**
     * A recompressao descarta o EXIF, entao a rotacao tem que virar pixel aqui:
     * sem isto metade das fotos de celular fica deitada para sempre.
     */
    private fun orient(source: BufferedImage, orientation: Int): BufferedImage {
        if (orientation == UPRIGHT_ORIENTATION) return source
        val swapsAxes = orientation >= 5
        val width = if (swapsAxes) source.height else source.width
        val height = if (swapsAxes) source.width else source.height
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = target.createGraphics()
        try {
            graphics.transform = orientationTransform(orientation, source.width, source.height)
            graphics.drawImage(source, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return target
    }

    private fun orientationTransform(orientation: Int, width: Int, height: Int) = AffineTransform().apply {
        when (orientation) {
            2 -> { translate(width.toDouble(), 0.0); scale(-1.0, 1.0) }
            3 -> { translate(width.toDouble(), height.toDouble()); rotate(Math.PI) }
            4 -> { translate(0.0, height.toDouble()); scale(1.0, -1.0) }
            5 -> { rotate(Math.PI / 2); scale(1.0, -1.0) }
            6 -> { translate(height.toDouble(), 0.0); rotate(Math.PI / 2) }
            7 -> { translate(height.toDouble(), width.toDouble()); rotate(3 * Math.PI / 2); scale(1.0, -1.0) }
            8 -> { translate(0.0, width.toDouble()); rotate(-Math.PI / 2) }
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
        val bytes = output.toByteArray()
        return UserPhotoConversion.Converted(
            UserPhotoImage(
                bytes = bytes,
                width = image.width,
                height = image.height,
                sha256Digest = MessageDigest.getInstance("SHA-256").digest(bytes),
            ),
        )
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
