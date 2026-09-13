package br.com.saqz.composeapp.receivables

import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.receivables.domain.ReceiptExportPort
import br.com.saqz.receivables.domain.ReceiptExportResult

internal class ReceiptExportBinding(private val share: NativeSharePort) : ReceiptExportPort {
    override fun export(text: String, done: (ReceiptExportResult) -> Unit) {
        if (text.isBlank()) { done(ReceiptExportResult.Failed); return }
        share.share(text, object : ResultCallback {
            override fun complete(result: OperationResult) {
                done(if (result == OperationResult.Success) ReceiptExportResult.Shared else ReceiptExportResult.Failed)
            }
        })
    }
}
