package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CreditCardTokenLookup
import br.com.saqz.subscriptions.application.CreditCardTokenStore
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

class JdbcCreditCardTokenStore(
    dataSource: DataSource,
) : CreditCardTokenStore, CreditCardTokenLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun save(asaasSubscriptionId: String, token: String?, lastFourDigits: String?, brand: String?) {
        jdbc.sql(
            """
            UPDATE subscriptions
            SET asaas_credit_card_token = :token,
                credit_card_last4 = :last4,
                credit_card_brand = :brand
            WHERE asaas_subscription_id = :asaasSubscriptionId
            """.trimIndent(),
        )
            .param("token", token)
            .param("last4", lastFourDigits)
            .param("brand", brand)
            .param("asaasSubscriptionId", asaasSubscriptionId)
            .update()
    }

    override fun tokenFor(asaasSubscriptionId: String): String? =
        jdbc.sql("SELECT asaas_credit_card_token FROM subscriptions WHERE asaas_subscription_id = :id")
            .param("id", asaasSubscriptionId)
            .query(String::class.java)
            .optional()
            .orElse(null)
}
