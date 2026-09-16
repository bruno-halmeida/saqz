package br.com.saqz.groups.whatsapp

import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcGroupWhatsAppBindingRepository
import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBinding
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupWhatsAppBindingMembersIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `memberPhones returns owner and active members normalized to digits`() {
        val owner = user("owner", "+5511988887777")
        val member = user("member", "+5511977776666")
        val silent = user("silent", null)
        val group = group(owner)
        membership(group, owner)
        membership(group, member)
        membership(group, silent)

        val phones = repository().memberPhones(group)

        assertEquals(setOf("5511988887777", "5511977776666"), phones.toSet())
    }

    @Test
    fun `memberPhones excludes soft deleted groups`() {
        val owner = user("owner", "+5511988887777")
        val group = group(owner)
        membership(group, owner)
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$group'")

        assertTrue(repository().memberPhones(group).isEmpty())
    }

    @Test
    fun `findByJid returns the holder of the whatsapp jid`() {
        val creator = user("owner", "+5511988887777")
        val group = group(creator)
        repository().upsert(binding(group, creator, jid = "5511888880001"))

        assertEquals(group, repository().findByJid("5511888880001")?.groupId)
        assertNull(repository().findByJid("5511000000000"))
    }

    private fun repository() = JdbcGroupWhatsAppBindingRepository(dataSource)

    private fun binding(groupId: UUID, creator: UUID, jid: String) = GroupWhatsAppBinding(
        groupId = groupId,
        whatsappJid = jid,
        inviteCode = "Invite1234",
        groupName = "Vôlei do CERET",
        instanceJid = "551153040175",
        enabled = true,
        brokenAt = null,
        createdBy = creator,
    )

    private fun user(subject: String, phone: String?): UUID {
        val id = UUID.randomUUID()
        val phoneValue = phone?.let { "'$it'" } ?: "NULL"
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, phone, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '${subject.replaceFirstChar(Char::uppercase)} Person', " +
                "$phoneValue, now(), now())",
        )
        return id
    }

    private fun group(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, " +
                "modality, composition, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'UTC', 'COMPLETE', " +
                "'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return id
    }

    private fun membership(groupId: UUID, userId: UUID) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$groupId', '$userId', 'ATHLETE', now(), now())",
    )

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection
}
