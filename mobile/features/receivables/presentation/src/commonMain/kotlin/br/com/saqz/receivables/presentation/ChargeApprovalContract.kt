package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable data class ChargeApprovalRoute(val groupId: String, val chargeId: String) : NavKey
@Serializable data class ApprovalAttempt(val actor: String, val groupId: String, val chargeId: String, val accountId: String,
    val requestId: String, val fingerprint: String, val orderId: String? = null) {
    val target get() = ChargeApprovalTarget(groupId, chargeId, accountId)
}
data class ChargeApprovalState(val loading: Boolean = true, val accounts: List<ReceiptAccount> = emptyList(),
    val accountId: String? = null, val review: ChargeApprovalReview? = null, val terms: List<ReceiptTerms> = emptyList(),
    val accepted: Boolean = false, val detail: MemberPaymentDetail? = null, val attempt: ApprovalAttempt? = null,
    val confirmCancel: Boolean = false, val error: ReceiptError? = null) {
    val canReview get() = !loading && attempt == null && detail == null && review != null && error == null
    val canAccept get() = canReview && review!!.quotes.isNotEmpty() && review.quotes.all { quote ->
        terms.any { it.version == quote.termsVersion && it.content.isNotBlank() }
    }
    val canApprove get() = canAccept && accepted
    val canCancel get() = !loading && attempt == null && error == null && detail?.order?.status == "ISSUED"
}
sealed interface ChargeApprovalIntent {
    data object Refresh : ChargeApprovalIntent
    data object ChooseAccount : ChargeApprovalIntent
    data class Account(val id: String) : ChargeApprovalIntent
    data class Accept(val value: Boolean) : ChargeApprovalIntent
    data object Approve : ChargeApprovalIntent
    data object Replay : ChargeApprovalIntent
    data object RequestCancel : ChargeApprovalIntent
    data object DismissCancel : ChargeApprovalIntent
    data object ConfirmCancel : ChargeApprovalIntent
}
data class ChargeApprovalEffect(val generation: Int)
