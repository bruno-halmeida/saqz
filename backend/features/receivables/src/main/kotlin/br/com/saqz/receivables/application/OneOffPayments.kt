package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.sharedkernel.group.GroupChargePayments
import br.com.saqz.sharedkernel.group.PayableGroupCharge
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PaymentPayer(val name: String, val cpfCnpj: String) {
    fun valid() = name.trim().length in 2..120 && name.none(Char::isISOControl) && cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}"))
}

data class ChargePaymentReview(val accountId: UUID, val chargeId: UUID, val groupId: UUID,
    val payerId: UUID, val dueDate: LocalDate, val billingMonth: LocalDate?, val quotes: List<FeeQuote>, val fingerprint: String)
data class PaymentOrder(val id: UUID, val accountId: UUID, val chargeId: UUID, val groupId: UUID,
    val payerId: UUID, val dueDate: LocalDate, val status: String, val quotes: List<FeeQuote>, val fingerprint: String)
data class PaymentInstrument(val id: UUID, val accountId: UUID, val orderId: UUID, val quote: FeeQuote,
    val status: String, val paymentId: String? = null, val checkoutId: String? = null,
    val pixPayload: String? = null, val pixImage: String? = null, val checkoutUrl: String? = null,
    val confirmed: Boolean = false, val settled: Boolean = false, val available: Boolean = false,
    val splitSettled: Boolean = false, val expiresAt: Instant? = null)
data class PaymentOrderDetail(val order: PaymentOrder, val instruments: List<PaymentInstrument>)
data class PaymentOrderPage(val orders: List<PaymentOrder>, val nextCursor: UUID?)
data class ChargeOrderLookup(val detail: PaymentOrderDetail?)

interface PaymentStore {
    fun <T> transaction(block: () -> T): T
    fun webhookReady(accountId: UUID): Boolean
    fun order(id: UUID): PaymentOrder?
    fun orderForCharge(chargeId: UUID): PaymentOrder?
    fun ordersForPayer(payerId: UUID, after: UUID?): List<PaymentOrder>
    fun insertOrder(id: UUID, review: ChargePaymentReview, request: FinancialRequest, at: Instant): PaymentOrder
    fun instruments(orderId: UUID): List<PaymentInstrument>
    fun insertInstrument(order: PaymentOrder, quote: FeeQuote, request: FinancialRequest, digest: String, payer: PaymentPayer, at: Instant): PaymentInstrument
    fun instrumentRequest(accountId: UUID, request: FinancialRequest, digest: String): PaymentInstrument?
    fun changeOrder(id: UUID, status: String)
    fun registerLocal(accountId: UUID, resource: UUID, kind: String, request: FinancialRequest, digest: String, at: Instant): UUID
    fun liveInstrument(orderId: UUID): PaymentInstrument? = instruments(orderId).firstOrNull { it.status !in setOf("CANCELLED", "EXPIRED") }
}

/** Network effects run after the durable transaction, with operation leases and recovery-only retries. */
interface PaymentExecution {
    fun create(instrumentId: UUID)
    fun cancel(instrumentId: UUID, request: FinancialRequest)
    fun reconcile(instrumentId: UUID): Boolean
}

class OneOffPayments(private val store: PaymentStore, private val groupCharges: GroupChargePayments,
    private val accounts: FinancialAccountRepository, private val groups: GroupReceivablesStore,
    private val administrators: GroupAdministrationDirectory, private val conditions: FinancialConditions,
    private val eligibility: ReceivablesEligibility, private val rollout: ReceivablesRolloutAccess,
    private val execution: PaymentExecution, private val clock: Clock) {

    fun ownOrders(request: FinancialRequest, after: UUID?): FinancialResult<PaymentOrderPage> = safely(request) {
        store.transaction {
            val rows = store.ordersForPayer(request.actorUserId, after)
            val page = rows.take(50)
            PaymentOrderPage(page, if (rows.size > page.size) page.last().id else null)
        }
    }

    fun preview(chargeId: UUID, accountId: UUID, request: FinancialRequest): FinancialResult<ChargePaymentReview> = safely(request) {
        store.transaction {
            val charge = groupCharges.lock(chargeId) ?: fail(FinancialError.NOT_FOUND)
            authorize(accountId, charge.groupId, request, true)
            if (accounts.findById(accountId)?.ownerUserId != charge.ownerUserId) fail(FinancialError.NOT_FOUND)
            if (!charge.pending || !charge.groupActive || charge.reservedOrderId != null) fail(FinancialError.CONFLICT)
            review(charge, accountId)
        }
    }
    fun orderForCharge(chargeId: UUID, accountId: UUID, request: FinancialRequest): FinancialResult<ChargeOrderLookup> = safely(request) {
        store.transaction {
            val charge = groupCharges.lock(chargeId) ?: fail(FinancialError.NOT_FOUND)
            val order = store.orderForCharge(chargeId)
            if (order != null && order.accountId != accountId) fail(FinancialError.NOT_FOUND)
            authorize(accountId, order?.groupId ?: charge.groupId, request, false)
            if (order == null && (!charge.groupActive || accounts.findById(accountId)?.ownerUserId != charge.ownerUserId)) {
                fail(FinancialError.NOT_FOUND)
            }
            ChargeOrderLookup(order?.let { detail(it.id) })
        }
    }
    fun approve(chargeId: UUID, accountId: UUID, request: FinancialRequest, fingerprint: String,
                accepted: Boolean): FinancialResult<PaymentOrder> = safely(request) {
        require(accepted && fingerprint.matches(Regex("[a-f0-9]{64}")))
        store.transaction {
            val charge = groupCharges.lock(chargeId) ?: fail(FinancialError.NOT_FOUND)
            // Updated authorization precedes idempotent replay; maintenance ignores rollout/plan.
            authorize(accountId, charge.groupId, request, false)
            val digest = paymentDigest("APPROVE:$chargeId:$fingerprint")
            val existing = store.orderForCharge(chargeId)
            if (existing != null) {
                if (existing.accountId != accountId || existing.fingerprint != fingerprint) fail(FinancialError.CONFLICT)
                if (store.registerLocal(accountId, existing.id, "ISSUE_ORDER", request, digest, clock.instant()) != existing.id)
                    fail(FinancialError.CONFLICT)
                return@transaction existing
            }
            authorize(accountId, charge.groupId, request, true)
            if (accounts.findById(accountId)?.ownerUserId != charge.ownerUserId) fail(FinancialError.NOT_FOUND)
            if (!charge.pending || !charge.groupActive || charge.reservedOrderId != null) fail(FinancialError.CONFLICT)
            val review = review(charge, accountId)
            if (review.fingerprint != fingerprint) fail(FinancialError.CONFLICT)
            val id = UUID.randomUUID()
            if (!groupCharges.reserve(chargeId, id)) fail(FinancialError.CONFLICT)
            store.registerLocal(accountId, id, "ISSUE_ORDER", request, digest, clock.instant())
            store.insertOrder(id, review, request, clock.instant())
        }
    }
    fun get(orderId: UUID, request: FinancialRequest): FinancialResult<PaymentOrderDetail> = safely(request) {
        store.transaction { val order = lockedOrder(orderId); readAccess(order, request); detail(order.id) }
    }
    fun instrument(orderId: UUID, request: FinancialRequest, method: PaymentMethod, fingerprint: String,
                   accepted: Boolean, payer: PaymentPayer): FinancialResult<PaymentInstrument> = safely(request) {
        require(accepted && payer.valid())
        val instrument = store.transaction {
            val order = lockedOrder(orderId)
            if (request.actorUserId != order.payerId) fail(FinancialError.NOT_FOUND)
            if (fingerprint != order.fingerprint) fail(FinancialError.CONFLICT)
            val quote = order.quotes.singleOrNull { it.method == method } ?: fail(FinancialError.INVALID_INPUT)
            val digest = paymentDigest("INSTRUMENT:$orderId:$method:$fingerprint:${payer.name}:${payer.cpfCnpj}")
            store.instrumentRequest(order.accountId, request, digest)?.let { return@transaction it }
            if (order.status != "ISSUED" || store.liveInstrument(orderId) != null) fail(FinancialError.CONFLICT)
            store.insertInstrument(order, quote, request, digest, payer, clock.instant())
        }
        execution.create(instrument.id)
        store.transaction { store.instruments(orderId).single { it.id == instrument.id } }
    }
    fun reconcile(orderId: UUID, request: FinancialRequest): FinancialResult<PaymentOrderDetail> = safely(request) {
        val ids = store.transaction {
            val order = lockedOrder(orderId); readAccess(order, request)
            store.registerLocal(order.accountId, orderId, "RECONCILE_PAYMENT", request,
                paymentDigest("RECONCILE:$orderId"), clock.instant())
            store.instruments(orderId).map { it.id }
        }
        ids.forEach { execution.reconcile(it) }
        store.transaction { detail(orderId) }
    }
    fun cancel(orderId: UUID, request: FinancialRequest): FinancialResult<PaymentOrderDetail> = safely(request) {
        val live = store.transaction {
            val order = lockedOrder(orderId)
            authorize(order.accountId, order.groupId, request, false)
            store.registerLocal(order.accountId, orderId, "CANCEL_ORDER", request,
                paymentDigest("CANCEL:$orderId"), clock.instant())
            if (order.status == "CANCELLED") return@transaction null
            if (order.status !in setOf("ISSUED", "CANCEL_PENDING")) fail(FinancialError.CONFLICT)
            val live = store.liveInstrument(orderId)
            if (live == null) {
                store.changeOrder(orderId, "CANCELLED"); groupCharges.release(order.chargeId, orderId)
            } else store.changeOrder(orderId, "CANCEL_PENDING")
            live
        }
        if (live != null) execution.cancel(live.id, request)
        store.transaction { detail(orderId) }
    }
    private fun lockedOrder(id: UUID): PaymentOrder {
        val order = store.order(id) ?: fail(FinancialError.NOT_FOUND)
        groupCharges.lock(order.chargeId) ?: fail(FinancialError.NOT_FOUND)
        groups.lockAccount(order.accountId) ?: fail(FinancialError.NOT_FOUND)
        return store.order(id) ?: fail(FinancialError.NOT_FOUND)
    }
    private fun detail(id: UUID) = PaymentOrderDetail(store.order(id)!!, store.instruments(id))
    private fun readAccess(order: PaymentOrder, request: FinancialRequest) {
        if (order.payerId != request.actorUserId) authorize(order.accountId, order.groupId, request, false)
    }
    private fun authorize(accountId: UUID, groupId: UUID, request: FinancialRequest, newBusiness: Boolean) {
        val account = groups.lockAccount(accountId) ?: fail(FinancialError.NOT_FOUND)
        val context = FinancialAccessContext(account, request.actorUserId,
            accounts.findDelegation(accountId, request.actorUserId),
            administrators.isAdministrator(account.ownerUserId, request.actorUserId), false,
            newBusiness && eligibility.forOwner(account.ownerUserId, clock.instant()).eligible,
            groups.state(accountId, groupId)?.enabled == true,
            newBusiness && rollout.availability(account.ownerUserId).backendEnabled)
        val permission = FinancialAccess.permission(if (newBusiness) FinancialAction.ISSUE_ORDER else FinancialAction.READ, context)
        if (!permission.allowed) fail(when (permission.reason) {
            UnavailabilityReason.INELIGIBLE_PLAN -> FinancialError.INELIGIBLE_PLAN
            UnavailabilityReason.REGISTRATION_NOT_APPROVED -> FinancialError.REGISTRATION_RESTRICTED
            UnavailabilityReason.OPERATIONS_DISABLED -> FinancialError.OPERATIONS_DISABLED
            else -> FinancialError.NOT_FOUND
        })
        if (newBusiness && !store.webhookReady(accountId)) fail(FinancialError.CONFIGURATION_UNAVAILABLE)
    }
    private fun review(charge: PayableGroupCharge, account: UUID): ChargePaymentReview {
        val state = groups.state(account, charge.groupId) ?: fail(FinancialError.NOT_FOUND)
        val methods = buildSet { if (state.pixEnabled) add(PaymentMethod.PIX); if (state.cardEnabled) add(PaymentMethod.CARD) }
        require(methods.isNotEmpty())
        val schedules = conditions.current(methods, clock.instant()).sortedBy { it.method.ordinal }
        if (schedules.map { it.method }.toSet() != methods) fail(FinancialError.CONFIGURATION_UNAVAILABLE)
        val quotes = schedules.map { FeeCalculator.quote(charge.baseCents, it) }
        return ChargePaymentReview(account, charge.id, charge.groupId, charge.payerId, charge.dueDate, charge.billingMonth, quotes,
            paymentDigest("$account:${charge.id}:${charge.version}:${charge.dueDate}:" + quotes.joinToString("|")))
    }
    private class Failed(val code: FinancialError) : RuntimeException()
    private fun fail(error: FinancialError): Nothing = throw Failed(error)
    private fun <T> safely(request: FinancialRequest, block: () -> T): FinancialResult<T> = try {
        FinancialResult.Success(block(), request.requestId)
    } catch (e: Failed) { FinancialResult.Failure(e.code, request.requestId) }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
    catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
    catch (_: ArithmeticException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
}

fun paymentDigest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
