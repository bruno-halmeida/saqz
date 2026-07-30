package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcSubscriptionRepository(
    dataSource: DataSource,
) : SubscriptionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? =
        jdbc.sql(
            """
            SELECT owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                   current_period_end, canceled_at, pending_plan, pending_plan_effective_at,
                   coupon_id, coupon_cycles_remaining, past_due_since
            FROM subscriptions
            WHERE asaas_subscription_id = :asaasSubscriptionId
            FOR UPDATE
            """.trimIndent(),
        )
            .param("asaasSubscriptionId", asaasSubscriptionId)
            .query { rs, _ -> mapSubscription(rs) }
            .optional()
            .orElse(null)

    override fun findByOwnerUserId(ownerUserId: UUID): Subscription? =
        jdbc.sql(
            """
            SELECT owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                   current_period_end, canceled_at, pending_plan, pending_plan_effective_at,
                   coupon_id, coupon_cycles_remaining, past_due_since
            FROM subscriptions
            WHERE owner_user_id = :ownerUserId
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ -> mapSubscription(rs) }
            .optional()
            .orElse(null)

    override fun save(subscription: Subscription) {
        val now = Timestamp.from(Instant.now())
        jdbc.sql(
            """
            UPDATE subscriptions
            SET plan = :plan,
                cycle = :cycle,
                status = :status,
                asaas_customer_id = :asaasCustomerId,
                asaas_subscription_id = :asaasSubscriptionId,
                current_period_end = :currentPeriodEnd,
                canceled_at = :canceledAt,
                pending_plan = :pendingPlan,
                pending_plan_effective_at = :pendingPlanEffectiveAt,
                coupon_id = :couponId,
                coupon_cycles_remaining = :couponCyclesRemaining,
                past_due_since = :pastDueSince,
                updated_at = :updatedAt
            WHERE owner_user_id = :ownerUserId
            """.trimIndent(),
        )
            .param("ownerUserId", subscription.ownerUserId)
            .param("plan", subscription.plan.name)
            .param("cycle", subscription.cycle.name)
            .param("status", subscription.status.name)
            .param("asaasCustomerId", subscription.asaasCustomerId)
            .param("asaasSubscriptionId", subscription.asaasSubscriptionId)
            .param("currentPeriodEnd", Timestamp.from(subscription.currentPeriodEnd))
            .param("canceledAt", subscription.canceledAt?.let(Timestamp::from))
            .param("pendingPlan", subscription.pendingPlan?.name)
            .param("pendingPlanEffectiveAt", subscription.pendingPlanEffectiveAt?.let(Timestamp::from))
            .param("couponId", subscription.couponId)
            .param("couponCyclesRemaining", subscription.couponCyclesRemaining)
            .param("pastDueSince", subscription.pastDueSince?.let(Timestamp::from))
            .param("updatedAt", now)
            .update()
    }

    private fun mapSubscription(rs: java.sql.ResultSet): Subscription = Subscription(
        ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
        plan = Plan.valueOf(rs.getString("plan")),
        cycle = SubscriptionCycle.valueOf(rs.getString("cycle")),
        status = SubscriptionStatus.valueOf(rs.getString("status")),
        asaasCustomerId = rs.getString("asaas_customer_id"),
        asaasSubscriptionId = rs.getString("asaas_subscription_id"),
        currentPeriodEnd = rs.getTimestamp("current_period_end").toInstant(),
        canceledAt = rs.getTimestamp("canceled_at")?.toInstant(),
        pendingPlan = rs.getString("pending_plan")?.let(Plan::valueOf),
        pendingPlanEffectiveAt = rs.getTimestamp("pending_plan_effective_at")?.toInstant(),
        couponId = rs.getObject("coupon_id") as UUID?,
        couponCyclesRemaining = rs.getObject("coupon_cycles_remaining") as Int?,
        pastDueSince = rs.getTimestamp("past_due_since")?.toInstant(),
    )
}
