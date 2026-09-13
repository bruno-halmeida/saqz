package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import javax.sql.DataSource

class JdbcPaymentProviderCredentials(dataSource: DataSource, private val secrets: FinancialSecrets) : PaymentProviderCredentials {
    private val jdbc = JdbcClient.create(dataSource)
    private val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()
    override fun apiKey(accountId: UUID): String = secrets.decrypt(accountId, "provider-key",
        jdbc.sql("SELECT credentials_encrypted FROM receivable_accounts WHERE id=:id").param("id", accountId)
            .query(String::class.java).single())
    override fun customer(accountId: UUID, payerId: UUID): PaymentCustomer = tx.execute {
        val row = jdbc.sql("SELECT * FROM receivable_customers WHERE account_id=:account AND payer_id=:payer FOR UPDATE")
            .param("account", accountId).param("payer", payerId).query { r, _ -> PaymentCustomer(
                r.getObject("external_reference", UUID::class.java), r.getString("provider_customer_id"), r.getString("state") == "READY",
                mapper.readValue<PaymentPayer>(secrets.decrypt(accountId, "payment-payer", r.getString("payer_data_encrypted")))) }.single()
        if (row.canCreate) jdbc.sql("UPDATE receivable_customers SET state='UNKNOWN' WHERE account_id=:account AND payer_id=:payer")
            .param("account", accountId).param("payer", payerId).update()
        row
    }!!
    override fun rejectCustomer(accountId: UUID, payerId: UUID) {
        jdbc.sql("UPDATE receivable_customers SET state='REJECTED' WHERE account_id=:account AND payer_id=:payer AND provider_customer_id IS NULL")
            .param("account", accountId).param("payer", payerId).update()
    }
    override fun saveCustomer(accountId: UUID, payerId: UUID, providerId: String) {
        val changed = jdbc.sql("""UPDATE receivable_customers SET provider_customer_id=:id,state='SUCCEEDED'
            WHERE account_id=:account AND payer_id=:payer AND (provider_customer_id IS NULL OR provider_customer_id=:id)""")
            .param("id", providerId).param("account", accountId).param("payer", payerId).update()
        check(changed == 1) { "Conflicting provider customer" }
    }
}
