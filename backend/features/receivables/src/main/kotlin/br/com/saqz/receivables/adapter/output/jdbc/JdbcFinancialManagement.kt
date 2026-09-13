package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcFinancialManagement(dataSource: DataSource, private val secrets: FinancialSecrets) : FinancialManagementStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()

    override fun recordIdentitySnapshot(accountId: UUID, requestId: UUID, registration: CommercialRegistration) {
        val payload = secrets.encrypt(accountId, "registration-identity-snapshot", mapper.writeValueAsString(mapOf(
            "personType" to registration.personType, "cpfCnpj" to registration.cpfCnpj,
            "birthDate" to registration.birthDate, "companyType" to registration.companyType,
            "companyName" to registration.companyName, "taxRegime" to registration.taxRegime)))
        val changed = jdbc.sql("""UPDATE receivable_registration_corrections SET identity_snapshot_encrypted=:payload
            WHERE account_id=:account AND identity_snapshot_encrypted IS NULL AND operation_id=(
                SELECT id FROM receivable_operations WHERE account_id=:account AND request_id=:request
                AND kind='CORRECT_REGISTRATION')""")
            .param("payload", payload).param("account", accountId).param("request", requestId).update()
        check(changed == 1)
    }

    override fun begin(accountId: UUID, request: FinancialRequest, correction: RegistrationCorrection, now: Instant): StoredRegistrationCorrection =
        transaction.execute {
            jdbc.sql("SELECT id FROM receivable_accounts WHERE id=:id FOR UPDATE").param("id", accountId)
                .query(UUID::class.java).optional().orElseThrow()
            val json = json(correction)
            val digest = sha256(json)
            val existing = find(accountId, request.requestId)
            if (existing != null) {
                val recorded = jdbc.sql("SELECT request_digest FROM receivable_operations WHERE account_id=:account AND request_id=:request")
                    .param("account", accountId).param("request", request.requestId).query(String::class.java).single()
                if (existing.actorUserId != request.actorUserId || recorded != digest) throw FinancialRequestConflict()
                return@execute existing.copy(shouldExecute = false)
            }
            val operationId = UUID.randomUUID()
            jdbc.sql("""
                INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,
                    status,next_attempt_at,created_at,updated_at)
                VALUES (:id,:account,:request,:actor,'CORRECT_REGISTRATION',:account,:digest,'READY',:now,:now,:now)
            """.trimIndent()).param("id", operationId).param("account", accountId).param("request", request.requestId)
                .param("actor", request.actorUserId).param("digest", digest).param("now", java.sql.Timestamp.from(now)).update()
            jdbc.sql("""
                INSERT INTO receivable_registration_corrections(account_id,operation_id,payload_encrypted,created_at,updated_at)
                VALUES (:account,:operation,:payload,:now,:now)
            """.trimIndent()).param("account", accountId).param("operation", operationId)
                .param("payload", secrets.encrypt(accountId, "registration-correction", json))
                .param("now", java.sql.Timestamp.from(now)).update()
            find(accountId, request.requestId)!!.copy(shouldExecute = true)
        }

    override fun find(accountId: UUID, requestId: UUID): StoredRegistrationCorrection? = jdbc.sql("""
        SELECT o.actor_user_id,o.status,c.payload_encrypted,c.identity_snapshot_encrypted FROM receivable_operations o
        JOIN receivable_registration_corrections c ON c.account_id=o.account_id AND c.operation_id=o.id
        WHERE o.account_id=:account AND o.request_id=:request AND o.kind='CORRECT_REGISTRATION'
    """.trimIndent()).param("account", accountId).param("request", requestId).query { rs, _ ->
        val node = mapper.readTree(secrets.decrypt(accountId, "registration-correction", rs.getString("payload_encrypted")))
        val correction = RegistrationCorrection(node["email"].asText(), node["phone"]?.takeUnless { it.isNull }?.asText(),
                node["mobilePhone"].asText(), node["site"]?.takeUnless { it.isNull }?.asText(), node["incomeCents"].asLong(),
                node["postalCode"].asText(), node["address"].asText(), node["addressNumber"].asText(),
                node["complement"]?.takeUnless { it.isNull }?.asText(), node["province"].asText())
        StoredRegistrationCorrection(accountId, requestId, rs.getObject("actor_user_id", UUID::class.java), correction,
            RegistrationCorrectionStatus.valueOf(rs.getString("status")),
            identitySnapshot = rs.getString("identity_snapshot_encrypted")?.let {
                val identity = mapper.readTree(secrets.decrypt(accountId, "registration-identity-snapshot", it))
                fun optional(name: String) = identity[name]?.takeUnless { field -> field.isNull }?.asText()
                CommercialRegistration(identity["personType"].asText(), identity["cpfCnpj"].asText(),
                    optional("birthDate"), optional("companyType"), optional("companyName"), optional("taxRegime"), correction)
            })
    }.optional().orElse(null)

    override fun mark(accountId: UUID, requestId: UUID, status: RegistrationCorrectionStatus, now: Instant) {
        jdbc.sql("""
            UPDATE receivable_operations SET status=:status,updated_at=:now,next_attempt_at=:now
            WHERE account_id=:account AND request_id=:request AND kind='CORRECT_REGISTRATION'
        """.trimIndent()).param("status", status.name).param("now", java.sql.Timestamp.from(now))
            .param("account", accountId).param("request", requestId).update()
        jdbc.sql("""
            UPDATE receivable_registration_corrections SET updated_at=:now WHERE account_id=:account AND operation_id=(
                SELECT id FROM receivable_operations WHERE account_id=:account AND request_id=:request)
        """.trimIndent()).param("now", java.sql.Timestamp.from(now)).param("account", accountId)
            .param("request", requestId).update()
    }

    private fun json(c: RegistrationCorrection) = mapper.writeValueAsString(linkedMapOf(
        "email" to c.email, "phone" to c.phone, "mobilePhone" to c.mobilePhone, "site" to c.site,
        "incomeCents" to c.incomeCents, "postalCode" to c.postalCode, "address" to c.address,
        "addressNumber" to c.addressNumber, "complement" to c.complement, "province" to c.province,
    ))
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
