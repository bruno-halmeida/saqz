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
class AthleteEnumMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.empty(owner = this).dataSource
    }

    @Test
    fun `v42 registers athlete enums and converts the closed-set columns`() {
        flyway().migrate()

        assertEquals(
            listOf(
                "athlete_level" to listOf("INICIANTE", "INTERMEDIARIO", "AVANCADO"),
                "athlete_membership_type" to listOf("MENSALISTA", "AVULSO"),
                "athlete_position" to listOf("LIBERO", "PONTA", "CENTRAL", "OPOSTO", "LEVANTADOR"),
                "athlete_preferred_side" to listOf("DIREITA", "ESQUERDA", "TANTO_FAZ"),
            ),
            enumCatalog(),
        )
        assertEquals("athlete_position", udt("group_memberships", "position"))
        assertEquals("athlete_position", udt("group_memberships", "secondary_position"))
        assertEquals("athlete_membership_type", udt("group_memberships", "membership_type"))
        assertEquals("athlete_level", udt("group_memberships", "level"))
        assertEquals("athlete_preferred_side", udt("group_memberships", "preferred_side"))
        assertTrue(
            queryStrings(
                """
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'group_memberships'::regclass
                  AND contype = 'c'
                  AND conname IN (
                      'ck_group_memberships_position',
                      'ck_group_memberships_membership_type',
                      'ck_group_memberships_level',
                      'ck_group_memberships_preferred_side'
                  )
                """.trimIndent(),
            ).isEmpty(),
        )
        assertEquals(
            1,
            int(
                """
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'group_memberships'::regclass
                  AND conname = 'ck_group_memberships_secondary_position'
                """.trimIndent(),
            ),
        )
        assertEquals("AVULSO", defaultLabel("group_memberships", "membership_type"))
    }

    @Test
    fun `v41 to v42 preserves athlete labels including null position level and side`() {
        flyway("41").migrate()
        val fixture = fixture()
        execute(
            """
            UPDATE group_memberships SET
                position = 'PONTA',
                secondary_position = 'CENTRAL',
                membership_type = 'MENSALISTA',
                level = 'AVANCADO',
                preferred_side = 'DIREITA'
            WHERE group_id = '${fixture.group}' AND user_id = '${fixture.member}'
            """.trimIndent(),
        )
        val blank = user("enum-blank")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('${fixture.group}', '$blank', 'ATHLETE', now(), now())",
        )

        flyway().migrate()

        assertEquals("PONTA", string("SELECT position::text FROM group_memberships WHERE user_id = '${fixture.member}'"))
        assertEquals("CENTRAL", string("SELECT secondary_position::text FROM group_memberships WHERE user_id = '${fixture.member}'"))
        assertEquals("MENSALISTA", string("SELECT membership_type::text FROM group_memberships WHERE user_id = '${fixture.member}'"))
        assertEquals("AVANCADO", string("SELECT level::text FROM group_memberships WHERE user_id = '${fixture.member}'"))
        assertEquals("DIREITA", string("SELECT preferred_side::text FROM group_memberships WHERE user_id = '${fixture.member}'"))
        assertEquals("AVULSO", string("SELECT membership_type::text FROM group_memberships WHERE user_id = '$blank'"))
        assertEquals(null, nullableString("SELECT position::text FROM group_memberships WHERE user_id = '$blank'"))
        assertEquals(null, nullableString("SELECT secondary_position::text FROM group_memberships WHERE user_id = '$blank'"))
        assertEquals(null, nullableString("SELECT level::text FROM group_memberships WHERE user_id = '$blank'"))
        assertEquals(null, nullableString("SELECT preferred_side::text FROM group_memberships WHERE user_id = '$blank'"))
        assertEquals("athlete_position", udt("group_memberships", "position"))
    }

    @Test
    fun `v42 keeps avulso as the membership type default`() {
        flyway().migrate()
        val fixture = fixture()
        assertEquals("AVULSO", string("SELECT membership_type::text FROM group_memberships WHERE user_id = '${fixture.member}'"))
    }

    @Test
    fun `v42 rejects labels that are not in the enums`() {
        flyway().migrate()
        val fixture = fixture()
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET position = 'GOALKEEPER' WHERE user_id = '${fixture.member}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET membership_type = 'VIP' WHERE user_id = '${fixture.member}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET level = 'BEGINNER' WHERE user_id = '${fixture.member}'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET preferred_side = 'FRENTE' WHERE user_id = '${fixture.member}'")
        }
        execute("UPDATE group_memberships SET position = 'CENTRAL' WHERE user_id = '${fixture.member}'")
        assertFailsWith<Exception> {
            execute("UPDATE group_memberships SET secondary_position = 'CENTRAL' WHERE user_id = '${fixture.member}'")
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

    private fun fixture(): Fixture {
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
                          'athlete_position', 'athlete_membership_type',
                          'athlete_level', 'athlete_preferred_side'
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

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }

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
