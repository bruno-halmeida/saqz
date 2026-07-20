package br.com.saqz.androidapp.groups.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoByteSource
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoMediaType
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

internal data class AndroidPixelCrop(val left: Int, val top: Int, val side: Int)

internal object AndroidSquareCrop {
    fun calculate(width: Int, height: Int, crop: GroupPhotoCrop): AndroidPixelCrop {
        require(width > 0 && height > 0)
        val side = (min(width, height) / crop.zoom).roundToInt().coerceIn(1, min(width, height))
        val left = (crop.centerX * width - side / 2f).roundToInt().coerceIn(0, width - side)
        val top = (crop.centerY * height - side / 2f).roundToInt().coerceIn(0, height - side)
        return AndroidPixelCrop(left, top, side)
    }
}

internal class AndroidPhotoEncoder(private val files: AndroidPhotoFiles) : GroupPhotoEncoderPort {
    override suspend fun encode(
        source: GroupPhotoSourceHandle,
        crop: GroupPhotoCrop,
    ): GroupPhotoEncodingResult = withContext(Dispatchers.IO) {
        val file = files.file(source) ?: return@withContext GroupPhotoEncodingResult.Failed
        files.metadata(file) ?: return@withContext GroupPhotoEncodingResult.Failed
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return@withContext GroupPhotoEncodingResult.Failed
        try {
            val region = AndroidSquareCrop.calculate(bitmap.width, bitmap.height, crop)
            val square = Bitmap.createBitmap(bitmap, region.left, region.top, region.side, region.side)
            try {
                val bytes = encodeBounded(square) ?: return@withContext GroupPhotoEncodingResult.Failed
                GroupPhotoEncodingResult.Encoded(
                    EncodedGroupPhoto(
                        GroupPhotoMediaType.JPEG,
                        bytes.size.toLong(),
                        GroupPhotoByteSource { bytes },
                    ),
                )
            } finally {
                if (square !== bitmap) square.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    override fun cancel(source: GroupPhotoSourceHandle) = Unit

    private fun encodeBounded(bitmap: Bitmap): ByteArray? {
        for (quality in listOf(92, 85, 75, 65)) {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue
            if (output.size().toLong() <= EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) return output.toByteArray()
        }
        return null
    }
}
