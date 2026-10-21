package br.com.saqz.androidapp.groups.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

internal class AndroidPhotoEncoder(
    private val files: AndroidPhotoFiles,
    private val maxDimension: Int = AndroidPhotoFiles.MAX_DIMENSION,
) : GroupPhotoEncoderPort {
    override suspend fun encode(
        source: String,
        crop: GroupPhotoCrop,
    ): GroupPhotoEncodingResult = withContext(Dispatchers.IO) {
        val file = files.file(GroupPhotoSourceHandle(source)) ?: return@withContext GroupPhotoEncodingResult.Failed
        val metadata = files.metadata(file) ?: return@withContext GroupPhotoEncodingResult.Failed
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(metadata.width, metadata.height, maxDimension)
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return@withContext GroupPhotoEncodingResult.Failed
        try {
            val region = AndroidSquareCrop.calculate(bitmap.width, bitmap.height, crop)
            val side = min(region.side, maxDimension)
            val scale = Matrix().apply { setScale(side.toFloat() / region.side, side.toFloat() / region.side) }
            val square = Bitmap.createBitmap(bitmap, region.left, region.top, region.side, region.side, scale, true)
            try {
                val bytes = encodeJpegBounded(square) ?: return@withContext GroupPhotoEncodingResult.Failed
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

    override fun cancel(source: String) = Unit
}
