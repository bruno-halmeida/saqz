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
import kotlin.test.assertFalse
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
        assertEquals(ownerId, events.rows.getValue("evt_pay_1").ownerUserId)
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
    fun `PAYMENT_RECEIVED activates a past-due subscription just like PAYMENT_CONFIRMED`() {
        val result = useCase.execute(
            token,
            command(
                eventId = "evt_recv",
                type = ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED,
                paymentId = "pay_first",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertEquals(periodEnd, sub.currentPeriodEnd)
        assertNull(sub.pastDueSince)
        assertEquals(fixedNow, sub.firstConfirmedAt)
        assertEquals("pay_first", sub.lastConfirmedPaymentId)
        assertEquals(ownerId, events.rows.getValue("evt_recv").ownerUserId)
    }

    @Test
    fun `CONFIRMED and RECEIVED for one charge confirm it once`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_pair_conf", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED, paymentId = "pay_pair"),
        )
        val afterFirst = subscriptions.get("sub_123").currentPeriodEnd

        // Evento irmao: id de evento diferente, entao passa pela trava de asaasEventId.
        val result = useCase.execute(
            token,
            command(eventId = "evt_pair_recv", type = ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED, paymentId = "pay_pair"),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), afterFirst)
        assertEquals(afterFirst, subscriptions.get("sub_123").currentPeriodEnd)
    }

    @Test
    fun `next cycle charge still renews after the previous pair was collapsed`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                lastConfirmedPaymentId = "pay_july",
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_august", type = ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED, paymentId = "pay_august"),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), sub.currentPeriodEnd)
        assertEquals("pay_august", sub.lastConfirmedPaymentId)
    }

    @Test
    fun `RECEIVED sibling of an upgrade charge is not billed as a renewal`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                plan = Plan.TITULAR,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upgrade_pair",
            ),
        )

        useCase.execute(token, upgradeCommand("evt_upg_conf", ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED))
        // applyPendingUpgrade limpou pendingUpgradeChargeId, entao o irmao so resolve pela
        // coluna lastConfirmedPaymentId — antes disso caia em SubscriptionNotReady (503 eterno).
        val result = useCase.execute(token, upgradeCommand("evt_upg_recv", ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED))

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(Plan.ORGANIZADOR, sub.plan)
        assertEquals(periodEnd, sub.currentPeriodEnd)
        assertEquals(listOf("sub_123" to Plan.ORGANIZADOR.monthlyPriceCents), gateway.updates)
    }

    @Test
    fun `refund revokes access immediately instead of keeping the paid cycle`() {
        val futureEnd = Instant.parse("2026-08-30T00:00:00Z")
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-07-01T00:00:00Z"),
                currentPeriodEnd = futureEnd,
                lastConfirmedPaymentId = "pay_refunded",
            ),
        )

        val result = useCase.execute(
            token,
            command(eventId = "evt_refund", type = ProcessAsaasWebhook.EVENT_PAYMENT_REFUNDED, paymentId = "pay_refunded"),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.CANCELED, sub.status)
        assertEquals(fixedNow, sub.canceledAt)
        // Periodo cortado para agora: sem isto CANCELED seguiria dando acesso ate 30/08.
        assertEquals(fixedNow, sub.currentPeriodEnd)
        assertFalse(sub.isEntitlingAt(fixedNow))
    }

    @Test
    fun `cash payment undone also revokes`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-07-01T00:00:00Z"),
                currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
                lastConfirmedPaymentId = "pay_cash",
            ),
        )

        useCase.execute(
            token,
            command(
                eventId = "evt_cash_undone",
                type = ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED_IN_CASH_UNDONE,
                paymentId = "pay_cash",
            ),
        )

        assertFalse(subscriptions.get("sub_123").isEntitlingAt(fixedNow))
    }

    @Test
    fun `refund of an older charge does not revoke the cycle a renewal already paid`() {
        val futureEnd = Instant.parse("2026-08-30T00:00:00Z")
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"),
                currentPeriodEnd = futureEnd,
                lastConfirmedPaymentId = "pay_agosto",
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_refund_old", type = ProcessAsaasWebhook.EVENT_PAYMENT_REFUNDED, paymentId = "pay_junho"),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertEquals(futureEnd, sub.currentPeriodEnd)
    }

    @Test
    fun `plain cancellation keeps access until the end of the paid cycle`() {
        val futureEnd = Instant.parse("2026-08-30T00:00:00Z")
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-07-01T00:00:00Z"),
                currentPeriodEnd = futureEnd,
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_sub_del", type = ProcessAsaasWebhook.EVENT_SUBSCRIPTION_DELETED),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.CANCELED, sub.status)
        // Diferenca deliberada para o estorno: o ciclo pago e preservado.
        assertEquals(futureEnd, sub.currentPeriodEnd)
        assertTrue(sub.isEntitlingAt(fixedNow))
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
    fun `stale PAYMENT_OVERDUE after a newer PAYMENT_CONFIRMED does not regress status`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_pay_renew", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )
        val afterConfirm = subscriptions.get("sub_123")
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), afterConfirm.currentPeriodEnd)

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_stale_od",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE,
                asaasSubscriptionId = "sub_123",
                asaasPaymentId = null,
                rawPayload = """{"id":"evt_stale_od","event":"PAYMENT_OVERDUE","payment":{"dueDate":"2026-07-30"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertNull(sub.pastDueSince)
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), sub.currentPeriodEnd)
    }

    @Test
    fun `PAYMENT_OVERDUE with dueDate equal to the current period end still marks past due`() {
        // The normal renewal invoice's dueDate IS the currentPeriodEnd set at the prior confirm —
        // only a dueDate strictly BEFORE it is stale.
        subscriptions.save(baseSubscription().copy(status = SubscriptionStatus.ACTIVE, pastDueSince = null))

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_od_current_cycle",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE,
                asaasSubscriptionId = "sub_123",
                asaasPaymentId = null,
                rawPayload = """{"id":"evt_od_current_cycle","event":"PAYMENT_OVERDUE","payment":{"dueDate":"2026-07-30"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.PAST_DUE, sub.status)
        assertEquals(fixedNow, sub.pastDueSince)
    }

    @Test
    fun `PAYMENT_OVERDUE for a newer invoice still marks past due`() {
        subscriptions.save(baseSubscription().copy(status = SubscriptionStatus.ACTIVE, pastDueSince = null))

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_od_fresh",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE,
                asaasSubscriptionId = "sub_123",
                asaasPaymentId = null,
                rawPayload = """{"id":"evt_od_fresh","event":"PAYMENT_OVERDUE","payment":{"dueDate":"2026-08-30"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.PAST_DUE, sub.status)
        assertEquals(fixedNow, sub.pastDueSince)
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
    fun `masks a full card number in the stored raw payload, PCI defense in depth`() {
        val eventId = "evt_pan_guard"
        useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = eventId,
                eventType = "PAYMENT_CREATED",
                asaasSubscriptionId = null,
                asaasPaymentId = null,
                rawPayload = """{"id":"$eventId","event":"PAYMENT_CREATED","creditCard":{"number":"4111111111111111"}}""",
            ),
        )

        val stored = events.rows.getValue(eventId).payload
        assertTrue(stored.contains("\"number\":\"************1111\""))
        assertFalse(stored.contains("4111111111111111"))
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
    fun `pending plan is applied on any renewal confirmation regardless of wall clock`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
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
        assertEquals(Plan.ILIMITADO, sub.plan)
        assertNull(sub.pendingPlan)
        assertNull(sub.pendingPlanEffectiveAt)
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
    fun `missing subscription is audited without 503 so Asaas does not interrupt the queue`() {
        val result = useCase.execute(
            token,
            command(
                eventId = "evt_orphan",
                type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
                subscriptionId = "sub_missing",
            ),
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        assertEquals(fixedNow, events.rows.getValue("evt_orphan").processedAt)
        assertNull(events.rows.getValue("evt_orphan").ownerUserId)
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
                asaasPaymentId = null,
                rawPayload = """{"id":"evt_no_sub","event":"PAYMENT_CONFIRMED"}""",
            ),
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        assertEquals(fixedNow, events.rows.getValue("evt_no_sub").processedAt)
        assertNull(events.rows.getValue("evt_no_sub").ownerUserId)
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.get("sub_123").status)
    }

    @Test
    fun `unresolved payment is claimed so a later retry does not 503 the queue`() {
        val cmd = command(
            eventId = "evt_race",
            type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
            subscriptionId = "sub_new",
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, useCase.execute(token, cmd))
        assertEquals(fixedNow, events.rows.getValue("evt_race").processedAt)

        subscriptions.save(
            baseSubscription().copy(
                asaasSubscriptionId = "sub_new",
                status = SubscriptionStatus.PAST_DUE,
            ),
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, useCase.execute(token, cmd))
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.get("sub_new").status)
    }

    @Test
    fun `PAYMENT_CONFIRMED on pending upgrade charge applies plan without advancing period`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                plan = Plan.TITULAR,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upgrade_1",
            ),
        )

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_upg",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
                asaasSubscriptionId = null,
                asaasPaymentId = "pay_upgrade_1",
                rawPayload = """{"id":"evt_upg","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_upgrade_1"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(Plan.ORGANIZADOR, sub.plan)
        assertNull(sub.pendingUpgradePlan)
        assertNull(sub.pendingUpgradeChargeId)
        assertEquals(periodEnd, sub.currentPeriodEnd)
        assertEquals(listOf("sub_123" to Plan.ORGANIZADOR.monthlyPriceCents), gateway.updates)
        assertEquals(ownerId, events.rows.getValue("evt_upg").ownerUserId)

        val receipts = ListReceipts(events).execute(ownerId)
        assertEquals("evt_upg", receipts.single().asaasEventId)
        assertEquals("pay_upgrade_1", receipts.single().asaasPaymentId)
    }

    @Test
    fun `PAYMENT_OVERDUE on pending upgrade charge clears pending without marking past due`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                plan = Plan.TITULAR,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upgrade_od",
            ),
        )

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_upg_od",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE,
                asaasSubscriptionId = null,
                asaasPaymentId = "pay_upgrade_od",
                rawPayload = """{"id":"evt_upg_od","event":"PAYMENT_OVERDUE","payment":{"id":"pay_upgrade_od"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertNull(sub.pastDueSince)
        assertEquals(Plan.TITULAR, sub.plan)
        assertNull(sub.pendingUpgradePlan)
        assertNull(sub.pendingUpgradeChargeId)
    }

    @Test
    fun `redelivery of processed upgrade event is accepted without SubscriptionNotReady`() {
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                plan = Plan.TITULAR,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upgrade_re",
            ),
        )
        val cmd = AsaasWebhookCommand(
            asaasEventId = "evt_upg_re",
            eventType = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
            asaasSubscriptionId = null,
            asaasPaymentId = "pay_upgrade_re",
            rawPayload = """{"id":"evt_upg_re","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_upgrade_re"}}""",
        )
        assertEquals(ProcessAsaasWebhookResult.Accepted, useCase.execute(token, cmd))
        assertEquals(Plan.ORGANIZADOR, subscriptions.get("sub_123").plan)

        // Charge id cleared — redelivery must still Accepted (not 503 forever).
        assertEquals(ProcessAsaasWebhookResult.Accepted, useCase.execute(token, cmd))
        assertEquals(Plan.ORGANIZADOR, subscriptions.get("sub_123").plan)
    }

    @Test
    fun `recurring PAYMENT_CONFIRMED with no pending upgrade still advances period after canceledAt is set`() {
        // Paid before cancel, but the webhook only arrives after CancelSubscription already set
        // canceledAt locally (SUBSCRIPTION_DELETED hasn't landed yet) — this is a legitimate
        // already-paid period and must not be dropped just because canceledAt is non-null.
        val original = Instant.parse("2026-01-01T00:00:00Z")
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = original,
                canceledAt = Instant.parse("2026-07-25T00:00:00Z"),
            ),
        )

        useCase.execute(
            token,
            command(eventId = "evt_pay_after_cancel", type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED),
        )

        val sub = subscriptions.get("sub_123")
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), sub.currentPeriodEnd)
        assertEquals(original, sub.firstConfirmedAt)
    }

    @Test
    fun `late payment on an abandoned pending upgrade charge after cancel is accepted without applying anything`() {
        // CancelSubscription sets canceledAt but keeps status/pendingUpgrade fields — there is no
        // gateway call that cancels this one-off charge, so it can still be paid afterward.
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                canceledAt = Instant.parse("2026-07-25T00:00:00Z"),
                plan = Plan.TITULAR,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upgrade_after_cancel",
            ),
        )

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_upg_after_cancel",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
                asaasSubscriptionId = null,
                asaasPaymentId = "pay_upgrade_after_cancel",
                rawPayload = """{"id":"evt_upg_after_cancel","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_upgrade_after_cancel"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(Plan.TITULAR, sub.plan)
        assertEquals(Plan.ORGANIZADOR, sub.pendingUpgradePlan)
        assertEquals("pay_upgrade_after_cancel", sub.pendingUpgradeChargeId)
        assertTrue(gateway.updates.isEmpty())
    }

    @Test
    fun `PAYMENT_OVERDUE on abandoned pending upgrade charge after cancel preserves the mapping`() {
        // Same canceledAt gap as the CONFIRMED case: clearing the mapping here would recreate the
        // SubscriptionNotReady bug if the charge is paid later.
        subscriptions.save(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                canceledAt = Instant.parse("2026-07-25T00:00:00Z"),
                plan = Plan.TITULAR,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upgrade_od_after_cancel",
            ),
        )

        val result = useCase.execute(
            token,
            AsaasWebhookCommand(
                asaasEventId = "evt_upg_od_after_cancel",
                eventType = ProcessAsaasWebhook.EVENT_PAYMENT_OVERDUE,
                asaasSubscriptionId = null,
                asaasPaymentId = "pay_upgrade_od_after_cancel",
                rawPayload = """{"id":"evt_upg_od_after_cancel","event":"PAYMENT_OVERDUE","payment":{"id":"pay_upgrade_od_after_cancel"}}""",
            ),
        )

        assertEquals(ProcessAsaasWebhookResult.Accepted, result)
        val sub = subscriptions.get("sub_123")
        assertEquals(Plan.TITULAR, sub.plan)
        assertEquals(Plan.ORGANIZADOR, sub.pendingUpgradePlan)
        assertEquals("pay_upgrade_od_after_cancel", sub.pendingUpgradeChargeId)
    }

    private fun baseSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_123",
        asaasSubscriptionId = "sub_123",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = periodEnd,
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = Instant.parse("2026-07-20T00:00:00Z"),
    )

    private fun command(
        eventId: String,
        type: String,
        subscriptionId: String = "sub_123",
        paymentId: String? = null,
    ) = AsaasWebhookCommand(
        asaasEventId = eventId,
        eventType = type,
        asaasSubscriptionId = subscriptionId,
        asaasPaymentId = paymentId,
        rawPayload = """{"id":"$eventId","event":"$type"}""",
    )

    /** Cobranca avulsa de upgrade: o payload do Asaas nao traz `payment.subscription`. */
    private fun upgradeCommand(
        eventId: String,
        type: String,
        paymentId: String = "pay_upgrade_pair",
    ) = AsaasWebhookCommand(
        asaasEventId = eventId,
        eventType = type,
        asaasSubscriptionId = null,
        asaasPaymentId = paymentId,
        rawPayload = """{"id":"$eventId","event":"$type","payment":{"id":"$paymentId"}}""",
    )

    private class InMemorySubscriptionEventStore : SubscriptionEventStore {
        data class Row(
            val id: UUID,
            val asaasEventId: String,
            val type: String,
            val payload: String,
            val createdAt: Instant,
            val ownerUserId: UUID?,
            var processedAt: Instant? = null,
        )

        val rows = linkedMapOf<String, Row>()

        override fun tryInsert(
            id: UUID,
            asaasEventId: String,
            type: String,
            payload: String,
            now: Instant,
            ownerUserId: UUID?,
        ): Boolean {
            if (rows.containsKey(asaasEventId)) return false
            rows[asaasEventId] = Row(id, asaasEventId, type, payload, now, ownerUserId)
            return true
        }

        override fun markProcessed(asaasEventId: String, processedAt: Instant) {
            rows.getValue(asaasEventId).processedAt = processedAt
        }

        override fun exists(asaasEventId: String): Boolean = rows.containsKey(asaasEventId)

        override fun listProcessedByTypesForOwner(
            types: Collection<String>,
            ownerUserId: UUID,
            limit: Int,
            offset: Int,
        ) = rows.values
            .filter { it.type in types && it.ownerUserId == ownerUserId && it.processedAt != null }
            .sortedByDescending { it.processedAt }
            .drop(offset)
            .take(limit)
            .map {
                br.com.saqz.subscriptions.domain.SubscriptionEvent(
                    id = it.id,
                    asaasEventId = it.asaasEventId,
                    type = it.type,
                    payload = it.payload,
                    processedAt = it.processedAt,
                )
            }
    }

    private class InMemorySubscriptionRepository : SubscriptionRepository {
        private val byAsaasId = linkedMapOf<String, Subscription>()
        private val byOwnerId = linkedMapOf<UUID, Subscription>()
        private val byUpgradeCharge = linkedMapOf<String, Subscription>()
        private val byConfirmedPayment = linkedMapOf<String, Subscription>()

        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? =
            byAsaasId[asaasSubscriptionId]

        override fun findByOwnerUserId(ownerUserId: UUID): Subscription? = byOwnerId[ownerUserId]

        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID): Subscription? = byOwnerId[ownerUserId]

        override fun findByPendingUpgradeChargeId(chargeId: String): Subscription? =
            byUpgradeCharge[chargeId]

        override fun findByLastConfirmedPaymentId(paymentId: String): Subscription? =
            byConfirmedPayment[paymentId]

        override fun lockOwner(ownerUserId: UUID) = Unit

        override fun insert(subscription: Subscription) = save(subscription)

        override fun save(subscription: Subscription) {
            byAsaasId.values.removeIf { it.ownerUserId == subscription.ownerUserId }
            byUpgradeCharge.entries.removeIf { it.value.ownerUserId == subscription.ownerUserId }
            byConfirmedPayment.entries.removeIf { it.value.ownerUserId == subscription.ownerUserId }
            byAsaasId[subscription.asaasSubscriptionId] = subscription
            byOwnerId[subscription.ownerUserId] = subscription
            subscription.pendingUpgradeChargeId?.let { byUpgradeCharge[it] = subscription }
            subscription.lastConfirmedPaymentId?.let { byConfirmedPayment[it] = subscription }
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
            creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?,
            remoteIp: String?,
        ): AsaasSubscriptionCreation = error("unused")

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

        override fun regeneratePixPayload(asaasChargeId: String): PixCode = error("unused")

        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String? = null

        override fun findPaymentInvoiceUrl(asaasPaymentId: String): String? = null
        override fun findPayment(asaasPaymentId: String) = null
    }
}
