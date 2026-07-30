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
          AND (
            status = 'ACTIVE'
            OR (status = 'PAST_DUE' AND first_confirmed_at IS NOT NULL)
            OR (status = 'CANCELED' AND current_period_end > now())
          )
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
