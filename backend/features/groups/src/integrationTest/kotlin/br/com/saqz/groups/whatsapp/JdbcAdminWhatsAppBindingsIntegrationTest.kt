package br.com.saqz.groups.whatsapp

import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcAdminWhatsAppBindings
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminWhatsAppBindingsIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `only enabled bindings of living groups count, with the group name and broken state`() {
        val owner = UUID.randomUUID()
        execute("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
            "VALUES ('$owner', 's-$owner', true, 'Dona', now(), now())")
        val active = bind(owner, "ativo@g.us", "Grupo ativo", enabled = true, broken = false, deleted = false)
        bind(owner, "quebrado@g.us", "Grupo quebrado", enabled = true, broken = true, deleted = false)
        bind(owner, "desligado@g.us", "Grupo desligado", enabled = false, broken = false, deleted = false)
        bind(owner, "apagado@g.us", "Grupo apagado", enabled = true, broken = false, deleted = true)

        val links = JdbcAdminWhatsAppBindings(dataSource).activeByJid()

        assertEquals(setOf("ativo@g.us", "quebrado@g.us"), links.keys)
        assertEquals(active, links.getValue("ativo@g.us").id)
        assertEquals("Grupo ativo", links.getValue("ativo@g.us").name)
        assertFalse(links.getValue("ativo@g.us").broken)
        assertTrue(links.getValue("quebrado@g.us").broken)
    }

    private fun bind(owner: UUID, jid: String, name: String, enabled: Boolean, broken: Boolean, deleted: Boolean): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, " +
            "created_at, updated_at, deleted_at) VALUES ('$id', '$owner', '${UUID.randomUUID()}', '$name', 'UTC', 'COMPLETE', " +
            "'COURT_VOLLEYBALL', 'MIXED', now(), now(), ${if (deleted) "now()" else "NULL"})")
        execute("INSERT INTO group_whatsapp_bindings (group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, broken_at, created_by) " +
            "VALUES ('$id', '$jid', 'c', 'nome no whatsapp', 'i', $enabled, ${if (broken) "now()" else "NULL"}, '$owner')")
        return id
    }

    private fun execute(sql: String) {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } }
    }
}
