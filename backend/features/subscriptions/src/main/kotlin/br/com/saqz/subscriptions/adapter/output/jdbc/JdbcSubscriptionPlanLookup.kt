package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.EntitlingSubscription
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import br.com.saqz.subscriptions.domain.Plan
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcSubscriptionPlanLookup(dataSource: DataSource) : SubscriptionPlanLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findEntitlingPlan(ownerId: UUID): EntitlingSubscription? = jdbc.sql(
        """
        SELECT plan, pending_plan
        FROM subscriptions
        WHERE owner_user_id = :ownerId
          AND status IN ('ACTIVE', 'PAST_DUE')
        """.trimIndent(),
    )
        .param("ownerId", ownerId)
        .query { result, _ ->
            EntitlingSubscription(
                plan = Plan.valueOf(result.getString("plan")),
                pendingPlan = result.getString("pending_plan")?.let(Plan::valueOf),
            )
        }
        .optional()
        .orElse(null)
}
