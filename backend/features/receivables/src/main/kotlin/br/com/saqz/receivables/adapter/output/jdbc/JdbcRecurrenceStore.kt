package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeQuote
import br.com.saqz.receivables.domain.PaymentMethod
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

class JdbcRecurrenceStore(dataSource: DataSource, private val secrets: FinancialSecrets) : RecurrenceStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()
    private class Box<T>(val value: T)
    override fun <T> transaction(block: () -> T): T = try { tx.execute { Box(block()) }!!.value }
        catch (_: DuplicateKeyException) { throw FinancialRequestConflict() }

    override fun monthlyTerms(accountId: UUID, groupId: UUID, memberId: UUID): MonthlyRecurrenceTerms? = jdbc.sql("""
        SELECT coalesce(m.monthly_fee_cents,g.monthly_fee_cents) fee,
               coalesce(m.monthly_due_day,g.monthly_due_day) due_day,m.active
        FROM receivable_group_links l
        JOIN access_groups g ON g.id=l.group_id AND g.deleted_at IS NULL
        JOIN group_memberships m ON m.group_id=g.id AND m.user_id=:member AND m.membership_type='MENSALISTA'
        WHERE l.account_id=:account AND l.group_id=:group AND l.enabled
        """).param("account", accountId).param("group", groupId).param("member", memberId).query { r, _ ->
        val fee = r.getObject("fee") as? Number ?: return@query null
        val due = r.getObject("due_day") as? Number ?: return@query null
        MonthlyRecurrenceTerms(fee.toLong(), due.toInt(), r.getBoolean("active"))
    }.optional().orElse(null)
    override fun webhookReady(accountId: UUID) = jdbc.sql("SELECT count(*) FROM receivable_webhook_credentials WHERE account_id=:id AND state='SUCCEEDED'")
        .param("id", accountId).query(Int::class.java).single() == 1

    override fun find(id: UUID): RecurrenceAuthorization? = jdbc.sql("SELECT * FROM receivable_recurrences WHERE id=:id")
        .param("id", id).query(::row).optional().orElse(null)
    override fun live(groupId: UUID, memberId: UUID): RecurrenceAuthorization? = jdbc.sql("""SELECT * FROM receivable_recurrences
        WHERE group_id=:group AND member_user_id=:member AND status IN ('AUTHORIZING','ACTIVE','STOP_PENDING')""")
        .param("group", groupId).param("member", memberId).query(::row).optional().orElse(null)
    override fun current(accountId: UUID, groupId: UUID, memberId: UUID): RecurrenceAuthorization? = jdbc.sql("""SELECT *
        FROM receivable_recurrences WHERE account_id=:account AND group_id=:group AND member_user_id=:member
        ORDER BY CASE WHEN status='STOPPED' THEN 1 ELSE 0 END,created_at DESC,id DESC LIMIT 1""")
        .param("account", accountId).param("group", groupId).param("member", memberId).query(::row).optional().orElse(null)
    override fun byActorRequest(requestId: UUID, actorUserId: UUID): RecurrenceAuthorization? = jdbc.sql("""SELECT *
        FROM receivable_recurrences WHERE request_id=:request AND approved_by=:actor""")
        .param("request", requestId).param("actor", actorUserId).query(::row).optional().orElse(null)
    override fun byRequest(accountId: UUID, requestId: UUID): Pair<RecurrenceAuthorization, String>? = jdbc.sql("""
        SELECT * FROM receivable_recurrences WHERE account_id=:account AND request_id=:request""")
        .param("account", accountId).param("request", requestId).query { r, i -> row(r, i) to r.getString("request_digest") }
        .optional().orElse(null)

    override fun insert(review: RecurrenceReview, quote: FeeQuote, payer: PaymentPayer, request: FinancialRequest,
                        digest: String, at: Instant): RecurrenceAuthorization {
        val acceptance = UUID.randomUUID(); val id = UUID.randomUUID()
        jdbc.sql("""INSERT INTO receivable_customers(account_id,payer_id,external_reference,payer_data_encrypted,state)
            VALUES (:account,:payer,:reference,:data,'READY') ON CONFLICT(account_id,payer_id) DO UPDATE
            SET payer_data_encrypted=EXCLUDED.payer_data_encrypted WHERE receivable_customers.state='REJECTED'""")
            .param("account", review.accountId).param("payer", review.memberUserId).param("reference", UUID.randomUUID())
            .param("data", secrets.encrypt(review.accountId, "payment-payer", mapper.writeValueAsString(payer))).update()
        val encrypted = jdbc.sql("SELECT payer_data_encrypted FROM receivable_customers WHERE account_id=:account AND payer_id=:payer")
            .param("account", review.accountId).param("payer", review.memberUserId).query(String::class.java).single()
        if (mapper.readValue<PaymentPayer>(secrets.decrypt(review.accountId, "payment-payer", encrypted)) != payer) throw FinancialRequestConflict()
        jdbc.sql("""INSERT INTO receivable_terms_acceptances(id,account_id,actor_user_id,terms_version,purpose,request_id,accepted_at)
            VALUES (:id,:account,:actor,:terms,'RECURRENCE',:request,:at)""")
            .param("id", acceptance).param("account", review.accountId).param("actor", request.actorUserId)
            .param("terms", review.termsVersion).param("request", request.requestId).param("at", Timestamp.from(at)).update()
        jdbc.sql("""INSERT INTO receivable_recurrences(id,account_id,group_id,member_user_id,method,fee_schedule_id,acceptance_id,
            base_cents,total_cents,first_due_date,status,created_at,request_id,request_digest,approval_fingerprint,approved_by,
            quote,payer_data_encrypted,next_reconcile_at,updated_at)
            VALUES (:id,:account,:group,:member,:method,:fee,:acceptance,:base,:total,:due,'AUTHORIZING',:at,:request,:digest,
            :fingerprint,:actor,CAST(:quote AS jsonb),:payerData,:at,:at)""")
            .param("id", id).param("account", review.accountId).param("group", review.groupId).param("member", review.memberUserId)
            .param("method", review.method.name).param("fee", review.feeScheduleId).param("acceptance", acceptance)
            .param("base", review.baseCents).param("total", review.totalCents).param("due", review.firstDueDate)
            .param("at", Timestamp.from(at)).param("request", request.requestId).param("digest", digest)
            .param("fingerprint", review.fingerprint).param("actor", request.actorUserId).param("quote", mapper.writeValueAsString(quote))
            .param("payerData", encrypted).update()
        return find(id)!!
    }

    override fun requestStop(id: UUID, request: FinancialRequest, reason: String, digest: String, at: Instant): RecurrenceAuthorization {
        val recurrence = find(id) ?: throw IllegalArgumentException()
        val previous = jdbc.sql("SELECT actor_user_id,request_digest,recurrence_id FROM receivable_recurrence_cutoffs WHERE account_id=:account AND request_id=:request")
            .param("account", recurrence.recurrence.accountId).param("request", request.requestId)
            .query { r, _ -> Triple(r.getObject("actor_user_id", UUID::class.java), r.getString("request_digest"), r.getObject("recurrence_id", UUID::class.java)) }
            .optional().orElse(null)
        if (previous != null) {
            if (previous.first != request.actorUserId || previous.second != digest || previous.third != id) throw FinancialRequestConflict()
            return find(id)!!
        }
        jdbc.sql("""INSERT INTO receivable_recurrence_cutoffs(recurrence_id,account_id,request_id,actor_user_id,reason,request_digest,requested_at)
            VALUES (:id,:account,:request,:actor,:reason,:digest,:at)""").param("id", id).param("account", recurrence.recurrence.accountId)
            .param("request", request.requestId).param("actor", request.actorUserId).param("reason", reason).param("digest", digest)
            .param("at", Timestamp.from(at)).update()
        jdbc.sql("""UPDATE receivable_recurrences SET status='STOP_PENDING',cutoff_at=coalesce(cutoff_at,:at),
            remote_state='READY',next_reconcile_at=:at,updated_at=:at WHERE id=:id AND status<>'STOPPED'""")
            .param("id", id).param("at", Timestamp.from(at)).update()
        return find(id)!!
    }

    override fun claim(id: UUID, at: Instant, leaseUntil: Instant): RecurrenceClaim? = transaction {
        val current = jdbc.sql("""SELECT * FROM receivable_recurrences WHERE id=:id AND
            ((remote_state IN ('READY','UNKNOWN') AND next_reconcile_at<=:at) OR (remote_state='RUNNING' AND remote_lease_until<=:at))
            FOR UPDATE SKIP LOCKED""").param("id", id).param("at", Timestamp.from(at)).query(::row).optional().orElse(null) ?: return@transaction null
        val token = UUID.randomUUID()
        jdbc.sql("""UPDATE receivable_recurrences SET remote_state='RUNNING',remote_attempts=remote_attempts+1,
            remote_lease_token=:token,remote_lease_until=:lease,updated_at=:at WHERE id=:id""")
            .param("token", token).param("lease", Timestamp.from(leaseUntil)).param("at", Timestamp.from(at)).param("id", id).update()
        RecurrenceClaim(current, token, jdbc.sql("SELECT remote_attempts FROM receivable_recurrences WHERE id=:id")
            .param("id", id).query(Int::class.java).single() > 1)
    }

    override fun finish(claim: RecurrenceClaim, result: ProviderRecurrenceResult?, stopped: Boolean, at: Instant): Boolean {
        val succeeded = if (stopped) result?.status == "STOPPED" else result?.status == "ACTIVE"
        val changed = jdbc.sql("""UPDATE receivable_recurrences SET status=:status,
            provider_subscription_id=coalesce(:subscription,provider_subscription_id),provider_checkout_id=coalesce(:checkout,provider_checkout_id),
            hosted_checkout_url=coalesce(:url,hosted_checkout_url),remote_state=:remote,next_reconcile_at=:next,
            remote_lease_token=NULL,remote_lease_until=NULL,updated_at=:at WHERE id=:id AND remote_lease_token=:token AND remote_state='RUNNING'""")
            .param("status", if (stopped && succeeded) "STOPPED" else if (stopped) "STOP_PENDING" else result?.status ?: claim.authorization.recurrence.status)
            .param("subscription", result?.subscriptionId).param("checkout", result?.checkoutId).param("url", result?.checkoutUrl)
            .param("remote", if (succeeded) "SUCCEEDED" else "UNKNOWN")
            .param("next", Timestamp.from(at.plusSeconds(if (succeeded) 300 else 60))).param("at", Timestamp.from(at))
            .param("id", claim.authorization.recurrence.id).param("token", claim.token).update()
        if (changed == 1 && stopped && succeeded) {
            val id = claim.authorization.recurrence.id
            jdbc.sql("""UPDATE receivable_instruments i SET status='CANCELLED' FROM receivable_orders o,receivable_recurrences r
                WHERE i.order_id=o.id AND o.recurrence_id=r.id AND r.id=:id
                AND o.due_date>(r.cutoff_at AT TIME ZONE 'America/Sao_Paulo')::date
                AND i.status IN ('CREATING','ACTIVE','UNKNOWN','EXPIRED')""").param("id", id).update()
            jdbc.sql("""INSERT INTO group_charge_events(id,charge_id,group_id,actor_user_id,old_status,new_status,note,occurred_at)
                SELECT gen_random_uuid(),c.id,c.group_id,r.member_user_id,'PENDING','CANCELLED','Recorrência encerrada antes da competência',:at
                FROM receivable_orders o JOIN receivable_recurrences r ON r.id=o.recurrence_id
                JOIN group_charges c ON c.id=o.group_charge_id
                WHERE r.id=:id AND o.due_date>(r.cutoff_at AT TIME ZONE 'America/Sao_Paulo')::date AND c.status='PENDING'""")
                .param("id", id).param("at", Timestamp.from(at)).update()
            jdbc.sql("""UPDATE group_charges c SET status='CANCELLED',electronic_order_id=NULL,version=version+1,updated_at=:at
                FROM receivable_orders o,receivable_recurrences r WHERE c.id=o.group_charge_id AND o.recurrence_id=r.id AND r.id=:id
                AND o.due_date>(r.cutoff_at AT TIME ZONE 'America/Sao_Paulo')::date AND c.status='PENDING'""")
                .param("id", id).param("at", Timestamp.from(at)).update()
            jdbc.sql("""UPDATE receivable_orders o SET status='CANCELLED' FROM receivable_recurrences r
                WHERE o.recurrence_id=r.id AND r.id=:id AND o.due_date>(r.cutoff_at AT TIME ZONE 'America/Sao_Paulo')::date
                AND o.status IN ('ISSUED','CANCEL_PENDING')""").param("id", id).update()
            jdbc.sql("UPDATE receivable_recurrence_cutoffs SET completed_at=:at WHERE recurrence_id=:id")
                .param("at", Timestamp.from(at)).param("id", id).update()
        }
        return changed == 1
    }

    override fun recoveryDue(at: Instant, limit: Int): List<UUID> = jdbc.sql("""SELECT id FROM receivable_recurrences
        WHERE status IN ('AUTHORIZING','STOP_PENDING') AND remote_state IN ('READY','UNKNOWN') AND next_reconcile_at<=:at
        ORDER BY next_reconcile_at,id LIMIT :limit""").param("at", Timestamp.from(at)).param("limit", limit).query(UUID::class.java).list().filterNotNull()
    override fun active(limit: Int): List<UUID> = jdbc.sql("SELECT id FROM receivable_recurrences WHERE status='ACTIVE' ORDER BY updated_at,id LIMIT :limit")
        .param("limit", limit).query(UUID::class.java).list().filterNotNull()
    override fun cutoffCandidates(at: Instant, limit: Int): List<Pair<UUID, String>> = jdbc.sql("""
        SELECT r.id,CASE WHEN g.id IS NULL THEN 'GROUP_DELETED' WHEN NOT l.enabled THEN 'GROUP_DISABLED' ELSE 'MEMBER_INACTIVE' END reason
        FROM receivable_recurrences r
        LEFT JOIN access_groups g ON g.id=r.group_id AND g.deleted_at IS NULL
        LEFT JOIN receivable_group_links l ON l.account_id=r.account_id AND l.group_id=r.group_id
        LEFT JOIN group_memberships m ON m.group_id=r.group_id AND m.user_id=r.member_user_id
        WHERE r.status='ACTIVE' AND (g.id IS NULL OR coalesce(l.enabled,false)=false OR coalesce(m.active,false)=false)
        ORDER BY r.updated_at,r.id LIMIT :limit""").param("limit", limit).query { r, _ -> r.getObject("id", UUID::class.java) to r.getString("reason") }.list()

    override fun materialize(recurrence: RecurrenceAuthorization, payments: List<ProviderRecurringPayment>, at: Instant): List<UUID> {
        val result = mutableListOf<UUID>()
        payments.sortedBy { it.dueDate }.forEach { payment ->
            require(payment.subscriptionId == recurrence.recurrence.providerSubscriptionId && payment.method == recurrence.recurrence.method && payment.totalCents == recurrence.quote.totalCents)
            val month = payment.dueDate.withDayOfMonth(1)
            val chargeId = UUID.nameUUIDFromBytes("recurrence-charge:${recurrence.recurrence.id}:$month".toByteArray())
            val orderId = UUID.nameUUIDFromBytes("recurrence-order:${recurrence.recurrence.id}:$month".toByteArray())
            val instrumentId = UUID.nameUUIDFromBytes("recurrence-instrument:${recurrence.recurrence.id}:${payment.id}".toByteArray())
            jdbc.sql("""INSERT INTO group_charges(id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status,
                created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name)
                SELECT :id,:group,:member,'MONTHLY',:month,:amount,:due,'PENDING',:member,:member,:at,:at,coalesce(nickname,display_name)
                FROM access_users WHERE id=:member ON CONFLICT(group_id,billing_month,member_user_id) WHERE kind='MONTHLY' DO NOTHING""")
                .param("id", chargeId).param("group", recurrence.recurrence.groupId).param("member", recurrence.recurrence.memberUserId)
                .param("month", month).param("amount", recurrence.quote.baseCents).param("due", payment.dueDate).param("at", Timestamp.from(at)).update()
            val actualCharge = jdbc.sql("SELECT id FROM group_charges WHERE group_id=:group AND billing_month=:month AND member_user_id=:member")
                .param("group", recurrence.recurrence.groupId).param("month", month).param("member", recurrence.recurrence.memberUserId)
                .query(UUID::class.java).single()
            val inserted = jdbc.sql("""INSERT INTO receivable_orders(id,account_id,group_id,member_user_id,group_charge_id,recurrence_id,billing_month,
                due_date,status,base_cents,request_id,issued_at,approval_fingerprint,approved_by)
                VALUES (:id,:account,:group,:member,:charge,:recurrence,:month,:due,'ISSUED',:base,:request,:at,:fingerprint,:member)
                ON CONFLICT(group_id,member_user_id,billing_month) WHERE billing_month IS NOT NULL DO NOTHING""")
                .param("id", orderId).param("account", recurrence.recurrence.accountId).param("group", recurrence.recurrence.groupId)
                .param("member", recurrence.recurrence.memberUserId).param("charge", actualCharge).param("recurrence", recurrence.recurrence.id)
                .param("month", month).param("due", payment.dueDate).param("base", recurrence.quote.baseCents)
                .param("request", UUID.nameUUIDFromBytes("recurrence-order-request:${recurrence.recurrence.id}:$month".toByteArray()))
                .param("at", Timestamp.from(at)).param("fingerprint", paymentDigest("${recurrence.recurrence.id}:$month:${recurrence.quote}" )).update()
            if (inserted == 1) {
                jdbc.sql("INSERT INTO receivable_order_quotes VALUES (:id,:method,CAST(:quote AS jsonb))")
                    .param("id", orderId).param("method", recurrence.quote.method.name).param("quote", mapper.writeValueAsString(recurrence.quote)).update()
                jdbc.sql("UPDATE group_charges SET electronic_order_id=:order WHERE id=:charge AND status='PENDING' AND electronic_order_id IS NULL")
                    .param("order", orderId).param("charge", actualCharge).update()
            }
            val actualOrder = jdbc.sql("SELECT id FROM receivable_orders WHERE group_id=:group AND member_user_id=:member AND billing_month=:month")
                .param("group", recurrence.recurrence.groupId).param("member", recurrence.recurrence.memberUserId).param("month", month)
                .query(UUID::class.java).single()
            val instrumentStatus = when (payment.status) { "CONFIRMED" -> "CONFIRMED"; "RECEIVED" -> "AVAILABLE"; "REFUNDED" -> "REFUNDED"; "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE" -> "CHARGEBACK"; else -> "ACTIVE" }
            jdbc.sql("""INSERT INTO receivable_instruments(id,account_id,order_id,method,fee_schedule_id,base_cents,fees_cents,total_cents,
                commission_cents,expected_provider_fee_cents,expected_net_cents,acceptance_id,provider_payment_id,status,created_at,
                request_id,request_digest,confirmed,settled,available,split_settled)
                SELECT :id,:account,:order,:method,:fee,:base,:fees,:total,:commission,:provider,:net,r.acceptance_id,:payment,:status,:at,
                :request,:digest,false,false,false,false FROM receivable_recurrences r WHERE r.id=:recurrence
                ON CONFLICT(account_id,provider_payment_id) DO NOTHING""").param("id", instrumentId).param("account", recurrence.recurrence.accountId)
                .param("order", actualOrder).param("method", recurrence.quote.method.name).param("fee", recurrence.quote.feeScheduleId)
                .param("base", recurrence.quote.baseCents).param("fees", recurrence.quote.feesCents).param("total", recurrence.quote.totalCents)
                .param("commission", recurrence.quote.commissionCents).param("provider", recurrence.quote.providerFeeCents).param("net", recurrence.quote.expectedNetCents)
                .param("payment", payment.id).param("status", instrumentStatus).param("at", Timestamp.from(at))
                .param("request", UUID.nameUUIDFromBytes("recurrence-instrument-request:${recurrence.recurrence.id}:${payment.id}".toByteArray()))
                .param("digest", paymentDigest("RECURRENCE_PAYMENT:${recurrence.recurrence.id}:${payment.id}" )).param("recurrence", recurrence.recurrence.id).update()
            result += jdbc.sql("SELECT id FROM receivable_instruments WHERE account_id=:account AND provider_payment_id=:payment")
                .param("account", recurrence.recurrence.accountId).param("payment", payment.id).query(UUID::class.java).single()
        }
        return result
    }

    private fun row(r: ResultSet, ignored: Int): RecurrenceAuthorization {
        val account = r.getObject("account_id", UUID::class.java)
        val quote = mapper.readValue<FeeQuote>(r.getString("quote"))
        val payer = mapper.readValue<PaymentPayer>(secrets.decrypt(account, "payment-payer", r.getString("payer_data_encrypted")))
        val recurrence = PaymentRecurrence(r.getObject("id", UUID::class.java), account, r.getObject("group_id", UUID::class.java),
            r.getObject("member_user_id", UUID::class.java), PaymentMethod.valueOf(r.getString("method")), r.getLong("base_cents"),
            quote.feesCents, r.getLong("total_cents"), r.getObject("first_due_date", LocalDate::class.java), r.getString("status"),
            r.getString("provider_subscription_id"), r.getString("hosted_checkout_url"), r.getTimestamp("cutoff_at")?.toInstant())
        return RecurrenceAuthorization(recurrence, quote, payer, r.getString("provider_checkout_id"))
    }
}
