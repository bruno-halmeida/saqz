package br.com.saqz.composeapp.receivables

import br.com.saqz.access.domain.port.*
import br.com.saqz.receivables.domain.ReceiptExportResult
import kotlin.test.*

class ReceiptExportBindingTest {
    @Test fun nativeResultIsRequiredBeforeExportCanReportSuccess() {
        var callback: ResultCallback? = null; var text = ""; var result: ReceiptExportResult? = null
        val binding = ReceiptExportBinding(object : NativeSharePort {
            override fun share(value: String, done: ResultCallback) { text = value; callback = done }
        })
        binding.export("Pagamento confirmado: R$ 123,45") { result = it }
        assertNull(result); assertEquals("Pagamento confirmado: R$ 123,45", text)
        callback!!.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
        assertEquals(ReceiptExportResult.Failed, result)
        binding.export("Pagamento confirmado: R$ 123,45") { result = it }
        callback!!.complete(OperationResult.Success)
        assertEquals(ReceiptExportResult.Shared, result)
    }
}
