package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminRevenueReader
import br.com.saqz.subscriptions.application.AdminRevenueReport
import br.com.saqz.subscriptions.application.AdminRevenueRow
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
        )
    }
}
