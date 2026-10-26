package br.com.saqz.receivables.application

import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.sharedkernel.group.LedgerCharge
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ReviewItem(val id: UUID, val payerName: String, val amountCents: Long, val receivedAt: Instant)
data class ReviewList(val reviews: List<ReviewItem>)
data class SuggestedCharge(val chargeId: UUID, val label: String, val dueDate: LocalDate, val payableCents: Long, val overdue: Boolean)
data class ReviewSuggestion(val charges: List<SuggestedCharge>, val coveredCents: Long)
data class ReviewAssignment(val settledChargeIds: List<UUID>, val leftoverCents: Long)
data class ReviewNotCharge(val expenseId: UUID)

data class StoredReview(val id: UUID, val accountId: UUID, val paymentId: String, val payerName: String, val amountCents: Long,
    val feesCents: Long, val receivedAt: Instant, val status: String, val requestId: UUID?, val resolvedBy: UUID?,
    val settledChargeIds: List<UUID>, val leftoverCents: Long?, val expenseId: UUID?)

interface ReviewStore {
    fun <T> transaction(block: () -> T): T
    fun accountOfGroup(groupId: UUID): UUID?
    fun open(accountId: UUID): List<ReviewItem>
    fun lock(reviewId: UUID): StoredReview?
    fun accountGroups(accountId: UUID): List<UUID>
    fun resolve(reviewId: UUID, status: String, groupId: UUID, memberId: UUID?, chargeIds: List<UUID>, leftover: Long?,
                expenseId: UUID?, requestId: UUID, actor: UUID, at: Instant)
}

/** Pix fora de código (FIN-014): o gestor diz de quem é ou que não é cobrança. Dono e admins do grupo (FIN-009). */
class PaymentReviews(private val store: ReviewStore, private val ledger: GroupChargeLedger, private val clock: Clock,
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")) {

    fun list(groupId: UUID, accountId: UUID, request: FinancialRequest): FinancialResult<ReviewList> = safely(request) {
        store.transaction {
            if (store.accountOfGroup(groupId) != accountId || request.actorUserId !in ledger.managers(groupId)) fail(FinancialError.NOT_FOUND)
            ReviewList(store.open(accountId))
        }
    }

    fun suggestion(reviewId: UUID, memberId: UUID, request: FinancialRequest): FinancialResult<ReviewSuggestion> = safely(request) {
        store.transaction {
            val review = store.lock(reviewId)?.takeIf { it.status == "OPEN" } ?: fail(FinancialError.NOT_FOUND)
            val group = groupFor(review, memberId, request.actorUserId)
            val today = LocalDate.now(clock.withZone(zone))
            var left = review.amountCents
            val covered = ledger.openCharges(group, memberId).takeWhile { (it.payableCents <= left).also { fits -> if (fits) left -= it.payableCents } }
            ReviewSuggestion(covered.map { SuggestedCharge(it.id, label(it), it.dueDate, it.payableCents, it.dueDate < today) },
                covered.sumOf { it.payableCents })
        }
    }

    fun assign(reviewId: UUID, memberId: UUID, chargeIds: List<UUID>, request: FinancialRequest): FinancialResult<ReviewAssignment> = safely(request) {
        require(chargeIds.distinct().size == chargeIds.size && chargeIds.size <= 100)
        store.transaction {
            val review = store.lock(reviewId) ?: fail(FinancialError.NOT_FOUND)
            if (review.status != "OPEN") {
                if (review.status == "ASSIGNED" && review.requestId == request.requestId && review.resolvedBy == request.actorUserId)
                    return@transaction ReviewAssignment(review.settledChargeIds, review.leftoverCents ?: 0)
                fail(FinancialError.CONFLICT)
            }
            val group = groupFor(review, memberId, request.actorUserId)
            val charges = ledger.lock(chargeIds)
            if (charges.size != chargeIds.size || charges.any { it.groupId != group || it.memberUserId != memberId || it.status != "PENDING" })
                fail(FinancialError.CHARGE_NOT_PAYABLE)
            val total = charges.sumOf { it.payableCents }
            if (total > review.amountCents) fail(FinancialError.INVALID_INPUT)
            val now = clock.instant()
            charges.forEach { ledger.settleByApp(it.id, "PIX", request.actorUserId, now) }
            val leftover = review.amountCents - total
            val expense = if (leftover > 0) income(review, group, leftover, request.actorUserId, now) else null
            fees(review, group, request.actorUserId, now)
            store.resolve(review.id, "ASSIGNED", group, memberId, chargeIds, leftover, expense, request.requestId, request.actorUserId, now)
            ReviewAssignment(chargeIds, leftover)
        }
    }

    fun notCharge(reviewId: UUID, request: FinancialRequest): FinancialResult<ReviewNotCharge> = safely(request) {
        store.transaction {
            val review = store.lock(reviewId) ?: fail(FinancialError.NOT_FOUND)
            if (review.status != "OPEN") {
                if (review.status == "NOT_CHARGE" && review.requestId == request.requestId && review.resolvedBy == request.actorUserId)
                    return@transaction ReviewNotCharge(review.expenseId!!)
                fail(FinancialError.CONFLICT)
            }
            val group = store.accountGroups(review.accountId).firstOrNull { request.actorUserId in ledger.managers(it) }
                ?: fail(FinancialError.NOT_FOUND)
            val now = clock.instant()
            val expense = income(review, group, review.amountCents, request.actorUserId, now)
            fees(review, group, request.actorUserId, now)
            store.resolve(review.id, "NOT_CHARGE", group, null, emptyList(), null, expense, request.requestId, request.actorUserId, now)
            ReviewNotCharge(expense)
        }
    }

    /** Grupo ativo da conta em que a pessoa é membro e quem resolve é dono ou admin. */
    private fun groupFor(review: StoredReview, memberId: UUID, actor: UUID): UUID =
        store.accountGroups(review.accountId).firstOrNull { actor in ledger.managers(it) && ledger.isActiveMember(it, memberId) }
            ?: fail(FinancialError.NOT_FOUND)

    private fun income(review: StoredReview, group: UUID, amount: Long, actor: UUID, now: Instant): UUID {
        val id = UUID.nameUUIDFromBytes("review-income:${review.id}".toByteArray())
        ledger.record(id, group, "IN", "RECEIVABLES_REVIEW", "Pix de ${review.payerName}", amount, LocalDate.ofInstant(review.receivedAt, zone), actor, now)
        return id
    }

    private fun fees(review: StoredReview, group: UUID, actor: UUID, now: Instant) {
        if (review.feesCents <= 0) return
        ledger.record(UUID.nameUUIDFromBytes("review-fees:${review.id}".toByteArray()), group, "OUT", "RECEIVABLES_FEES",
            PaymentSettlement.FEES_LABEL, review.feesCents, LocalDate.ofInstant(review.receivedAt, zone), actor, now)
    }

    private fun label(charge: LedgerCharge): String {
        val day = (charge.gameDate ?: charge.dueDate).format(DateTimeFormatter.ofPattern("dd/MM"))
        return when {
            charge.kind == "MONTHLY" -> "Mensalidade · " + PaymentSettlement.month(charge.billingMonth ?: charge.dueDate)
            charge.guestName != null -> "Convidado: ${charge.guestName} · $day"
            else -> "Diária · $day"
        }
    }

    private class Failed(val code: FinancialError) : RuntimeException()
    private fun fail(error: FinancialError): Nothing = throw Failed(error)
    private fun <T> safely(request: FinancialRequest, block: () -> T): FinancialResult<T> = try {
        FinancialResult.Success(block(), request.requestId)
    } catch (e: Failed) { FinancialResult.Failure(e.code, request.requestId) }
    catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
}
