package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.AppOnboardingDigest
import br.com.saqz.access.application.session.SecureAppOnboardingSecrets
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPhoneConfirmationStoreIntegrationTest {
    private lateinit var dataSource: javax.sql.DataSource
    private lateinit var store: JdbcPhoneConfirmationStore
    private val secrets = SecureAppOnboardingSecrets()
    private val now = Instant.parse("2026-09-25T12:00:00Z")
    private val phone = "+5541999990000"

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated("classpath:db/migration", groupMigrationLocation()).dataSource
        store = JdbcPhoneConfirmationStore(dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE access_phone_confirmations, access_users CASCADE")
    }

    @Test
    fun `link aberto confirma o telefone atual e so vale uma vez`() {
        val userId = insertUser("subject", phone)
        val digest = secrets.next().digest

        assertEquals(phone, store.issue("subject", digest, now.plusSeconds(60)))
        assertTrue(store.confirm(digest, now))
        assertFalse(store.confirm(digest, now))

        assertEquals(phone, verifiedPhone(userId))
        assertNull(store.issue("subject", secrets.next().digest, now.plusSeconds(60)))
    }

    @Test
    fun `token novo derruba o anterior`() {
        insertUser("subject", phone)
        val first = secrets.next().digest
        val second = secrets.next().digest
        store.issue("subject", first, now.plusSeconds(60))
        store.issue("subject", second, now.plusSeconds(60))

        assertFalse(store.confirm(first, now))
        assertTrue(store.confirm(second, now))
    }

    @Test
    fun `token vencido ou telefone trocado nao confirma`() {
        val userId = insertUser("subject", phone)
        val expired = secrets.next().digest
        store.issue("subject", expired, now)
        assertFalse(store.confirm(expired, now))

        val changed = secrets.next().digest
        store.issue("subject", changed, now.plusSeconds(60))
        execute("UPDATE access_users SET phone = '+5541988887777' WHERE id = '$userId'")
        assertFalse(store.confirm(changed, now))
        assertNull(verifiedPhone(userId))
    }

    @Test
    fun `sem telefone nao emite`() {
        insertUser("subject", phone = null)

        assertNull(store.issue("subject", secrets.next().digest, now.plusSeconds(60)))
        assertFalse(store.confirm(AppOnboardingDigest.from(ByteArray(32)), now))
    }

    private fun verifiedPhone(userId: UUID): String? = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT verified_phone FROM access_users WHERE id = '$userId'")
                .use { result -> result.next(); result.getString(1) }
        }
    }

    private fun insertUser(subject: String, phone: String?): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, phone, created_at, updated_at) " +
                "VALUES ('$id', '$subject', false, 'Pessoa', ${phone?.let { "'$it'" } ?: "NULL"}, now(), now())",
        )
        return id
    }

    private fun execute(sql: String) = dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } }

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
