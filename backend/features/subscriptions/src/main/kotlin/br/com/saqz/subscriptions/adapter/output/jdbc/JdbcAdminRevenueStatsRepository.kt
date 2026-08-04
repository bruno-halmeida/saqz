package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminRevenueStats
import br.com.saqz.subscriptions.application.ChurnStats
import br.com.saqz.subscriptions.application.PlanSplitEntry
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.application.SubscribedCohortWeek
import br.com.saqz.subscriptions.application.SubscriptionPricing
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

class JdbcAdminRevenueStatsRepository(
    dataSource: DataSource,
) : AdminRevenueStats {
    private val jdbc = JdbcClient.create(dataSource)

    /**
     * Mesma leitura de payload que ListReceipts.paymentValueToCents faz em Kotlin
     * (payment.value em reais), agregada no banco para não streamar payloads.
     */
    override fun revenueCents(from: Instant?, to: Instant): Long = jdbc.sql(
        """
        SELECT COALESCE(SUM(round(((payload::jsonb)#>>'{payment,value}')::numeric * 100)), 0)::bigint
        FROM subscription_events
        WHERE type = :type
          AND processed_at IS NOT NULL
          AND (
              jsonb_typeof((payload::jsonb)#>'{payment,value}') = 'number'
              OR (payload::jsonb)#>>'{payment,value}' ~ '^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$'
          )
          AND processed_at < :to
          AND (:from::timestamptz IS NULL OR processed_at >= :from)
        """.trimIndent(),
    )
        .param("type", ProcessAsaasWebhook.EVENT_PAYMENT_CONFIRMED)
        .param("from", from?.atOffset(ZoneOffset.UTC))
        .param("to", to.atOffset(ZoneOffset.UTC))
        .query(Long::class.java)
        .single()

    override fun churn(from: Instant?, to: Instant): ChurnStats {
        val canceled = jdbc.sql(
            """
            SELECT count(*) FROM subscriptions
            WHERE canceled_at IS NOT NULL AND first_confirmed_at IS NOT NULL
              AND canceled_at < :to
              AND (:from::timestamptz IS NULL OR canceled_at >= :from)
            """.trimIndent(),
        )
            .param("from", from?.atOffset(ZoneOffset.UTC))
            .param("to", to.atOffset(ZoneOffset.UTC))
            .query(Long::class.java).single()

        // Denominador só com quem já pagou alguma vez: o checkout cria a linha PAST_DUE
        // com first_confirmed_at nulo, e abandono de checkout não é base de churn.
        val activeAtStart = if (from == null) {
            jdbc.sql("SELECT count(*) FROM subscriptions WHERE first_confirmed_at IS NOT NULL")
                .query(Long::class.java).single()
        } else {
            jdbc.sql(
                """
                SELECT count(*) FROM subscriptions
                WHERE first_confirmed_at IS NOT NULL AND first_confirmed_at < :from
                  AND (canceled_at IS NULL OR canceled_at >= :from)
                """.trimIndent(),
            ).param("from", from.atOffset(ZoneOffset.UTC)).query(Long::class.java).single()
        }
        return ChurnStats(canceled, activeAtStart)
    }

    override fun planSplit(): List<PlanSplitEntry> {
        data class Row(val plan: Plan, val cycle: SubscriptionCycle, val discountPercent: Int?)

        val rows = jdbc.sql(
            """
            SELECT s.plan, s.cycle,
                   CASE WHEN s.coupon_id IS NOT NULL
                             AND (c.duration_cycles IS NULL OR COALESCE(s.coupon_cycles_remaining, 0) > 0)
                        THEN c.discount_percent END AS discount_percent
            FROM subscriptions s
            LEFT JOIN coupons c ON c.id = s.coupon_id
            WHERE (s.status = 'ACTIVE' OR (s.status = 'PAST_DUE' AND s.first_confirmed_at IS NOT NULL))
            """.trimIndent(),
        ).query { rs, _ ->
            Row(
                plan = Plan.valueOf(rs.getString("plan")),
                cycle = SubscriptionCycle.valueOf(rs.getString("cycle")),
                discountPercent = rs.getObject("discount_percent")?.let { (it as Number).toInt() },
            )
        }.list()

        return rows
            .groupBy { it.plan }
            .map { (plan, subscriptions) ->
                PlanSplitEntry(
                    plan = plan,
                    subscribers = subscriptions.size.toLong(),
                    mrrCents = subscriptions.sumOf { row ->
                        val monthly = when (row.cycle) {
                            SubscriptionCycle.MONTHLY -> plan.monthlyPriceCents
                            SubscriptionCycle.ANNUAL -> plan.annualPriceCents / MONTHS_PER_YEAR
                        }
                        row.discountPercent?.let { SubscriptionPricing.applyDiscount(monthly, it) } ?: monthly
                    },
                )
            }
            .sortedBy { it.plan.ordinal }
    }

    override fun subscribedCohort(weeksBack: Int, now: Instant): List<SubscribedCohortWeek> {
        val currentWeekStart = now.atOffset(ZoneOffset.UTC).toLocalDate().with(DayOfWeek.MONDAY)
        return ((weeksBack - 1).toLong() downTo 0L).map { back ->
            val weekStart = currentWeekStart.minusWeeks(back)
            val subscribed = jdbc.sql(
                """
                SELECT count(*) FROM subscriptions s
                JOIN access_users u ON u.id = s.owner_user_id
                WHERE u.created_at >= :from AND u.created_at < :to
                """.trimIndent(),
            )
                .param("from", weekStart.atStartOfDay().atOffset(ZoneOffset.UTC))
                .param("to", weekStart.plusWeeks(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .query(Long::class.java)
                .single()
            SubscribedCohortWeek(weekStart, subscribed)
        }
    }

    private companion object {
        const val MONTHS_PER_YEAR = 12L
    }
}
