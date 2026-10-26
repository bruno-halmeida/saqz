package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.receivables.domain.RegistrationStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcPaymentCodeStore(dataSource: DataSource, private val secrets: FinancialSecrets) : PaymentCodeStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()
    private class Holder<T>(val value: T)

    override fun <T> transaction(block: () -> T): T = try { transactions.execute { Holder(block()) }!!.value }
    catch (_: DuplicateKeyException) { throw FinancialRequestConflict() }

    override fun receivingAccount(groupId: UUID): ReceivingAccount? = jdbc.sql("""
        SELECT a.id,a.owner_user_id,a.registration,a.new_operations_enabled,CASE WHEN a.pix_key_ready THEN a.pix_key END AS pix_key,
            EXISTS (SELECT 1 FROM receivable_webhook_credentials w WHERE w.account_id=a.id AND w.state='SUCCEEDED') AS webhook_ready
        FROM receivable_group_links l JOIN receivable_accounts a ON a.id=l.account_id
        WHERE l.group_id=:group AND l.enabled
    """.trimIndent()).param("group", groupId).query { rs, _ ->
        ReceivingAccount(rs.getObject("id", UUID::class.java), rs.getObject("owner_user_id", UUID::class.java),
            RegistrationStatus.valueOf(rs.getString("registration")), rs.getBoolean("new_operations_enabled"),
            rs.getString("pix_key"), rs.getBoolean("webhook_ready"))
    }.optional().orElse(null)

    override fun hasDocument(userId: UUID) = jdbc.sql("SELECT count(*) FROM receivable_payer_documents WHERE user_id=:user")
        .param("user", userId).query(Int::class.java).single() > 0

    override fun document(userId: UUID): String? = jdbc.sql("SELECT document_encrypted FROM receivable_payer_documents WHERE user_id=:user")
        .param("user", userId).query(String::class.java).optional().orElse(null)?.let { secrets.decrypt(userId, "payer-document", it) }

    override fun saveDocument(userId: UUID, cpf: String, at: Instant) {
        jdbc.sql("""
            INSERT INTO receivable_payer_documents(user_id,document_encrypted,document_digest,updated_at) VALUES (:user,:doc,:digest,:at)
            ON CONFLICT(user_id) DO UPDATE SET document_encrypted=EXCLUDED.document_encrypted,document_digest=EXCLUDED.document_digest,updated_at=EXCLUDED.updated_at
        """.trimIndent()).param("user", userId).param("doc", secrets.encrypt(userId, "payer-document", cpf))
            .param("digest", secrets.legalIdentityDigest(cpf)).param("at", Timestamp.from(at)).update()
    }

    override fun requested(payerId: UUID, requestId: UUID): Pair<UUID, String>? = jdbc.sql(
        "SELECT code_id,request_digest FROM payment_code_requests WHERE payer_user_id=:payer AND request_id=:request",
    ).param("payer", payerId).param("request", requestId).query { rs, _ ->
        rs.getObject("code_id", UUID::class.java) to rs.getString("request_digest")
    }.optional().orElse(null)

    override fun lockPayer(groupId: UUID, payerId: UUID) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "payment-code:$groupId:$payerId")
            .query { _, _ -> Unit }.single()
    }

    override fun live(groupId: UUID, payerId: UUID, method: PaymentMethod, chargesKey: String): StoredPaymentCode? = jdbc.sql("""
        SELECT * FROM payment_codes WHERE group_id=:group AND payer_user_id=:payer AND method=:method AND charges_key=:key
            AND status IN ('CREATING','PENDING') ORDER BY created_at DESC LIMIT 1
    """.trimIndent()).param("group", groupId).param("payer", payerId).param("method", method.name).param("key", chargesKey)
        .query(::map).optional().orElse(null)

    override fun insert(code: StoredPaymentCode, chargesKey: String, feeScheduleId: UUID?, at: Instant) {
        jdbc.sql("""
            INSERT INTO payment_codes(id,account_id,group_id,payer_user_id,method,amount_cents,commission_cents,fee_schedule_id,charges_key,status,created_at)
            VALUES (:id,:account,:group,:payer,:method,:amount,:commission,:schedule,:key,:status,:at)
        """.trimIndent()).param("id", code.id).param("account", code.accountId).param("group", code.groupId)
            .param("payer", code.payerId).param("method", code.method.name).param("amount", code.amountCents)
            .param("commission", code.commissionCents).param("schedule", feeScheduleId).param("key", chargesKey)
            .param("status", code.status).param("at", Timestamp.from(at)).update()
        code.chargeIds.forEach {
            jdbc.sql("INSERT INTO payment_code_charges VALUES (:code,:charge)").param("code", code.id).param("charge", it).update()
        }
    }

    override fun remember(payerId: UUID, requestId: UUID, digest: String, codeId: UUID) {
        jdbc.sql("INSERT INTO payment_code_requests VALUES (:payer,:request,:digest,:code)")
            .param("payer", payerId).param("request", requestId).param("digest", digest).param("code", codeId).update()
    }

    override fun find(id: UUID): StoredPaymentCode? = jdbc.sql("SELECT * FROM payment_codes WHERE id=:id").param("id", id)
        .query(::map).optional().orElse(null)

    override fun lock(id: UUID): StoredPaymentCode? = jdbc.sql("SELECT * FROM payment_codes WHERE id=:id FOR UPDATE").param("id", id)
        .query(::map).optional().orElse(null)

    override fun created(id: UUID, providerQrId: String?, providerPaymentId: String?, pix: PixCode?, checkoutUrl: String?) {
        val account = find(id)!!.accountId
        val payload = mapper.writeValueAsString(mapOf("payload" to pix?.payload, "image" to pix?.image, "checkoutUrl" to checkoutUrl))
        jdbc.sql("""UPDATE payment_codes SET status='PENDING',provider_qr_id=:qr,provider_payment_id=:payment,payload_encrypted=:payload
            WHERE id=:id AND status='CREATING'""").param("qr", providerQrId).param("payment", providerPaymentId)
            .param("payload", secrets.encrypt(account, "payment-code", payload)).param("id", id).update()
    }

    override fun changeStatus(id: UUID, from: Set<String>, to: String, at: Instant): Boolean = jdbc.sql("""
        UPDATE payment_codes SET status=:to,paid_at=CASE WHEN :to='PAID' THEN :at ELSE paid_at END WHERE id=:id AND status IN (:from)
    """.trimIndent()).param("to", to).param("at", Timestamp.from(at)).param("id", id).param("from", from).update() == 1

    override fun customer(accountId: UUID, payerId: UUID, name: String, cpf: String): CardCustomer {
        val payer = mapper.writeValueAsString(PaymentPayer(name, cpf))
        jdbc.sql("""
            INSERT INTO receivable_customers(account_id,payer_id,external_reference,payer_data_encrypted,state)
            VALUES (:account,:payer,:reference,:data,'READY') ON CONFLICT(account_id,payer_id) DO NOTHING
        """.trimIndent()).param("account", accountId).param("payer", payerId).param("reference", UUID.randomUUID())
            .param("data", secrets.encrypt(accountId, "payment-payer", payer)).update()
        return jdbc.sql("SELECT * FROM receivable_customers WHERE account_id=:account AND payer_id=:payer FOR UPDATE")
            .param("account", accountId).param("payer", payerId).query { rs, _ ->
                val stored = mapper.readValue<PaymentPayer>(secrets.decrypt(accountId, "payment-payer", rs.getString("payer_data_encrypted")))
                CardCustomer(rs.getObject("external_reference", UUID::class.java), rs.getString("provider_customer_id"), stored.name, stored.cpfCnpj)
            }.single()
    }

    override fun saveCustomer(accountId: UUID, payerId: UUID, providerId: String) {
        jdbc.sql("""UPDATE receivable_customers SET provider_customer_id=:id,state='SUCCEEDED'
            WHERE account_id=:account AND payer_id=:payer AND (provider_customer_id IS NULL OR provider_customer_id=:id)""")
            .param("id", providerId).param("account", accountId).param("payer", payerId).update()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): StoredPaymentCode {
        val id = rs.getObject("id", UUID::class.java)
        val account = rs.getObject("account_id", UUID::class.java)
        val payload = rs.getString("payload_encrypted")?.let { mapper.readValue<Map<String, String?>>(secrets.decrypt(account, "payment-code", it)) }
        val charges = jdbc.sql("SELECT charge_id FROM payment_code_charges WHERE code_id=:id ORDER BY charge_id").param("id", id)
            .query(UUID::class.java).list().filterNotNull()
        return StoredPaymentCode(id, account, rs.getObject("group_id", UUID::class.java), rs.getObject("payer_user_id", UUID::class.java),
            PaymentMethod.valueOf(rs.getString("method")), rs.getLong("amount_cents"), rs.getLong("commission_cents"), charges,
            rs.getString("status"), rs.getString("provider_qr_id"), rs.getString("provider_payment_id"),
            payload?.get("payload")?.let { PixCode(it, payload["image"].orEmpty()) }, payload?.get("checkoutUrl"))
    }
}

/** Cancelamento remoto dos códigos cancelados ao desligar ou excluir o grupo (G, DEB-029): QR estático e cobrança de cartão. */
class JdbcPaymentCodeCancellations(dataSource: DataSource, private val operations: FinancialOperationStore,
    private val provider: PaymentCodeProvider, private val clock: java.time.Clock) {
    private val jdbc = JdbcClient.create(dataSource)

    fun recoverPending() {
        br.com.saqz.sharedkernel.job.JobLog.frequent("payment_code_cancellations", {
            jdbc.sql("""SELECT o.account_id,o.id,c.provider_qr_id,c.provider_payment_id FROM receivable_operations o
                JOIN payment_codes c ON c.id=o.resource_id WHERE o.kind='CANCEL_PAYMENT_CODE'
                AND ((o.status IN ('READY','UNKNOWN') AND o.next_attempt_at<=:now) OR (o.status='RUNNING' AND o.lease_until<=:now)) LIMIT 50""")
                .param("now", Timestamp.from(clock.instant())).query { rs, _ ->
                    listOf(rs.getObject("account_id", UUID::class.java), rs.getObject("id", UUID::class.java),
                        rs.getString("provider_qr_id"), rs.getString("provider_payment_id"))
                }.list().count { (account, operation, qr, payment) -> run(account as UUID, operation as UUID, qr as String?, payment as String?) }
        }) { mapOf("cancelled" to it) }
    }

    private fun run(account: UUID, operation: UUID, qr: String?, payment: String?): Boolean {
        val now = clock.instant()
        val claim = operations.claim(account, operation, now, now.plusSeconds(90)) ?: return false
        val done = try {
            (qr == null || provider.cancelPixQr(account, qr)) && (payment == null || provider.cancelPayment(account, payment))
        } catch (_: Exception) { false }
        operations.finish(claim, if (done) OperationStatus.SUCCEEDED else OperationStatus.UNKNOWN, if (done) (payment ?: qr ?: "none") else null,
            now, now.plusSeconds(300))
        return done
    }
}
