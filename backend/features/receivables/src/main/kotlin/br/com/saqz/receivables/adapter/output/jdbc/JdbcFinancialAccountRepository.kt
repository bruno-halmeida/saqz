package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.FinancialAccountRepository
import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.FinancialDelegation
import br.com.saqz.receivables.domain.RegistrationStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialAccountRepository(dataSource: DataSource) : FinancialAccountRepository {
    private val jdbc = JdbcClient.create(dataSource)
    override fun listForUser(userId: UUID): List<FinancialAccount> = jdbc.sql("""
        SELECT a.* FROM receivable_accounts a WHERE a.owner_user_id=:user OR EXISTS(
            SELECT 1 FROM receivable_delegations d WHERE d.account_id=a.id AND d.user_id=:user AND d.revoked_at IS NULL)
        ORDER BY a.created_at,a.id
    """.trimIndent()).param("user", userId).query { rs, _ -> account(rs) }.list()
    override fun findById(accountId: UUID): FinancialAccount? = jdbc.sql("SELECT * FROM receivable_accounts WHERE id=:id")
        .param("id", accountId).query { rs, _ -> account(rs) }.optional().orElse(null)
    override fun findByOwner(ownerUserId: UUID): FinancialAccount? = jdbc.sql("SELECT * FROM receivable_accounts WHERE owner_user_id=:id")
        .param("id", ownerUserId).query { rs, _ -> account(rs) }.optional().orElse(null)
    override fun findDelegation(accountId: UUID, userId: UUID): FinancialDelegation? =
        jdbc.sql("SELECT * FROM receivable_delegations WHERE account_id=:account AND user_id=:user")
            .param("account", accountId).param("user", userId).query { rs, _ ->
                FinancialDelegation(accountId, userId, rs.getTimestamp("granted_at").toInstant(),
                    rs.getTimestamp("revoked_at")?.toInstant())
            }.optional().orElse(null)

    /** Uses Spring's bound connection, participating in the group membership transaction. */
    fun revokeForOwner(ownerUserId: UUID, userId: UUID, at: Instant) {
        jdbc.sql("SELECT id FROM receivable_accounts WHERE owner_user_id=:owner FOR UPDATE")
            .param("owner", ownerUserId).query(UUID::class.java).optional().orElse(null) ?: return
        jdbc.sql("""
            UPDATE receivable_delegations SET revoked_at=:at WHERE user_id=:user AND revoked_at IS NULL
                AND account_id IN (SELECT id FROM receivable_accounts WHERE owner_user_id=:owner)
        """.trimIndent()).param("at", java.sql.Timestamp.from(at)).param("user", userId).param("owner", ownerUserId).update()
    }

    private fun account(rs: ResultSet) = FinancialAccount(rs.getObject("id", UUID::class.java),
        rs.getObject("owner_user_id", UUID::class.java), RegistrationStatus.valueOf(rs.getString("registration")),
        rs.getBoolean("new_operations_enabled"))
}
