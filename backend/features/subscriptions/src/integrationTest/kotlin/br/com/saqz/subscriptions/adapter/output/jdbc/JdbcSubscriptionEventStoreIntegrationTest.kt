package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSubscriptionEventStoreIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var store: JdbcSubscriptionEventStore

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        store = JdbcSubscriptionEventStore(dataSource)
    }

    @Test
    fun `lists processed events by owner with database pagination`() {
        val ownerId = UUID.randomUUID()
        val otherOwnerId = UUID.randomUUID()
        val firstProcessedAt = Instant.parse("2026-07-30T12:00:00Z")
        val secondProcessedAt = Instant.parse("2026-07-31T12:00:00Z")

        insert("evt_owner_old", ownerId, firstProcessedAt)
        insert("evt_owner_upgrade", ownerId, secondProcessedAt)
        insert("evt_other_owner", otherOwnerId, secondProcessedAt)
        insert("evt_unprocessed", ownerId, null)

        val firstPage = store.listProcessedByTypesForOwner(
            ProcessAsaasWebhook.CONFIRMING_EVENT_TYPES,
            ownerId,
            limit = 1,
            offset = 0,
        )
        val secondPage = store.listProcessedByTypesForOwner(
            ProcessAsaasWebhook.CONFIRMING_EVENT_TYPES,
            ownerId,
            limit = 1,
            offset = 1,
        )

        assertEquals(listOf("evt_owner_upgrade"), firstPage.map { it.asaasEventId })
        assertEquals(listOf("evt_owner_old"), secondPage.map { it.asaasEventId })
        assertTrue(firstPage.single().payload.contains("pay_evt_owner_upgrade"))
    }

    private fun insert(eventId: String, ownerUserId: UUID, processedAt: Instant?) {
        assertTrue(
            store.tryInsert(
                id = UUID.randomUUID(),
                asaasEventId = eventId,
                type = ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED,
                payload = """{"payment":{"id":"pay_$eventId"}}""",
                now = Instant.parse("2026-07-30T11:00:00Z"),
                ownerUserId = ownerUserId,
            ),
        )
        processedAt?.let { store.markProcessed(eventId, it) }
    }
}
