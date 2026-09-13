package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class RecurrencePaymentsTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val account = FinancialAccount(UUID.randomUUID(), owner, RegistrationStatus.APPROVED, true)
    private val group = UUID.randomUUID()
    private val fee = FeeSchedule(UUID.randomUUID(), PaymentMethod.PIX, BigDecimal.ZERO, 90,
        BigDecimal.ZERO, 10, "terms-2026-09")
    private val store = MemoryStore()
    private val execution = RecordingExecution()
    private val service = RecurrencePayments(store, accounts(), groups(), conditions(),
        ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) },
        ReceivablesRolloutAccess { ReceivablesAvailability(true, true) }, execution, clock)

    @Test fun `preview fixes payer method terms and exact cents in fingerprint`() {
        val result = service.preview(account.id, group, PaymentMethod.PIX, LocalDate.of(2026, 10, 10), request()) as FinancialResult.Success
        val review = result.value
        assertEquals(member, review.memberUserId)
        assertEquals(10_000, review.baseCents)
        assertEquals(100, review.feesCents)
        assertEquals(10_100, review.totalCents)
        assertEquals(10, review.commissionCents)
        assertEquals(90, review.expectedProviderFeeCents)
        assertEquals(10_000, review.expectedNetCents)
        assertEquals("terms-2026-09", review.termsVersion)
        assertTrue(review.fingerprint.matches(Regex("[a-f0-9]{64}")))
    }

    @Test fun `explicit payer acceptance is persisted before execution and request replay returns same recurrence`() {
        val preview = preview()
        val request = request()
        val payer = PaymentPayer("Maria Silva", "12345678901")
        val first = service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            preview.fingerprint, true, payer, request) as FinancialResult.Success
        val second = service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            preview.fingerprint, true, payer, request) as FinancialResult.Success
        assertEquals(first.value.id, second.value.id)
        assertEquals(member, store.rows.single().recurrence.memberUserId)
        assertEquals(payer, store.rows.single().payer)
        assertEquals(listOf(first.value.id, first.value.id), execution.started)
        assertEquals(1, store.inserts)
    }

    @Test fun `tampered review and reused request with another payload are conflicts`() {
        val preview = preview(); val request = request(); val payer = PaymentPayer("Maria Silva", "12345678901")
        assertEquals(FinancialError.CONFLICT, (service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            "0".repeat(64), true, payer, request) as FinancialResult.Failure).error)
        service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate, preview.fingerprint, true, payer, request)
        assertEquals(FinancialError.CONFLICT, (service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            preview.fingerprint, true, PaymentPayer("Outra Pessoa", "98765432100"), request) as FinancialResult.Failure).error)
        assertEquals(1, store.inserts)
    }

    @Test fun `only authenticated payer reads or cancels recurrence`() {
        val preview = preview(); val accepted = service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            preview.fingerprint, true, PaymentPayer("Maria Silva", "12345678901"), request()) as FinancialResult.Success
        val stranger = FinancialRequest(UUID.randomUUID(), UUID.randomUUID())
        assertEquals(FinancialError.NOT_FOUND, (service.get(accepted.value.id, stranger) as FinancialResult.Failure).error)
        assertEquals(FinancialError.NOT_FOUND, (service.cancel(accepted.value.id, stranger) as FinancialResult.Failure).error)
        val cancelled = service.cancel(accepted.value.id, request()) as FinancialResult.Success
        assertEquals("STOPPED", cancelled.value.status)
        assertEquals(listOf(accepted.value.id), execution.stopped)
    }

    @Test fun `mobile recovery finds current and original request only for payer`() {
        val preview = preview(); val authorizationRequest = request()
        val accepted = service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            preview.fingerprint, true, PaymentPayer("Maria Silva", "12345678901"), authorizationRequest) as FinancialResult.Success
        assertEquals(accepted.value.id, (service.current(account.id, group, request()) as FinancialResult.Success).value?.id)
        assertEquals(accepted.value.id, (service.byRequest(authorizationRequest) as FinancialResult.Success).value.id)
        assertEquals(FinancialError.NOT_FOUND, (service.byRequest(FinancialRequest(authorizationRequest.requestId,
            UUID.randomUUID())) as FinancialResult.Failure).error)
        assertNull((service.current(account.id, group, FinancialRequest(UUID.randomUUID(), UUID.randomUUID())) as FinancialResult.Success).value)
    }

    @Test fun `resume requires stopped state and creates a new acceptance and recurrence id`() {
        val preview = preview(); val firstRequest = request()
        val first = service.authorize(account.id, group, PaymentMethod.PIX, preview.firstDueDate, preview.fingerprint,
            true, PaymentPayer("Maria Silva", "12345678901"), firstRequest) as FinancialResult.Success
        store.replace(first.value.id) { it.copy(recurrence = it.recurrence.copy(status = "STOPPED")) }
        val resumed = service.resume(first.value.id, account.id, group, PaymentMethod.PIX, preview.firstDueDate,
            preview.fingerprint, true, PaymentPayer("Maria Silva", "12345678901"), request()) as FinancialResult.Success
        assertNotEquals(first.value.id, resumed.value.id)
        assertEquals("STOPPED", store.find(first.value.id)!!.recurrence.status)
        assertEquals(2, store.inserts)
    }

    @Test fun `inactive member and ineligible owner cannot start new recurrence`() {
        store.memberActive = false
        assertEquals(FinancialError.NOT_FOUND, (service.preview(account.id, group, PaymentMethod.PIX,
            LocalDate.of(2026, 10, 10), request()) as FinancialResult.Failure).error)
        val denied = RecurrencePayments(store.apply { memberActive = true }, accounts(), groups(), conditions(),
            ReceivablesEligibility { _, _ -> ReceivablesEntitlement(false, now) }, ReceivablesRolloutAccess { ReceivablesAvailability(true, true) }, execution, clock)
        assertEquals(FinancialError.INELIGIBLE_PLAN, (denied.preview(account.id, group, PaymentMethod.PIX,
            LocalDate.of(2026, 10, 10), request()) as FinancialResult.Failure).error)
    }

    private fun preview() = (service.preview(account.id, group, PaymentMethod.PIX,
        LocalDate.of(2026, 10, 10), request()) as FinancialResult.Success).value
    private fun request() = FinancialRequest(UUID.randomUUID(), member)
    private fun accounts() = object : FinancialAccountRepository {
        override fun listForUser(userId: UUID) = listOf(account)
        override fun findById(accountId: UUID) = account.takeIf { it.id == accountId }
        override fun findByOwner(ownerUserId: UUID) = account.takeIf { it.ownerUserId == ownerUserId }
        override fun findDelegation(accountId: UUID, userId: UUID) = null
    }
    private fun groups() = object : GroupReceivablesStore {
        override fun <T> transaction(block: () -> T) = block()
        override fun lockAccount(id: UUID) = account.takeIf { it.id == id }
        override fun state(accountId: UUID, groupId: UUID) = GroupReceivablesState(accountId, groupId, true, true, true)
        override fun replay(accountId: UUID, request: FinancialRequest, digest: String) = false
        override fun configure(accountId: UUID, groupId: UUID, request: FinancialRequest, digest: String, review: GroupReceivablesReview?, at: Instant) = state(accountId, groupId)!!
    }
    private fun conditions() = object : FinancialConditions {
        override fun current(methods: Set<PaymentMethod>, at: Instant) = listOf(fee.copy(method = methods.single()))
        override fun terms(version: String, at: Instant) = null
        override fun currentTerms(at: Instant) = null
    }

    private inner class MemoryStore : RecurrenceStore {
        val rows = mutableListOf<RecurrenceAuthorization>(); val digests = mutableMapOf<UUID, String>()
        var inserts = 0; var memberActive = true
        override fun <T> transaction(block: () -> T) = block()
        override fun monthlyTerms(accountId: UUID, groupId: UUID, memberId: UUID) = MonthlyRecurrenceTerms(10_000, 10, memberActive)
        override fun webhookReady(accountId: UUID) = true
        override fun find(id: UUID) = rows.singleOrNull { it.recurrence.id == id }
        override fun live(groupId: UUID, memberId: UUID) = rows.singleOrNull { it.recurrence.groupId == groupId && it.recurrence.memberUserId == memberId && it.recurrence.status in setOf("AUTHORIZING", "ACTIVE", "STOP_PENDING") }
        override fun current(accountId: UUID, groupId: UUID, memberId: UUID) = rows.filter { it.recurrence.accountId == accountId &&
            it.recurrence.groupId == groupId && it.recurrence.memberUserId == memberId }.sortedBy { it.recurrence.status == "STOPPED" }.firstOrNull()
        override fun byActorRequest(requestId: UUID, actorUserId: UUID) = rows.singleOrNull {
            digests[requestId] != null && it.recurrence.memberUserId == actorUserId }
        override fun byRequest(accountId: UUID, requestId: UUID) = rows.singleOrNull { digests[requestId] != null }?.let { it to digests.getValue(requestId) }
        override fun insert(review: RecurrenceReview, quote: FeeQuote, payer: PaymentPayer, request: FinancialRequest, digest: String, at: Instant): RecurrenceAuthorization {
            inserts++; val id = UUID.randomUUID(); val result = RecurrenceAuthorization(PaymentRecurrence(id, review.accountId, review.groupId,
                review.memberUserId, review.method, review.baseCents, review.feesCents, review.totalCents, review.firstDueDate,
                "AUTHORIZING", null, null, null), quote, payer)
            rows += result; digests[request.requestId] = digest; return result
        }
        override fun requestStop(id: UUID, request: FinancialRequest, reason: String, digest: String, at: Instant): RecurrenceAuthorization {
            replace(id) { it.copy(recurrence = it.recurrence.copy(status = "STOP_PENDING", cutoffAt = at)) }; return find(id)!!
        }
        fun replace(id: UUID, change: (RecurrenceAuthorization) -> RecurrenceAuthorization) { val index = rows.indexOfFirst { it.recurrence.id == id }; rows[index] = change(rows[index]) }
        override fun claim(id: UUID, at: Instant, leaseUntil: Instant) = null
        override fun finish(claim: RecurrenceClaim, result: ProviderRecurrenceResult?, stopped: Boolean, at: Instant) = false
        override fun recoveryDue(at: Instant, limit: Int) = emptyList<UUID>()
        override fun active(limit: Int) = emptyList<UUID>()
        override fun cutoffCandidates(at: Instant, limit: Int) = emptyList<Pair<UUID, String>>()
        override fun materialize(recurrence: RecurrenceAuthorization, payments: List<ProviderRecurringPayment>, at: Instant) = emptyList<UUID>()
    }
    private inner class RecordingExecution : RecurrenceExecution {
        val started = mutableListOf<UUID>(); val stopped = mutableListOf<UUID>()
        override fun start(id: UUID) { started += id; store.replace(id) { it.copy(recurrence = it.recurrence.copy(status = "ACTIVE", providerSubscriptionId = "sub-test")) } }
        override fun stop(id: UUID) { stopped += id; store.replace(id) { it.copy(recurrence = it.recurrence.copy(status = "STOPPED")) } }
        override fun recoverDue() = Unit
        override fun synchronize() = Unit
        override fun enforceCutoffs() = Unit
    }
}
