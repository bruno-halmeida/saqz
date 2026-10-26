package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class ReceiptMethod { PIX, CARD }
enum class AccountRegistration { INCOMPLETE, UNDER_REVIEW, CORRECTION_REQUIRED, APPROVED, REJECTED }
data class ReceiptAccount(val id: String, val registration: AccountRegistration, val operationsEnabled: Boolean)
enum class FeePolicy { ABSORB, PASS }
data class ReceiptConfiguration(val accountId: String, val groupId: String, val enabled: Boolean,
    val pixEnabled: Boolean, val cardEnabled: Boolean,
    /** v1 (H): ausentes no servidor de setembro. */
    val feePolicy: FeePolicy = FeePolicy.ABSORB, val pixReady: Boolean = false, val openReviews: Int = 0)
data class ReceiptPermission(val allowed: Boolean, val reason: String? = null)
data class ReceiptStatus(val state: ReceiptConfiguration, val permissions: Map<String, ReceiptPermission>)
data class ReceiptSchedule(val method: ReceiptMethod, val termsVersion: String,
    val providerRate: String, val providerFixedCents: Long, val commissionRate: String, val commissionFixedCents: Long,
    val providerMinimumCents: Long = 0, val providerMaximumCents: Long? = null)
data class ReceiptPrice(val kind: String, val method: ReceiptMethod, val baseCents: Long,
    val feesCents: Long, val totalCents: Long, val expectedNetCents: Long)
data class ReceiptReview(val state: ReceiptConfiguration, val schedules: List<ReceiptSchedule>,
    val prices: List<ReceiptPrice>, val permissions: Map<String, ReceiptPermission>,
    val effectiveCutoffAt: String?, val fingerprint: String)
data class ReceiptTerms(val version: String, val content: String)
data class ReceiptCommand(val requestId: String, val accountId: String, val methods: Set<ReceiptMethod> = emptySet(),
    val fingerprint: String? = null, val accepted: Boolean = false)
enum class ReceiptError : SaqzError { DENIED, STALE, INVALID, UNAVAILABLE, NETWORK, UNCERTAIN, SIGNED_OUT }
fun interface ReceiptAccountDirectory {
    suspend fun accounts(): SaqzResult<List<ReceiptAccount>, ReceiptError>
}
interface GroupReceivablesGateway : ReceiptAccountDirectory {
    suspend fun status(groupId: String, accountId: String): SaqzResult<ReceiptStatus, ReceiptError>
    suspend fun preview(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptReview, ReceiptError>
    suspend fun terms(version: String): SaqzResult<ReceiptTerms, ReceiptError>
    suspend fun activate(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptConfiguration, ReceiptError>
    suspend fun deactivate(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptConfiguration, ReceiptError>
}

/** Stable authenticated actor identity for restoring a command, never sent as a request parameter. */
fun interface ReceivablesRecoveryIdentity {
    fun currentUserId(): String?
}

/** "Atleta paga · você recebe" de um valor base, nas duas políticas (FIN-010). Quem calcula é o servidor. */
data class FeeQuoteSide(val athletePaysCents: Long, val receivesPixCents: Long, val receivesCardCents: Long)
data class FeeQuote(val absorb: FeeQuoteSide, val pass: FeeQuoteSide, val pixFeeLabel: String, val cardFeeLabel: String) {
    fun side(policy: FeePolicy) = if (policy == FeePolicy.PASS) pass else absorb
}

/** Pix que caiu fora de um código do Saqz (FIN-014). */
data class PaymentReview(val id: String, val payerName: String, val amountCents: Long, val receivedAt: String)
data class SuggestedCharge(val chargeId: String, val label: String, val dueDate: String, val payableCents: Long, val overdue: Boolean)
data class ReviewSuggestion(val charges: List<SuggestedCharge>, val coveredCents: Long)
data class ReviewAssignment(val settledChargeIds: List<String>, val leftoverCents: Long)

/** Rotas do gestor da seção H do plano v1. */
interface GroupPaymentsGateway {
    suspend fun setFeePolicy(groupId: String, requestId: String, accountId: String, policy: FeePolicy):
        SaqzResult<ReceiptStatus, ReceiptError>
    suspend fun quote(groupId: String, accountId: String, baseCents: Long): SaqzResult<FeeQuote, ReceiptError>
    suspend fun reviews(groupId: String, accountId: String): SaqzResult<List<PaymentReview>, ReceiptError>
    suspend fun suggestion(reviewId: String, memberUserId: String): SaqzResult<ReviewSuggestion, ReceiptError>
    suspend fun assign(reviewId: String, requestId: String, memberUserId: String, chargeIds: List<String>):
        SaqzResult<ReviewAssignment, ReceiptError>
    suspend fun notCharge(reviewId: String, requestId: String): SaqzResult<String, ReceiptError>
}

/** Membro do grupo com o que ele deve, para o "É de alguém do grupo" da revisão. */
data class GroupDebtor(val memberUserId: String, val name: String, val openCount: Int, val openCents: Long)

fun interface GroupDebtorsGateway {
    suspend fun debtors(groupId: String): SaqzResult<List<GroupDebtor>, ReceiptError>
}
