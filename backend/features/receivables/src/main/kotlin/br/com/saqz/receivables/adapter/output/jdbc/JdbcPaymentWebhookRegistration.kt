package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import org.springframework.jdbc.core.simple.JdbcClient
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

class JdbcPaymentWebhookRegistration(dataSource: DataSource, private val store: JdbcPaymentStore,
    private val secrets: FinancialSecrets, private val provider: PaymentWebhookRegistrationProvider,
    private val clock: Clock) : PaymentWebhookRegistration {
    private val jdbc = JdbcClient.create(dataSource)
    override fun configure(accountId: UUID, request: FinancialRequest): FinancialResult<String> = try {
        val state = store.transaction {
            val owner = jdbc.sql("SELECT owner_user_id FROM receivable_accounts WHERE id=:id FOR UPDATE")
                .param("id", accountId).query(UUID::class.java).optional().orElse(null)
            if (owner != request.actorUserId) return@transaction null
            store.registerLocal(accountId, accountId, "CONFIGURE_WEBHOOK", request, paymentDigest("CONFIGURE_WEBHOOK:$accountId"), clock.instant())
            val existing = jdbc.sql("SELECT token_encrypted,state FROM receivable_webhook_credentials WHERE account_id=:id")
                .param("id", accountId).query { r, _ -> r.getString("token_encrypted") to r.getString("state") }.optional().orElse(null)
            if (existing != null) {
                val canCreate = existing.second == "REJECTED"
                if (canCreate) jdbc.sql("UPDATE receivable_webhook_credentials SET state='UNKNOWN' WHERE account_id=:id")
                    .param("id", accountId).update()
                Triple(secrets.decrypt(accountId, "webhook-token", existing.first), canCreate, existing.second == "SUCCEEDED")
            } else {
                val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also(SecureRandom()::nextBytes))
                jdbc.sql("""INSERT INTO receivable_webhook_credentials(account_id,token_encrypted,state,request_id,actor_user_id)
                    VALUES (:id,:token,'UNKNOWN',:request,:actor)""")
                    .param("id", accountId).param("token", secrets.encrypt(accountId, "webhook-token", token))
                    .param("request", request.requestId).param("actor", request.actorUserId).update()
                Triple(token, true, false)
            }
        }
        if (state == null) FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
        else if (state.third) FinancialResult.Success("CONFIGURED", request.requestId)
        else {
            var rejected = false
            val remoteId = try { provider.configureWebhook(accountId, state.first, state.second) }
                catch (_: DefinitivePaymentRejection) {
                    if (state.second) {
                        jdbc.sql("UPDATE receivable_webhook_credentials SET state='REJECTED' WHERE account_id=:id")
                            .param("id", accountId).update()
                        rejected = true
                    }
                    null
                } catch (_: Exception) { null }
            if (rejected) FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, request.requestId)
            else if (remoteId == null) FinancialResult.Success("RESULT_PENDING", request.requestId)
            else {
                jdbc.sql("UPDATE receivable_webhook_credentials SET state='SUCCEEDED',provider_webhook_id=:remote WHERE account_id=:id")
                    .param("remote", remoteId).param("id", accountId).update()
                FinancialResult.Success("CONFIGURED", request.requestId)
            }
        }
    } catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
}
