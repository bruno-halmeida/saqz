package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.FinancialOperation
import br.com.saqz.receivables.application.FinancialOperationStore
import br.com.saqz.receivables.application.FinancialRequestConflict
import br.com.saqz.receivables.application.OperationClaim
import br.com.saqz.receivables.application.OperationKind
import br.com.saqz.receivables.application.OperationStatus
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialOperationStore(private val dataSource: DataSource) : FinancialOperationStore {
    override fun register(operation: FinancialOperation, now: Instant): FinancialOperation = transaction { connection ->
        require(operation.status == OperationStatus.READY && operation.providerReference == null)
        require(operation.requestDigest.matches(Regex("[a-f0-9]{64}")))
        connection.prepareStatement("""
            INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,
                request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,'READY',?,?,?) ON CONFLICT(account_id,request_id) DO NOTHING
        """.trimIndent()).use { statement ->
            listOf(operation.id, operation.accountId, operation.requestId, operation.actorUserId,
                operation.kind.name, operation.resourceId, operation.requestDigest,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)).forEachIndexed { index, value ->
                statement.setObject(index + 1, value)
            }
            statement.executeUpdate()
        }
        connection.prepareStatement("SELECT * FROM receivable_operations WHERE account_id=? AND request_id=?").use {
            it.setObject(1, operation.accountId)
            it.setObject(2, operation.requestId)
            it.executeQuery().use { result ->
                check(result.next())
                result.operation().also { existing ->
                    if (existing.actorUserId != operation.actorUserId || existing.kind != operation.kind ||
                        existing.resourceId != operation.resourceId || existing.requestDigest != operation.requestDigest) {
                        throw FinancialRequestConflict()
                    }
                }
            }
        }
    }

    override fun claim(accountId: UUID, operationId: UUID, now: Instant, leaseUntil: Instant): OperationClaim? = transaction { connection ->
        require(leaseUntil > now)
        connection.prepareStatement("""
            SELECT * FROM receivable_operations WHERE account_id=? AND id=?
                AND ((status IN ('READY','UNKNOWN') AND next_attempt_at<=?)
                    OR (status='RUNNING' AND lease_until<=?)) FOR UPDATE SKIP LOCKED
        """.trimIndent()).use { statement ->
            statement.setObject(1, accountId)
            statement.setObject(2, operationId)
            statement.setTimestamp(3, Timestamp.from(now))
            statement.setTimestamp(4, Timestamp.from(now))
            statement.executeQuery().use { result ->
                if (!result.next()) return@transaction null
                val operation = result.operation()
                val token = UUID.randomUUID()
                connection.prepareStatement("""
                    UPDATE receivable_operations SET status='RUNNING',lease_token=?,lease_until=?,
                        attempts=attempts+1,updated_at=? WHERE id=?
                """.trimIndent()).use {
                    it.setObject(1, token)
                    it.setTimestamp(2, Timestamp.from(leaseUntil))
                    it.setTimestamp(3, Timestamp.from(now))
                    it.setObject(4, operationId)
                    check(it.executeUpdate() == 1)
                }
                OperationClaim(operation, token, operation.status != OperationStatus.READY)
            }
        }
    }

    override fun finish(claim: OperationClaim, status: OperationStatus, providerReference: String?, now: Instant, retryAt: Instant): Boolean {
        require(status in setOf(OperationStatus.SUCCEEDED, OperationStatus.REJECTED, OperationStatus.UNKNOWN))
        require(status != OperationStatus.SUCCEEDED || !providerReference.isNullOrBlank())
        return dataSource.connection.use { connection ->
            connection.prepareStatement("""
                UPDATE receivable_operations SET status=?,provider_reference=?,updated_at=?,next_attempt_at=?,
                    lease_token=NULL,lease_until=NULL WHERE account_id=? AND id=? AND lease_token=? AND status='RUNNING'
            """.trimIndent()).use {
                it.setString(1, status.name)
                it.setString(2, providerReference)
                it.setTimestamp(3, Timestamp.from(now))
                it.setTimestamp(4, Timestamp.from(retryAt))
                it.setObject(5, claim.operation.accountId)
                it.setObject(6, claim.operation.id)
                it.setObject(7, claim.token)
                it.executeUpdate() == 1
            }
        }
    }

    private fun ResultSet.operation() = FinancialOperation(
        getObject("id", UUID::class.java), getObject("account_id", UUID::class.java),
        getObject("request_id", UUID::class.java), getObject("actor_user_id", UUID::class.java),
        OperationKind.valueOf(getString("kind")), getObject("resource_id", UUID::class.java),
        getString("request_digest"), OperationStatus.valueOf(getString("status")), getString("provider_reference"),
    )

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try { block(connection).also { connection.commit() } }
        catch (failure: Throwable) { connection.rollback(); throw failure }
    }
}
