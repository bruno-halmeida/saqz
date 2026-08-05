package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.SubscriptionEvent
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ListReceiptsTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val otherOwner = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val processedAt = Instant.parse("2026-07-30T12:00:00Z")

    @Test
    fun `lists confirmed payments from owner-scoped processed webhook events`() {
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
                    ownerUserId = otherOwner,
                    payload = """{"id":"evt_other","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_x","value":59.90,"subscription":"sub_other"}}""",
                ),
                event(
                    id = "evt_2",
                    payload = """{"id":"evt_2","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_2","value":39.90,"subscription":"sub_mine"}}""",
                ),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(3, receipts.size)
        assertEquals(listOf("evt_1", "evt_float", "evt_2"), receipts.map { it.asaasEventId })
        assertEquals(3_990L, receipts.first().valueCents)
        assertEquals(29L, receipts[1].valueCents)
        assertEquals("pay_1", receipts.first().asaasPaymentId)
    }

    @Test
    fun `a PAYMENT_RECEIVED-only payment still produces a receipt`() {
        // Boleto/PIX que liquida sem CONFIRMED: sem isto o app nunca via recibo e o botao
        // "Ja paguei" ficava girando contra uma lista vazia.
        val events = FakeEventStore(
            listOf(
                event(
                    id = "evt_recv",
                    type = ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED,
                    payload = """{"id":"evt_recv","event":"PAYMENT_RECEIVED","payment":{"id":"pay_boleto","value":59.90,"subscription":"sub_mine"}}""",
                ),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(1, receipts.size)
        assertEquals("pay_boleto", receipts.single().asaasPaymentId)
        assertEquals(5_990L, receipts.single().valueCents)
    }

    @Test
    fun `CONFIRMED and RECEIVED for one charge collapse into a single receipt`() {
        val events = FakeEventStore(
            listOf(
                event(
                    id = "evt_conf",
                    payload = """{"id":"evt_conf","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_same","value":59.90}}""",
                ),
                event(
                    id = "evt_recv",
                    type = ProcessAsaasWebhook.EVENT_PAYMENT_RECEIVED,
                    payload = """{"id":"evt_recv","event":"PAYMENT_RECEIVED","payment":{"id":"pay_same","value":59.90}}""",
                ),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(1, receipts.size)
        assertEquals("evt_conf", receipts.single().asaasEventId)
    }

    @Test
    fun `applies limit and offset to owner-scoped events`() {
        val events = FakeEventStore(
            listOf(
                event("evt_1", """{"payment":{"id":"pay_1","value":10.00}}"""),
                event("evt_2", """{"payment":{"id":"pay_2","value":20.00}}"""),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId, limit = 1, offset = 1)

        assertEquals(listOf("evt_2"), receipts.map { it.asaasEventId })
    }

    @Test
    fun `default limit returns the first receipt page`() {
        val events = FakeEventStore(
            (1..60).map { index ->
                event(
                    id = "evt_$index",
                    payload = """{"payment":{"id":"pay_$index","value":10.00}}""",
                )
            },
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(20, receipts.size)
        assertEquals("evt_1", receipts.first().asaasEventId)
        assertEquals("evt_20", receipts.last().asaasEventId)
    }

    @Test
    fun `rejects invalid pagination with a specific exception`() {
        val listReceipts = ListReceipts(FakeEventStore(emptyList()))

        assertFailsWith<InvalidReceiptPaginationException> { listReceipts.execute(ownerId, limit = 0) }
        assertFailsWith<InvalidReceiptPaginationException> { listReceipts.execute(ownerId, limit = -1) }
        assertFailsWith<InvalidReceiptPaginationException> { listReceipts.execute(ownerId, offset = -1) }
    }

    @Test
    fun `includes an upgrade receipt without subscription id in payload`() {
        val events = FakeEventStore(
            listOf(
                event(
                    id = "evt_upgrade",
                    payload = """{"id":"evt_upgrade","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_upgrade","value":79.90}}""",
                ),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(listOf("evt_upgrade"), receipts.map { it.asaasEventId })
        assertEquals("pay_upgrade", receipts.single().asaasPaymentId)
        assertEquals(7_990L, receipts.single().valueCents)
    }

    @Test
    fun `returns empty when owner has no events`() {
        assertTrue(ListReceipts(FakeEventStore(emptyList())).execute(ownerId).isEmpty())
    }

    @Test
    fun `does not leak events between owners`() {
        val events = FakeEventStore(
            listOf(
                event(
                    id = "evt_other",
                    ownerUserId = otherOwner,
                    payload = """{"payment":{"id":"pay_x","value":10.00}}""",
                ),
            ),
        )

        assertTrue(ListReceipts(events).execute(ownerId).isEmpty())
        assertEquals(listOf("evt_other"), ListReceipts(events).execute(otherOwner).map { it.asaasEventId })
    }

    private fun event(
        id: String,
        payload: String,
        ownerUserId: UUID = ownerId,
        type: String = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
    ) = StoredEvent(
        event = SubscriptionEvent(
            id = UUID.randomUUID(),
            asaasEventId = id,
            type = type,
            payload = payload,
            processedAt = processedAt,
        ),
        ownerUserId = ownerUserId,
    )

    private data class StoredEvent(
        val event: SubscriptionEvent,
        val ownerUserId: UUID,
    )

    private class FakeEventStore(private val rows: List<StoredEvent>) : SubscriptionEventStore {
        override fun tryInsert(
            id: UUID,
            asaasEventId: String,
            type: String,
            payload: String,
            now: Instant,
            ownerUserId: UUID?,
        ) = true

        override fun markProcessed(asaasEventId: String, processedAt: Instant) = Unit

        override fun exists(asaasEventId: String) = rows.any { it.event.asaasEventId == asaasEventId }

        override fun listProcessedByTypesForOwner(
            types: Collection<String>,
            ownerUserId: UUID,
            limit: Int,
            offset: Int,
        ) = rows
            .filter { it.ownerUserId == ownerUserId && it.event.type in types }
            .drop(offset)
            .take(limit)
            .map { it.event }
    }
}
