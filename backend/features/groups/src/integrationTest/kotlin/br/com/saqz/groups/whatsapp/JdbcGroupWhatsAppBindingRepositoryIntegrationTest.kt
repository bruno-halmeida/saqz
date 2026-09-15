package br.com.saqz.groups.whatsapp

import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcGroupWhatsAppBindingRepository
import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBinding
import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBindingStatus
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupWhatsAppBindingRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `V73 creates the binding table keyed by group with a unique whatsapp jid`() {
        assertEquals(
            1,
            int(
                "SELECT count(*) FROM pg_constraint " +
                    "WHERE conrelid = 'group_whatsapp_bindings'::regclass AND contype = 'p'",
            ),
        )
        assertEquals(
            1,
            int(
                "SELECT count(*) FROM pg_constraint " +
                    "WHERE conrelid = 'group_whatsapp_bindings'::regclass AND contype = 'u'",
            ),
        )
    }

    @Test
    fun `find returns null when the group has no binding`() {
        assertNull(repository().find(group()))
    }

    @Test
    fun `upsert persists the binding enabled on birth`() {
        val groupId = group()
        val creator = user("owner")

        repository().upsert(binding(groupId, creator, jid = "5511888880001", invite = "invite-abc"))

        val stored = assertNotNull(repository().find(groupId))
        assertEquals(groupId, stored.groupId)
        assertEquals("5511888880001", stored.whatsappJid)
        assertEquals("invite-abc", stored.inviteCode)
        assertEquals("Grupo da Pelada", stored.groupName)
        assertEquals("5511999990000", stored.instanceJid)
        assertEquals(creator, stored.createdBy)
        assertEquals(true, stored.enabled)
        assertNull(stored.brokenAt)
        assertEquals(GroupWhatsAppBindingStatus.ACTIVE, stored.status())
    }

    @Test
    fun `upsert replaces the existing binding of the same group and frees the previous jid`() {
        val groupId = group()
        val creator = user("owner")

        repository().upsert(binding(groupId, creator, jid = "5511888880001", invite = "old"))
        repository().upsert(binding(groupId, creator, jid = "5511888880002", invite = "new"))

        val stored = assertNotNull(repository().find(groupId))
        assertEquals("5511888880002", stored.whatsappJid)
        assertEquals("new", stored.inviteCode)
        assertEquals(1, int("SELECT count(*) FROM group_whatsapp_bindings WHERE group_id = '$groupId'"))

        val other = group()
        repository().upsert(binding(other, creator, jid = "5511888880001", invite = "reused"))
        assertEquals("5511888880001", assertNotNull(repository().find(other)).whatsappJid)
    }

    @Test
    fun `the same whatsapp jid cannot be bound to two saqz groups`() {
        val creator = user("owner")
        repository().upsert(binding(group(), creator, jid = "5511888880001"))

        assertFailsWith<DataIntegrityViolationException> {
            repository().upsert(binding(group(), creator, jid = "5511888880001"))
        }
    }

    @Test
    fun `disabling preserves the binding and reports DISABLED`() {
        val groupId = group()
        val creator = user("owner")
        repository().upsert(binding(groupId, creator, jid = "5511888880001"))

        repository().setEnabled(groupId, false)

        val stored = assertNotNull(repository().find(groupId))
        assertEquals(false, stored.enabled)
        assertEquals(GroupWhatsAppBindingStatus.DISABLED, stored.status())
        assertEquals("5511888880001", stored.whatsappJid)
    }

    @Test
    fun `re-enabling resumes the same binding as ACTIVE`() {
        val groupId = group()
        val creator = user("owner")
        repository().upsert(binding(groupId, creator, jid = "5511888880001"))
        repository().setEnabled(groupId, false)

        repository().setEnabled(groupId, true)

        val stored = assertNotNull(repository().find(groupId))
        assertEquals(true, stored.enabled)
        assertEquals("5511888880001", stored.whatsappJid)
        assertEquals(GroupWhatsAppBindingStatus.ACTIVE, stored.status())
    }

    @Test
    fun `markBroken records the break without losing the binding`() {
        val groupId = group()
        val creator = user("owner")
        repository().upsert(binding(groupId, creator, jid = "5511888880001"))

        repository().markBroken(groupId)

        val stored = assertNotNull(repository().find(groupId))
        assertNotNull(stored.brokenAt)
        assertEquals(true, stored.enabled)
        assertEquals("5511888880001", stored.whatsappJid)
        assertEquals("Grupo da Pelada", stored.groupName)
        assertEquals(GroupWhatsAppBindingStatus.BROKEN, stored.status())
    }

    @Test
    fun `broken takes precedence over disabled`() {
        val groupId = group()
        val creator = user("owner")
        repository().upsert(binding(groupId, creator, jid = "5511888880001"))

        repository().markBroken(groupId)
        repository().setEnabled(groupId, false)

        assertEquals(GroupWhatsAppBindingStatus.BROKEN, assertNotNull(repository().find(groupId)).status())
    }

    private fun repository() = JdbcGroupWhatsAppBindingRepository(dataSource)

    private fun binding(
        groupId: UUID,
        creator: UUID,
        jid: String,
        invite: String = "invite-code",
    ) = GroupWhatsAppBinding(
        groupId = groupId,
        whatsappJid = jid,
        inviteCode = invite,
        groupName = "Grupo da Pelada",
        instanceJid = "5511999990000",
        enabled = true,
        brokenAt = null,
        createdBy = creator,
    )

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '${subject.replaceFirstChar(Char::uppercase)} Person', now(), now())",
        )
        return id
    }

    private fun group(): UUID {
        val id = UUID.randomUUID()
        val owner = user("owner")
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, " +
                "modality, composition, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'UTC', 'COMPLETE', " +
                "'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun int(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getInt(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
}