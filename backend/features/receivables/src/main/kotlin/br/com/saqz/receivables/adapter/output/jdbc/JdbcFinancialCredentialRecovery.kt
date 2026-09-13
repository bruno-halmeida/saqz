package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialCredentialRecovery(dataSource: DataSource, private val secrets: FinancialSecrets) : CredentialRecoveryStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()

    override fun begin(command: CredentialRecoveryCommand, credential: RecoveryCredential, now: Instant): CredentialRecoveryTarget? =
        transaction.execute {
            lockRequest(command.requestId)
            val prior = audit(command.requestId)
            if (prior != null) {
                if (prior.command != command || !sameSecret(command.accountId, prior.encrypted, credential.value))
                    throw CredentialRecoveryConflict()
                if (prior.succeeded) return@execute null
            }
            val target = lockEligible(command)
            if (prior != null && prior.version != target.version) throw CredentialRecoveryConflict()
            if (prior == null) {
                jdbc.sql("""
                    INSERT INTO receivable_credential_recoveries(request_id,account_id,creation_operation_id,
                        owner_user_id,operator_id,provider_account_id,provider_wallet_id,credential_encrypted,
                        account_version,status,created_at)
                    VALUES (:request,:account,:operation,:owner,:operator,:provider,:wallet,:secret,:version,'PENDING',:now)
                """.trimIndent()).param("request", command.requestId).param("account", command.accountId)
                    .param("operation", command.creationOperationId).param("owner", command.ownerUserId)
                    .param("operator", command.operatorId).param("provider", command.providerAccountId)
                    .param("wallet", command.walletId)
                    .param("secret", secrets.encrypt(command.accountId, "recovery-key", credential.value))
                    .param("version", target.version).param("now", Timestamp.from(now)).update()
                recordEvent(command.requestId, "REQUESTED", now)
            }
            target
        }

    override fun complete(command: CredentialRecoveryCommand, target: CredentialRecoveryTarget, now: Instant) {
        transaction.executeWithoutResult {
            lockRequest(command.requestId)
            val prior = audit(command.requestId) ?: throw CredentialRecoveryConflict()
            if (prior.command != command || prior.version != target.version) throw CredentialRecoveryConflict()
            if (prior.succeeded) return@executeWithoutResult
            val current = lockEligible(command)
            if (current.version != target.version || current.cpfCnpj != target.cpfCnpj) throw CredentialRecoveryConflict()
            val encrypted = secrets.encrypt(command.accountId, "provider-key",
                secrets.decrypt(command.accountId, "recovery-key", prior.encrypted))
            check(jdbc.sql("""
                UPDATE receivable_accounts SET provider_account_id=:provider,provider_wallet_id=:wallet,
                    credentials_encrypted=:secret,provider_created_at=:now,updated_at=:now,
                    registration=CASE WHEN registration='INCOMPLETE' THEN 'UNDER_REVIEW' ELSE registration END,
                    version=version+1 WHERE id=:account AND credentials_encrypted IS NULL AND version=:version
            """.trimIndent()).param("provider", command.providerAccountId).param("wallet", command.walletId)
                .param("secret", encrypted).param("now", Timestamp.from(now)).param("account", command.accountId)
                .param("version", target.version).update() == 1)
            check(jdbc.sql("""
                UPDATE receivable_operations SET status='SUCCEEDED',provider_reference=:provider,
                    updated_at=:now,lease_token=NULL,lease_until=NULL
                WHERE id=:operation AND account_id=:account AND status='UNKNOWN' AND kind='CREATE_ACCOUNT'
            """.trimIndent()).param("provider", command.providerAccountId).param("now", Timestamp.from(now))
                .param("operation", command.creationOperationId).param("account", command.accountId).update() == 1)
            check(jdbc.sql("""
                UPDATE receivable_credential_recoveries SET status='SUCCEEDED',completed_at=:now
                WHERE request_id=:request AND status='PENDING'
            """.trimIndent()).param("now", Timestamp.from(now)).param("request", command.requestId).update() == 1)
            recordEvent(command.requestId, "IMPORTED", now)
        }
    }

    private fun recordEvent(requestId: UUID, event: String, now: Instant) {
        check(jdbc.sql("""
            INSERT INTO receivable_credential_recovery_events(request_id,event,account_id,creation_operation_id,
                owner_user_id,operator_id,provider_account_id,provider_wallet_id,occurred_at)
            SELECT request_id,:event,account_id,creation_operation_id,owner_user_id,operator_id,
                provider_account_id,provider_wallet_id,:now FROM receivable_credential_recoveries WHERE request_id=:request
        """.trimIndent()).param("request", requestId).param("event", event).param("now", Timestamp.from(now)).update() == 1)
    }

    private fun lockRequest(requestId: UUID) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:request,0))")
            .param("request", "credential-recovery:$requestId").query { _, _ -> true }.single()
    }

    private fun lockEligible(command: CredentialRecoveryCommand): CredentialRecoveryTarget {
        val operation = jdbc.sql("""
            SELECT status,actor_user_id,provider_reference FROM receivable_operations
            WHERE id=:operation AND account_id=:account AND resource_id=:account AND kind='CREATE_ACCOUNT' FOR UPDATE
        """.trimIndent()).param("operation", command.creationOperationId).param("account", command.accountId)
            .query { rs, _ -> Triple(rs.getString("status"), rs.getObject("actor_user_id", UUID::class.java), rs.getString("provider_reference")) }
            .optional().orElseThrow { CredentialRecoveryIneligible() }
        if (operation.first != "UNKNOWN" || operation.second != command.ownerUserId) throw CredentialRecoveryIneligible()
        if (operation.third != null && operation.third != command.providerAccountId) throw CredentialRecoveryConflict()
        return jdbc.sql("SELECT * FROM receivable_accounts WHERE id=:account FOR UPDATE")
            .param("account", command.accountId).query { rs, _ ->
                if (rs.getObject("owner_user_id", UUID::class.java) != command.ownerUserId ||
                    rs.getString("credentials_encrypted") != null) throw CredentialRecoveryIneligible()
                val provider = rs.getString("provider_account_id")
                val wallet = rs.getString("provider_wallet_id")
                if ((provider != null && provider != command.providerAccountId) ||
                    (wallet != null && wallet != command.walletId)) throw CredentialRecoveryConflict()
                val legal = mapper.readTree(secrets.decrypt(command.accountId, "legal-data", rs.getString("legal_data_encrypted")))
                val cpfCnpj = legal.path("cpfCnpj").asText()
                if (secrets.legalIdentityDigest(cpfCnpj) != rs.getString("legal_identity_digest")) throw CredentialRecoveryConflict()
                CredentialRecoveryTarget(cpfCnpj, rs.getLong("version"))
            }.optional().orElseThrow { CredentialRecoveryIneligible() }
    }

    private class Audit(val command: CredentialRecoveryCommand, val encrypted: String, val version: Long, val succeeded: Boolean)
    private fun audit(requestId: UUID): Audit? = jdbc.sql("SELECT * FROM receivable_credential_recoveries WHERE request_id=:request")
        .param("request", requestId).query { rs, _ -> Audit(CredentialRecoveryCommand(
            rs.getObject("request_id", UUID::class.java), rs.getObject("account_id", UUID::class.java),
            rs.getObject("owner_user_id", UUID::class.java), rs.getObject("creation_operation_id", UUID::class.java),
            rs.getObject("operator_id", UUID::class.java), rs.getString("provider_account_id"), rs.getString("provider_wallet_id")),
            rs.getString("credential_encrypted"), rs.getLong("account_version"), rs.getString("status") == "SUCCEEDED")
        }.optional().orElse(null)

    private fun sameSecret(accountId: UUID, encrypted: String, supplied: String): Boolean = MessageDigest.isEqual(
        secrets.decrypt(accountId, "recovery-key", encrypted).toByteArray(), supplied.toByteArray())
}
