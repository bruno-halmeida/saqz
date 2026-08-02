package br.com.saqz.profile.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

internal fun screenshotImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(ScreenshotPhotoFetcher.Factory())
    }
    .build()

private class ScreenshotPhotoFetcher(
    private val image: BitmapImage,
) : Fetcher {
    override suspend fun fetch(): FetchResult = ImageFetchResult(
        image = image,
        isSampled = false,
        dataSource = DataSource.MEMORY,
    )

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.path.isNullOrBlank()) return null
            return ScreenshotPhotoFetcher(portraitFixture().asImage())
        }
    }
}

private fun portraitFixture(): Bitmap {
    val size = 256f
    val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        0f,
        0f,
        size,
        size,
        Color.rgb(248, 210, 176),
        Color.rgb(67, 117, 129),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, size, size, paint)
    paint.shader = null

    paint.color = Color.rgb(232, 181, 137)
    canvas.drawCircle(204f, 48f, 27f, paint)

    paint.color = Color.rgb(29, 53, 66)
    canvas.drawOval(RectF(48f, 154f, 208f, 304f), paint)

    paint.color = Color.rgb(224, 161, 119)
    canvas.drawRect(112f, 137f, 145f, 190f, paint)
    canvas.drawOval(RectF(68f, 43f, 188f, 176f), paint)

    paint.color = Color.rgb(45, 31, 35)
    canvas.drawArc(RectF(65f, 35f, 191f, 139f), 180f, 180f, true, paint)
    canvas.drawOval(RectF(62f, 76f, 83f, 138f), paint)
    canvas.drawOval(RectF(173f, 76f, 194f, 138f), paint)

    paint.color = Color.rgb(52, 37, 38)
    canvas.drawCircle(105f, 105f, 6f, paint)
    canvas.drawCircle(151f, 105f, 6f, paint)

    paint.color = Color.rgb(190, 113, 93)
    canvas.drawOval(RectF(119f, 125f, 138f, 140f), paint)

    paint.color = Color.rgb(38, 93, 108)
    canvas.drawOval(RectF(38f, 184f, 218f, 350f), paint)
    paint.color = Color.rgb(250, 220, 174)
    canvas.drawCircle(128f, 201f, 18f, paint)

    return bitmap
}
