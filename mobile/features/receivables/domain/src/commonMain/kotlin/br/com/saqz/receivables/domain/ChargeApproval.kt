package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

data class ChargeApprovalTarget(val groupId: String, val chargeId: String, val accountId: String)
data class ChargeApprovalReview(val target: ChargeApprovalTarget, val payerId: String, val dueDate: String,
    val billingMonth: String?, val quotes: List<MemberPaymentQuote>, val fingerprint: String)
data class ChargeApprovalCommand(val requestId: String, val fingerprint: String, val accepted: Boolean)
interface ChargeApprovalGateway {
    suspend fun lookup(target: ChargeApprovalTarget): SaqzResult<MemberPaymentDetail?, ReceiptError>
    suspend fun preview(target: ChargeApprovalTarget, requestId: String): SaqzResult<ChargeApprovalReview, ReceiptError>
    suspend fun approve(target: ChargeApprovalTarget, command: ChargeApprovalCommand): SaqzResult<MemberPaymentOrder, ReceiptError>
    suspend fun cancel(target: ChargeApprovalTarget, orderId: String, requestId: String): SaqzResult<MemberPaymentDetail, ReceiptError>
}
