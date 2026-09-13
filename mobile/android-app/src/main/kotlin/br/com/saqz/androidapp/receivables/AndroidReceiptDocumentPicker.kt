package br.com.saqz.androidapp.receivables

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import br.com.saqz.receivables.domain.ReceiptDocumentFile
import br.com.saqz.receivables.domain.port.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidReceiptDocumentPicker(private val context: Context, private val scope: CoroutineScope) : ReceiptDocumentPicker {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var callback: ReceiptFileCallback? = null
    private var inFlight = false
    fun attach(activity: ComponentActivity) {
        launcher?.unregister()
        launcher = activity.activityResultRegistry.register("saqz-financial-document", activity, OpenDocument(), ::selected)
    }
    override fun choose(done: ReceiptFileCallback): ReceiptFileCancellation {
        if (inFlight) { done.onFileSelected(ReceiptFileSelection.Invalid); return ReceiptFileCancellation {} }
        inFlight = true; callback = done
        runCatching { checkNotNull(launcher).launch(ReceiptDocumentFile.DOCUMENT_TYPES.toTypedArray()) }
            .onFailure { finish(ReceiptFileSelection.Invalid) }
        return ReceiptFileCancellation { if (callback === done) callback = null }
    }
    private fun selected(uri: Uri?) {
        if (!inFlight) return
        if (uri == null) { finish(ReceiptFileSelection.Cancelled); return }
        scope.launch {
            val result = withContext(Dispatchers.IO) { read(uri) }
            finish(result)
        }
    }
    private fun read(uri: Uri): ReceiptFileSelection = runCatching {
        val type = context.contentResolver.getType(uri).orEmpty()
        require(type in ReceiptDocumentFile.DOCUMENT_TYPES)
        val file = checkNotNull(context.contentResolver.openInputStream(uri)).use { readReceiptFile(it, type) }
        ReceiptFileSelection.Selected(file)
    }.getOrElse { ReceiptFileSelection.Invalid }
    private fun finish(result: ReceiptFileSelection) {
        val done = callback; callback = null; inFlight = false; done?.onFileSelected(result)
    }
}

internal fun readReceiptFile(input: java.io.InputStream, type: String): ReceiptDocumentFile {
    require(type in ReceiptDocumentFile.DOCUMENT_TYPES)
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer, 0, minOf(buffer.size, ReceiptDocumentFile.MAX_DOCUMENT_BYTES + 1 - output.size()))
        if (read < 0) break
        output.write(buffer, 0, read)
        require(output.size() <= ReceiptDocumentFile.MAX_DOCUMENT_BYTES)
    }
    return ReceiptDocumentFile(output.toByteArray(), type).also { require(it.valid()) }
}
