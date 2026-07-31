package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.SubscriptionEventStore
import br.com.saqz.subscriptions.domain.SubscriptionEvent
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
        ownerUserId: UUID?,
    ): Boolean {
        val inserted = jdbc.sql(
            """
            INSERT INTO subscription_events (id, asaas_event_id, type, payload, owner_user_id, processed_at, created_at)
            VALUES (:id, :asaasEventId, :type, :payload, :ownerUserId, NULL, :now)
            ON CONFLICT (asaas_event_id) DO NOTHING
            """.trimIndent(),
        )
            .param("id", id)
            .param("asaasEventId", asaasEventId)
            .param("type", type)
            .param("payload", payload)
            .param("ownerUserId", ownerUserId)
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

    override fun exists(asaasEventId: String): Boolean {
        val count = jdbc.sql(
            """
            SELECT count(*)::int AS cnt
            FROM subscription_events
            WHERE asaas_event_id = :asaasEventId
            """.trimIndent(),
        )
            .param("asaasEventId", asaasEventId)
            .query { rs, _ -> rs.getInt("cnt") }
            .single()
        return count > 0
    }

    override fun listProcessedByTypeForOwner(
        type: String,
        ownerUserId: UUID,
        limit: Int,
        offset: Int,
    ): List<SubscriptionEvent> =
        jdbc.sql(
            """
            SELECT id, asaas_event_id, type, payload, processed_at
            FROM subscription_events
            WHERE type = :type
              AND owner_user_id = :ownerUserId
              AND processed_at IS NOT NULL
            ORDER BY processed_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
        )
            .param("type", type)
            .param("ownerUserId", ownerUserId)
            .param("limit", limit)
            .param("offset", offset)
            .query { rs, _ ->
                SubscriptionEvent(
                    id = rs.getObject("id", UUID::class.java),
                    asaasEventId = rs.getString("asaas_event_id"),
                    type = rs.getString("type"),
                    payload = rs.getString("payload"),
                    processedAt = rs.getTimestamp("processed_at")?.toInstant(),
                )
            }
            .list()
}
