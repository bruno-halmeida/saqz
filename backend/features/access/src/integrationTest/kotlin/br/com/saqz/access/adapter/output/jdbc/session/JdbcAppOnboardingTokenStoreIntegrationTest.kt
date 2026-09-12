package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.AppOnboardingCode
import br.com.saqz.access.application.session.SecureAppOnboardingSecrets
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAppOnboardingTokenStoreIntegrationTest {
    private lateinit var dataSource: javax.sql.DataSource
    private lateinit var repository: JdbcAppOnboardingTokenStore
    private var secretCounter = 0

    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated("classpath:db/migration", groupMigrationLocation()).dataSource
        repository = JdbcAppOnboardingTokenStore(dataSource, SecureAppOnboardingSecrets { bytes ->
            secretCounter += 1
            bytes.indices.forEach { bytes[it] = (it + secretCounter).toByte() }
        })
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_invites, group_memberships, access_groups, app_onboarding_login_tokens, access_users CASCADE")
    }

    @Test
    fun `issue stores only digest and active account identity`() {
        val userId = insertUser("issue-subject", "Issue Person")

        val issued = repository.issue("issue-subject", now)

        assertEquals(userId, issued!!.ownerUserId)
        assertEquals(43, issued.code.value.length)
        assertEquals(0, count("SELECT count(*) FROM app_onboarding_login_tokens WHERE encode(token_digest, 'base64') = '${issued.code.value}'"))
        assertEquals(1, count("SELECT count(*) FROM app_onboarding_login_tokens WHERE owner_user_id = '$userId' AND token_digest IS NOT NULL"))
        assertEquals(now.plusSeconds(600), issued.expiresAt)
    }

    @Test
    fun `issue does not create a record for unknown deleted or suspended subject`() {
        assertNull(repository.issue("missing", now))
        val deleted = insertUser("deleted-subject")
        execute("UPDATE access_users SET deleted_at = now() WHERE id = '$deleted'")
        assertNull(repository.issue("deleted-subject", now))
        val suspended = insertUser("suspended-subject")
        execute("UPDATE access_users SET suspended_at = now() WHERE id = '$suspended'")
        assertNull(repository.issue("suspended-subject", now))
        assertEquals(0, count("SELECT count(*) FROM app_onboarding_login_tokens"))
    }

    @Test
    fun `new issue invalidates prior open code for same owner`() {
        insertUser("renew-subject")
        val first = repository.issue("renew-subject", now)!!
        val second = repository.issue("renew-subject", now.plusSeconds(1))!!

        assertNotEquals(first.code, second.code)
        assertNull(repository.consumeOpen(first.code, now.plusSeconds(2)))
        assertEquals("renew-subject", repository.consumeOpen(second.code, now.plusSeconds(2))!!.firebaseSubject)
    }

    @Test
    fun `consume rejects exact expiry and inactive owner without exposing account`() {
        val userId = insertUser("expiry-subject")
        val issued = repository.issue("expiry-subject", now)!!

        assertNull(repository.consumeOpen(issued.code, issued.expiresAt))
        val fresh = repository.issue("expiry-subject", now)!!
        execute("UPDATE access_users SET suspended_at = now() WHERE id = '$userId'")
        assertNull(repository.consumeOpen(fresh.code, now.plusSeconds(1)))
    }

    @Test
    fun `consuming an open code is atomic and one use only`() {
        insertUser("consume-subject")
        val issued = repository.issue("consume-subject", now)!!

        val consumed = repository.consumeOpen(issued.code, now.plusSeconds(1))

        assertEquals("consume-subject", consumed!!.firebaseSubject)
        assertEquals("Consume Person", consumed.displayName.value)
        assertTrue(repository.consumeOpen(issued.code, now.plusSeconds(2)) == null)
        assertEquals(1, count("SELECT count(*) FROM app_onboarding_login_tokens WHERE consumed_at IS NOT NULL"))
    }

    private fun insertUser(subject: String, displayName: String = "Consume Person"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject', true, '$displayName', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) = dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } }
    private fun count(sql: String): Int = dataSource.connection.use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } }
    }

    private fun groupMigrationLocation(): String {
        var directory = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            val candidates = listOf(
                directory.resolve("backend/features/groups/src/main/resources/db/migration"),
                directory.resolve("features/groups/src/main/resources/db/migration"),
                directory.resolve("groups/src/main/resources/db/migration"),
            )
            candidates.firstOrNull(java.nio.file.Files::isDirectory)?.let { return "filesystem:$it" }
            directory = directory.parent ?: return@repeat
        }
        error("Cannot find groups migrations")
    }
}
