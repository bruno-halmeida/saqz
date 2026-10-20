package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminRevenueReader
import br.com.saqz.subscriptions.application.AdminRevenueCourtesyRow
import br.com.saqz.subscriptions.application.AdminRevenueReport
import br.com.saqz.subscriptions.application.AdminRevenueRow
import br.com.saqz.subscriptions.domain.Plan
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

class JdbcAdminRevenueReader(dataSource: DataSource) : AdminRevenueReader {
    private val jdbc = JdbcClient.create(dataSource)

    override fun monthly(months: Int): AdminRevenueReport {
        val rows = jdbc.sql(
            """
            SELECT test,
                   to_char(date_trunc('month', paid_at AT TIME ZONE 'America/Sao_Paulo'), 'YYYY-MM') AS month,
                   source, currency, count(*) AS payments,
                   CASE WHEN count(gross_cents) > 0 THEN sum(gross_cents)::bigint END AS gross_cents,
                   CASE WHEN count(net_cents) = count(*) THEN sum(net_cents)::bigint END AS net_cents
            FROM subscription_payments
            WHERE NOT refunded
              AND paid_at >= (date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo') - make_interval(months => :back)) AT TIME ZONE 'America/Sao_Paulo'
            GROUP BY test, 2, source, currency
            ORDER BY 2 DESC, source, currency
            """.trimIndent(),
        )
            .param("back", months - 1)
            .query { rs, _ ->
                rs.getBoolean("test") to AdminRevenueRow(
                    month = rs.getString("month"),
                    source = rs.getString("source"),
                    currency = rs.getString("currency"),
                    payments = rs.getLong("payments"),
                    grossCents = rs.getObject("gross_cents")?.let { (it as Number).toLong() },
                    netCents = rs.getObject("net_cents")?.let { (it as Number).toLong() },
                )
            }
            .list()
        return AdminRevenueReport(
            real = rows.filter { !it.first }.map { it.second },
            test = rows.filter { it.first }.map { it.second },
            courtesy = courtesy(months),
        )
    }

    /**
     * Cortesia ativa no mês: criada antes do fim do mês, não revogada antes do início e sem fim antes do início. Preço mensal de
     * tabela do plano (o do site), mês cheio. Meses sem cortesia não aparecem.
     */
    private fun courtesy(months: Int): List<AdminRevenueCourtesyRow> {
        data class Row(val month: String, val plan: Plan, val count: Long)

        val rows = jdbc.sql(
            """
            WITH meses AS (
                SELECT ms AS inicio_local,
                       ms AT TIME ZONE 'America/Sao_Paulo' AS inicio,
                       (ms + interval '1 month') AT TIME ZONE 'America/Sao_Paulo' AS fim
                FROM generate_series(
                    date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo') - make_interval(months => :back),
                    date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo'),
                    interval '1 month') AS ms
            )
            SELECT to_char(m.inicio_local, 'YYYY-MM') AS month, c.plan::text AS plan, count(*) AS active
            FROM meses m
            JOIN plan_courtesies c
              ON c.created_at < m.fim
             AND (c.revoked_at IS NULL OR c.revoked_at >= m.inicio)
             AND (c.ends_at IS NULL OR c.ends_at > m.inicio)
            GROUP BY 1, 2
            """.trimIndent(),
        )
            .param("back", months - 1)
            .query { rs, _ -> Row(rs.getString("month"), Plan.valueOf(rs.getString("plan")), rs.getLong("active")) }
            .list()
        return rows.groupBy { it.month }
            .map { (month, byPlan) ->
                AdminRevenueCourtesyRow(
                    month = month,
                    active = byPlan.sumOf { it.count },
                    cededCents = byPlan.sumOf { it.plan.monthlyPriceCents * it.count },
                )
            }
            .sortedByDescending { it.month }
    }
}
