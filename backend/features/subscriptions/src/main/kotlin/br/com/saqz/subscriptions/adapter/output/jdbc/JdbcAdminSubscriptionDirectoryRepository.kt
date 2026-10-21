package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminReceipt
import br.com.saqz.subscriptions.application.AdminSubscriptionDetail
import br.com.saqz.subscriptions.application.AdminSubscriptionCourtesy
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

    override fun list(
        query: String?,
        plan: String?,
        status: String?,
        page: Int,
        size: Int,
        source: String?,
        sort: String?,
        descending: Boolean,
    ): AdminSubscriptionPage {
        data class Row(val summary: AdminSubscriptionSummary, val total: Long)

        val rows = jdbc.sql(
            """
            $ALL_SOURCES
            SELECT *, count(*) OVER () AS total FROM all_subscriptions
            WHERE $FILTERS
            ORDER BY ${orderBy(sort, descending)}
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("plan", plan)
            .param("status", status)
            .param("source", source)
            .param("size", size)
            .param("offset", (page - 1).toLong() * size)
            .query { rs, _ -> Row(rs.summary(), rs.getLong("total")) }
            .list()

        val total = rows.firstOrNull()?.total ?: jdbc.sql(
            """
            $ALL_SOURCES
            SELECT count(*) FROM all_subscriptions WHERE $FILTERS
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("plan", plan)
            .param("status", status)
            .param("source", source)
            .query(Long::class.java)
            .single()

        return AdminSubscriptionPage(rows.map { it.summary }, total, page, size)
    }

    override fun find(ownerUserId: UUID): AdminSubscriptionDetail? {
        val summary = jdbc.sql(
            """
            SELECT s.owner_user_id, s.plan, s.cycle, s.current_period_end,
                   CASE WHEN s.canceled_at IS NOT NULL THEN 'CANCELED' ELSE s.status::text END AS status,
                   s.canceled_at, s.past_due_since, s.created_at,
                   s.coupon_id, s.coupon_cycles_remaining,
                   u.display_name AS owner_name, u.email AS owner_email,
                   c.code AS coupon_code, c.discount_percent, c.duration_cycles,
                   'SITE' AS source,
                   CASE WHEN EXISTS (SELECT 1 FROM subscription_payments p WHERE p.source = 'SITE' AND p.owner_user_id = s.owner_user_id)
                        THEN 'REAL' ELSE 'TEST' END AS billing,
                   cc.plan::text AS courtesy_plan, cc.ends_at AS courtesy_ends_at
            FROM subscriptions s
            JOIN access_users u ON u.id = s.owner_user_id
            LEFT JOIN coupons c ON c.id = s.coupon_id
            LEFT JOIN plan_courtesies cc ON cc.owner_user_id = s.owner_user_id
                 AND cc.revoked_at IS NULL AND (cc.ends_at IS NULL OR cc.ends_at > now())
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
                   CASE WHEN jsonb_typeof((payload::jsonb)#>'{payment,value}') = 'number'
                             OR (payload::jsonb)#>>'{payment,value}' ~ '^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$'
                        THEN round(((payload::jsonb)#>>'{payment,value}')::numeric * 100)::bigint
                   END AS value_cents
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
        val source = getString("source")
        val plan = Plan.valueOf(getString("plan"))
        val cycle = getString("cycle")?.let(SubscriptionCycle::valueOf)
        // Permanente = duration_cycles nulo; finito esgotado fica com remaining nulo
        // mantendo coupon_id (ProcessAsaasWebhook) e volta ao preço cheio.
        val couponActive = getObject("coupon_id", UUID::class.java) != null && (
            getObject("duration_cycles") == null ||
                (getObject("coupon_cycles_remaining")?.let { (it as Number).toInt() } ?: 0) > 0
            )
        val fullPrice = if (source == "SITE" && cycle != null) with(SubscriptionPricing) { plan.priceCents(cycle) } else null
        val discount = getObject("discount_percent")?.let { (it as Number).toInt() }
        return AdminSubscriptionSummary(
            ownerUserId = getObject("owner_user_id", UUID::class.java),
            ownerName = getString("owner_name"),
            ownerEmail = getString("owner_email"),
            plan = plan.name,
            cycle = cycle?.name,
            status = getString("status"),
            couponCode = if (couponActive) getString("coupon_code") else null,
            priceCents = if (couponActive && discount != null && fullPrice != null) {
                SubscriptionPricing.applyDiscount(fullPrice, discount)
            } else {
                fullPrice
            },
            currentPeriodEnd = instantOrNull("current_period_end"),
            canceledAt = instantOrNull("canceled_at"),
            pastDueSince = instantOrNull("past_due_since"),
            createdAt = instant("created_at"),
            source = source,
            billing = getString("billing"),
            courtesy = getString("courtesy_plan")?.let { AdminSubscriptionCourtesy(it, instantOrNull("courtesy_ends_at")) },
        )
    }

    // Lista fechada: só trechos fixos entram no SQL; origem e dono desempatam para a paginação ficar estável.
    private fun orderBy(sort: String?, descending: Boolean): String {
        val column = when (sort) {
            null -> return "created_at DESC, source, owner_user_id"
            "owner" -> "lower(owner_name)"
            "source" -> "source"
            "plan" -> "plan"
            "cycle" -> "cycle"
            "coupon" -> "coupon_code"
            "status" -> "status"
            "createdAt" -> "created_at"
            else -> throw IllegalArgumentException("ordenação desconhecida: $sort")
        }
        return "$column ${if (descending) "DESC" else "ASC"} NULLS LAST, source, owner_user_id"
    }

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.instantOrNull(column: String): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()

    private companion object {
        const val RECEIPT_LIMIT = 12

        const val FILTERS = """(:query::text IS NULL
                   OR owner_name ILIKE '%' || :query || '%'
                   OR owner_email ILIKE '%' || :query || '%')
              AND (:plan::text IS NULL OR plan = :plan)
              AND (:source::text IS NULL OR source = :source)
              AND (:status::text IS NULL
                   OR (:status = 'CANCELED' AND status = 'CANCELED')
                   OR (:status <> 'CANCELED' AND status = :status))"""

        /**
         * Todas as origens numa lista só (DEB-100). Site: linhas de `subscriptions` como antes (status CANCELED também quando só
         * `canceled_at` está preenchido); lojas: situação derivada das colunas de cada loja; cortesia e teste: só os ativos agora.
         * Site é `REAL` quando há pelo menos um pagamento confirmado na view; sem nenhum é `TEST`.
         */
        const val ALL_SOURCES = """
            WITH raw_subscriptions AS (
                SELECT 'SITE' AS source, s.owner_user_id, s.plan::text AS plan, s.cycle::text AS cycle,
                       CASE WHEN s.canceled_at IS NOT NULL THEN 'CANCELED' ELSE s.status::text END AS status,
                       CASE WHEN EXISTS (SELECT 1 FROM subscription_payments p WHERE p.source = 'SITE' AND p.owner_user_id = s.owner_user_id)
                            THEN 'REAL' ELSE 'TEST' END AS billing,
                       s.current_period_end, s.canceled_at, s.past_due_since, s.created_at,
                       s.coupon_id, s.coupon_cycles_remaining,
                       c.code AS coupon_code, c.discount_percent, c.duration_cycles
                FROM subscriptions s
                LEFT JOIN coupons c ON c.id = s.coupon_id
                UNION ALL
                SELECT 'APP_STORE', a.owner_user_id, a.plan::text, a.cycle::text,
                       CASE WHEN a.revoked_at IS NOT NULL THEN 'CANCELED'
                            WHEN a.expires_at > now() OR a.grace_period_expires_at > now()
                                 THEN CASE WHEN a.auto_renew IS false THEN 'CANCELED' ELSE 'ACTIVE' END
                            WHEN a.in_billing_retry THEN 'PAST_DUE'
                            ELSE 'CANCELED' END,
                       CASE WHEN upper(a.environment) = 'SANDBOX' THEN 'TEST' ELSE 'REAL' END,
                       a.expires_at, a.revoked_at, NULL::timestamptz, a.created_at,
                       NULL::uuid, NULL::int, NULL::text, NULL::int, NULL::int
                FROM app_store_subscriptions a
                UNION ALL
                SELECT 'GOOGLE_PLAY', g.owner_user_id, g.plan::text, g.cycle::text,
                       CASE WHEN g.state IN ('ACTIVE', 'IN_GRACE_PERIOD') THEN 'ACTIVE'
                            WHEN g.state IN ('CANCELED', 'EXPIRED') THEN 'CANCELED'
                            ELSE 'PAST_DUE' END,
                       CASE WHEN g.test_purchase THEN 'TEST' ELSE 'REAL' END,
                       g.expires_at, g.canceled_at, NULL::timestamptz, g.created_at,
                       NULL::uuid, NULL::int, NULL::text, NULL::int, NULL::int
                FROM google_play_subscriptions g
                WHERE g.superseded_at IS NULL
                UNION ALL
                SELECT 'COURTESY', pc.owner_user_id, pc.plan::text, NULL::text, 'ACTIVE', 'NO_CHARGE',
                       pc.ends_at, NULL::timestamptz, NULL::timestamptz, pc.created_at,
                       NULL::uuid, NULL::int, NULL::text, NULL::int, NULL::int
                FROM plan_courtesies pc
                WHERE pc.revoked_at IS NULL AND (pc.ends_at IS NULL OR pc.ends_at > now())
                UNION ALL
                SELECT 'TRIAL', t.owner_user_id, 'ORGANIZADOR', NULL::text, 'ACTIVE', 'NO_CHARGE',
                       t.ends_at, NULL::timestamptz, NULL::timestamptz, t.started_at,
                       NULL::uuid, NULL::int, NULL::text, NULL::int, NULL::int
                FROM organizer_trials t
                WHERE t.ends_at > now()
            ),
            all_subscriptions AS (
                SELECT x.*, u.display_name AS owner_name, u.email AS owner_email,
                       cc.plan::text AS courtesy_plan, cc.ends_at AS courtesy_ends_at
                FROM raw_subscriptions x JOIN access_users u ON u.id = x.owner_user_id
                LEFT JOIN plan_courtesies cc ON cc.owner_user_id = x.owner_user_id
                     AND cc.revoked_at IS NULL AND (cc.ends_at IS NULL OR cc.ends_at > now())
            )"""
    }
}
