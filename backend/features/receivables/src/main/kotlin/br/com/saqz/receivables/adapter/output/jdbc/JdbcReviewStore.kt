package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcReviewStore(dataSource: DataSource) : ReviewStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val settlements = JdbcSettlementStore(dataSource)
    private class Holder<T>(val value: T)
    override fun <T> transaction(block: () -> T): T = transactions.execute { Holder(block()) }!!.value

    override fun accountOfGroup(groupId: UUID): UUID? = jdbc.sql("SELECT account_id FROM receivable_group_links WHERE group_id=:group AND enabled")
        .param("group", groupId).query(UUID::class.java).optional().orElse(null)

    override fun open(accountId: UUID): List<ReviewItem> = jdbc.sql(
        "SELECT id,payer_name,amount_cents,received_at FROM payment_reviews WHERE account_id=:account AND status='OPEN' ORDER BY received_at,id",
    ).param("account", accountId).query { rs, _ ->
        ReviewItem(rs.getObject("id", UUID::class.java), rs.getString("payer_name"), rs.getLong("amount_cents"), rs.getTimestamp("received_at").toInstant())
    }.list()

    override fun lock(reviewId: UUID): StoredReview? = jdbc.sql("SELECT * FROM payment_reviews WHERE id=:id FOR UPDATE").param("id", reviewId)
        .query { rs, _ ->
            StoredReview(rs.getObject("id", UUID::class.java), rs.getObject("account_id", UUID::class.java), rs.getString("provider_payment_id"),
                rs.getString("payer_name"), rs.getLong("amount_cents"), rs.getLong("fees_cents"), rs.getTimestamp("received_at").toInstant(),
                rs.getString("status"), rs.getObject("resolve_request_id", UUID::class.java), rs.getObject("resolved_by", UUID::class.java),
                rs.getString("settled_charge_ids")?.split(',')?.filter { it.isNotBlank() }?.map(UUID::fromString).orEmpty(),
                rs.getObject("leftover_cents") as Long?, rs.getObject("expense_id", UUID::class.java))
        }.optional().orElse(null)

    override fun accountGroups(accountId: UUID) = settlements.accountGroups(accountId)

    override fun resolve(reviewId: UUID, status: String, groupId: UUID, memberId: UUID?, chargeIds: List<UUID>, leftover: Long?,
                         expenseId: UUID?, requestId: UUID, actor: UUID, at: Instant) {
        jdbc.sql("""
            UPDATE payment_reviews SET status=:status,group_id=:group,member_user_id=:member,settled_charge_ids=:charges,leftover_cents=:leftover,
                expense_id=:expense,resolve_request_id=:request,resolved_by=:actor,resolved_at=:at WHERE id=:id AND status='OPEN'
        """.trimIndent()).param("status", status).param("group", groupId).param("member", memberId)
            .param("charges", chargeIds.joinToString(",")).param("leftover", leftover).param("expense", expenseId)
            .param("request", requestId).param("actor", actor).param("at", Timestamp.from(at)).param("id", reviewId).update()
    }
}
