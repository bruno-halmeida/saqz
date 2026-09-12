package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.FinancialDelegation
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialDelegationStore(dataSource: DataSource) : FinancialDelegationStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val accounts = JdbcFinancialAccountRepository(dataSource)
    override fun <T> transaction(block: () -> T): T {
        // Wrapper preserves nullable generic results without a cast to a non-null T.
        return transactions.execute { ResultHolder(block()) }.value
    }
    private class ResultHolder<T>(val value: T)

    override fun lockAccount(accountId: UUID): FinancialAccount? {
        jdbc.sql("SELECT id FROM receivable_accounts WHERE id=:id FOR UPDATE").param("id", accountId)
            .query(UUID::class.java).optional().orElse(null) ?: return null
        return accounts.findById(accountId)
    }

    override fun list(accountId: UUID): List<FinancialDelegation> = jdbc.sql(
        "SELECT * FROM receivable_delegations WHERE account_id=:account ORDER BY granted_at,user_id",
    ).param("account", accountId).query { rs, _ -> FinancialDelegation(accountId,
        rs.getObject("user_id", UUID::class.java), rs.getTimestamp("granted_at").toInstant(),
        rs.getTimestamp("revoked_at")?.toInstant()) }.list()

    override fun grant(accountId: UUID, request: FinancialRequest, userId: UUID, termsVersion: String, now: Instant): FinancialDelegation {
        if (!recordCommand(accountId, request, userId, "GRANT_DELEGATION", termsVersion, now)) {
            return accounts.findDelegation(accountId, userId)!!
        }
        val published = jdbc.sql("SELECT count(*) FROM receivable_terms WHERE version=:version AND effective_at<=:now AND published_at<=:now")
            .param("version", termsVersion).param("now", java.sql.Timestamp.from(now)).query(Long::class.java).single()
        if (published != 1L) throw FinancialTermsUnavailable()
        val acceptance = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO receivable_terms_acceptances(id,account_id,actor_user_id,terms_version,purpose,request_id,accepted_at)
            VALUES (:id,:account,:actor,:terms,'DELEGATION',:request,:now)
        """.trimIndent()).param("id", acceptance).param("account", accountId).param("actor", request.actorUserId)
            .param("terms", termsVersion).param("request", request.requestId).param("now", java.sql.Timestamp.from(now)).update()
        jdbc.sql("""
            INSERT INTO receivable_delegations(account_id,user_id,granted_by,acceptance_id,granted_at,revoked_at)
            VALUES (:account,:user,:actor,:acceptance,:now,NULL)
            ON CONFLICT(account_id,user_id) DO UPDATE SET granted_by=EXCLUDED.granted_by,
                acceptance_id=EXCLUDED.acceptance_id,granted_at=EXCLUDED.granted_at,revoked_at=NULL
        """.trimIndent()).param("account", accountId).param("user", userId).param("actor", request.actorUserId)
            .param("acceptance", acceptance).param("now", java.sql.Timestamp.from(now)).update()
        return accounts.findDelegation(accountId, userId)!!
    }

    override fun revoke(accountId: UUID, request: FinancialRequest, userId: UUID, now: Instant) {
        if (!recordCommand(accountId, request, userId, "REVOKE_DELEGATION", "", now)) return
        jdbc.sql("UPDATE receivable_delegations SET revoked_at=:now WHERE account_id=:account AND user_id=:user AND revoked_at IS NULL")
            .param("now", java.sql.Timestamp.from(now)).param("account", accountId).param("user", userId).update()
    }

    private fun recordCommand(accountId: UUID, request: FinancialRequest, userId: UUID, kind: String,
                              terms: String, now: Instant): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest("$kind:$userId:$terms".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val existing = jdbc.sql("SELECT actor_user_id,request_digest FROM receivable_operations WHERE account_id=:account AND request_id=:request")
            .param("account", accountId).param("request", request.requestId)
            .query { rs, _ -> rs.getObject("actor_user_id", UUID::class.java) to rs.getString("request_digest") }.optional().orElse(null)
        if (existing != null) {
            if (existing.first != request.actorUserId || existing.second != digest) throw FinancialRequestConflict()
            return false
        }
        jdbc.sql("""
            INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,
                status,next_attempt_at,created_at,updated_at) VALUES (:id,:account,:request,:actor,:kind,:user,:digest,'SUCCEEDED',:now,:now,:now)
        """.trimIndent()).param("id", UUID.randomUUID()).param("account", accountId).param("request", request.requestId)
            .param("actor", request.actorUserId).param("kind", kind).param("user", userId).param("digest", digest)
            .param("now", java.sql.Timestamp.from(now)).update()
        return true
    }
}
