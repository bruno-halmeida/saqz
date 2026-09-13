package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialManagement
import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class JdbcFinancialManagementIntegrationTest {
    @Test fun `V62 persists correction encrypted and enforces actor payload idempotency`() {
        val db = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner = this)
        val jdbc = JdbcClient.create(db.dataSource)
        val account = UUID.randomUUID(); val owner = UUID.randomUUID(); val requestId = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,
                registration,created_at,updated_at) VALUES (:id,:owner,'digest','ciphertext','CORRECTION_REQUIRED',now(),now())
        """.trimIndent()).param("id", account).param("owner", owner).update()
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val store = JdbcFinancialManagement(db.dataSource, secrets)
        val correction = RegistrationCorrection("private@example.test", null, "11999999999", null, 250001,
            "01001000", "Rua", "10", null, "Centro")
        val first = store.begin(account, FinancialRequest(requestId, owner), correction, Instant.EPOCH)
        assertEquals(RegistrationCorrectionStatus.READY, first.status)
        val ciphertext = jdbc.sql("SELECT payload_encrypted FROM receivable_registration_corrections")
            .query(String::class.java).single()
        assertTrue(ciphertext.startsWith("v1.test.")); assertFalse(ciphertext.contains("private@example.test"))
        assertEquals(250001, store.find(account, requestId)!!.correction.incomeCents)
        val replay = store.begin(account, FinancialRequest(requestId, owner), correction, Instant.EPOCH)
        assertEquals(first.accountId, replay.accountId); assertEquals(first.requestId, replay.requestId)
        assertEquals(first.actorUserId, replay.actorUserId); assertEquals(first.status, replay.status)
        assertEquals(first.correction.incomeCents, replay.correction.incomeCents)
        assertFailsWith<FinancialRequestConflict> { store.begin(account, FinancialRequest(requestId, UUID.randomUUID()), correction, Instant.EPOCH) }
        assertFailsWith<FinancialRequestConflict> { store.begin(account, FinancialRequest(requestId, owner),
            RegistrationCorrection("private@example.test", null, "11999999999", null, 1,
                "01001000", "Rua", "10", null, "Centro"), Instant.EPOCH) }
        val identity = CommercialRegistration("JURIDICA", "12345678000199", null, "LIMITED", "Clube", "SIMPLES", correction)
        store.recordIdentitySnapshot(account, requestId, identity)
        val identityCiphertext = jdbc.sql("SELECT identity_snapshot_encrypted FROM receivable_registration_corrections")
            .query(String::class.java).single()
        assertFalse(identityCiphertext.contains("12345678000199"))
        assertFalse(identityCiphertext.contains("Clube"))
        val restored = store.find(account, requestId)!!.identitySnapshot!!
        assertEquals("12345678000199", restored.cpfCnpj)
        assertEquals("Clube", restored.companyName)
        assertEquals("SIMPLES", restored.taxRegime)
        assertFailsWith<IllegalStateException> { store.recordIdentitySnapshot(account, requestId, identity) }
        store.mark(account, requestId, RegistrationCorrectionStatus.UNKNOWN, Instant.EPOCH)
        assertEquals(RegistrationCorrectionStatus.UNKNOWN, store.find(account, requestId)!!.status)
    }
}
