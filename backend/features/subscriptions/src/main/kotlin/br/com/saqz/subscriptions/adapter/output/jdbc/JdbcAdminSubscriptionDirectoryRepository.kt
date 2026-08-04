package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminReceipt
import br.com.saqz.subscriptions.application.AdminSubscriptionDetail
import br.com.saqz.subscriptions.application.AdminSubscriptionDirectory
import br.com.saqz.subscriptions.application.AdminSubscriptionPage
import br.com.saqz.subscriptions.application.AdminSubscriptionSummary
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.application.SubscriptionPricing
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminSubscriptionDirectoryRepository(
    dataSource: DataSource,
) : AdminSubscriptionDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(query: String?, plan: String?, status: String?, page: Int, size: Int): AdminSubscriptionPage {
        data class Row(val summary: AdminSubscriptionSummary, val total: Long)

        val rows = jdbc.sql(
            """
            SELECT s.owner_user_id, s.plan, s.cycle, s.status, s.current_period_end,
                   s.canceled_at, s.past_due_since, s.created_at,
                   s.coupon_id, s.coupon_cycles_remaining,
                   u.display_name AS owner_name, u.email AS owner_email,
                   c.code AS coupon_code, c.discount_percent,
                   count(*) OVER () AS total
            FROM subscriptions s
            JOIN access_users u ON u.id = s.owner_user_id
            LEFT JOIN coupons c ON c.id = s.coupon_id
            WHERE (:query::text IS NULL
                   OR u.display_name ILIKE '%' || :query || '%'
                   OR u.email ILIKE '%' || :query || '%')
              AND (:plan::text IS NULL OR s.plan = :plan)
              AND (:status::text IS NULL OR s.status = :status)
            ORDER BY s.created_at DESC, s.owner_user_id
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("plan", plan)
            .param("status", status)
            .param("size", size)
            .param("offset", (page - 1) * size)
            .query { rs, _ -> Row(rs.summary(), rs.getLong("total")) }
            .list()

        return AdminSubscriptionPage(rows.map { it.summary }, rows.firstOrNull()?.total ?: 0, page, size)
    }

    override fun find(ownerUserId: UUID): AdminSubscriptionDetail? {
        val summary = jdbc.sql(
            """
            SELECT s.owner_user_id, s.plan, s.cycle, s.status, s.current_period_end,
                   s.canceled_at, s.past_due_since, s.created_at,
                   s.coupon_id, s.coupon_cycles_remaining,
                   u.display_name AS owner_name, u.email AS owner_email,
                   c.code AS coupon_code, c.discount_percent
            FROM subscriptions s
            JOIN access_users u ON u.id = s.owner_user_id
            LEFT JOIN coupons c ON c.id = s.coupon_id
            WHERE s.owner_user_id = :id
            """.trimIndent(),
        )
            .param("id", ownerUserId)
            .query { rs, _ -> rs.summary() }
            .optional()
            .orElse(null) ?: return null

        /** Mesma leitura de payload do ListReceipts (payment.value em reais). */
        val receipts = jdbc.sql(
            """
            SELECT asaas_event_id, processed_at,
                   round(((payload::jsonb)#>>'{payment,value}')::numeric * 100)::bigint AS value_cents
            FROM subscription_events
            WHERE owner_user_id = :id AND type = :type AND processed_at IS NOT NULL
            ORDER BY processed_at DESC
            LIMIT :limit
            """.trimIndent(),
        )
            .param("id", ownerUserId)
            .param("type", ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED)
            .param("limit", RECEIPT_LIMIT)
            .query { rs, _ ->
                AdminReceipt(
                    asaasEventId = rs.getString("asaas_event_id"),
                    valueCents = rs.getObject("value_cents")?.let { (it as Number).toLong() },
                    processedAt = rs.instant("processed_at"),
                )
            }
            .list()

        return AdminSubscriptionDetail(summary, receipts)
    }

    private fun ResultSet.summary(): AdminSubscriptionSummary {
        val plan = Plan.valueOf(getString("plan"))
        val cycle = SubscriptionCycle.valueOf(getString("cycle"))
        val couponActive = SubscriptionPricing.hasActiveCouponDiscount(
            getObject("coupon_id", UUID::class.java),
            getObject("coupon_cycles_remaining")?.let { (it as Number).toInt() },
        )
        val fullPrice = with(SubscriptionPricing) { plan.priceCents(cycle) }
        val discount = getObject("discount_percent")?.let { (it as Number).toInt() }
        return AdminSubscriptionSummary(
            ownerUserId = getObject("owner_user_id", UUID::class.java),
            ownerName = getString("owner_name"),
            ownerEmail = getString("owner_email"),
            plan = plan.name,
            cycle = cycle.name,
            status = getString("status"),
            couponCode = if (couponActive) getString("coupon_code") else null,
            priceCents = if (couponActive && discount != null) {
                SubscriptionPricing.applyDiscount(fullPrice, discount)
            } else {
                fullPrice
            },
            currentPeriodEnd = instant("current_period_end"),
            canceledAt = instantOrNull("canceled_at"),
            pastDueSince = instantOrNull("past_due_since"),
            createdAt = instant("created_at"),
        )
    }

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.instantOrNull(column: String): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()

    private companion object {
        const val RECEIPT_LIMIT = 12
    }
}
