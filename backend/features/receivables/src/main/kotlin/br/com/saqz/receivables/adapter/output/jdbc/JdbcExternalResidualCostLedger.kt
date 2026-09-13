package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.ExternalResidualCostLedger
import br.com.saqz.receivables.application.ObservedResidualCost
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcExternalResidualCostLedger(dataSource: DataSource) : ExternalResidualCostLedger {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun append(observation: ObservedResidualCost, occurredAt: Instant): Boolean = transaction.execute {
        val instrumentStatus = jdbc.sql("SELECT status FROM receivable_instruments WHERE id=:instrument AND account_id=:account")
            .param("instrument", observation.instrumentId).param("account", observation.accountId)
            .query(String::class.java).optional().orElse(null)
        require(instrumentStatus in setOf("REFUNDED", "CHARGEBACK")) {
            "Residual cost requires a reconciled reversal for the same account"
        }
        jdbc.sql("""
            INSERT INTO receivable_movements(id,account_id,instrument_id,kind,amount_cents,provider_reference,occurred_at)
            VALUES (:id,:account,:instrument,'RESIDUAL_COST',:amount,:reference,:at)
            ON CONFLICT(account_id,kind,provider_reference) DO NOTHING
        """.trimIndent()).param("id", UUID.randomUUID()).param("account", observation.accountId)
            .param("instrument", observation.instrumentId).param("amount", -observation.amountCents)
            .param("reference", observation.providerReference).param("at", Timestamp.from(occurredAt)).update() == 1
    }
}
