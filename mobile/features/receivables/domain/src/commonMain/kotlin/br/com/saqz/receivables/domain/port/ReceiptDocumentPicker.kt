package br.com.saqz.receivables.domain.port

import br.com.saqz.receivables.domain.ReceiptDocumentFile

sealed interface ReceiptFileSelection {
    data class Selected(val file: ReceiptDocumentFile) : ReceiptFileSelection
    data object Cancelled : ReceiptFileSelection
    data object Invalid : ReceiptFileSelection
}
fun interface ReceiptFileCallback { fun onFileSelected(selection: ReceiptFileSelection) }
fun interface ReceiptFileCancellation { fun cancel() }
interface ReceiptDocumentPicker { fun choose(done: ReceiptFileCallback): ReceiptFileCancellation }
