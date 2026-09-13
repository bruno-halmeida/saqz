package br.com.saqz.androidapp

import android.app.Activity
import android.app.Application
import androidx.activity.ComponentActivity
import br.com.saqz.androidapp.receivables.AndroidReceiptDocumentPicker
import br.com.saqz.androidapp.receivables.readReceiptFile
import br.com.saqz.receivables.domain.ReceiptDocumentFile
import br.com.saqz.receivables.domain.port.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ReceiptDocumentPickerTest {
    @Test fun readsExactBytesAndRejectsEmptyUnsupportedAndOverLimit() {
        val limit = ReceiptDocumentFile.MAX_DOCUMENT_BYTES
        for (mime in ReceiptDocumentFile.DOCUMENT_TYPES) {
            val bytes = ByteArray(limit) { 42 }
            val file = readReceiptFile(bytes.inputStream(), mime)
            assertEquals(mime, file.contentType); assertArrayEquals(bytes, file.bytes)
        }
        for ((bytes, type) in listOf(byteArrayOf() to "image/png", byteArrayOf(1) to "text/plain",
            ByteArray(limit + 1) to "application/pdf")) {
            assertThrows(IllegalArgumentException::class.java) { readReceiptFile(bytes.inputStream(), type) }
        }
    }
    @Test fun explicitPickerCancellationAndCancelledCallbacksNeverDeliverFile() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val picker = AndroidReceiptDocumentPicker(activity, CoroutineScope(Dispatchers.Main))
        picker.attach(activity); controller.start().resume()
        val results = mutableListOf<ReceiptFileSelection>()
        val cancellation = picker.choose { results += it }
        val launched = shadowOf(activity).nextStartedActivityForResult
        assertEquals("android.intent.action.OPEN_DOCUMENT", launched.intent.action)
        assertArrayEquals(ReceiptDocumentFile.DOCUMENT_TYPES.toTypedArray(),
            launched.intent.getStringArrayExtra("android.intent.extra.MIME_TYPES"))
        cancellation.cancel()
        picker.choose { results += it }; assertEquals(listOf(ReceiptFileSelection.Invalid), results)
        activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_CANCELED, null)
        assertEquals(listOf(ReceiptFileSelection.Invalid), results)
        picker.choose { results += it }
        val again = shadowOf(activity).nextStartedActivityForResult
        activity.activityResultRegistry.dispatchResult(again.requestCode, Activity.RESULT_CANCELED, null)
        assertEquals(listOf(ReceiptFileSelection.Invalid, ReceiptFileSelection.Cancelled), results)
        controller.pause().stop().destroy()
    }
}
