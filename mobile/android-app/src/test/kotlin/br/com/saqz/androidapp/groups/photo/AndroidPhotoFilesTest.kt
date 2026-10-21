package br.com.saqz.androidapp.groups.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/** DEB-125: a original vem como o celular salva; a escolha reduz e confere o resultado. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPhotoFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = AndroidPhotoFiles(context)

    @Test fun jpegOf50MegapixelsIsReducedWithoutFullDecode() {
        val reduced = assertAccepted(jpeg(8160, 6120, Bitmap.Config.RGB_565))
        val metadata = files.metadata(reduced)!!
        assertEquals(listOf(4080, 3060), listOf(metadata.width, metadata.height))
    }

    @Test fun jpegOf8MegabytesIsAccepted() {
        val original = jpeg(3200, 3200, noise = true)
        assertTrue(original.length() > 8L * 1024 * 1024)
        assertAccepted(original)
    }

    @Test fun originalAbove50MegabytesIsRejected() {
        val original = File(context.cacheDir, "huge.jpg")
        RandomAccessFile(original, "rw").use { it.setLength(AndroidPhotoFiles.MAX_SOURCE_BYTES + 1) }
        assertNull(files.copySelected(Uri.fromFile(original)))
        assertNull(files.reduce(original))
    }

    @Test fun nonImageIsRejected() {
        val original = File(context.cacheDir, "text.jpg").apply { writeText("nada") }
        assertNull(files.copySelected(Uri.fromFile(original)))
    }

    @Test fun exifRotationOf90DegreesBecomesRotatedPixels() {
        val original = jpeg(400, 200)
        ExifInterface(original.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val reduced = files.reduce(original)!!
        val bitmap = BitmapFactory.decodeFile(reduced.path)
        assertEquals(listOf(200, 400), listOf(bitmap.width, bitmap.height))
        assertTrue(Color.red(bitmap.getPixel(100, 50)) > 200)
        assertTrue(Color.red(bitmap.getPixel(100, 350)) < 60)
    }

    @Test fun sampleSizeKeepsDecodedSideWithinTarget() {
        assertEquals(1, sampleSize(4096, 3072, 4096))
        assertEquals(2, sampleSize(8160, 6120, 4096))
        assertEquals(4, sampleSize(16320, 12240, 4096))
    }

    private fun assertAccepted(original: File): File {
        val reduced = requireNotNull(files.reduce(original))
        val metadata = files.metadata(reduced)!!
        assertTrue(maxOf(metadata.width, metadata.height) <= AndroidPhotoFiles.MAX_DIMENSION)
        for (dimension in listOf(AndroidPhotoFiles.MAX_DIMENSION, AndroidGroupPhotoAdapters.PROFILE_DIMENSION)) {
            val result = runBlocking {
                AndroidPhotoEncoder(files, dimension).encode(files.handle(reduced).value, GroupPhotoCrop())
            } as GroupPhotoEncodingResult.Encoded
            assertTrue(result.value.contentLength <= EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES)
            val bytes = result.value.source.read()
            val output = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(output.width, output.height)
            assertTrue(output.width <= dimension)
        }
        return reduced
    }

    /** Gerada no teste: metade esquerda vermelha, direita azul (ou ruído, para pesar). */
    private fun jpeg(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888, noise: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(width, height, config)
        if (noise) {
            val random = Random(7)
            val row = IntArray(width)
            for (y in 0 until height) {
                for (x in 0 until width) row[x] = random.nextInt() or 0xFF000000.toInt()
                bitmap.setPixels(row, 0, width, 0, y, width, 1)
            }
        } else {
            Canvas(bitmap).apply {
                drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
                drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
            }
        }
        val file = File.createTempFile("original", ".jpg", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, if (noise) 100 else 90, it) }
        bitmap.recycle()
        return file
    }
}
