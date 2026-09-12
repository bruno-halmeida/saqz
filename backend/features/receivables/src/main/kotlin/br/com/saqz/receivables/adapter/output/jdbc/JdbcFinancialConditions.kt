package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.FinancialTerms
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialConditions(dataSource: DataSource) : FinancialConditions {
    private val jdbc = JdbcClient.create(dataSource)

    override fun current(methods: Set<PaymentMethod>, at: Instant): List<FeeSchedule> {
        if (methods.isEmpty()) return emptyList()
        return jdbc.sql("""
            SELECT DISTINCT ON (f.method) f.* FROM receivable_fee_schedules f
            JOIN receivable_terms t ON t.version=f.terms_version
            WHERE f.method IN (:methods) AND f.effective_at<=:at AND f.published_at<=:at
                AND t.effective_at<=:at AND t.published_at<=:at
            ORDER BY f.method,f.effective_at DESC
        """.trimIndent()).param("methods", methods.map { it.name }).param("at", Timestamp.from(at))
            .query { rs, _ -> FeeSchedule(rs.getObject("id", UUID::class.java),
                PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("provider_rate"),
                rs.getLong("provider_fixed_cents"), rs.getBigDecimal("commission_rate"),
                rs.getLong("commission_fixed_cents"), rs.getString("terms_version")) }.list()
    }

    // Historical accepted versions remain readable after new terms take effect.
    override fun terms(version: String, at: Instant): FinancialTerms? = jdbc.sql("""
        SELECT * FROM receivable_terms WHERE version=:version AND published_at<=:at
    """.trimIndent()).param("version", version).param("at", Timestamp.from(at)).query { rs, _ ->
        FinancialTerms(rs.getString("version"), rs.getString("content"), rs.getString("content_sha256"),
            rs.getTimestamp("effective_at").toInstant(), rs.getTimestamp("published_at").toInstant())
    }.optional().orElse(null)
}
