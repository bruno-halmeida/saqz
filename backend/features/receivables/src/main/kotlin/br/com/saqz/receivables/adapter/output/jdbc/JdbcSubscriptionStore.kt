package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
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

class JdbcSubscriptionStore(dataSource: DataSource) : SubscriptionStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private class Holder<T>(val value: T)
    override fun <T> transaction(block: () -> T): T = try { transactions.execute { Holder(block()) }!!.value }
    catch (_: DuplicateKeyException) { throw FinancialRequestConflict() }

    override fun lockMember(groupId: UUID, memberId: UUID) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "card-subscription:$groupId:$memberId")
            .query { _, _ -> Unit }.single()
    }

    override fun live(groupId: UUID, memberId: UUID) = jdbc.sql(
        "SELECT * FROM card_subscriptions WHERE group_id=:group AND member_user_id=:member AND status IN ('ACTIVE','AUTHORIZING') ORDER BY created_at",
    ).param("group", groupId).param("member", memberId).query(::map).list()

    override fun byRequest(memberId: UUID, requestId: UUID) = jdbc.sql(
        "SELECT * FROM card_subscriptions WHERE member_user_id=:member AND request_id=:request",
    ).param("member", memberId).param("request", requestId).query(::map).optional().orElse(null)

    override fun find(id: UUID) = jdbc.sql("SELECT * FROM card_subscriptions WHERE id=:id").param("id", id).query(::map).optional().orElse(null)

    override fun byProvider(accountId: UUID, subscriptionId: String?, checkoutId: String?, reference: String?): CardSubscription? {
        val id = reference?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return jdbc.sql("""
            SELECT * FROM card_subscriptions WHERE account_id=:account AND (provider_subscription_id=:sub OR provider_checkout_id=:checkout OR id=:id)
            ORDER BY (provider_subscription_id=:sub) DESC NULLS LAST LIMIT 1
        """.trimIndent()).param("account", accountId).param("sub", subscriptionId, java.sql.Types.VARCHAR)
            .param("checkout", checkoutId, java.sql.Types.VARCHAR).param("id", id, java.sql.Types.OTHER).query(::map).optional().orElse(null)
    }

    override fun insert(subscription: CardSubscription, requestId: UUID) {
        jdbc.sql("""
            INSERT INTO card_subscriptions(id,account_id,group_id,member_user_id,status,amount_cents,commission_cents,first_due_date,request_id,
                replaces_id,created_at,updated_at)
            VALUES (:id,:account,:group,:member,:status,:amount,:commission,:first,:request,:replaces,:at,:at)
        """.trimIndent()).param("id", subscription.id).param("account", subscription.accountId).param("group", subscription.groupId)
            .param("member", subscription.memberId).param("status", subscription.status).param("amount", subscription.amountCents)
            .param("commission", subscription.commissionCents).param("first", subscription.firstDueDate).param("request", requestId)
            .param("replaces", subscription.replacesId, java.sql.Types.OTHER).param("at", Timestamp.from(subscription.createdAt)).update()
    }

    override fun checkout(id: UUID, checkout: HostedCheckout) {
        jdbc.sql("UPDATE card_subscriptions SET provider_checkout_id=:checkout,checkout_url=:url,updated_at=now() WHERE id=:id")
            .param("checkout", checkout.id).param("url", checkout.url).param("id", id).update()
    }

    override fun activate(id: UUID, providerSubscriptionId: String, at: Instant) = jdbc.sql("""
        UPDATE card_subscriptions SET status='ACTIVE',provider_subscription_id=:sub,updated_at=:at WHERE id=:id AND status='AUTHORIZING'
    """.trimIndent()).param("sub", providerSubscriptionId).param("at", Timestamp.from(at)).param("id", id).update() == 1

    override fun end(id: UUID, status: String, endReason: String, cancelReason: String?, at: Instant) = jdbc.sql("""
        UPDATE card_subscriptions SET status=:status,end_reason=:reason,cancel_reason=:cancel,remote_cancel_pending=true,ended_at=:at,updated_at=:at
        WHERE id=:id AND status IN ('ACTIVE','AUTHORIZING')
    """.trimIndent()).param("status", status).param("reason", endReason).param("cancel", cancelReason, java.sql.Types.VARCHAR)
        .param("at", Timestamp.from(at)).param("id", id).update() == 1

    override fun remoteCancelled(id: UUID) {
        jdbc.sql("UPDATE card_subscriptions SET remote_cancel_pending=false WHERE id=:id").param("id", id).update()
    }

    override fun pendingRemoteCancels() = jdbc.sql("SELECT * FROM card_subscriptions WHERE remote_cancel_pending ORDER BY ended_at LIMIT 50")
        .query(::map).list()

    override fun withStatus(vararg statuses: String) = jdbc.sql("SELECT * FROM card_subscriptions WHERE status IN (:statuses) ORDER BY created_at")
        .param("statuses", statuses.toList()).query(::map).list()

    override fun scheduleChange(id: UUID, amountCents: Long?, from: LocalDate?) {
        jdbc.sql("UPDATE card_subscriptions SET next_amount_cents=:amount,next_amount_from=:from,change_notified_at=NULL,updated_at=now() WHERE id=:id")
            .param("amount", amountCents, java.sql.Types.BIGINT).param("from", from, java.sql.Types.DATE).param("id", id).update()
    }

    override fun changeNotified(id: UUID, at: Instant) {
        jdbc.sql("UPDATE card_subscriptions SET change_notified_at=:at WHERE id=:id").param("at", Timestamp.from(at)).param("id", id).update()
    }

    override fun applyChange(id: UUID, amountCents: Long, commissionCents: Long) {
        jdbc.sql("""UPDATE card_subscriptions SET amount_cents=:amount,commission_cents=:commission,next_amount_cents=NULL,next_amount_from=NULL,
            updated_at=now() WHERE id=:id""").param("amount", amountCents).param("commission", commissionCents).param("id", id).update()
    }

    override fun refused(id: UUID, chargeId: UUID?) {
        jdbc.sql("UPDATE card_subscriptions SET refused_charge_id=:charge,updated_at=now() WHERE id=:id")
            .param("charge", chargeId, java.sql.Types.OTHER).param("id", id).update()
    }

    override fun clearRefused(chargeIds: Collection<UUID>) {
        if (chargeIds.isEmpty()) return
        jdbc.sql("UPDATE card_subscriptions SET refused_charge_id=NULL WHERE refused_charge_id IN (:ids)").param("ids", chargeIds.toList()).update()
    }

    override fun recordPayment(accountId: UUID, paymentId: String, subscriptionId: UUID, dueDate: LocalDate, valueCents: Long, at: Instant): Instant {
        jdbc.sql("""
            INSERT INTO card_subscription_payments(account_id,provider_payment_id,subscription_id,due_date,value_cents,first_seen_at)
            VALUES (:account,:payment,:sub,:due,:value,:at) ON CONFLICT DO NOTHING
        """.trimIndent()).param("account", accountId).param("payment", paymentId).param("sub", subscriptionId).param("due", dueDate)
            .param("value", valueCents).param("at", Timestamp.from(at)).update()
        return jdbc.sql("SELECT first_seen_at FROM card_subscription_payments WHERE account_id=:account AND provider_payment_id=:payment")
            .param("account", accountId).param("payment", paymentId).query(Timestamp::class.java).single().toInstant()
    }

    override fun refusedNotified(accountId: UUID, paymentId: String, at: Instant) = jdbc.sql("""
        UPDATE card_subscription_payments SET refused_notified_at=:at
        WHERE account_id=:account AND provider_payment_id=:payment AND refused_notified_at IS NULL
    """.trimIndent()).param("at", Timestamp.from(at)).param("account", accountId).param("payment", paymentId).update() == 1

    override fun exemptRemoved(accountId: UUID, paymentId: String, at: Instant) = jdbc.sql("""
        UPDATE card_subscription_payments SET exempt_removed_at=:at
        WHERE account_id=:account AND provider_payment_id=:payment AND exempt_removed_at IS NULL
    """.trimIndent()).param("at", Timestamp.from(at)).param("account", accountId).param("payment", paymentId).update() == 1

    override fun history(groupId: UUID, memberId: UUID): List<RecurrenceHistoryItem> = jdbc.sql("""
        SELECT date_trunc('month', p.due_date)::date AS month, s.value_cents, s.settled_at
        FROM card_subscription_payments p JOIN card_subscriptions c ON c.id=p.subscription_id
        JOIN payment_settlements s ON s.account_id=p.account_id AND s.provider_payment_id=p.provider_payment_id
        WHERE c.group_id=:group AND c.member_user_id=:member AND s.reversed_at IS NULL
        ORDER BY p.due_date DESC LIMIT 12
    """.trimIndent()).param("group", groupId).param("member", memberId).query { rs, _ ->
        RecurrenceHistoryItem(rs.getObject("month", LocalDate::class.java), rs.getLong("value_cents"), rs.getTimestamp("settled_at").toInstant())
    }.list()

    override fun managerStatuses(groupId: UUID): List<MemberRecurrence> = jdbc.sql("""
        SELECT c.member_user_id, CASE WHEN c.refused_charge_id IS NOT NULL AND EXISTS (SELECT 1 FROM group_charges g
            WHERE g.id=c.refused_charge_id AND g.status='PENDING') THEN 'REFUSED' ELSE 'ACTIVE' END AS status
        FROM card_subscriptions c WHERE c.group_id=:group AND c.status='ACTIVE' ORDER BY c.member_user_id
    """.trimIndent()).param("group", groupId).query { rs, _ ->
        MemberRecurrence(rs.getObject("member_user_id", UUID::class.java), rs.getString("status"))
    }.list()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = CardSubscription(
        rs.getObject("id", UUID::class.java), rs.getObject("account_id", UUID::class.java), rs.getObject("group_id", UUID::class.java),
        rs.getObject("member_user_id", UUID::class.java), rs.getString("status"), rs.getLong("amount_cents"), rs.getLong("commission_cents"),
        rs.getObject("next_amount_cents") as Long?, rs.getObject("next_amount_from", LocalDate::class.java),
        rs.getTimestamp("change_notified_at")?.toInstant(), rs.getObject("first_due_date", LocalDate::class.java),
        rs.getObject("replaces_id", UUID::class.java), rs.getString("provider_checkout_id"), rs.getString("checkout_url"),
        rs.getString("provider_subscription_id"), rs.getObject("refused_charge_id", UUID::class.java), rs.getTimestamp("created_at").toInstant(),
    )
}
