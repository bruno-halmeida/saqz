package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable data object MemberPaymentHistoryRoute : NavKey
@Serializable data class MemberPaymentRoute(val orderId: String) : NavKey

data class MemberPaymentRuntime(
    val session: ReceivablesSessionContext,
    val identity: ReceivablesRecoveryIdentity,
    val clock: Clock,
)

data class MemberPaymentHistoryState(val loading: Boolean = true, val orders: List<MemberPaymentOrder> = emptyList(),
    val nextCursor: String? = null, val error: ReceiptError? = null)
sealed interface MemberPaymentHistoryIntent {
    data object Refresh : MemberPaymentHistoryIntent
    data object More : MemberPaymentHistoryIntent
    data class Open(val id: String) : MemberPaymentHistoryIntent
}
data class MemberPaymentHistoryEffect(val orderId: String, val generation: Int)

data class MemberPaymentState(val loading: Boolean = true, val detail: MemberPaymentDetail? = null,
    val method: ReceiptMethod? = null, val terms: ReceiptTerms? = null, val accepted: Boolean = false,
    val name: String = "", val document: String = "", val pending: Boolean = false, val canReplay: Boolean = false,
    val pixExpired: Boolean = false, val renewalDueDate: String = "", val renewalPending: Boolean = false,
    val error: ReceiptError? = null, val copied: Boolean = false, val openFailed: Boolean = false,
    val receiptSharing: Boolean = false, val receiptShared: Boolean = false, val receiptShareFailed: Boolean = false) {
    val quote get() = detail?.order?.quotes?.singleOrNull { it.method == method }
    val instrument get() = detail?.instruments?.lastOrNull()
    val canReview get() = !loading && !pending && detail?.order?.status == "ISSUED" &&
        detail.instruments.all { it.status == "CANCELLED" }
    val canAccept get() = canReview && quote != null && terms?.version == quote?.termsVersion && !terms?.content.isNullOrBlank()
    val canPay get() = canAccept && accepted && name.trim().length in 2..120 && name.none(Char::isISOControl) &&
        document.matches(Regex("[0-9]{11}|[0-9]{14}"))
    val canUseInstrument get() = !loading && !pending && error == null &&
        detail?.order?.status == "ISSUED" && instrument?.status == "ACTIVE" && !pixExpired
    val canRenew get() = !loading && !pending && !renewalPending && error != ReceiptError.SIGNED_OUT &&
        detail?.let { value -> instrument?.let { value.order.canRenew(it, pixExpired) } } == true &&
        renewalDueDate.isPaymentDate()
    val canExportReceipt get() = !loading && !pending && !renewalPending && !receiptSharing && error == null &&
        detail?.receiptInstrument() != null
}
sealed interface MemberPaymentIntent {
    data object Refresh : MemberPaymentIntent
    data class Method(val method: ReceiptMethod) : MemberPaymentIntent
    data class Name(val value: String) : MemberPaymentIntent
    data class Document(val value: String) : MemberPaymentIntent
    data class Accept(val value: Boolean) : MemberPaymentIntent
    data object Pay : MemberPaymentIntent
    data object Replay : MemberPaymentIntent
    data object CopyPix : MemberPaymentIntent
    data object Copied : MemberPaymentIntent
    data object OpenCard : MemberPaymentIntent
    data object OpenFailed : MemberPaymentIntent
    data class RenewalDueDate(val value: String) : MemberPaymentIntent
    data object RenewPix : MemberPaymentIntent
    data object ExportReceipt : MemberPaymentIntent
    data class ReceiptExported(val result: ReceiptExportResult, val generation: Int) : MemberPaymentIntent
}
sealed interface MemberPaymentEffect {
    val generation: Int
    data class Copy(val payload: String, override val generation: Int) : MemberPaymentEffect
    data class Open(val url: String, override val generation: Int) : MemberPaymentEffect
}
internal fun MemberPaymentInstrument.expired(now: Instant): Boolean = status == "EXPIRED" ||
    (quote.method == ReceiptMethod.PIX && expiresAt?.let { runCatching { Instant.parse(it) <= now }.getOrDefault(true) } == true)
internal fun hostedPaymentUrl(url: String): Boolean =
    Regex("https://(www\\.)?(sandbox\\.)?asaas\\.com/[^\\s?#]+([?#][^\\s]*)?").matches(url)
internal fun MemberPaymentDetail.instrumentForRenewal(id: String? = null) = instruments.lastOrNull {
    it.quote.method == ReceiptMethod.PIX && (id == null || it.id == id)
}
