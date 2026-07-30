package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessAsaasWebhookTest {
    private val fixedNow = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val token = "webhook-token-test-not-a-real-secret"
    private lateinit var events: InMemorySubscriptionEventStore
    private lateinit var subscriptions: InMemorySubscriptionRepository
    private lateinit var gateway: RecordingAsaasGateway
    private lateinit var useCase: ProcessAsaasWebhook

    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val periodEnd = Instant.parse("2026-07-30T00:00:00Z")

    @BeforeEach
    fun setUp() {
        events = InMemorySubscriptionEventStore()
        subscriptions = InMemorySubscriptionRepository()
        gateway = RecordingAsaasGateway()
        useCase = ProcessAsaasWebhook(
            expectedToken = token,
            events = events,
            subscriptions = subscriptions,
            asaasGateway = gateway,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock = clock,
            newEventId = { UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb") },
        )
        subscriptions.save(baseSubscription())
    }

    @Test
    fun `rejects invalid webhook token without recording event or mutating subscription`() {
        val result = useCase.execute(
            "wrong-token",
            command(
                eventId = "evt_1",
                type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Unauthorized, result)
        assertTrue(events.rows.isEmpty())
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.get("sub_123").status)
        assertTrue(gateway.updates.isEmpty())
    }

    @Test
    fun `rejects missing webhook token`() {
        val result = useCase.execute(
            null,
            command(eventId = "evt_missing", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )
        assertEquals(ProcessAsaasWebhookResult.Unauthorized, result)
        assertTrue(events.rows.isEmpty())
    }

    @Test
    fun `first PAYMENT_CONFIRMED activates without advancing period set at create`() {
        val result = useCase.execute(
            token,
            command(eventId = "evt_pay_1", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertEquals(periodEnd, sub.currentPeriodEnd)
        assertNull(sub.pastDueSince)
        assertEquals(fixedNow, sub.firstConfirmedAt)
        assertEquals(fixedNow, events.rows.getValue("evt_pay_1").processedAt)
    }

    @Test
    fun `renewal PAYMENT_CONFIRMED advances period and keeps firstConfirmedAt`() {
        val original = Instant.parse("2026-01-01T00:00:00Z")
        subscriptions.save(baseSubscription().copy(firstConfirmedAt = original, status = SubscriptionStatus.ACTIVE, pastDueSince = null))

        useCase.execute(
            token,
            command(eventId = "evt_pay_renew", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), sub.currentPeriodEnd)
        assertEquals(original, sub.firstConfirmedAt)
    }

    @Test
    fun `same asaasEventId twice does not reapply effects`() {
        val first = command(eventId = "evt_dup", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED)
        useCase.execute(token, first)
        val afterFirst = subscriptions.get("sub_123")

        subscriptions.save(
            afterFirst.copy(
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = fixedNow,
                currentPeriodEnd = periodEnd,
            ),
        )

        val second = useCase.execute(token, first)
        assertEquals(ProcessAsaasWebhookResult.Accepted, second)
        val afterSecond = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.PAST_DUE, afterSecond.status)
        assertEquals(periodEnd, afterSecond.currentPeriodEnd)
        assertEquals(1, events.rows.size)
        assertEquals(0, gateway.updates.size)
    }

    @Test
    fun `PAYMENT_OVERDUE sets past due once`() {
        subscriptions.save(baseSubscription().copy(status = SubscriptionStatus.ACTIVE, pastDueSince = null))

        useCase.execute(
            token,
            command(eventId = "evt_od_1", type = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE),
        )
        val first = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.PAST_DUE, first.status)
        assertEquals(fixedNow, first.pastDueSince)

        val later = Instant.parse("2026-08-01T00:00:00Z")
        val laterClock = Clock.fixed(later, ZoneOffset.UTC)
        val laterUseCase = ProcessAsaasWebhook(
            expectedToken = token,
            events = events,
            subscriptions = subscriptions,
            asaasGateway = gateway,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock = laterClock,
        )
        laterUseCase.execute(
            token,
            command(eventId = "evt_od_2", type = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE),
        )
        assertEquals(fixedNow, subscriptions.get("sub_123").pastDueSince)
    }

    @Test
    fun `SUBSCRIPTION_DELETED cancels once`() {
        subscriptions.save(baseSubscription().copy(status = SubscriptionStatus.ACTIVE, canceledAt = null))

        useCase.execute(
            token,
            command(
                eventId = "evt_del_1",
                type = ProcessAsaasWebhook.EVENT_SUBSCRIPTION_DELETED,
                subscriptionId = "sub_123",
            ),
        )
        val first = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.CANCELED, first.status)
        assertEquals(fixedNow, first.canceledAt)

        val later = Instant.parse("2026-08-05T00:00:00Z")
        ProcessAsaasWebhook(
            expectedToken = token,
            events = events,
            subscriptions = subscriptions,
            asaasGateway = gateway,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock = Clock.fixed(later, ZoneOffset.UTC),
        ).execute(
            token,
            command(eventId = "evt_del_2", type = ProcessAsaasWebhook.EVENT_SUBSCRIPTION_DELETED),
        )
        assertEquals(fixedNow, subscriptions.get("sub_123").canceledAt)
    }

    @Test
    fun `unknown event is audited without domain effect or error`() {
        val before = subscriptions.get("sub_123")
        val result = useCase.execute(
            token,
            command(eventId = "evt_unknown", type = "PAYMENT_CREATED"),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        assertEquals(before, subscriptions.get("sub_123"))
        assertEquals("PAYMENT_CREATED", events.rows.getValue("evt_unknown").type)
        assertEquals(fixedNow, events.rows.getValue("evt_unknown").processedAt)
        assertTrue(gateway.updates.isEmpty())
    }

    @Test
    fun `coupon cycles decrement and push full price when reaching zero`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                plan = Plan.ORGANIZADOR,
                cycle = SubscriptionCycle.MONTHLY,
                couponCyclesRemaining = 1,
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_coupon_0", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        val sub = subscriptions.get("sub_123")
        assertNull(sub.couponCyclesRemaining)
        assertEquals(listOf("sub_123" to Plan.ORGANIZADOR.monthlyPriceCents), gateway.updates)
    }

    @Test
    fun `coupon cycles above one decrement without calling Asaas`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                couponCyclesRemaining = 3,
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_coupon_2", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        assertEquals(2, subscriptions.get("sub_123").couponCyclesRemaining)
        assertTrue(gateway.updates.isEmpty())
    }

    @Test
    fun `pending plan is applied on renewal when effective at has passed`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                plan = Plan.TITULAR,
                pendingPlan = Plan.ILIMITADO,
                pendingPlanEffectiveAt = Instant.parse("2026-07-01T00:00:00Z"),
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_pending", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(Plan.ILIMITADO, sub.plan)
        assertNull(sub.pendingPlan)
        assertNull(sub.pendingPlanEffectiveAt)
    }

    @Test
    fun `pending plan is not applied before effective at`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                plan = Plan.TITULAR,
                pendingPlan = Plan.ILIMITADO,
                pendingPlanEffectiveAt = Instant.parse("2026-08-15T00:00:00Z"),
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_pending_later", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(Plan.TITULAR, sub.plan)
        assertEquals(Plan.ILIMITADO, sub.pendingPlan)
        assertEquals(Instant.parse("2026-08-15T00:00:00Z"), sub.pendingPlanEffectiveAt)
    }

    @Test
    fun `coupon zero uses price of plan after pending plan is applied`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.ANNUAL,
                pendingPlan = Plan.ILIMITADO,
                pendingPlanEffectiveAt = Instant.parse("2026-07-01T00:00:00Z"),
                couponCyclesRemaining = 1,
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_combo", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        assertEquals(Plan.ILIMITADO, subscriptions.get("sub_123").plan)
        assertEquals(listOf("sub_123" to Plan.ILIMITADO.annualPriceCents), gateway.updates)
    }

    @Test
    fun `PAYMENT_CONFIRMED does not resurrect a canceled subscription`() {
        val canceledAt = Instant.parse("2026-07-25T00:00:00Z")
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = canceledAt,
                pastDueSince = null,
                couponCyclesRemaining = 1,
                currentPeriodEnd = periodEnd,
            ),
        )

        val result = useCase.execute(
            token,
            command(eventId = "evt_late_pay", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.CANCELED, sub.status)
        assertEquals(canceledAt, sub.canceledAt)
        assertEquals(periodEnd, sub.currentPeriodEnd)
        assertEquals(1, sub.couponCyclesRemaining)
        assertTrue(gateway.updates.isEmpty())
        assertEquals(fixedNow, events.rows.getValue("evt_late_pay").processedAt)
    }

    @Test
    fun `PAYMENT_OVERDUE does not mutate a canceled subscription`() {
        val canceledAt = Instant.parse("2026-07-25T00:00:00Z")
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = canceledAt,
                pastDueSince = null,
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_late_od", type = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.CANCELED, sub.status)
        assertNull(sub.pastDueSince)
        assertEquals(canceledAt, sub.canceledAt)
    }

    @Test
    fun `missing subscription returns not ready without claiming the event`() {
        val result = useCase.execute(
            token,
            command(
                eventId = "evt_orphan",
                type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
                subscriptionId = "sub_missing",
            ),
        )
        assertEquals(ProcessAsaasWebhookResult.SubscriptionNotReady, result)
        assertTrue(events.rows.isEmpty())
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.get("sub_123").status)
    }

    @Test
    fun `domain event without subscription id is audited without not ready`() {
        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_no_sub",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
                asaasSubscriptionId = null,
                rawPayload = """{"id":"evt_no_sub","event":"PAYMENT_CONFIRMED"}""",
            ),
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        assertEquals(fixedNow, events.rows.getValue("evt_no_sub").processedAt)
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.get("sub_123").status)
    }

    @Test
    fun `missing subscription later becomes processable on retry`() {
        val cmd = command(
            eventId = "evt_race",
            type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
            subscriptionId = "sub_new",
        )
        assertEquals(ProcessAsaasWebhookResult.SubscriptionNotReady, useCase.execute(token, cmd))
        assertTrue(events.rows.isEmpty())

        subscriptions.save(
            baseSubscription().copy(
                asaasSubscriptionId = "sub_new",
                status = SubscriptionStatus.PAST_DUE,
            ),
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, useCase.execute(token, cmd))
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.get("sub_new").status)
        assertEquals(fixedNow, events.rows.getValue("evt_race").processedAt)
    }

    private fun baseSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_123",
        asaasSubscriptionId = "sub_123",
        currentPeriodEnd = periodEnd,
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = Instant.parse("2026-07-20T00:00:00Z"),
    )

    private fun command(
        eventId: String,
        type: String,
        subscriptionId: String = "sub_123",
    ) = AsaasWebhookCommand(
        asaasEventId = eventId,
        eventType = type,
        asaasSubscriptionId = subscriptionId,
        rawPayload = """{"id":"$eventId","event":"$type"}""",
    )

    private class InMemorySubscriptionEventStore : SubscriptionEventStore {
        data class Row(
            val id: UUID,
            val type: String,
            val payload: String,
            val createdAt: Instant,
            var processedAt: Instant? = null,
        )

        val rows = linkedMapOf<String, Row>()

        override fun tryInsert(
            id: UUID,
            asaasEventId: String,
            type: String,
            payload: String,
            now: Instant,
        ): Boolean {
            if (rows.containsKey(asaasEventId)) return false
            rows[asaasEventId] = Row(id, type, payload, now)
            return true
        }

        override fun markProcessed(asaasEventId: String, processedAt: Instant) {
            rows.getValue(asaasEventId).processedAt = processedAt
        }

        override fun listProcessedByType(type: String) = emptyList<br.com.saqz.subscriptions.domain.SubscriptionEvent>()
    }

    private class InMemorySubscriptionRepository : SubscriptionRepository {
        private val byAsaasId = linkedMapOf<String, Subscription>()
        private val byOwnerId = linkedMapOf<UUID, Subscription>()

        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? =
            byAsaasId[asaasSubscriptionId]

        override fun findByOwnerUserId(ownerUserId: UUID): Subscription? = byOwnerId[ownerUserId]

        override fun insert(subscription: Subscription) = save(subscription)

        override fun save(subscription: Subscription) {
            byAsaasId[subscription.asaasSubscriptionId] = subscription
            byOwnerId[subscription.ownerUserId] = subscription
        }

        fun get(asaasSubscriptionId: String): Subscription = byAsaasId.getValue(asaasSubscriptionId)
    }

    private class RecordingAsaasGateway : AsaasGateway {
        val updates = mutableListOf<Pair<String, Long>>()
        private val calls = AtomicInteger(0)

        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String): String =
            error("unused")

        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
        ): String = error("unused")

        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) {
            calls.incrementAndGet()
            updates += asaasSubscriptionId to valueCents
        }

        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")

        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ): String = error("unused")

        override fun regeneratePixPayload(asaasChargeId: String): String = error("unused")

        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String? = null

        override fun findPaymentInvoiceUrl(asaasPaymentId: String): String? = null
    }
}
