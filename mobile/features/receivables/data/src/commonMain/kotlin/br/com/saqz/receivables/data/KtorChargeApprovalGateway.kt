package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorChargeApprovalGateway(private val network: AuthenticatedNetworkClient) : ChargeApprovalGateway {
    override suspend fun lookup(target: ChargeApprovalTarget) = call("${path(target)}/order", ChargeLookupTransport.serializer(),
        request = NetworkRequest(query = mapOf("accountId" to target.accountId))) { response ->
        response.detail?.let { detail -> detail.domain(detail.order.id).also { it.order.requireTarget(target) } }
    }
    override suspend fun preview(target: ChargeApprovalTarget, requestId: String) = call("${path(target)}/preview",
        ChargeReviewTransport.serializer(), requestId, request = body(ApprovalTransport(requestId, target.accountId))) { it.domain(target) }

    override suspend fun approve(target: ChargeApprovalTarget, command: ChargeApprovalCommand):
        SaqzResult<MemberPaymentOrder, ReceiptError> {
        if (!command.accepted || !command.fingerprint.matches(Regex("[a-f0-9]{64}"))) return SaqzResult.Failure(ReceiptError.INVALID)
        return call("${path(target)}/approve", MemberOrderTransport.serializer(), command.requestId, true,
            body(ApprovalTransport(command.requestId, target.accountId, command.fingerprint, command.accepted))) {
            it.domain().also { order -> order.requireTarget(target); require(order.fingerprint == command.fingerprint) }
        }
    }
    override suspend fun cancel(target: ChargeApprovalTarget, orderId: String, requestId: String) =
        call("api/receivables/orders/${orderId.encodeURLPathPart()}/cancel", MemberDetailTransport.serializer(), requestId, true,
            NetworkRequest(body = Json.encodeToString(MemberReconcileTransport(requestId)))) {
            it.domain(orderId).also { detail -> detail.order.requireTarget(target) }
        }
    private fun body(command: ApprovalTransport) = NetworkRequest(body = Json.encodeToString(command))
    private fun path(target: ChargeApprovalTarget) = "api/receivables/charges/${target.chargeId.encodeURLPathPart()}"
    private suspend fun <T, R> call(path: String, serializer: KSerializer<T>, requestId: String? = null, writing: Boolean = false,
        request: NetworkRequest = NetworkRequest(), map: (T) -> R): SaqzResult<R, ReceiptError> {
        if (requestId != null && requestId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        val invalid = if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID
        return when (val result = retryTransport(if (writing) RetrySafety.IdempotentWrite else RetrySafety.Read) {
            network.execute(if (requestId == null) HttpMethod.Get else HttpMethod.Post, path,
                EnvelopeTransport.serializer(serializer), request)
        }) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(writing))
            is NetworkResult.Success -> {
                val envelope = result.value
                val wrongRequest = envelope.requestId.isBlank() || (requestId != null && envelope.requestId != requestId)
                if (envelope.value == null || envelope.error != null || wrongRequest) SaqzResult.Failure(invalid)
                else try { SaqzResult.Success(map(envelope.value)) }
                catch (_: IllegalArgumentException) { SaqzResult.Failure(invalid) }
            }
        }
    }
}
private fun MemberPaymentOrder.requireTarget(target: ChargeApprovalTarget) {
    require(accountId == target.accountId && chargeId == target.chargeId && groupId == target.groupId)
}
@Serializable private data class ApprovalTransport(val requestId: String, val accountId: String,
    val fingerprint: String? = null, val accepted: Boolean = false)
@Serializable private data class ChargeLookupTransport(val detail: MemberDetailTransport?)
@Serializable private data class ChargeReviewTransport(val accountId: String, val chargeId: String, val groupId: String,
    val payerId: String, val dueDate: String, val billingMonth: String? = null,
    val quotes: List<MemberQuoteTransport>, val fingerprint: String) {
    fun domain(target: ChargeApprovalTarget): ChargeApprovalReview {
        require(accountId == target.accountId && chargeId == target.chargeId && groupId == target.groupId)
        require(payerId.isNotBlank() && dueDate.isNotBlank() && fingerprint.matches(Regex("[a-f0-9]{64}")))
        require(quotes.isNotEmpty() && quotes.map { it.method }.distinct().size == quotes.size)
        return ChargeApprovalReview(target, payerId, dueDate, billingMonth, quotes.map { it.domain() }, fingerprint)
    }
}
