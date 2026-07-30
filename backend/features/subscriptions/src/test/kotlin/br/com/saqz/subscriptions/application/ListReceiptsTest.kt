package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionEvent
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListReceiptsTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val otherOwner = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val processedAt = Instant.parse("2026-07-30T12:00:00Z")

    @Test
    fun `lists confirmed payments from processed webhook events for owner subscription only`() {
        val subscriptions = FakeSubscriptionRepository()
        subscriptions.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_mine",
                currentPeriodEnd = processedAt,
                status = SubscriptionStatus.ACTIVE,
            ),
        )
        val events = FakeEventStore(
            listOf(
                event(
                    id = "evt_1",
                    payload = """{"id":"evt_1","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_1","value":39.90,"subscription":"sub_mine","confirmedDate":"2026-07-30"}}""",
                ),
                event(
                    id = "evt_float",
                    payload = """{"id":"evt_float","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_float","value":0.29,"subscription":"sub_mine"}}""",
                ),
                event(
                    id = "evt_other",
                    payload = """{"id":"evt_other","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_x","value":59.90,"subscription":"sub_other"}}""",
                ),
                event(
                    id = "evt_2",
                    payload = """{"id":"evt_2","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_2","value":39.90,"subscription":"sub_mine"}}""",
                ),
            ),
        )

        val receipts = ListReceipts(subscriptions, events).execute(ownerId)

        assertEquals(3, receipts.size)
        assertEquals(listOf("evt_1", "evt_float", "evt_2"), receipts.map { it.asaasEventId })
        assertEquals(3_990L, receipts.first().valueCents)
        assertEquals(29L, receipts[1].valueCents)
        assertEquals("pay_1", receipts.first().asaasPaymentId)
    }

    @Test
    fun `returns empty list when owner has no subscription`() {
        assertTrue(ListReceipts(FakeSubscriptionRepository(), FakeEventStore(emptyList())).execute(ownerId).isEmpty())
    }

    @Test
    fun `returns empty when events exist only for another owner`() {
        val subscriptions = FakeSubscriptionRepository()
        subscriptions.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_mine",
                currentPeriodEnd = processedAt,
            ),
        )
        val events = FakeEventStore(
            listOf(
                event(
                    id = "evt_x",
                    payload = """{"payment":{"id":"pay_x","value":10.00,"subscription":"sub_other"}}""",
                ),
            ),
        )
        assertTrue(ListReceipts(subscriptions, events).execute(ownerId).isEmpty())
        assertTrue(ListReceipts(subscriptions, events).execute(otherOwner).isEmpty())
    }

    private fun event(id: String, payload: String) = SubscriptionEvent(
        id = UUID.randomUUID(),
        asaasEventId = id,
        type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
        payload = payload,
        processedAt = processedAt,
    )

    private class FakeSubscriptionRepository : SubscriptionRepository {
        private val byOwner = linkedMapOf<UUID, Subscription>()
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) {
            byOwner[subscription.ownerUserId] = subscription
        }
    }

    private class FakeEventStore(private val rows: List<SubscriptionEvent>) : SubscriptionEventStore {
        override fun tryInsert(id: UUID, asaasEventId: String, type: String, payload: String, now: Instant) = true
        override fun markProcessed(asaasEventId: String, processedAt: Instant) = Unit
        override fun exists(asaasEventId: String) = rows.any { it.asaasEventId == asaasEventId }
        override fun listProcessedByType(type: String) = rows.filter { it.type == type }
    }
}
