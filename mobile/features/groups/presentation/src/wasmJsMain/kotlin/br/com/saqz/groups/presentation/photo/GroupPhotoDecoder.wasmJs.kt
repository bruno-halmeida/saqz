package br.com.saqz.groups.presentation.photo

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Ver AvatarPhotoDecoder.wasmJs.kt: decodificação inteira pelo Skia e redução pelo Canvas. */
internal actual suspend fun decodeGroupPhoto(bytes: ByteArray, targetPx: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    val source = runCatching { bytes.decodeToImageBitmap() }.getOrNull() ?: return null
    return source.scaledToFit(targetPx)
}

private fun ImageBitmap.scaledToFit(maxPx: Int): ImageBitmap {
    val longest = maxOf(width, height)
    if (longest <= maxPx) return this
    val scale = maxPx.toFloat() / longest
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val target = ImageBitmap(targetWidth, targetHeight)
    Canvas(target).drawImageRect(
        image = this,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(targetWidth, targetHeight),
        paint = Paint().apply { filterQuality = FilterQuality.Medium },
    )
    return target
}
