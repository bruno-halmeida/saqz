package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessProfileEnumMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.empty(owner = this).dataSource
    }

    @Test
    fun `v43 registers access profile enums and converts the closed-set columns`() {
        flyway().migrate()

        assertEquals(
            listOf(
                "court_play_style" to listOf("SIX_ZERO", "FOUR_TWO", "FIVE_ONE", "CUSTOM"),
                "group_composition" to listOf("WOMEN", "MEN", "MIXED"),
                "group_level" to listOf("BEGINNER", "INTERMEDIATE", "ADVANCED", "MIXED_LEVELS", "CUSTOM"),
                "group_modality" to listOf("COURT_VOLLEYBALL", "BEACH_VOLLEYBALL", "FOOTVOLLEY"),
                "group_profile_status" to listOf("INCOMPLETE", "COMPLETE"),
                "group_role" to listOf("ADMIN", "ATHLETE"),
                "phone_visibility" to listOf("EVERYONE", "ADMINS", "NOBODY"),
                "promotion_mode" to listOf("FIFO", "MANUAL"),
            ),
            enumCatalog(),
        )
        assertEquals("group_role", udt("group_memberships", "role"))
        assertEquals("phone_visibility", udt("access_users", "phone_visibility"))
        assertEquals("group_profile_status", udt("access_groups", "profile_status"))
        assertEquals("group_modality", udt("access_groups", "modality"))
        assertEquals("group_composition", udt("access_groups", "composition"))
        assertEquals("group_level", udt("access_groups", "level"))
        assertEquals("court_play_style", udt("access_groups", "play_style"))
        assertEquals("promotion_mode", udt("access_groups", "promotion_mode"))
        assertEquals("varchar", udt("access_groups", "privacy"))
        assertEquals("bpchar", udt("access_groups", "currency"))
        assertTrue(
            queryStrings(
                """
                SELECT conname FROM pg_constraint
                WHERE conrelid IN (
                    'group_memberships'::regclass, 'access_users'::regclass, 'access_groups'::regclass
                )
                  AND contype = 'c'
                  AND conname IN (
                      'ck_group_memberships_role',
                      'ck_access_users_phone_visibility',
                      'ck_access_groups_modality',
                      'ck_access_groups_composition',
                      'ck_access_groups_level',
                      'ck_access_groups_play_style',
                      'ck_access_groups_promotion_mode'
                  )
                """.trimIndent(),
            ).isEmpty(),
        )
        assertEquals(
            setOf(
                "ck_access_groups_profile_status",
                "ck_access_groups_custom_level",
                "ck_access_groups_court_play_style",
                "ck_access_groups_custom_play_style",
            ),
            queryStrings(
                """
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'access_groups'::regclass
                  AND contype = 'c'
                  AND conname IN (
                      'ck_access_groups_profile_status',
                      'ck_access_groups_custom_level',
                      'ck_access_groups_court_play_style',
                      'ck_access_groups_custom_play_style'
                  )
                """.trimIndent(),
            ).toSet(),
        )
        assertEquals("ADMINS", defaultLabel("access_users", "phone_visibility"))
        assertEquals("INCOMPLETE", defaultLabel("access_groups", "profile_status"))
        assertEquals("FIFO", defaultLabel("access_groups", "promotion_mode"))
    }

    @Test
    fun `v42 to v43 preserves profile labels including null modality and default phone visibility`() {
        flyway("42").migrate()
        val complete = completeGroup()
        execute(
            """
            UPDATE access_groups SET
                level = 'CUSTOM',
                custom_level = 'Sub 18',
                play_style = 'FIVE_ONE',
                promotion_mode = 'MANUAL'
            WHERE id = '${complete.group}'
            """.trimIndent(),
        )
        execute("UPDATE access_users SET phone_visibility = 'EVERYONE' WHERE id = '${complete.owner}'")
        execute(
            "UPDATE group_memberships SET role = 'ADMIN' WHERE group_id = '${complete.group}' AND user_id = '${complete.member}'",
        )
        val incomplete = incompleteGroup()

        flyway().migrate()

        assertEquals("COMPLETE", string("SELECT profile_status::text FROM access_groups WHERE id = '${complete.group}'"))
        assertEquals("COURT_VOLLEYBALL", string("SELECT modality::text FROM access_groups WHERE id = '${complete.group}'"))
        assertEquals("MIXED", string("SELECT composition::text FROM access_groups WHERE id = '${complete.group}'"))
        assertEquals("CUSTOM", string("SELECT level::text FROM access_groups WHERE id = '${complete.group}'"))
        assertEquals("FIVE_ONE", string("SELECT play_style::text FROM access_groups WHERE id = '${complete.group}'"))
        assertEquals("MANUAL", string("SELECT promotion_mode::text FROM access_groups WHERE id = '${complete.group}'"))
        assertEquals("EVERYONE", string("SELECT phone_visibility::text FROM access_users WHERE id = '${complete.owner}'"))
        assertEquals("ADMIN", string("SELECT role::text FROM group_memberships WHERE user_id = '${complete.member}'"))
        assertEquals("INCOMPLETE", string("SELECT profile_status::text FROM access_groups WHERE id = '${incomplete.group}'"))
        assertEquals(null, nullableString("SELECT modality::text FROM access_groups WHERE id = '${incomplete.group}'"))
        assertEquals("FIFO", string("SELECT promotion_mode::text FROM access_groups WHERE id = '${incomplete.group}'"))
        assertEquals("ADMINS", string("SELECT phone_visibility::text FROM access_users WHERE id = '${incomplete.owner}'"))
        assertEquals("group_role", udt("group_memberships", "role"))
    }

    @Test
    fun `v43 keeps incomplete and fifo defaults`() {
        flyway().migrate()
        val incomplete = incompleteGroup()
        assertEquals("INCOMPLETE", string("SELECT profile_status::text FROM access_groups WHERE id = '${incomplete.group}'"))
        assertEquals("FIFO", string("SELECT promotion_mode::text FROM access_groups WHERE id = '${incomplete.group}'"))
        assertEquals("ADMINS", string("SELECT phone_visibility::text FROM access_users WHERE id = '${incomplete.owner}'"))
        assertEquals("ATHLETE", string("SELECT role::text FROM group_memberships WHERE user_id = '${incomplete.member}'"))
    }

    @Test
    fun `v43 rejects labels that are not in the enums and keeps composite checks`() {
        flyway().migrate()
        val complete = completeGroup()
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET role = 'OWNER' WHERE user_id = '${complete.member}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE access_users SET phone_visibility = 'FRIENDS' WHERE id = '${complete.owner}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE access_groups SET modality = 'HANDBALL' WHERE id = '${complete.group}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE access_groups SET profile_status = 'COMPLETE', modality = NULL WHERE id = '${complete.group}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE access_groups SET level = 'BEGINNER', custom_level = 'Sub 18' WHERE id = '${complete.group}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE access_groups SET modality = 'BEACH_VOLLEYBALL', play_style = 'SIX_ZERO' WHERE id = '${complete.group}'")
        }
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun completeGroup(): Fixture {
        val owner = user("enum-owner")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (
                id, owner_user_id, creation_key, name, time_zone, profile_status,
                modality, composition, created_at, updated_at
            ) VALUES (
                '$group', '$owner', '${UUID.randomUUID()}', 'Enum Group', 'America/Sao_Paulo',
                'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now()
            )
            """.trimIndent(),
        )
        val member = user("enum-member")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$member', 'ATHLETE', now(), now())",
        )
        return Fixture(owner, group, member)
    }

    private fun incompleteGroup(): Fixture {
        val owner = user("enum-incomplete")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (
                id, owner_user_id, creation_key, name, time_zone, created_at, updated_at
            ) VALUES (
                '$group', '$owner', '${UUID.randomUUID()}', 'Draft Group', 'America/Sao_Paulo',
                now(), now()
            )
            """.trimIndent(),
        )
        val member = user("enum-incomplete-member")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$member', 'ATHLETE', now(), now())",
        )
        return Fixture(owner, group, member)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, 'User', now(), now())",
        )
        return id
    }

    private fun enumCatalog(): List<Pair<String, List<String>>> {
        val labels = mutableMapOf<String, MutableList<String>>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT t.typname, e.enumlabel
                    FROM pg_type t
                    JOIN pg_namespace n ON n.oid = t.typnamespace
                    JOIN pg_enum e ON e.enumtypid = t.oid
                    WHERE n.nspname = 'public'
                      AND t.typname IN (
                          'group_role', 'phone_visibility', 'group_profile_status',
                          'group_modality', 'group_composition', 'group_level',
                          'court_play_style', 'promotion_mode'
                      )
                    ORDER BY t.typname, e.enumsortorder
                    """.trimIndent(),
                ).use { result ->
                    while (result.next()) {
                        labels.getOrPut(result.getString("typname")) { mutableListOf() }
                            .add(result.getString("enumlabel"))
                    }
                }
            }
        }
        return labels.toSortedMap().map { it.key to it.value }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun string(sql: String): String = query(sql) { it.getString(1) }

    private fun nullableString(sql: String): String? = query(sql) { it.getString(1) }

    private fun udt(table: String, column: String): String =
        string("SELECT udt_name FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column'")

    private fun defaultLabel(table: String, column: String): String {
        val default = string(
            "SELECT column_default FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column'",
        )
        return default.substringAfter("'").substringBefore("'")
    }

    private fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    while (result.next()) values += result.getString(1)
                }
            }
        }
        return values
    }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(val owner: UUID, val group: UUID, val member: UUID)
}
