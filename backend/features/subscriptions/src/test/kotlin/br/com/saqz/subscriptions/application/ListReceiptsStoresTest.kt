package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.SubscriptionEvent
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListReceiptsStoresTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val processedAt = Instant.parse("2026-10-18T12:00:00Z")

    private class Events(private val events: List<SubscriptionEvent>) : SubscriptionEventStore {
        var lastLimit: Int? = null
        var lastOffset: Int? = null
        override fun tryInsert(
            id: UUID,
            asaasEventId: String,
            type: String,
            payload: String,
            now: Instant,
            ownerUserId: UUID?,
        ) = true

        override fun markProcessed(asaasEventId: String, processedAt: Instant) = Unit
        override fun exists(asaasEventId: String) = false
        override fun listProcessedByTypesForOwner(
            types: Collection<String>,
            ownerUserId: UUID,
            limit: Int,
            offset: Int,
        ): List<SubscriptionEvent> {
            lastLimit = limit
            lastOffset = offset
            return events.drop(offset).take(limit)
        }
    }

    private fun event(id: String, payload: String, at: Instant? = processedAt) = SubscriptionEvent(
        id = UUID.randomUUID(),
        asaasEventId = id,
        type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
        payload = payload,
        processedAt = at,
    )

    @Test
    fun `payment value accepts numbers and numeric text and rounds half up to cents`() {
        val events = Events(
            listOf(
                event("e1", """{"payment":{"id":"p1","value":59.9}}"""),
                event("e2", """{"payment":{"id":"p2","value":"39.90"}}"""),
                event("e3", """{"payment":{"id":"p3","value":0.005}}"""),
                event("e4", """{"payment":{"id":"p4","value":"abc"}}"""),
                event("e5", """{"payment":{"id":"p5","value":null}}"""),
                event("e6", """{"payment":{"id":"p6"}}"""),
                event("e7", """{"payment":{"id":"p7","value":true}}"""),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId, limit = 20)

        assertEquals(listOf(5_990L, 3_990L, 1L, null, null, null, null), receipts.map { it.valueCents })
    }

    @Test
    fun `confirmation time prefers confirmedDate with time, then client payment date, then processing time`() {
        val events = Events(
            listOf(
                event("e1", """{"payment":{"id":"p1","confirmedDate":"2026-10-17T09:30:00Z","clientPaymentDate":"2026-10-16T08:00:00Z"}}"""),
                event("e2", """{"payment":{"id":"p2","confirmedDate":"2026-10-17","clientPaymentDate":"2026-10-16T08:00:00Z"}}"""),
                event("e3", """{"payment":{"id":"p3","confirmedDate":"","clientPaymentDate":"2026-10-16"}}"""),
                event("e4", """{"payment":{"id":"p4","confirmedDate":"lixo-T-lixo"}}"""),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(Instant.parse("2026-10-17T09:30:00Z"), receipts[0].confirmedAt)
        assertEquals(Instant.parse("2026-10-16T08:00:00Z"), receipts[1].confirmedAt)
        assertEquals(processedAt, receipts[2].confirmedAt)
        assertEquals(processedAt, receipts[3].confirmedAt)
        assertEquals(processedAt, receipts[3].processedAt)
    }

    @Test
    fun `events that cannot be turned into a receipt are skipped`() {
        val events = Events(
            listOf(
                event("e1", "isto nao e json"),
                event("e2", """{"payment":{"id":"p2","value":10}}""", at = null),
                event("e3", """{"payment":{"id":"p3","value":10}}"""),
            ),
        )

        assertEquals(listOf("e3"), ListReceipts(events).execute(ownerId).map { it.asaasEventId })
    }

    @Test
    fun `a receipt without a payment id keeps its event id and is not collapsed with others`() {
        val events = Events(
            listOf(
                event("e1", """{"payment":{"value":10}}"""),
                event("e2", """{"payment":{"value":10}}"""),
            ),
        )

        val receipts = ListReceipts(events).execute(ownerId)

        assertEquals(listOf("e1", "e2"), receipts.map { it.asaasEventId })
        assertNull(receipts.first().asaasPaymentId)
    }

    @Test
    fun `pagination boundaries 1 and 0 are accepted and zero limit or negative offset is refused`() {
        val events = Events(listOf(event("e1", """{"payment":{"id":"p1","value":10}}""")))
        val useCase = ListReceipts(events)

        assertEquals(1, useCase.execute(ownerId, limit = 1, offset = 0).size)
        org.junit.jupiter.api.assertThrows<InvalidReceiptPaginationException> { useCase.execute(ownerId, limit = 0) }
        org.junit.jupiter.api.assertThrows<InvalidReceiptPaginationException> { useCase.execute(ownerId, limit = 1, offset = -1) }
        assertEquals(20, ListReceipts.DEFAULT_LIMIT)
    }

    @Test
    fun `App Store purchases become receipts with the price in cents only for BRL`() {
        val store = InMemoryAppStoreSubscriptions()
        store.recordTransaction(
            appStoreTransaction(ownerId, transactionId = "t1", purchaseDate = Instant.parse("2026-10-10T12:00:00Z")),
            ownerId,
        )
        store.recordTransaction(
            appStoreTransaction(ownerId, transactionId = "t2", purchaseDate = Instant.parse("2026-10-11T12:00:00Z"))
                .copy(currency = "USD"),
            ownerId,
        )

        val receipts = ListReceipts(Events(emptyList()), appStoreSubscriptions = store).execute(ownerId)

        assertEquals(listOf("t2", "t1"), receipts.map { it.asaasEventId })
        assertNull(receipts[0].valueCents)
        assertEquals(5_990L, receipts[1].valueCents)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), receipts[1].confirmedAt)
        assertNull(receipts[1].asaasPaymentId)
    }

    @Test
    fun `receipts from several sources are merged newest first and paginated after the merge`() {
        val store = InMemoryAppStoreSubscriptions()
        store.recordTransaction(
            appStoreTransaction(ownerId, transactionId = "t1", purchaseDate = Instant.parse("2026-10-05T12:00:00Z")),
            ownerId,
        )
        store.recordTransaction(
            appStoreTransaction(ownerId, transactionId = "t2", purchaseDate = Instant.parse("2026-10-15T12:00:00Z")),
            ownerId,
        )
        val events = Events(
            listOf(
                event("e1", """{"payment":{"id":"p1","value":10,"confirmedDate":"2026-10-12T00:00:00Z"}}"""),
                event("e2", """{"payment":{"id":"p2","value":10,"confirmedDate":"2026-10-01T00:00:00Z"}}"""),
            ),
        )
        val useCase = ListReceipts(events, appStoreSubscriptions = store)

        assertEquals(listOf("t2", "e1", "t1", "e2"), useCase.execute(ownerId, limit = 10).map { it.asaasEventId })
        assertEquals(listOf("e1", "t1"), useCase.execute(ownerId, limit = 2, offset = 1).map { it.asaasEventId })
        // Cada fonte é consultada pelas primeiras limit + offset linhas, a página sai depois de juntar.
        assertEquals(3, events.lastLimit)
        assertEquals(0, events.lastOffset)
    }

    @Test
    fun `with no store receipts the web page is read straight from the event store`() {
        val events = Events(listOf(event("e1", """{"payment":{"id":"p1","value":10}}""")))

        ListReceipts(events, appStoreSubscriptions = InMemoryAppStoreSubscriptions()).execute(ownerId, limit = 5, offset = 3)

        assertEquals(5, events.lastLimit)
        assertEquals(3, events.lastOffset)
    }

    @Test
    fun `Google Play orders become receipts without price`() {
        val play = object : GooglePlaySubscriptionRepository {
            override fun insertIfAbsent(subscription: br.com.saqz.subscriptions.domain.GooglePlaySubscription) = Unit
            override fun findForUpdate(purchaseToken: String) = null
            override fun save(subscription: br.com.saqz.subscriptions.domain.GooglePlaySubscription) = Unit
            override fun findByOwner(ownerUserId: UUID) = emptyList<br.com.saqz.subscriptions.domain.GooglePlaySubscription>()
            override fun ownerExists(ownerUserId: UUID) = true
            override fun supersede(purchaseToken: String, at: Instant) = Unit
            override fun markAcknowledged(purchaseToken: String) = Unit
            override fun recordOrder(subscription: br.com.saqz.subscriptions.domain.GooglePlaySubscription) = Unit
            override fun listOrdersForOwner(ownerUserId: UUID, limit: Int) =
                listOf(GooglePlayOrderRecord("GPA.1", "app.saqz.titular", Instant.parse("2026-10-14T00:00:00Z")))
        }

        val receipt = ListReceipts(Events(emptyList()), googlePlaySubscriptions = play).execute(ownerId).single()

        assertEquals("GPA.1", receipt.asaasEventId)
        assertNull(receipt.valueCents)
        assertNull(receipt.asaasPaymentId)
        assertEquals(Instant.parse("2026-10-14T00:00:00Z"), receipt.confirmedAt)
        assertEquals(Instant.parse("2026-10-14T00:00:00Z"), receipt.processedAt)
    }
}
