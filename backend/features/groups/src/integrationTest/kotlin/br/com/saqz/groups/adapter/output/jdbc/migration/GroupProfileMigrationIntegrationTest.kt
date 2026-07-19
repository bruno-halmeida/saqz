package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.accessMigrationLocation
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupProfileMigrationIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private val migrationLocation = accessMigrationLocation()

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @Test
    fun `v2 exposes every group profile default and finance column`() {
        resetLatest()

        val columns = strings(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'access_groups'",
        )

        assertEquals(
            setOf(
                "privacy",
                "currency",
                "profile_status",
                "modality",
                "composition",
                "description",
                "city",
                "level",
                "custom_level",
                "play_style",
                "custom_play_style",
                "default_venue_id",
                "default_capacity",
                "default_confirmation_lead_minutes",
                "default_game_fee_cents",
                "monthly_fee_cents",
                "monthly_due_day",
            ),
            columns.filter {
                it in setOf(
                    "privacy",
                    "currency",
                    "profile_status",
                    "modality",
                    "composition",
                    "description",
                    "city",
                    "level",
                    "custom_level",
                    "play_style",
                    "custom_play_style",
                    "default_venue_id",
                    "default_capacity",
                    "default_confirmation_lead_minutes",
                    "default_game_fee_cents",
                    "monthly_fee_cents",
                    "monthly_due_day",
                )
            }.toSet(),
        )
    }

    @Test
    fun `v2 creates reusable venue and regular slot tables`() {
        resetLatest()

        assertEquals(1, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'group_venues'"))
        assertEquals(1, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'group_regular_slots'"))
    }

    @Test
    fun `legacy v1 group migrates as private brl incomplete without guessed modality or composition`() {
        val group = migrateV1GroupThenLatest()

        assertEquals("PRIVATE", string("SELECT privacy FROM access_groups WHERE id = '$group'"))
        assertEquals("BRL", string("SELECT currency FROM access_groups WHERE id = '$group'"))
        assertEquals("INCOMPLETE", string("SELECT profile_status FROM access_groups WHERE id = '$group'"))
        assertNull(nullableString("SELECT modality FROM access_groups WHERE id = '$group'"))
        assertNull(nullableString("SELECT composition FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `v1 to v2 upgrade preserves group identity owner creation key name timezone and version`() {
        val owner = UUID.randomUUID()
        val group = UUID.randomUUID()
        val creationKey = UUID.randomUUID()
        migrateToV1()
        insertUser(owner, "owner-preserve")
        insertV1Group(group, owner, creationKey, "Legacy Group", "America/Sao_Paulo")

        migrateLatest()

        assertEquals(owner, uuid("SELECT owner_user_id FROM access_groups WHERE id = '$group'"))
        assertEquals(creationKey, uuid("SELECT creation_key FROM access_groups WHERE id = '$group'"))
        assertEquals("Legacy Group", string("SELECT name FROM access_groups WHERE id = '$group'"))
        assertEquals("America/Sao_Paulo", string("SELECT time_zone FROM access_groups WHERE id = '$group'"))
        assertEquals(1, count("SELECT version FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `v1 to v2 upgrade preserves membership and invite rows`() {
        val owner = UUID.randomUUID()
        val athlete = UUID.randomUUID()
        val group = UUID.randomUUID()
        migrateToV1()
        insertUser(owner, "owner-relations")
        insertUser(athlete, "athlete-relations")
        insertV1Group(group, owner, UUID.randomUUID(), "Relations Group", "UTC")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES " +
                "('$group', '$athlete', 'ATHLETE', now(), now())",
        )
        execute(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at) VALUES " +
                "('$group', decode('001122', 'hex'), '$owner', now())",
        )

        migrateLatest()

        assertEquals(1, count("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$athlete'"))
        assertEquals(1, count("SELECT count(*) FROM group_invites WHERE group_id = '$group'"))
    }

    @Test
    fun `complete group accepts required modality composition and scalar defaults`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "complete-owner")
        val group = insertCompleteGroup(owner)

        execute(
            "UPDATE access_groups SET description = 'Competitive group', city = 'São Paulo', " +
                "level = 'CUSTOM', custom_level = 'Open Plus', play_style = 'FIVE_ONE', " +
                "default_capacity = 18, default_confirmation_lead_minutes = 1440, " +
                "default_game_fee_cents = 2500, monthly_fee_cents = 7500, monthly_due_day = 10 " +
                "WHERE id = '$group'",
        )

        assertEquals("COMPLETE", string("SELECT profile_status FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `complete status requires modality and composition`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "complete-required")

        assertFailsWith<Exception> {
            insertGroup(owner, extra = "profile_status = 'COMPLETE'")
        }
    }

    @Test
    fun `invalid modality is rejected`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "invalid-modality")

        assertFailsWith<Exception> {
            insertGroup(owner, extra = "modality = 'SOCCER', composition = 'MIXED', profile_status = 'COMPLETE'")
        }
    }

    @Test
    fun `invalid composition is rejected`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "invalid-composition")

        assertFailsWith<Exception> {
            insertGroup(owner, extra = "modality = 'COURT_VOLLEYBALL', composition = 'COED', profile_status = 'COMPLETE'")
        }
    }

    @Test
    fun `non court modality rejects play style fields`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "non-court-style")

        assertFailsWith<Exception> {
            insertGroup(
                owner,
                extra = "modality = 'BEACH_VOLLEYBALL', composition = 'MIXED', " +
                    "profile_status = 'COMPLETE', play_style = 'FIVE_ONE'",
            )
        }
    }

    @Test
    fun `preset level rejects obsolete custom level text`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "preset-level")

        assertFailsWith<Exception> {
            insertGroup(
                owner,
                extra = "modality = 'COURT_VOLLEYBALL', composition = 'MIXED', " +
                    "profile_status = 'COMPLETE', level = 'ADVANCED', custom_level = 'Elite'",
            )
        }
    }

    @Test
    fun `custom play style requires custom text`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "custom-style")

        assertFailsWith<Exception> {
            insertGroup(
                owner,
                extra = "modality = 'COURT_VOLLEYBALL', composition = 'MIXED', " +
                    "profile_status = 'COMPLETE', play_style = 'CUSTOM'",
            )
        }
    }

    @Test
    fun `monthly fee requires due day in allowed range`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "monthly-due")

        assertFailsWith<Exception> {
            insertGroup(
                owner,
                extra = "modality = 'COURT_VOLLEYBALL', composition = 'MIXED', " +
                    "profile_status = 'COMPLETE', monthly_fee_cents = 5000",
            )
        }
        assertFailsWith<Exception> {
            insertGroup(
                owner,
                extra = "modality = 'COURT_VOLLEYBALL', composition = 'MIXED', " +
                    "profile_status = 'COMPLETE', monthly_fee_cents = 5000, monthly_due_day = 31",
            )
        }
    }

    @Test
    fun `venue validates text timestamps and version`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "venue-owner")
        val group = insertCompleteGroup(owner)
        val venue = UUID.randomUUID()

        execute(
            "INSERT INTO group_venues (id, group_id, name, address, court, created_at, updated_at) VALUES " +
                "('$venue', '$group', 'Arena Beach Sports', 'Rua Teste 123', 'Quadra 2', now(), now())",
        )

        assertEquals(1, count("SELECT version FROM group_venues WHERE id = '$venue'"))
        assertNotNull(string("SELECT created_at::text FROM group_venues WHERE id = '$venue'"))
        assertFailsWith<Exception> {
            execute(
                "INSERT INTO group_venues (id, group_id, name, address, created_at, updated_at) VALUES " +
                    "('${UUID.randomUUID()}', '$group', ' A ', 'Rua', now(), now())",
            )
        }
    }

    @Test
    fun `regular slot validates weekday duration and version`() {
        resetLatest()
        val owner = insertUser(UUID.randomUUID(), "slot-owner")
        val group = insertCompleteGroup(owner)
        val slot = UUID.randomUUID()

        execute(
            "INSERT INTO group_regular_slots (id, group_id, weekday, start_time, duration_minutes, created_at, updated_at) VALUES " +
                "('$slot', '$group', 2, '19:30', 120, now(), now())",
        )

        assertEquals(1, count("SELECT version FROM group_regular_slots WHERE id = '$slot'"))
        assertFailsWith<Exception> {
            execute(
                "INSERT INTO group_regular_slots (id, group_id, weekday, start_time, duration_minutes, created_at, updated_at) VALUES " +
                    "('${UUID.randomUUID()}', '$group', 8, '19:30', 120, now(), now())",
            )
        }
        assertFailsWith<Exception> {
            execute(
                "INSERT INTO group_regular_slots (id, group_id, weekday, start_time, duration_minutes, created_at, updated_at) VALUES " +
                    "('${UUID.randomUUID()}', '$group', 2, '19:30', 10, now(), now())",
            )
        }
    }

    @Test
    fun `default venue and slot venue must belong to the same group`() {
        resetLatest()
        val firstOwner = insertUser(UUID.randomUUID(), "first-venue-owner")
        val secondOwner = insertUser(UUID.randomUUID(), "second-venue-owner")
        val firstGroup = insertCompleteGroup(firstOwner)
        val secondGroup = insertCompleteGroup(secondOwner)
        val secondVenue = UUID.randomUUID()
        execute(
            "INSERT INTO group_venues (id, group_id, name, address, created_at, updated_at) VALUES " +
                "('$secondVenue', '$secondGroup', 'Other Arena', 'Rua Outra 456', now(), now())",
        )

        assertFailsWith<Exception> {
            execute("UPDATE access_groups SET default_venue_id = '$secondVenue' WHERE id = '$firstGroup'")
        }
        assertFailsWith<Exception> {
            execute(
                "INSERT INTO group_regular_slots (id, group_id, venue_id, weekday, start_time, duration_minutes, created_at, updated_at) VALUES " +
                    "('${UUID.randomUUID()}', '$firstGroup', '$secondVenue', 2, '19:30', 120, now(), now())",
            )
        }
    }

    private fun migrateV1GroupThenLatest(): UUID {
        val owner = UUID.randomUUID()
        val group = UUID.randomUUID()
        migrateToV1()
        insertUser(owner, "legacy-owner")
        insertV1Group(group, owner, UUID.randomUUID(), "Legacy Group", "UTC")
        migrateLatest()
        return group
    }

    private fun resetLatest() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
    }

    private fun migrateToV1() {
        val flyway = flyway("1")
        flyway.clean()
        flyway.migrate()
    }

    private fun migrateLatest() {
        flyway().migrate()
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(migrationLocation)
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun insertCompleteGroup(owner: UUID): UUID =
        insertGroup(
            owner,
            extra = "modality = 'COURT_VOLLEYBALL', composition = 'MIXED', profile_status = 'COMPLETE'",
        )

    private fun insertGroup(owner: UUID, extra: String): UUID {
        val group = UUID.randomUUID()
        val columns = mutableListOf(
            "id",
            "owner_user_id",
            "creation_key",
            "name",
            "time_zone",
            "created_at",
            "updated_at",
        )
        val values = mutableListOf(
            "'$group'",
            "'$owner'",
            "'${UUID.randomUUID()}'",
            "'Complete Group'",
            "'UTC'",
            "now()",
            "now()",
        )
        extra.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach {
                columns += it.substringBefore("=").trim()
                values += it.substringAfter("=").trim()
            }
        execute("INSERT INTO access_groups (${columns.joinToString()}) VALUES (${values.joinToString()})")
        return group
    }

    private fun insertV1Group(group: UUID, owner: UUID, creationKey: UUID, name: String, timeZone: String) {
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES " +
                "('$group', '$owner', '$creationKey', '$name', '$timeZone', now(), now())",
        )
    }

    private fun insertUser(id: UUID, subject: String): UUID {
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES " +
                "('$id', '$subject', true, 'Valid User', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun count(sql: String): Int =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun strings(sql: String): List<String> =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    val values = mutableListOf<String>()
                    while (result.next()) values += result.getString(1)
                    values
                }
            }
        }

    private fun string(sql: String): String =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }

    private fun nullableString(sql: String): String? =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }

    private fun uuid(sql: String): UUID =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getObject(1, UUID::class.java)
                }
            }
        }

    private fun connection(): Connection = dataSource.connection

}
