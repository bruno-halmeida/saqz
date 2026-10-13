package br.com.saqz.webapp.receivables

import br.com.saqz.receivables.domain.port.ReceiptDocumentPicker
import br.com.saqz.receivables.domain.port.ReceiptFileCallback
import br.com.saqz.receivables.domain.port.ReceiptFileCancellation
import br.com.saqz.receivables.domain.port.ReceiptFileSelection
import kotlinx.coroutines.CoroutineScope

// STUB: substituir por <input type="file"> (PDF/imagem) espelhando AndroidReceiptDocumentPicker.kt.
internal class WebReceiptDocumentPicker(private val scope: CoroutineScope) : ReceiptDocumentPicker {
    override fun choose(done: ReceiptFileCallback): ReceiptFileCancellation {
        done.onFileSelected(ReceiptFileSelection.Cancelled)
        return ReceiptFileCancellation { }
    }
}
