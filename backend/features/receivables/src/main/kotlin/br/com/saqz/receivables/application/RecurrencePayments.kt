package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.FeeQuote
import br.com.saqz.receivables.domain.PaymentMethod
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class MonthlyRecurrenceTerms(val baseCents: Long, val dueDay: Int, val memberActive: Boolean)
data class RecurrenceReview(val accountId: UUID, val groupId: UUID, val memberUserId: UUID,
    val method: PaymentMethod, val baseCents: Long, val feesCents: Long, val totalCents: Long,
    val commissionCents: Long, val expectedProviderFeeCents: Long, val expectedNetCents: Long,
    val feeScheduleId: UUID, val termsVersion: String, val firstDueDate: LocalDate,
    val cycle: String = "MONTHLY", val fingerprint: String)
data class PaymentRecurrence(val id: UUID, val accountId: UUID, val groupId: UUID,
    val memberUserId: UUID, val method: PaymentMethod, val baseCents: Long, val feesCents: Long,
    val totalCents: Long, val firstDueDate: LocalDate, val status: String,
    val providerSubscriptionId: String?, val hostedCheckoutUrl: String?, val cutoffAt: Instant?)
data class RecurrenceAuthorization(val recurrence: PaymentRecurrence, val quote: FeeQuote,
    val payer: PaymentPayer, val providerCheckoutId: String? = null)
data class ProviderRecurrenceResult(val subscriptionId: String? = null, val checkoutId: String? = null,
    val checkoutUrl: String? = null, val status: String)
data class ProviderRecurringPayment(val id: String, val subscriptionId: String,
    val dueDate: LocalDate, val status: String, val method: PaymentMethod, val totalCents: Long)
data class RecurrenceClaim(val authorization: RecurrenceAuthorization, val token: UUID, val recoveryOnly: Boolean)

interface RecurrenceProvider {
    fun prepareCustomer(context: RecurrenceAuthorization): Boolean
    fun create(context: RecurrenceAuthorization): ProviderRecurrenceResult?
    fun recover(context: RecurrenceAuthorization): ProviderRecurrenceResult?
    fun payments(context: RecurrenceAuthorization): List<ProviderRecurringPayment>
    /** Suspends generation, cancels only not-yet-due generated payments, and preserves overdue payments. */
    fun stop(context: RecurrenceAuthorization, cutoffDate: LocalDate): Boolean
}

interface RecurrenceStore {
    fun <T> transaction(block: () -> T): T
    fun monthlyTerms(accountId: UUID, groupId: UUID, memberId: UUID): MonthlyRecurrenceTerms?
    fun webhookReady(accountId: UUID): Boolean
    fun find(id: UUID): RecurrenceAuthorization?
    fun live(groupId: UUID, memberId: UUID): RecurrenceAuthorization?
    fun current(accountId: UUID, groupId: UUID, memberId: UUID): RecurrenceAuthorization?
    fun byActorRequest(requestId: UUID, actorUserId: UUID): RecurrenceAuthorization?
    fun byRequest(accountId: UUID, requestId: UUID): Pair<RecurrenceAuthorization, String>?
    fun insert(review: RecurrenceReview, quote: FeeQuote, payer: PaymentPayer,
               request: FinancialRequest, digest: String, at: Instant): RecurrenceAuthorization
    fun requestStop(id: UUID, request: FinancialRequest, reason: String, digest: String, at: Instant): RecurrenceAuthorization
    fun claim(id: UUID, at: Instant, leaseUntil: Instant): RecurrenceClaim?
    fun finish(claim: RecurrenceClaim, result: ProviderRecurrenceResult?, stopped: Boolean, at: Instant): Boolean
    fun recoveryDue(at: Instant, limit: Int): List<UUID>
    fun active(limit: Int): List<UUID>
    fun cutoffCandidates(at: Instant, limit: Int): List<Pair<UUID, String>>
    fun materialize(recurrence: RecurrenceAuthorization, payments: List<ProviderRecurringPayment>, at: Instant): List<UUID>
}

interface RecurrenceExecution {
    fun start(id: UUID)
    fun stop(id: UUID)
    fun recoverDue()
    fun synchronize()
    fun enforceCutoffs()
}

class RecurrencePayments(private val store: RecurrenceStore,
    private val accounts: FinancialAccountRepository, private val groups: GroupReceivablesStore,
    private val conditions: FinancialConditions, private val eligibility: ReceivablesEligibility,
    private val rollout: ReceivablesRolloutAccess, private val execution: RecurrenceExecution,
    private val clock: Clock) {

    fun preview(accountId: UUID, groupId: UUID, method: PaymentMethod, firstDueDate: LocalDate,
                request: FinancialRequest): FinancialResult<RecurrenceReview> = safely(request) {
        store.transaction { review(accountId, groupId, method, firstDueDate, request) }
    }

    fun authorize(accountId: UUID, groupId: UUID, method: PaymentMethod, firstDueDate: LocalDate,
                  fingerprint: String, accepted: Boolean, payer: PaymentPayer,
                  request: FinancialRequest): FinancialResult<PaymentRecurrence> = safely(request) {
        require(accepted && payer.valid() && fingerprint.matches(Regex("[a-f0-9]{64}")))
        val authorization = store.transaction {
            val currentReview = review(accountId, groupId, method, firstDueDate, request)
            if (currentReview.fingerprint != fingerprint) fail(FinancialError.CONFLICT)
            val digest = paymentDigest("RECURRENCE:$groupId:$method:$firstDueDate:$fingerprint:${payer.name}:${payer.cpfCnpj}")
            store.byRequest(accountId, request.requestId)?.let { (existing, priorDigest) ->
                if (priorDigest != digest || existing.recurrence.memberUserId != request.actorUserId) fail(FinancialError.CONFLICT)
                return@transaction existing
            }
            if (store.live(groupId, request.actorUserId) != null) fail(FinancialError.CONFLICT)
            val quote = quote(currentReview)
            store.insert(currentReview, quote, payer, request, digest, clock.instant())
        }
        execution.start(authorization.recurrence.id)
        store.transaction { store.find(authorization.recurrence.id)!!.recurrence.also {
            if (it.status == "AUTHORIZING" && it.hostedCheckoutUrl == null) fail(FinancialError.RESULT_PENDING)
        } }
    }

    fun get(id: UUID, request: FinancialRequest): FinancialResult<PaymentRecurrence> = safely(request) {
        store.transaction { own(id, request).recurrence }
    }

    fun current(accountId: UUID, groupId: UUID, request: FinancialRequest): FinancialResult<PaymentRecurrence?> = safely(request) {
        store.transaction { store.current(accountId, groupId, request.actorUserId)?.recurrence }
    }

    fun byRequest(request: FinancialRequest): FinancialResult<PaymentRecurrence> = safely(request) {
        store.transaction { store.byActorRequest(request.requestId, request.actorUserId)?.recurrence ?: fail(FinancialError.NOT_FOUND) }
    }

    fun cancel(id: UUID, request: FinancialRequest): FinancialResult<PaymentRecurrence> = safely(request) {
        val recurrence = store.transaction {
            val current = own(id, request)
            if (current.recurrence.status == "STOPPED") return@transaction current
            store.requestStop(id, request, "PAYER_CANCELLED", paymentDigest("STOP:$id"), clock.instant())
        }
        execution.stop(recurrence.recurrence.id)
        store.transaction { store.find(id)!!.recurrence.also { if (it.status == "STOP_PENDING") fail(FinancialError.RESULT_PENDING) } }
    }

    fun resume(stoppedId: UUID, accountId: UUID, groupId: UUID, method: PaymentMethod,
               firstDueDate: LocalDate, fingerprint: String, accepted: Boolean, payer: PaymentPayer,
               request: FinancialRequest): FinancialResult<PaymentRecurrence> {
        val old = store.transaction { store.find(stoppedId) }
        if (old == null || old.recurrence.memberUserId != request.actorUserId) return FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
        if (old.recurrence.status != "STOPPED" || old.recurrence.accountId != accountId || old.recurrence.groupId != groupId)
            return FinancialResult.Failure(FinancialError.CONFLICT, request.requestId)
        return authorize(accountId, groupId, method, firstDueDate, fingerprint, accepted, payer, request)
    }

    private fun review(accountId: UUID, groupId: UUID, method: PaymentMethod, firstDueDate: LocalDate,
                       request: FinancialRequest): RecurrenceReview {
        val account = accounts.findById(accountId) ?: fail(FinancialError.NOT_FOUND)
        if (account.registration != br.com.saqz.receivables.domain.RegistrationStatus.APPROVED) fail(FinancialError.REGISTRATION_RESTRICTED)
        if (!account.newOperationsEnabled) fail(FinancialError.OPERATIONS_DISABLED)
        val state = groups.state(accountId, groupId) ?: fail(FinancialError.NOT_FOUND)
        if (!state.enabled || (method == PaymentMethod.PIX && !state.pixEnabled) || (method == PaymentMethod.CARD && !state.cardEnabled))
            fail(FinancialError.OPERATIONS_DISABLED)
        if (!eligibility.forOwner(account.ownerUserId, clock.instant()).eligible) fail(FinancialError.INELIGIBLE_PLAN)
        if (!rollout.availability(account.ownerUserId).backendEnabled) fail(FinancialError.OPERATIONS_DISABLED)
        if (!store.webhookReady(accountId)) fail(FinancialError.CONFIGURATION_UNAVAILABLE)
        val monthly = store.monthlyTerms(accountId, groupId, request.actorUserId) ?: fail(FinancialError.NOT_FOUND)
        if (!monthly.memberActive) fail(FinancialError.NOT_FOUND)
        require(firstDueDate >= LocalDate.now(clock) && firstDueDate.dayOfMonth == monthly.dueDay.coerceAtMost(firstDueDate.lengthOfMonth()))
        val schedule = conditions.current(setOf(method), clock.instant()).singleOrNull() ?: fail(FinancialError.CONFIGURATION_UNAVAILABLE)
        val quote = FeeCalculator.quote(monthly.baseCents, schedule)
        val fingerprint = paymentDigest("$accountId:$groupId:${request.actorUserId}:$method:${quote.feeScheduleId}:${quote.baseCents}:${quote.feesCents}:${quote.totalCents}:${quote.commissionCents}:${quote.providerFeeCents}:${quote.expectedNetCents}:${quote.termsVersion}:$firstDueDate:MONTHLY")
        return RecurrenceReview(accountId, groupId, request.actorUserId, method, quote.baseCents, quote.feesCents,
            quote.totalCents, quote.commissionCents, quote.providerFeeCents, quote.expectedNetCents,
            quote.feeScheduleId, quote.termsVersion, firstDueDate, fingerprint = fingerprint)
    }

    private fun quote(review: RecurrenceReview) = FeeQuote(review.feeScheduleId, review.termsVersion, review.method,
        review.baseCents, review.feesCents, review.totalCents, review.expectedNetCents,
        review.commissionCents, review.expectedProviderFeeCents)
    private fun own(id: UUID, request: FinancialRequest) = store.find(id)?.takeIf { it.recurrence.memberUserId == request.actorUserId }
        ?: fail(FinancialError.NOT_FOUND)
    private class Failed(val code: FinancialError) : RuntimeException()
    private fun fail(error: FinancialError): Nothing = throw Failed(error)
    private fun <T> safely(request: FinancialRequest, block: () -> T): FinancialResult<T> = try {
        FinancialResult.Success(block(), request.requestId)
    } catch (e: Failed) { FinancialResult.Failure(e.code, request.requestId) }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
    catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
    catch (_: ArithmeticException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
}
