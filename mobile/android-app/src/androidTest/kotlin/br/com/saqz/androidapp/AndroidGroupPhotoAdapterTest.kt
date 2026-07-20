package br.com.saqz.androidapp

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.saqz.androidapp.groups.photo.AndroidPhotoEncoder
import br.com.saqz.androidapp.groups.photo.AndroidPhotoFiles
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AndroidGroupPhotoAdapterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = AndroidPhotoFiles(context)

    @After fun cleanup() = files.purgeOrphans()

    @Test fun manifestRequestsNoBroadPhotoOrStoragePermission() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, 0x00001000).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.READ_MEDIA_IMAGES"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
    }

    @Test fun sourceLivesInPrivateCacheAndExplicitCleanupDeletesIt() {
        val file = files.createSource()
        assertTrue(file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
        val handle = files.handle(file)
        files.remove(handle)
        assertFalse(file.exists())
    }

    @Test fun invalidBytesFailBoundsInspectionBeforeDecode() {
        val file = files.createSource().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(null, files.metadata(file))
    }

    @Test fun encoderProducesBoundedStaticSquareJpeg() = runBlocking {
        val file = files.createSource()
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val result = AndroidPhotoEncoder(files).encode(files.handle(file), GroupPhotoCrop())

        val encoded = (result as GroupPhotoEncodingResult.Encoded).value
        assertEquals("image/jpeg", encoded.mediaType.value)
        assertTrue(encoded.contentLength in 1..(5L * 1024L * 1024L))
        val boundsFile = files.createSource().apply { writeBytes(encoded.source.read()) }
        val metadata = files.metadata(boundsFile)
        assertNotNull(metadata)
        assertEquals(metadata!!.width, metadata.height)
    }
}
