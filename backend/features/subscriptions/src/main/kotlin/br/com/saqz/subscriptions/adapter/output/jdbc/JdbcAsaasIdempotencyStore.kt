package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AsaasIdempotencyReservation
import br.com.saqz.subscriptions.application.AsaasIdempotencyStore
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcAsaasIdempotencyStore(
    dataSource: DataSource,
) : AsaasIdempotencyStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun tryBegin(key: String, now: Instant): Boolean {
        val inserted = jdbc.sql(
            """
            INSERT INTO asaas_idempotent_operations (idempotency_key, resource_id, created_at)
            VALUES (:key, NULL, :now)
            ON CONFLICT (idempotency_key) DO NOTHING
            """.trimIndent(),
        )
            .param("key", key)
            .param("now", Timestamp.from(now))
            .update()
        return inserted == 1
    }

    override fun find(key: String): AsaasIdempotencyReservation? =
        jdbc.sql(
            """
            SELECT resource_id, created_at
            FROM asaas_idempotent_operations
            WHERE idempotency_key = :key
            """.trimIndent(),
        )
            .param("key", key)
            .query { rs, _ ->
                AsaasIdempotencyReservation(
                    resourceId = rs.getString("resource_id"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .optional()
            .orElse(null)

    override fun complete(key: String, resourceId: String) {
        jdbc.sql(
            """
            UPDATE asaas_idempotent_operations
            SET resource_id = :resourceId
            WHERE idempotency_key = :key
            """.trimIndent(),
        )
            .param("resourceId", resourceId)
            .param("key", key)
            .update()
    }

    override fun release(key: String) {
        jdbc.sql(
            """
            DELETE FROM asaas_idempotent_operations
            WHERE idempotency_key = :key
              AND resource_id IS NULL
            """.trimIndent(),
        )
            .param("key", key)
            .update()
    }
}
