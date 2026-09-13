package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeQuote
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class JdbcPaymentStore(dataSource: DataSource, private val secrets: FinancialSecrets) : PaymentStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()
    private class Box<T>(val value: T)
    override fun <T> transaction(block: () -> T): T = try { tx.execute { Box(block()) }!!.value }
        catch (_: DuplicateKeyException) { throw FinancialRequestConflict() }
    override fun webhookReady(accountId: UUID) = jdbc.sql("SELECT count(*) FROM receivable_webhook_credentials WHERE account_id=:id AND state='SUCCEEDED'")
        .param("id", accountId).query(Int::class.java).single() == 1
    override fun order(id: UUID): PaymentOrder? = jdbc.sql("SELECT * FROM receivable_orders WHERE id=:id")
        .param("id", id).query(::orderRow).optional().orElse(null)
    override fun orderForCharge(chargeId: UUID): PaymentOrder? = jdbc.sql("SELECT * FROM receivable_orders WHERE group_charge_id=:id")
        .param("id", chargeId).query(::orderRow).optional().orElse(null)
    override fun ordersForPayer(payerId: UUID, after: UUID?): List<PaymentOrder> {
        val cursor = if (after == null) "" else """AND (issued_at,id) <
            (SELECT issued_at,id FROM receivable_orders WHERE id=:after AND member_user_id=:payer)"""
        val query = jdbc.sql("""SELECT * FROM receivable_orders WHERE member_user_id=:payer $cursor
            ORDER BY issued_at DESC,id DESC LIMIT 51""").param("payer", payerId)
        if (after != null) query.param("after", after)
        return query.query(::orderRow).list()
    }
    private fun orderRow(r: ResultSet, ignored: Int): PaymentOrder {
        val id = r.getObject("id", UUID::class.java)
        return PaymentOrder(id, r.getObject("account_id", UUID::class.java), r.getObject("group_charge_id", UUID::class.java),
            r.getObject("group_id", UUID::class.java), r.getObject("member_user_id", UUID::class.java),
            r.getObject("due_date", LocalDate::class.java), r.getString("status"), quotes(id), r.getString("approval_fingerprint").orEmpty())
    }
    private fun quotes(order: UUID): List<FeeQuote> = jdbc.sql("SELECT quote::text FROM receivable_order_quotes WHERE order_id=:id ORDER BY method DESC")
        .param("id", order).query(String::class.java).list().map { mapper.readValue<FeeQuote>(requireNotNull(it)) }
    override fun insertOrder(id: UUID, review: ChargePaymentReview, request: FinancialRequest, at: Instant): PaymentOrder {
        jdbc.sql("""INSERT INTO receivable_orders(id,account_id,group_id,member_user_id,group_charge_id,due_date,status,
            base_cents,request_id,issued_at,approval_fingerprint,approved_by,billing_month)
            VALUES (:id,:account,:group,:payer,:charge,:due,'ISSUED',:base,:request,:at,:fingerprint,:actor,:month)""")
            .param("id", id).param("account", review.accountId).param("group", review.groupId).param("payer", review.payerId)
            .param("charge", review.chargeId).param("due", review.dueDate).param("base", review.quotes.first().baseCents)
            .param("request", request.requestId).param("at", Timestamp.from(at)).param("fingerprint", review.fingerprint)
            .param("actor", request.actorUserId).param("month", review.billingMonth).update()
        review.quotes.forEach { quote -> jdbc.sql("INSERT INTO receivable_order_quotes VALUES (:id,:method,CAST(:quote AS jsonb))")
            .param("id", id).param("method", quote.method.name).param("quote", mapper.writeValueAsString(quote)).update() }
        return order(id)!!
    }
    override fun instruments(orderId: UUID): List<PaymentInstrument> = jdbc.sql("SELECT * FROM receivable_instruments WHERE order_id=:id ORDER BY created_at,id")
        .param("id", orderId).query(::instrumentRow).list()
    fun instrument(id: UUID): PaymentInstrument? = jdbc.sql("SELECT * FROM receivable_instruments WHERE id=:id")
        .param("id", id).query(::instrumentRow).optional().orElse(null)
    private fun instrumentRow(r: ResultSet, ignored: Int): PaymentInstrument {
        val account = r.getObject("account_id", UUID::class.java)
        val order = r.getObject("order_id", UUID::class.java)
        val payload = r.getString("payload_encrypted")?.let { mapper.readTree(secrets.decrypt(account, "payment-instrument", it)) }
        return PaymentInstrument(r.getObject("id", UUID::class.java), account, order,
            quotes(order).single { it.method.name == r.getString("method") }, r.getString("status"),
            r.getString("provider_payment_id"), r.getString("provider_checkout_id"),
            payload?.get("pixPayload")?.asText(), payload?.get("pixImage")?.asText(), payload?.get("checkoutUrl")?.asText(),
            r.getBoolean("confirmed"), r.getBoolean("settled"), r.getBoolean("available"), r.getBoolean("split_settled"), r.getTimestamp("expires_at")?.toInstant())
    }
    override fun instrumentRequest(accountId: UUID, request: FinancialRequest, digest: String): PaymentInstrument? {
        val row = jdbc.sql("SELECT id,request_digest FROM receivable_instruments WHERE account_id=:account AND request_id=:request")
            .param("account", accountId).param("request", request.requestId).query { r, _ -> r.getObject("id", UUID::class.java) to r.getString("request_digest") }
            .optional().orElse(null) ?: return null
        if (row.second != digest) throw FinancialRequestConflict()
        return instrument(row.first)
    }
    override fun insertInstrument(order: PaymentOrder, quote: FeeQuote, request: FinancialRequest, digest: String, payer: PaymentPayer, at: Instant): PaymentInstrument {
        val acceptance = UUID.randomUUID(); val id = UUID.randomUUID()
        jdbc.sql("""INSERT INTO receivable_customers(account_id,payer_id,external_reference,payer_data_encrypted,state)
            VALUES (:account,:payer,:reference,:data,'READY') ON CONFLICT(account_id,payer_id) DO UPDATE SET payer_data_encrypted=EXCLUDED.payer_data_encrypted,
                external_reference=EXCLUDED.external_reference,state='READY' WHERE receivable_customers.state='REJECTED'""")
            .param("account", order.accountId).param("payer", order.payerId).param("reference", UUID.randomUUID())
            .param("data", secrets.encrypt(order.accountId, "payment-payer", mapper.writeValueAsString(payer))).update()
        val original = jdbc.sql("SELECT payer_data_encrypted FROM receivable_customers WHERE account_id=:account AND payer_id=:payer")
            .param("account", order.accountId).param("payer", order.payerId).query(String::class.java).single()
        if (mapper.readValue<PaymentPayer>(secrets.decrypt(order.accountId, "payment-payer", original)) != payer) throw FinancialRequestConflict()
        jdbc.sql("""INSERT INTO receivable_terms_acceptances(id,account_id,actor_user_id,terms_version,purpose,request_id,accepted_at)
            VALUES (:id,:account,:actor,:terms,'CHARGE',:request,:at)""")
            .param("id", acceptance).param("account", order.accountId).param("actor", request.actorUserId)
            .param("terms", quote.termsVersion).param("request", request.requestId).param("at", Timestamp.from(at)).update()
        jdbc.sql("""INSERT INTO receivable_instruments(id,account_id,order_id,method,fee_schedule_id,base_cents,fees_cents,total_cents,
            commission_cents,expected_provider_fee_cents,expected_net_cents,acceptance_id,status,created_at,request_id,request_digest)
            VALUES (:id,:account,:order,:method,:fee,:base,:fees,:total,:commission,:provider,:net,:acceptance,'CREATING',:at,:request,:digest)""")
            .param("id", id).param("account", order.accountId).param("order", order.id).param("method", quote.method.name)
            .param("fee", quote.feeScheduleId).param("base", quote.baseCents).param("fees", quote.feesCents).param("total", quote.totalCents)
            .param("commission", quote.commissionCents).param("provider", quote.providerFeeCents).param("net", quote.expectedNetCents)
            .param("acceptance", acceptance).param("at", Timestamp.from(at)).param("request", request.requestId).param("digest", digest).update()
        jdbc.sql("""INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES (:id,:account,:request,:actor,'CREATE_INSTRUMENT',:id,:digest,'READY',:at,:at,:at)""")
            .param("id", id).param("account", order.accountId).param("request", request.requestId).param("actor", request.actorUserId)
            .param("digest", digest).param("at", Timestamp.from(at)).update()
        return instrument(id)!!
    }
    override fun changeOrder(id: UUID, status: String) {
        jdbc.sql("UPDATE receivable_orders SET status=:status WHERE id=:id").param("status", status).param("id", id).update()
    }
    override fun registerLocal(accountId: UUID, resource: UUID, kind: String, request: FinancialRequest, digest: String, at: Instant): UUID {
        val row = jdbc.sql("SELECT actor_user_id,request_digest,resource_id FROM receivable_operations WHERE account_id=:account AND request_id=:request")
            .param("account", accountId).param("request", request.requestId).query { r, _ -> Triple(r.getObject("actor_user_id", UUID::class.java),
                r.getString("request_digest"), r.getObject("resource_id", UUID::class.java)) }.optional().orElse(null)
        if (row != null) {
            if (row.first != request.actorUserId || row.second != digest || row.third != resource) throw FinancialRequestConflict()
            return row.third
        }
        jdbc.sql("""INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES (:id,:account,:request,:actor,:kind,:resource,:digest,'SUCCEEDED',:at,:at,:at)""")
            .param("id", UUID.randomUUID()).param("account", accountId).param("request", request.requestId).param("actor", request.actorUserId)
            .param("kind", kind).param("resource", resource).param("digest", digest).param("at", Timestamp.from(at)).update()
        return resource
    }
    override fun pixRenewalRequest(accountId: UUID, instrumentId: UUID, dueDate: LocalDate,
                                   request: FinancialRequest, digest: String): Boolean {
        val previous = jdbc.sql("""SELECT actor_user_id,request_digest,instrument_id,due_date FROM receivable_pix_renewals
            WHERE account_id=:account AND request_id=:request""").param("account", accountId).param("request", request.requestId)
            .query { r, _ -> listOf(r.getObject("actor_user_id", UUID::class.java), r.getString("request_digest"),
                r.getObject("instrument_id", UUID::class.java), r.getObject("due_date", LocalDate::class.java)) }.optional().orElse(null)
        if (previous == null) return false
        if (previous[0] != request.actorUserId || previous[1] != digest || previous[2] != instrumentId || previous[3] != dueDate)
            throw FinancialRequestConflict()
        return true
    }
    override fun registerPixRenewal(instrumentId: UUID, dueDate: LocalDate, request: FinancialRequest, digest: String, at: Instant) {
        val instrument = instrument(instrumentId) ?: throw IllegalArgumentException()
        jdbc.sql("""INSERT INTO receivable_pix_renewals(id,account_id,order_id,instrument_id,request_id,actor_user_id,
            request_digest,due_date,status,next_attempt_at,created_at,updated_at)
            VALUES (:id,:account,:order,:instrument,:request,:actor,:digest,:due,'READY',:at,:at,:at)""")
            .param("id", UUID.randomUUID()).param("account", instrument.accountId).param("order", instrument.orderId)
            .param("instrument", instrumentId).param("request", request.requestId).param("actor", request.actorUserId)
            .param("digest", digest).param("due", dueDate).param("at", Timestamp.from(at)).update()
    }
    override fun pixRenewal(orderId: UUID, requestId: UUID, actorUserId: UUID): StoredPixRenewal? = jdbc.sql("""SELECT instrument_id,due_date,status
        FROM receivable_pix_renewals WHERE order_id=:order AND request_id=:request AND actor_user_id=:actor""")
        .param("order", orderId).param("request", requestId).param("actor", actorUserId)
        .query { r, _ -> StoredPixRenewal(r.getObject("instrument_id", UUID::class.java),
            r.getObject("due_date", LocalDate::class.java), r.getString("status")) }.optional().orElse(null)
}
