package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.PaidSubscriptionHistory
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcPaidSubscriptionHistory(dataSource: DataSource) : PaidSubscriptionHistory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun hasPaidHistory(ownerId: UUID): Boolean = jdbc.sql(
        """
        SELECT EXISTS (SELECT 1 FROM subscriptions WHERE owner_user_id = :owner
            AND (first_confirmed_at IS NOT NULL OR status = 'ACTIVE'))
        OR EXISTS (SELECT 1 FROM subscription_events WHERE owner_user_id = :owner
            AND processed_at IS NOT NULL AND type IN ('PAYMENT_CONFIRMED', 'PAYMENT_RECEIVED'))
        """.trimIndent(),
    ).param("owner", ownerId).query(Boolean::class.java).single()
}
