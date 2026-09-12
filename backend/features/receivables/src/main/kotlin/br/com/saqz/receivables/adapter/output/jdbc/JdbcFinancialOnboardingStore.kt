package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.RegistrationStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialOnboardingStore(dataSource: DataSource, private val secrets: FinancialSecrets) : FinancialOnboardingStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()

    override fun begin(request: FinancialRequest, termsVersion: String, registration: LegalRegistration, now: Instant): FinancialAccount =
        transaction.execute {
            // Serialize same-owner registration across different request IDs without holding an HTTP call open.
            jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:owner,0))")
                .param("owner", request.actorUserId.toString()).query { _, _ -> true }.single()
            val legalDigest = secrets.legalIdentityDigest(registration.cpfCnpj)
            val legalJson = registrationJson(registration)
            val digest = MessageDigest.getInstance("SHA-256").digest((legalJson + "\n" + termsVersion).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val existing = findOwned(request.actorUserId)
            if (existing != null) {
                val sameIdentity = jdbc.sql("SELECT legal_identity_digest=:digest FROM receivable_accounts WHERE id=:id")
                    .param("digest", legalDigest).param("id", existing.id).query(Boolean::class.java).single()
                if (!sameIdentity) throw FinancialRequestConflict()
                val creation = creationOperation(existing.id)
                if (creation.requestId == request.requestId && creation.requestDigest != digest) throw FinancialRequestConflict()
                return@execute existing
            }
            val validTerms = jdbc.sql("SELECT count(*) FROM receivable_terms WHERE version=:version AND effective_at<=:now AND published_at<=:now")
                .param("version", termsVersion).param("now", java.sql.Timestamp.from(now)).query(Long::class.java).single()
            if (validTerms != 1L) throw FinancialTermsUnavailable()
            val occupied = jdbc.sql("SELECT count(*) FROM receivable_accounts WHERE legal_identity_digest=:digest")
                .param("digest", legalDigest).query(Long::class.java).single()
            if (occupied != 0L) throw FinancialRequestConflict()
            val id = UUID.randomUUID()
            val inserted = jdbc.sql("""
                INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,
                    registration,created_at,updated_at) VALUES (:id,:owner,:digest,:legal,'INCOMPLETE',:now,:now)
                ON CONFLICT DO NOTHING
            """.trimIndent()).param("id", id).param("owner", request.actorUserId).param("digest", legalDigest)
                .param("legal", secrets.encrypt(id, "legal-data", legalJson)).param("now", java.sql.Timestamp.from(now)).update()
            if (inserted != 1) throw FinancialRequestConflict()
            jdbc.sql("""
                INSERT INTO receivable_terms_acceptances(id,account_id,actor_user_id,terms_version,purpose,request_id,accepted_at)
                VALUES (:id,:account,:actor,:terms,'ACCOUNT',:request,:now)
            """.trimIndent()).param("id", UUID.randomUUID()).param("account", id).param("actor", request.actorUserId)
                .param("terms", termsVersion).param("request", request.requestId).param("now", java.sql.Timestamp.from(now)).update()
            jdbc.sql("""
                INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,
                    request_digest,status,next_attempt_at,created_at,updated_at)
                VALUES (:id,:account,:request,:actor,'CREATE_ACCOUNT',:account,:digest,'READY',:now,:now,:now)
            """.trimIndent()).param("id", UUID.randomUUID()).param("account", id).param("request", request.requestId)
                .param("actor", request.actorUserId).param("digest", digest).param("now", java.sql.Timestamp.from(now)).update()
            FinancialAccount(id, request.actorUserId, RegistrationStatus.INCOMPLETE, false)
        }

    override fun findOwned(ownerUserId: UUID): FinancialAccount? =
        jdbc.sql("SELECT * FROM receivable_accounts WHERE owner_user_id=:owner").param("owner", ownerUserId)
            .query { rs, _ -> FinancialAccount(rs.getObject("id", UUID::class.java), ownerUserId,
                RegistrationStatus.valueOf(rs.getString("registration")), rs.getBoolean("new_operations_enabled")) }
            .optional().orElse(null)

    override fun creationOperation(accountId: UUID): FinancialOperation =
        jdbc.sql("SELECT * FROM receivable_operations WHERE account_id=:account AND kind='CREATE_ACCOUNT'")
            .param("account", accountId).query { rs, _ -> FinancialOperation(
                rs.getObject("id", UUID::class.java), accountId, rs.getObject("request_id", UUID::class.java),
                rs.getObject("actor_user_id", UUID::class.java), OperationKind.CREATE_ACCOUNT, accountId,
                rs.getString("request_digest"), OperationStatus.valueOf(rs.getString("status")), rs.getString("provider_reference"),
            ) }.single()

    override fun registration(accountId: UUID): LegalRegistration {
        val encrypted = jdbc.sql("SELECT legal_data_encrypted FROM receivable_accounts WHERE id=:id")
            .param("id", accountId).query(String::class.java).single()
        val json = mapper.readTree(secrets.decrypt(accountId, "legal-data", encrypted))
        return LegalRegistration(json["name"].asText(), json["email"].asText(), json["cpfCnpj"].asText(),
            json["mobilePhone"].asText(), json["incomeCents"].asLong(), json["address"].asText(),
            json["addressNumber"].asText(), json["province"].asText(), json["postalCode"].asText(),
            json["birthDate"]?.takeUnless { it.isNull }?.asText()?.let(LocalDate::parse),
            json["companyType"]?.takeUnless { it.isNull }?.asText())
    }

    override fun credentials(accountId: UUID): AccountCredentials? = jdbc.sql("""
        SELECT credentials_encrypted, provider_created_at, provider_account_id FROM receivable_accounts
        WHERE id=:id AND credentials_encrypted IS NOT NULL AND provider_created_at IS NOT NULL
    """.trimIndent()).param("id", accountId).query { rs, _ -> AccountCredentials(
        secrets.decrypt(accountId, "provider-key", rs.getString("credentials_encrypted")),
        rs.getTimestamp("provider_created_at").toInstant(), rs.getString("provider_account_id"),
    ) }.optional().orElse(null)

    override fun saveProviderAccount(accountId: UUID, providerAccount: ProviderAccount, now: Instant) {
        require(providerAccount.id.isNotBlank() && providerAccount.walletId.isNotBlank() && providerAccount.apiKey.isNotBlank())
        val updated = jdbc.sql("""
            UPDATE receivable_accounts SET provider_account_id=:provider,provider_wallet_id=:wallet,
                credentials_encrypted=:secret,provider_created_at=:now,updated_at=:now,registration='UNDER_REVIEW',version=version+1
            WHERE id=:id AND provider_account_id IS NULL
        """.trimIndent()).param("provider", providerAccount.id).param("wallet", providerAccount.walletId)
            .param("secret", secrets.encrypt(accountId, "provider-key", providerAccount.apiKey))
            .param("now", java.sql.Timestamp.from(now)).param("id", accountId).update()
        check(updated == 1) { "Financial account provisioning requires reconciliation" }
    }

    override fun updateStatus(accountId: UUID, status: RegistrationStatus, now: Instant) {
        jdbc.sql("UPDATE receivable_accounts SET registration=:status,updated_at=:now,version=version+1 WHERE id=:id")
            .param("status", status.name).param("now", java.sql.Timestamp.from(now)).param("id", accountId).update()
    }

    private fun registrationJson(registration: LegalRegistration): String = mapper.writeValueAsString(mapOf(
        "name" to registration.name, "email" to registration.email, "cpfCnpj" to registration.cpfCnpj,
        "mobilePhone" to registration.mobilePhone, "incomeCents" to registration.incomeCents,
        "address" to registration.address, "addressNumber" to registration.addressNumber,
        "province" to registration.province, "postalCode" to registration.postalCode,
        "birthDate" to registration.birthDate?.toString(), "companyType" to registration.companyType,
    ))
}
