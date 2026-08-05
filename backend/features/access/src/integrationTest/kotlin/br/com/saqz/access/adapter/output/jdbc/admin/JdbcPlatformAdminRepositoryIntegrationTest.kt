package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPlatformAdminRepositoryIntegrationTest {
    private val database = TestPostgres.migrated("classpath:db/migration")
    private lateinit var repository: JdbcPlatformAdminRepository

    @BeforeAll
    fun startDatabase() {
        repository = JdbcPlatformAdminRepository(database.dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE access_users CASCADE")
    }

    @Test
    fun `admin marcado retorna id email e nome exatos`() {
        val id = insertUser(subject = "admin-subject", email = "admin@saqz.test", name = "Ana Admin", admin = true)

        val found = repository.findBySubject("admin-subject")

        assertEquals(PlatformAdminView(id, "admin@saqz.test", "Ana Admin"), found)
    }

    @Test
    fun `usuario comum nao aparece como admin`() {
        insertUser(subject = "user-subject", email = "user@saqz.test", name = "Uso Comum", admin = false)

        assertNull(repository.findBySubject("user-subject"))
    }

    @Test
    fun `admin com conta apagada deixa de ser admin`() {
        insertUser(subject = "gone-subject", email = "gone@saqz.test", name = "Foi Embora", admin = true, deleted = true)

        assertNull(repository.findBySubject("gone-subject"))
    }

    @Test
    fun `sujeito desconhecido nao e admin`() {
        assertNull(repository.findBySubject("nobody"))
    }

    private fun insertUser(
        subject: String,
        email: String,
        name: String,
        admin: Boolean,
        deleted: Boolean = false,
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_users (
                id, firebase_subject, email, email_verified, display_name, platform_admin,
                created_at, updated_at, deleted_at
            ) VALUES (
                '$id', '$subject', '$email', true, '$name', $admin,
                now(), now(), ${if (deleted) "now()" else "NULL"}
            )
            """.trimIndent(),
        )
        return id
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
