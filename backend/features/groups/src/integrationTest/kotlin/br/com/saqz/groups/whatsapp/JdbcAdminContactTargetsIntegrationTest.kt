package br.com.saqz.groups.whatsapp

import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcAdminContactTargets
import br.com.saqz.groups.application.whatsapp.SaqzAccountLink
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminContactTargetsIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var targets: JdbcAdminContactTargets

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        targets = JdbcAdminContactTargets(dataSource)
    }

    @Test
    fun `find reads the phone of the record and the latest contact decides do not contact`() {
        val carla = user("Carla", "+5511988887777")
        val admin = user("Ana Admin", null)
        assertEquals("+5511988887777", targets.find(carla)?.phone)
        assertFalse(targets.find(carla)!!.doNotContact)

        contact(carla, admin, "DO_NOT_CONTACT", "now() - interval '1 day'")
        assertTrue(targets.find(carla)!!.doNotContact)
        contact(carla, admin, "NO_ANSWER", "now()")
        assertFalse(targets.find(carla)!!.doNotContact)

        val deleted = user("Apagada", "+5511977776666", deleted = true)
        assertNull(targets.find(deleted))
        assertNull(targets.find(UUID.randomUUID()))
    }

    @Test
    fun `by phone links one account per phone and leaves shared or deleted phones out`() {
        val carla = user("Carla", "+5511988887777")
        user("Rita", "+5511966665555")
        user("Rita de novo", "+5511966665555")
        user("Apagada", "+5511944443333", deleted = true)

        val links = targets.byPhone(listOf("+5511988887777", "+5511966665555", "+5511944443333", "+5511900000000"))

        assertEquals(mapOf("+5511988887777" to SaqzAccountLink(carla, "Carla")), links)
        assertTrue(targets.byPhone(emptyList()).isEmpty())
    }

    @Test
    fun `record sent notes the message once per request with the long text cut to the note limit`() {
        val carla = user("Carla", "+5511988887777")
        val admin = user("Ana Admin", null)
        val request = UUID.randomUUID()

        targets.recordSent(carla, admin, request, "Oi, Carla!")
        targets.recordSent(carla, admin, request, "Oi, Carla!")
        targets.recordSent(carla, admin, UUID.randomUUID(), "a".repeat(1500))

        val notes = query("SELECT result || '|' || note FROM admin_user_contacts WHERE user_id = '$carla' ORDER BY char_length(note)")
        assertEquals(2, notes.size)
        assertEquals("SAQZ_MESSAGE|Oi, Carla!", notes[0])
        assertEquals("SAQZ_MESSAGE|" + "a".repeat(999) + "…", notes[1])
        assertFalse(targets.find(carla)!!.doNotContact)

        val deleted = user("Apagada", "+5511977776666", deleted = true)
        targets.recordSent(deleted, admin, UUID.randomUUID(), "oi")
        assertTrue(query("SELECT id::text FROM admin_user_contacts WHERE user_id = '$deleted'").isEmpty())
    }

    @Test
    fun `manual contact still refuses the server only result and keeps working without a request id`() {
        val carla = user("Carla", "+5511988887777")
        val admin = user("Ana Admin", null)
        contact(carla, admin, "OTHER", "now()")
        contact(carla, admin, "OTHER", "now()")
        assertEquals(2, query("SELECT id::text FROM admin_user_contacts WHERE user_id = '$carla' AND request_id IS NULL").size)
        val refused = runCatching { contact(carla, admin, "WHATEVER", "now()") }
        assertTrue(refused.isFailure)
    }

    private fun user(name: String, phone: String?, deleted: Boolean = false): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, phone, created_at, updated_at, deleted_at) " +
            "VALUES ('$id', 's-$id', true, '$name', ${phone?.let { "'$it'" } ?: "NULL"}, now(), now(), ${if (deleted) "now()" else "NULL"})")
        return id
    }

    private fun contact(user: UUID, author: UUID, result: String, at: String) {
        execute("INSERT INTO admin_user_contacts (id, user_id, result, created_by, created_at) " +
            "VALUES ('${UUID.randomUUID()}', '$user', '$result', '$author', $at)")
    }

    private fun query(sql: String): List<String> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    private fun execute(sql: String) {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } }
    }
}
