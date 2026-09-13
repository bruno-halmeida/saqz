package br.com.saqz.receivables.domain

sealed interface ReceiptExportResult {
    /** The native share flow accepted the content; this does not confirm delivery to a recipient. */
    data object Shared : ReceiptExportResult
    data object Failed : ReceiptExportResult
}

fun interface ReceiptExportPort {
    fun export(text: String, done: (ReceiptExportResult) -> Unit)
}

fun MemberPaymentDetail.receiptInstrument(): MemberPaymentInstrument? {
    if (order.status !in setOf("ISSUED", "PAID")) return null
    return instruments.lastOrNull { instrument ->
        instrument.orderId == order.id && instrument.accountId == order.accountId &&
            instrument.quote in order.quotes && instrument.status in setOf("PAID", "CONFIRMED", "SETTLED", "AVAILABLE") &&
            !instrument.paymentId.isNullOrBlank()
    }
}
