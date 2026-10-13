package br.com.saqz.webapp.receivables

import br.com.saqz.receivables.domain.ReceiptDocumentFile
import br.com.saqz.receivables.domain.port.ReceiptDocumentPicker
import br.com.saqz.receivables.domain.port.ReceiptFileCallback
import br.com.saqz.receivables.domain.port.ReceiptFileCancellation
import br.com.saqz.receivables.domain.port.ReceiptFileSelection
import br.com.saqz.webapp.interop.BrowserFilePick
import br.com.saqz.webapp.interop.BrowserFilePicker
import kotlinx.coroutines.CoroutineScope

/**
 * Comprovante/documento financeiro por `<input type="file">`, com os mesmos tipos (PDF, JPEG, PNG) e o mesmo
 * teto (5 MiB) do `AndroidReceiptDocumentPicker`. O arquivo é validado pelo próprio `ReceiptDocumentFile`.
 *
 * Diferença do Android: uma segunda escolha com a primeira ainda pendente não vira `Invalid`; ela substitui a
 * primeira, que termina `Cancelled`. No navegador um seletor aberto é modal, então "pendente" só sobra quando o
 * navegador não avisou o cancelamento, e travar a pessoa fora do envio seria pior.
 *
 * `scope` não é usado: o `click()` do input precisa ser síncrono com o toque (ativação do usuário).
 */
internal class WebReceiptDocumentPicker(@Suppress("UnusedPrivateProperty") scope: CoroutineScope) : ReceiptDocumentPicker {
    private var pending: ReceiptFileCallback? = null

    override fun choose(done: ReceiptFileCallback): ReceiptFileCancellation {
        pending?.let { previous ->
            pending = null
            previous.onFileSelected(ReceiptFileSelection.Cancelled)
        }
        pending = done
        BrowserFilePicker.pick(ReceiptDocumentFile.DOCUMENT_TYPES, ReceiptDocumentFile.MAX_DOCUMENT_BYTES) { result ->
            // Cancelado ou substituído: o navegador ainda pode responder, e essa resposta não vale mais.
            if (pending !== done) return@pick
            pending = null
            done.onFileSelected(result.toSelection())
        }
        return ReceiptFileCancellation { if (pending === done) pending = null }
    }

    private fun BrowserFilePick.toSelection(): ReceiptFileSelection = when (this) {
        BrowserFilePick.Cancelled -> ReceiptFileSelection.Cancelled
        BrowserFilePick.Invalid -> ReceiptFileSelection.Invalid
        is BrowserFilePick.Picked -> ReceiptDocumentFile(bytes, mimeType).takeIf(ReceiptDocumentFile::valid)
            ?.let(ReceiptFileSelection::Selected) ?: ReceiptFileSelection.Invalid
    }
}
