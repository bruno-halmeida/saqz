package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Rotas do gestor da seção H do plano v1: política de taxa, prévia e revisão de Pix. */
class KtorGroupPaymentsGateway(private val network: AuthenticatedNetworkClient) : GroupPaymentsGateway {
    override suspend fun setFeePolicy(groupId: String, requestId: String, accountId: String, policy: FeePolicy) =
        envelope(network, HttpMethod.Put, "${group(groupId)}/fee-policy", StatusTransport.serializer(), requestId,
            Json.encodeToString(FeePolicyCommand(requestId, accountId, policy.name))) {
            require(it.state.groupId == groupId && it.state.accountId == accountId)
            ReceiptStatus(it.state.domain(), it.permissions.mapValues { entry -> entry.value.domain() })
        }.receipt()

    override suspend fun quote(groupId: String, accountId: String, baseCents: Long) =
        envelope(network, HttpMethod.Get, "${group(groupId)}/quote", QuoteResponseTransport.serializer(),
            query = mapOf("accountId" to accountId, "baseCents" to baseCents.toString())) {
            FeeQuote(it.absorb.domain(), it.pass.domain(), it.pixFeeLabel, it.cardFeeLabel)
        }.receipt()

    override suspend fun reviews(groupId: String, accountId: String) =
        envelope(network, HttpMethod.Get, "${group(groupId)}/reviews", ReviewsTransport.serializer(),
            query = mapOf("accountId" to accountId)) { list ->
            list.reviews.map {
                require(it.id.isNotBlank() && it.amountCents > 0)
                PaymentReview(it.id, it.payerName, it.amountCents, it.receivedAt)
            }
        }.receipt()

    override suspend fun suggestion(reviewId: String, memberUserId: String) =
        envelope(network, HttpMethod.Get, "api/receivables/reviews/${reviewId.encodeURLPathPart()}/suggestion",
            SuggestionTransport.serializer(), query = mapOf("memberUserId" to memberUserId)) { suggestion ->
            ReviewSuggestion(suggestion.charges.map {
                SuggestedCharge(it.chargeId, it.label, it.dueDate, it.payableCents, it.overdue)
            }, suggestion.coveredCents)
        }.receipt()

    override suspend fun assign(reviewId: String, requestId: String, memberUserId: String, chargeIds: List<String>) =
        envelope(network, HttpMethod.Post, "api/receivables/reviews/${reviewId.encodeURLPathPart()}/assign",
            AssignmentTransport.serializer(), requestId,
            Json.encodeToString(AssignCommand(requestId, memberUserId, chargeIds))) {
            require(chargeIds.containsAll(it.settledChargeIds) && it.leftoverCents >= 0)
            ReviewAssignment(it.settledChargeIds, it.leftoverCents)
        }.receipt()

    override suspend fun notCharge(reviewId: String, requestId: String) =
        envelope(network, HttpMethod.Post, "api/receivables/reviews/${reviewId.encodeURLPathPart()}/not-charge",
            NotChargeTransport.serializer(), requestId, Json.encodeToString(RequestOnly(requestId))) {
            require(it.expenseId.isNotBlank()); it.expenseId
        }.receipt()

    private fun group(groupId: String) = "api/receivables/groups/${groupId.encodeURLPathPart()}"
}

/**
 * Quem deve o quê no grupo, para o gestor escolher de quem é um Pix da revisão. Lê as rotas de grupo que o caixa
 * já usa: os nomes de `memberships` e as cobranças em aberto de `charges` (convidado conta para quem o trouxe).
 */
class KtorGroupDebtorsGateway(private val network: AuthenticatedNetworkClient) : GroupDebtorsGateway {
    override suspend fun debtors(groupId: String): SaqzResult<List<GroupDebtor>, ReceiptError> {
        val path = "api/groups/${groupId.encodeURLPathPart()}"
        val members = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "$path/memberships", ListSerializer(DebtorMemberTransport.serializer()))
        }
        val charges = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "$path/charges", DebtorChargesTransport.serializer())
        }
        if (members is NetworkResult.Failure) return SaqzResult.Failure(members.error.athleteError().receiptError())
        if (charges is NetworkResult.Failure) return SaqzResult.Failure(charges.error.athleteError().receiptError())
        val open = (charges as NetworkResult.Success).value.charges.filter { it.status == "PENDING" }.groupBy { it.memberId }
        return SaqzResult.Success((members as NetworkResult.Success).value.filter { it.userId.isNotBlank() }.map { member ->
            val owed = open[member.userId].orEmpty()
            GroupDebtor(member.userId, member.displayName, owed.size, owed.sumOf { it.payableCents ?: it.amountCents })
        }.sortedWith(compareByDescending<GroupDebtor> { it.openCount > 0 }.thenBy { it.name.lowercase() }))
    }
}

private fun <T> SaqzResult<T, PaymentError>.receipt(): SaqzResult<T, ReceiptError> = when (this) {
    is SaqzResult.Success -> this
    is SaqzResult.Failure -> SaqzResult.Failure(error.receiptError())
}

internal fun PaymentError.receiptError(): ReceiptError = when (this) {
    PaymentError.SIGNED_OUT -> ReceiptError.SIGNED_OUT
    PaymentError.NETWORK -> ReceiptError.NETWORK
    PaymentError.INVALID, PaymentError.INVALID_DOCUMENT -> ReceiptError.INVALID
    PaymentError.CHARGE_NOT_PAYABLE -> ReceiptError.STALE
    PaymentError.UNAVAILABLE, PaymentError.DOCUMENT_REQUIRED, PaymentError.PIX_NOT_READY -> ReceiptError.DENIED
}

@Serializable internal data class FeePolicyCommand(val requestId: String, val accountId: String, val feePolicy: String)
@Serializable internal data class QuoteSideTransport(val athletePaysCents: Long, val receivesPixCents: Long,
    val receivesCardCents: Long)
private fun QuoteSideTransport.domain(): FeeQuoteSide {
    require(athletePaysCents >= 0 && receivesPixCents >= 0 && receivesCardCents >= 0)
    return FeeQuoteSide(athletePaysCents, receivesPixCents, receivesCardCents)
}
@Serializable internal data class QuoteResponseTransport(val absorb: QuoteSideTransport, val pass: QuoteSideTransport,
    val pixFeeLabel: String, val cardFeeLabel: String)
@Serializable internal data class PaymentReviewTransport(val id: String, val payerName: String, val amountCents: Long,
    val receivedAt: String)
@Serializable internal data class ReviewsTransport(val reviews: List<PaymentReviewTransport>)
@Serializable internal data class SuggestedChargeTransport(val chargeId: String, val label: String, val dueDate: String,
    val payableCents: Long, val overdue: Boolean = false)
@Serializable internal data class SuggestionTransport(val charges: List<SuggestedChargeTransport>, val coveredCents: Long)
@Serializable internal data class AssignCommand(val requestId: String, val memberUserId: String, val chargeIds: List<String>)
@Serializable internal data class AssignmentTransport(val settledChargeIds: List<String>, val leftoverCents: Long)
@Serializable internal data class NotChargeTransport(val expenseId: String)
@Serializable internal data class DebtorMemberTransport(val userId: String = "", val displayName: String = "")
@Serializable internal data class DebtorChargeTransport(val memberId: String, val status: String, val amountCents: Long,
    val payableCents: Long? = null)
@Serializable internal data class DebtorChargesTransport(val charges: List<DebtorChargeTransport>)
