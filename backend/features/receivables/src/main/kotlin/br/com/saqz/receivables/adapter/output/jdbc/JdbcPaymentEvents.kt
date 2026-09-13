package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.PaymentExecution
import br.com.saqz.receivables.application.PaymentEventInbox
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

/** Accept commits ciphertext before returning. Processing is independent and uses account-scoped provider queries. */
class JdbcPaymentEvents(dataSource: DataSource, private val secrets: FinancialSecrets,
    private val execution: PaymentExecution, private val clock: Clock) : PaymentEventInbox {
    private val jdbc = JdbcClient.create(dataSource)
    private val mapper = jacksonObjectMapper()
    override fun accept(account: UUID, token: String, body: String): Boolean {
        val encrypted = jdbc.sql("SELECT token_encrypted FROM receivable_webhook_credentials WHERE account_id=:id")
            .param("id", account).query(String::class.java).optional().orElse(null) ?: return false
        val expected = secrets.decrypt(account, "webhook-token", encrypted)
        if (expected.length < 32 || token.length > 512 || !MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())) return false
        require(body.toByteArray().size <= 262144)
        val node = try { mapper.readTree(body) } catch (_: com.fasterxml.jackson.core.JsonProcessingException) { throw IllegalArgumentException("Invalid webhook JSON") }
        val eventId = node.path("id").asText(); val eventType = node.path("event").asText()
        require(eventId.length in 1..128 && eventType.matches(Regex("[A-Z_]{1,128}")))
        val remoteAccount = jdbc.sql("SELECT provider_account_id FROM receivable_accounts WHERE id=:id")
            .param("id", account).query(String::class.java).single()
        if (node.has("account") && node.path("account").path("id").asText() != remoteAccount) return false
        jdbc.sql("""INSERT INTO receivable_provider_events(id,account_id,provider_event_id,event_type,payload_encrypted,received_at)
            VALUES (:id,:account,:event,:type,:payload,:at) ON CONFLICT(account_id,provider_event_id) DO NOTHING""")
            .param("id", UUID.randomUUID()).param("account", account).param("event", eventId).param("type", eventType)
            .param("payload", secrets.encrypt(account, "provider-event", body)).param("at", Timestamp.from(clock.instant())).update()
        return true
    }
    fun processPending(limit: Int = 50) {
        val pending = jdbc.sql("SELECT id,account_id,payload_encrypted FROM receivable_provider_events WHERE processed_at IS NULL AND next_attempt_at<=:now ORDER BY next_attempt_at,received_at LIMIT :limit")
            .param("now", Timestamp.from(clock.instant())).param("limit", limit.coerceIn(1, 100)).query { r, _ -> Triple(r.getObject("id", UUID::class.java),
                r.getObject("account_id", UUID::class.java), r.getString("payload_encrypted")) }.list()
        for ((event, account, encrypted) in pending) {
            try {
                val node = mapper.readTree(secrets.decrypt(account, "provider-event", encrypted))
                val payment = node.path("payment")
                val ref = runCatching { UUID.fromString(payment.path("externalReference").asText()) }.getOrNull()
                val id = jdbc.sql("""SELECT id FROM receivable_instruments WHERE account_id=:account
                    AND (id=:ref OR provider_payment_id=:payment)""").param("account", account)
                    .param("ref", ref).param("payment", payment.path("id").asText()).query(UUID::class.java).optional().orElse(null)
                if (id == null) { failure(event, "UNMATCHED_EVENT"); continue }
                // The event is only a wake-up signal. Never trust redirect state or payload amounts as current facts.
                val reconciled = execution.reconcile(id)
                val known = jdbc.sql("SELECT provider_payment_id IS NOT NULL FROM receivable_instruments WHERE id=:id")
                    .param("id", id).query(Boolean::class.java).single()
                if (!known || !reconciled) { failure(event, "PROVIDER_RESULT_PENDING"); continue }
                jdbc.sql("UPDATE receivable_provider_events SET processed_at=:at,attempts=attempts+1,failure_code=NULL WHERE id=:id")
                    .param("at", Timestamp.from(clock.instant())).param("id", event).update()
            } catch (_: Exception) { failure(event, "PROCESSING_FAILED") }
        }
    }
    /** Bounded recovery also covers lost webhooks and abandoned creation leases. */
    fun recoverPending(limit: Int = 50) {
        jdbc.sql("""SELECT i.id FROM receivable_instruments i JOIN receivable_orders o ON o.id=i.order_id
            WHERE (i.status IN ('CREATING','UNKNOWN','ACTIVE','CONFIRMED','SETTLED','AVAILABLE','DISPUTED','RECOVERY_PENDING','CANCEL_PENDING')
               OR o.status='CANCEL_PENDING') AND i.next_reconcile_at<=:now ORDER BY i.next_reconcile_at,i.created_at LIMIT :limit""")
            .param("now", Timestamp.from(clock.instant())).param("limit", limit.coerceIn(1, 100)).query(UUID::class.java).list().filterNotNull().forEach {
                jdbc.sql("UPDATE receivable_instruments SET next_reconcile_at=:next WHERE id=:id")
                    .param("next", Timestamp.from(clock.instant().plusSeconds(300))).param("id", it).update()
                try { execution.reconcile(it) } catch (_: Exception) { /* Durable state remains available for the next pass. */ }
            }
    }
    private fun failure(id: UUID, code: String) {
        jdbc.sql("UPDATE receivable_provider_events SET attempts=attempts+1,failure_code=:code,next_attempt_at=:next WHERE id=:id")
            .param("code", code).param("next", Timestamp.from(clock.instant().plusSeconds(60))).param("id", id).update()
    }
}
