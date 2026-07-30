package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.SubscriptionEventStore
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcSubscriptionEventStore(
    dataSource: DataSource,
) : SubscriptionEventStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun tryInsert(
        id: UUID,
        asaasEventId: String,
        type: String,
        payload: String,
        now: Instant,
    ): Boolean {
        val inserted = jdbc.sql(
            """
            INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at)
            VALUES (:id, :asaasEventId, :type, :payload, NULL, :now)
            ON CONFLICT (asaas_event_id) DO NOTHING
            """.trimIndent(),
        )
            .param("id", id)
            .param("asaasEventId", asaasEventId)
            .param("type", type)
            .param("payload", payload)
            .param("now", Timestamp.from(now))
            .update()
        return inserted == 1
    }

    override fun markProcessed(asaasEventId: String, processedAt: Instant) {
        jdbc.sql(
            """
            UPDATE subscription_events
            SET processed_at = :processedAt
            WHERE asaas_event_id = :asaasEventId
            """.trimIndent(),
        )
            .param("processedAt", Timestamp.from(processedAt))
            .param("asaasEventId", asaasEventId)
            .update()
    }
}
