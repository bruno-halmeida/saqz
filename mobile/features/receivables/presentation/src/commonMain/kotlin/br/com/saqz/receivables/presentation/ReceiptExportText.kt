package br.com.saqz.receivables.presentation

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.formatting.formatLocalDatePtBrString
import br.com.saqz.receivables.domain.MemberPaymentDetail
import br.com.saqz.receivables.domain.receiptInstrument

internal fun receiptExportText(detail: MemberPaymentDetail): String? {
    val instrument = detail.receiptInstrument() ?: return null
    val method = if (instrument.quote.method.name == "PIX") "Pix" else "Cartão"
    return listOf(
        "Comprovante Saqz",
        "Referência: ${detail.order.id}",
        "Cobrança: ${instrument.paymentId}",
        "Meio: $method",
        "Vencimento: ${formatLocalDatePtBrString(detail.order.dueDate)}",
        "Valor base: ${formatBrl(instrument.quote.baseCents)}",
        "Tarifas: ${formatBrl(instrument.quote.feesCents)}",
        "Total: ${formatBrl(instrument.quote.totalCents)}",
        "Status: Pagamento confirmado",
    ).joinToString("\n")
}
