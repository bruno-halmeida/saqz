package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.FeeSnapshot
import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.chargePricing
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.group.ChargePricing
import br.com.saqz.sharedkernel.group.GroupReceivables
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

/**
 * Lado de recebimentos da porta [GroupReceivables]. Roda dentro da transação dos grupos: só lê o vínculo ativo, a tabela
 * publicada (comissão) e o retrato das tarifas do Asaas gravado na ativação. Nunca consulta o Asaas aqui.
 */
class JdbcGroupReceivablesPricing(private val dataSource: DataSource, private val published: FinancialConditions,
    private val clock: Clock) : GroupReceivables {
    private val jdbc = JdbcClient.create(dataSource)

    override fun active(groupId: UUID): Boolean = jdbc.sql(
        "SELECT count(*) FROM receivable_group_links WHERE group_id=:group AND enabled",
    ).param("group", groupId).query(Int::class.java).single() > 0

    override fun groupDeleted(groupId: UUID, actorId: UUID) = JdbcGroupReceivablesStore(dataSource).groupDeleted(groupId, actorId, clock.instant())

    override fun price(groupId: UUID, amountCents: Long): ChargePricing? {
        val link = jdbc.sql("""
            SELECT account_id,fee_policy,fee_snapshot::text AS snapshot FROM receivable_group_links
            WHERE group_id=:group AND enabled
        """.trimIndent()).param("group", groupId).query { rs, _ ->
            Triple(rs.getObject("account_id", UUID::class.java), rs.getString("fee_policy"), rs.getString("snapshot"))
        }.optional().orElse(null) ?: return null
        val (account, policy, snapshot) = link
        if (policy != "PASS") return ChargePricing(amountCents, "ABSORB", null)
        val fees = snapshot?.let(FeeSnapshot::parse) ?: return ChargePricing(amountCents, "ABSORB", null)
        val schedules = published.current(PaymentMethod.entries.toSet(), clock.instant(), account)
            .mapNotNull { schedule -> fees[schedule.method]?.applyTo(schedule) }
        return chargePricing(amountCents, policy, schedules) ?: ChargePricing(amountCents, "ABSORB", null)
    }
}
