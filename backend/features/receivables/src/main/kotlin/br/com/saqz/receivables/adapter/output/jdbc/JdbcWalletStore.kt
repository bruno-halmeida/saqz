package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcWalletStore(private val dataSource: DataSource, private val secrets: FinancialSecrets) : WalletStore {
    private val mapper = jacksonObjectMapper()

    override fun listDestinations(accountId: UUID): List<BankDestination> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM receivable_bank_destinations WHERE account_id=? AND disabled_at IS NULL ORDER BY created_at,id").use {
            it.setObject(1, accountId)
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.destination()) } }
        }
    }

    override fun findDestinationByRequest(accountId: UUID, requestId: UUID): BankDestination? =
        dataSource.connection.use { it.destinationByRequest(accountId, requestId) }

    override fun saveDestination(accountId: UUID, request: FinancialRequest, details: BankDestinationDetails,
                                 now: Instant): BankDestination = transaction { connection ->
        connection.advisoryLock(accountId)
        val normalized = details.copy(ownerName = details.ownerName.trim(), cpfCnpj = details.cpfCnpj.filter(Char::isDigit))
        val digest = destinationDigest(normalized)
        connection.destinationByRequest(accountId, request.requestId)?.let { existing ->
            val storedDigest = connection.prepareStatement("SELECT actor_user_id,request_digest FROM receivable_bank_destinations WHERE id=?").use {
                it.setObject(1, existing.id); it.executeQuery().use { row -> row.next(); row.getObject(1, UUID::class.java) to row.getString(2) }
            }
            if (storedDigest.first != request.actorUserId || storedDigest.second != digest) throw FinancialRequestConflict()
            return@transaction existing
        }
        val legalDigest = connection.prepareStatement("SELECT legal_identity_digest FROM receivable_accounts WHERE id=? FOR UPDATE").use {
            it.setObject(1, accountId); it.executeQuery().use { rs -> if (!rs.next()) throw WalletDestinationMismatch() else rs.getString(1) }
        }
        if (secrets.legalIdentityDigest(normalized.cpfCnpj) != legalDigest) throw WalletDestinationMismatch()
        val id = UUID.randomUUID()
        val json = mapper.writeValueAsString(mapOf(
            "bankCode" to normalized.bankCode, "accountType" to normalized.accountType.name,
            "ownerName" to normalized.ownerName, "cpfCnpj" to normalized.cpfCnpj,
            "agency" to normalized.agency, "account" to normalized.account,
            "accountDigit" to normalized.accountDigit,
        ))
        connection.prepareStatement("""
            INSERT INTO receivable_bank_destinations(id,account_id,details_encrypted,legal_identity_digest,
                verified_at,disabled_at,created_at,request_id,actor_user_id,request_digest)
            VALUES (?,?,?,?,NULL,NULL,?,?,?,?)
        """.trimIndent()).use {
            it.setObject(1, id); it.setObject(2, accountId)
            it.setString(3, secrets.encrypt(accountId, "bank-destination", json)); it.setString(4, legalDigest)
            it.setTimestamp(5, Timestamp.from(now)); it.setObject(6, request.requestId)
            it.setObject(7, request.actorUserId); it.setString(8, digest); check(it.executeUpdate() == 1)
        }
        BankDestination(id, accountId, normalized, null, null)
    }

    override fun prepareWithdrawal(accountId: UUID, request: FinancialRequest, destinationId: UUID,
                                   amountCents: Long, availableBalanceCents: Long, now: Instant): Withdrawal = transaction { connection ->
        connection.advisoryLock(accountId)
        val digest = sha256("withdraw|$destinationId|$amountCents")
        connection.operationByRequest(accountId, request.requestId)?.let { operation ->
            if (operation.actorUserId != request.actorUserId || operation.kind != OperationKind.WITHDRAW ||
                operation.requestDigest != digest) throw FinancialRequestConflict()
            return@transaction connection.withdrawalByOperation(operation.id) ?: throw FinancialRequestConflict()
        }
        val destinationExists = connection.prepareStatement("SELECT count(*) FROM receivable_bank_destinations WHERE account_id=? AND id=? AND disabled_at IS NULL").use {
            it.setObject(1, accountId); it.setObject(2, destinationId); it.executeQuery().use { rs -> rs.next(); rs.getLong(1) == 1L }
        }
        if (!destinationExists) throw WalletDestinationMismatch()
        val reserved = connection.prepareStatement("SELECT COALESCE(sum(reserved_cents),0) FROM receivable_transfers WHERE account_id=?").use {
            it.setObject(1, accountId); it.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
        if (amountCents <= 0 || availableBalanceCents < amountCents || availableBalanceCents - amountCents < reserved) {
            throw WalletInsufficientBalance()
        }
        val withdrawalId = UUID.randomUUID()
        val operationId = UUID.randomUUID()
        connection.prepareStatement("""
            INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,
                request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,'WITHDRAW',?,?,'READY',?,?,?)
        """.trimIndent()).use {
            listOf(operationId, accountId, request.requestId, request.actorUserId, withdrawalId, digest,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)).forEachIndexed { index, value -> it.setObject(index + 1, value) }
            check(it.executeUpdate() == 1)
        }
        connection.prepareStatement("""
            INSERT INTO receivable_transfers(id,account_id,destination_id,operation_id,amount_cents,fee_cents,
                status,created_at,explicitly_authorized_at,reserved_cents,updated_at)
            VALUES (?,?,?,?,?,0,'REQUESTED',?,?,?,?)
        """.trimIndent()).use {
            it.setObject(1, withdrawalId); it.setObject(2, accountId); it.setObject(3, destinationId)
            it.setObject(4, operationId); it.setLong(5, amountCents); it.setTimestamp(6, Timestamp.from(now))
            it.setTimestamp(7, Timestamp.from(now)); it.setLong(8, amountCents); it.setTimestamp(9, Timestamp.from(now))
            check(it.executeUpdate() == 1)
        }
        connection.withdrawalById(accountId, withdrawalId)!!
    }

    override fun claimWithdrawal(accountId: UUID, withdrawalId: UUID, now: Instant): WithdrawalClaim? = transaction { connection ->
        val withdrawal = connection.prepareStatement("""
            SELECT t.*,o.request_id,o.status operation_status,o.actor_user_id,o.request_digest,o.lease_until
            FROM receivable_transfers t JOIN receivable_operations o ON o.id=t.operation_id
            WHERE t.account_id=? AND t.id=? AND t.status IN ('REQUESTED','UNKNOWN','PROCESSING')
              AND ((o.status IN ('READY','UNKNOWN','SUCCEEDED') AND o.next_attempt_at<=?) OR (o.status='RUNNING' AND o.lease_until<=?))
            FOR UPDATE OF t,o SKIP LOCKED
        """.trimIndent()).use {
            it.setObject(1, accountId); it.setObject(2, withdrawalId); it.setTimestamp(3, Timestamp.from(now)); it.setTimestamp(4, Timestamp.from(now))
            it.executeQuery().use { rs -> if (rs.next()) rs.withdrawal() to (rs.getString("operation_status") != "READY") else null }
        } ?: return@transaction null
        val token = UUID.randomUUID()
        connection.prepareStatement("UPDATE receivable_operations SET status='RUNNING',lease_token=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE id=?").use {
            it.setObject(1, token); it.setTimestamp(2, Timestamp.from(now.plusSeconds(90)))
            it.setTimestamp(3, Timestamp.from(now)); it.setObject(4, withdrawal.first.operationId); check(it.executeUpdate() == 1)
        }
        val destination = connection.destination(accountId, withdrawal.first.destinationId) ?: throw WalletDestinationMismatch()
        WithdrawalClaim(withdrawal.first, token, withdrawal.second, destination)
    }

    override fun finishWithdrawal(claim: WithdrawalClaim, result: ProviderWithdrawalResult,
                                  now: Instant): Withdrawal = transaction { connection ->
        val (status, reference, fee, failure, operationStatus, reservation) = when (result) {
            is ProviderWithdrawalResult.Known -> Finish(result.status, result.reference, result.feeCents, null,
                OperationStatus.SUCCEEDED, if (result.status == WithdrawalStatus.UNKNOWN) claim.withdrawal.amountCents else 0)
            is ProviderWithdrawalResult.Rejected -> Finish(WithdrawalStatus.REJECTED, result.reference, 0, result.code, OperationStatus.REJECTED, 0)
            ProviderWithdrawalResult.Unknown -> Finish(WithdrawalStatus.UNKNOWN, claim.withdrawal.providerTransferId, claim.withdrawal.feeCents, null,
                OperationStatus.UNKNOWN, claim.withdrawal.amountCents)
        }
        val operationUpdated = connection.prepareStatement("""
            UPDATE receivable_operations SET status=?,provider_reference=?,failure_code=?,lease_token=NULL,
                lease_until=NULL,next_attempt_at=?,updated_at=? WHERE id=? AND account_id=? AND status='RUNNING' AND lease_token=?
        """.trimIndent()).use {
            it.setString(1, operationStatus.name); it.setString(2, reference); it.setString(3, failure)
            it.setTimestamp(4, Timestamp.from(now.plusSeconds(60))); it.setTimestamp(5, Timestamp.from(now))
            it.setObject(6, claim.withdrawal.operationId); it.setObject(7, claim.withdrawal.accountId); it.setObject(8, claim.token)
            it.executeUpdate()
        }
        if (operationUpdated == 1) connection.prepareStatement("""
            UPDATE receivable_transfers SET status=?,provider_transfer_id=?,fee_cents=?,failure_code=?,reserved_cents=?,updated_at=?
            WHERE account_id=? AND id=?
        """.trimIndent()).use {
            it.setString(1, status.name); it.setString(2, reference); it.setLong(3, fee); it.setString(4, failure)
            it.setLong(5, reservation); it.setTimestamp(6, Timestamp.from(now)); it.setObject(7, claim.withdrawal.accountId)
            it.setObject(8, claim.withdrawal.id); check(it.executeUpdate() == 1)
        }
        connection.withdrawalById(claim.withdrawal.accountId, claim.withdrawal.id)!!
    }

    override fun findWithdrawal(accountId: UUID, withdrawalId: UUID): Withdrawal? = dataSource.connection.use {
        it.withdrawalById(accountId, withdrawalId)
    }

    override fun findWithdrawalByRequest(accountId: UUID, requestId: UUID): Withdrawal? = dataSource.connection.use {
        it.prepareStatement("""
            SELECT t.*,o.request_id FROM receivable_transfers t JOIN receivable_operations o ON o.id=t.operation_id
            WHERE t.account_id=? AND o.account_id=? AND o.request_id=? AND o.kind='WITHDRAW'
        """.trimIndent()).use { statement ->
            statement.setObject(1, accountId); statement.setObject(2, accountId); statement.setObject(3, requestId)
            statement.executeQuery().use { rs -> if (rs.next()) rs.withdrawal() else null }
        }
    }

    private data class Finish(val status: WithdrawalStatus, val reference: String?, val fee: Long,
                              val failure: String?, val operationStatus: OperationStatus, val reservation: Long)

    private fun Connection.destination(accountId: UUID, id: UUID): BankDestination? = prepareStatement(
        "SELECT * FROM receivable_bank_destinations WHERE account_id=? AND id=? AND disabled_at IS NULL").use {
        it.setObject(1, accountId); it.setObject(2, id); it.executeQuery().use { rs -> if (rs.next()) rs.destination() else null }
    }
    private fun Connection.destinationByRequest(accountId: UUID, requestId: UUID): BankDestination? = prepareStatement(
        "SELECT * FROM receivable_bank_destinations WHERE account_id=? AND request_id=?").use {
        it.setObject(1, accountId); it.setObject(2, requestId); it.executeQuery().use { rs -> if (rs.next()) rs.destination() else null }
    }
    private fun ResultSet.destination(): BankDestination {
        val id = getObject("id", UUID::class.java); val accountId = getObject("account_id", UUID::class.java)
        val details = mapper.readValue(secrets.decrypt(accountId, "bank-destination", getString("details_encrypted")), BankDestinationDetails::class.java)
        return BankDestination(id, accountId, details, getTimestamp("verified_at")?.toInstant(), getTimestamp("disabled_at")?.toInstant())
    }
    private fun Connection.withdrawalById(accountId: UUID, id: UUID): Withdrawal? = prepareStatement("""
        SELECT t.*,o.request_id FROM receivable_transfers t JOIN receivable_operations o ON o.id=t.operation_id
        WHERE t.account_id=? AND t.id=?
    """.trimIndent()).use { it.setObject(1, accountId); it.setObject(2, id); it.executeQuery().use { rs -> if (rs.next()) rs.withdrawal() else null } }
    private fun Connection.withdrawalByOperation(operationId: UUID): Withdrawal? = prepareStatement("""
        SELECT t.*,o.request_id FROM receivable_transfers t JOIN receivable_operations o ON o.id=t.operation_id WHERE t.operation_id=?
    """.trimIndent()).use { it.setObject(1, operationId); it.executeQuery().use { rs -> if (rs.next()) rs.withdrawal() else null } }
    private fun ResultSet.withdrawal() = Withdrawal(getObject("id", UUID::class.java), getObject("account_id", UUID::class.java),
        getObject("destination_id", UUID::class.java), getObject("operation_id", UUID::class.java), getObject("request_id", UUID::class.java),
        getLong("amount_cents"), getLong("fee_cents"), WithdrawalStatus.valueOf(getString("status")), getString("provider_transfer_id"))
    private fun Connection.operationByRequest(accountId: UUID, requestId: UUID): FinancialOperation? = prepareStatement(
        "SELECT * FROM receivable_operations WHERE account_id=? AND request_id=?").use {
        it.setObject(1, accountId); it.setObject(2, requestId); it.executeQuery().use { rs -> if (!rs.next()) null else FinancialOperation(
            rs.getObject("id", UUID::class.java), accountId, requestId, rs.getObject("actor_user_id", UUID::class.java),
            OperationKind.valueOf(rs.getString("kind")), rs.getObject("resource_id", UUID::class.java), rs.getString("request_digest"),
            OperationStatus.valueOf(rs.getString("status")), rs.getString("provider_reference")) }
    }
    private fun Connection.advisoryLock(accountId: UUID) { prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { it.setString(1, accountId.toString()); it.executeQuery().close() } }
    private fun destinationDigest(details: BankDestinationDetails) = sha256(listOf(details.bankCode, details.accountType.name,
        details.ownerName, details.cpfCnpj, details.agency, details.account, details.accountDigit).joinToString("|"))
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try { block(connection).also { connection.commit() } } catch (failure: Throwable) { connection.rollback(); throw failure }
    }
}
