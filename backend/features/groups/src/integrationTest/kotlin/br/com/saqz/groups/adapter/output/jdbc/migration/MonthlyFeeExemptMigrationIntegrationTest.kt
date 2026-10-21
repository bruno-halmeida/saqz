package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// DEB-115: coluna da isenção e o gatilho set_mensalista_since recriado na V181.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonthlyFeeExemptMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `nova linha nasce sem isencao`() {
        val (group, member) = membership("default")
        assertEquals(false, bool("SELECT monthly_fee_exempt FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `avulso nao pode ser isento`() {
        val (group, member) = membership("avulso")
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET monthly_fee_exempt = true WHERE group_id = '$group' AND user_id = '$member'")
        }
    }

    @Test
    fun `virar avulso zera a isencao`() {
        val (group, member) = membership("to-avulso")
        execute("UPDATE group_memberships SET membership_type = 'MENSALISTA', monthly_fee_exempt = true WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET membership_type = 'AVULSO' WHERE group_id = '$group' AND user_id = '$member'")
        assertEquals(false, bool("SELECT monthly_fee_exempt FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertNull(stringOrNull("SELECT mensalista_since::text FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `linha reativada volta sem isencao`() {
        val (group, member) = membership("reactivated")
        execute("UPDATE group_memberships SET membership_type = 'MENSALISTA', monthly_fee_exempt = true WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET active = false WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET active = true WHERE group_id = '$group' AND user_id = '$member'")
        assertEquals(false, bool("SELECT monthly_fee_exempt FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `tirar a isencao grava mensalista_since hoje no fuso do grupo`() {
        val (group, member) = membership("lift")
        execute("UPDATE group_memberships SET membership_type = 'MENSALISTA', monthly_fee_exempt = true WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET mensalista_since = DATE '2026-01-01' WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET monthly_fee_exempt = false WHERE group_id = '$group' AND user_id = '$member'")
        assertEquals(
            true,
            bool("SELECT mensalista_since = (now() AT TIME ZONE 'America/Sao_Paulo')::date FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"),
        )
    }

    @Test
    fun `isentar nao mexe em mensalista_since`() {
        val (group, member) = membership("grant")
        execute("UPDATE group_memberships SET membership_type = 'MENSALISTA' WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET mensalista_since = DATE '2026-01-01' WHERE group_id = '$group' AND user_id = '$member'")
        execute("UPDATE group_memberships SET monthly_fee_exempt = true WHERE group_id = '$group' AND user_id = '$member'")
        assertEquals("2026-01-01", stringOrNull("SELECT mensalista_since::text FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    private fun membership(subject: String): Pair<UUID, UUID> {
        val owner = user("$subject-owner")
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Grupo', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        val member = user("$subject-member")
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$member', 'ATHLETE', now(), now())")
        return group to member
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, 'Pessoa', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun bool(sql: String): Boolean = query(sql) { it.getBoolean(1) }
    private fun stringOrNull(sql: String): String? = query(sql) { it.getString(1) }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }
}
