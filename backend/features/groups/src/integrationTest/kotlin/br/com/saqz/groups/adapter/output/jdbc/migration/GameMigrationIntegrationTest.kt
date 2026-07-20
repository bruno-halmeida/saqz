package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameMigrationIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach
    fun resetDatabase() {
        flyway().clean()
        flyway().migrate()
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @Test
    fun `v4 creates series slots and games tables`() {
        assertEquals(3, int("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('game_series', 'game_series_slots', 'games')"))
    }

    @Test
    fun `v3 to v4 upgrade preserves existing group identity and version`() {
        flyway().clean()
        flyway("3").migrate()
        val group = completeGroup("upgrade-owner")
        execute("UPDATE access_groups SET version = 7 WHERE id = '$group'")

        flyway().migrate()

        assertEquals(7, int("SELECT version FROM access_groups WHERE id = '$group'"))
        assertEquals(1, int("SELECT count(*) FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `series stores local zone boundaries and revision lineage`() {
        val group = completeGroup("series-owner")
        val first = series(group, revision = 1)
        val successor = series(group, lineage = first.lineage, previous = first.revision, revision = 2, start = "2026-08-01")

        assertEquals(first.revision, uuid("SELECT previous_revision_id FROM game_series WHERE id = '${successor.revision}'"))
        assertEquals("America/Sao_Paulo", string("SELECT zone_id FROM game_series WHERE id = '${first.revision}'"))
        assertEquals(2, int("SELECT revision_number FROM game_series WHERE id = '${successor.revision}'"))
    }

    @Test fun `series rejects end before start`() {
        val group = completeGroup("series-date")
        assertFails { series(group, start = "2026-08-10", end = "2026-08-09") }
    }

    @Test fun `series rejects active boundary outside local range`() {
        val group = completeGroup("series-boundary")
        assertFails { series(group, start = "2026-08-10", end = "2026-08-20", activeThrough = "2026-08-21") }
        assertFails { series(group, start = "2026-08-10", end = "2026-08-20", activeThrough = "2026-08-08") }
    }

    @Test fun `series rejects invalid revision and version`() {
        val group = completeGroup("series-version")
        assertFails { series(group, revision = 0) }
        assertFails {
            execute("UPDATE game_series SET version = 0 WHERE id = '${series(group).revision}'")
        }
    }

    @Test fun `series predecessor must belong to same group`() {
        val firstGroup = completeGroup("series-first")
        val secondGroup = completeGroup("series-second")
        val first = series(firstGroup)

        assertFails { series(secondGroup, previous = first.revision, revision = 2) }
    }

    @Test fun `series predecessor must belong to same lineage`() {
        val group = completeGroup("series-lineage")
        val first = series(group)

        assertFails { series(group, previous = first.revision, revision = 2) }
    }

    @Test fun `series revision after first requires predecessor`() {
        val group = completeGroup("series-predecessor")

        assertFails { series(group, revision = 2) }
    }

    @Test fun `series lineage revision number is unique`() {
        val group = completeGroup("series-unique")
        val first = series(group)

        assertFails { series(group, lineage = first.lineage, revision = 1) }
    }

    @Test
    fun `slot stores stable key local schedule and immutable defaults`() {
        val group = completeGroup("slot-snapshot")
        val venue = venue(group)
        val revision = series(group).revision
        val slot = slot(group, revision, venue)

        assertEquals(3, int("SELECT weekday FROM game_series_slots WHERE slot_key = '$slot'"))
        assertEquals(90, int("SELECT duration_minutes FROM game_series_slots WHERE slot_key = '$slot'"))
        assertEquals(24, int("SELECT capacity FROM game_series_slots WHERE slot_key = '$slot'"))
        assertEquals(2500, int("SELECT game_fee_cents FROM game_series_slots WHERE slot_key = '$slot'"))
    }

    @Test fun `slot rejects invalid weekday`() = invalidSlot("weekday", "8")
    @Test fun `slot rejects invalid duration`() = invalidSlot("duration_minutes", "14")
    @Test fun `slot rejects invalid capacity`() = invalidSlot("capacity", "101")
    @Test fun `slot rejects invalid deadline lead`() = invalidSlot("confirmation_lead_minutes", "10081")
    @Test fun `slot rejects invalid fee`() = invalidSlot("game_fee_cents", "0")

    @Test fun `slot venue must belong to same group`() {
        val firstGroup = completeGroup("slot-group-first")
        val secondGroup = completeGroup("slot-group-second")
        val revision = series(firstGroup).revision
        val otherVenue = venue(secondGroup)

        assertFails { slot(firstGroup, revision, otherVenue) }
    }

    @Test
    fun `one time game stores resolved local and venue snapshots`() {
        val group = completeGroup("game-snapshot")
        val venue = venue(group)
        val game = game(group, venue)

        assertEquals("Treino de terça", string("SELECT title FROM games WHERE id = '$game'"))
        assertEquals("America/Sao_Paulo", string("SELECT zone_id FROM games WHERE id = '$game'"))
        assertEquals("PUBLISHED", string("SELECT status FROM games WHERE id = '$game'"))
        assertEquals(1, int("SELECT version FROM games WHERE id = '$game'"))
    }

    @Test fun `game rejects invalid lifecycle status`() = invalidGame("status", "'OPEN'")
    @Test fun `game rejects title limit`() = invalidGame("title", "'X'")
    @Test fun `game rejects duration limit`() = invalidGame("duration_minutes", "481")
    @Test fun `game rejects capacity limit`() = invalidGame("capacity", "1")
    @Test fun `game rejects non positive fee`() = invalidGame("game_fee_cents", "0")
    @Test fun `game rejects blank notes`() = invalidGame("notes", "' '")

    @Test fun `game rejects deadline after resolved start`() = invalidGame(
        "confirmation_deadline",
        "TIMESTAMPTZ '2026-08-12 23:00:01+00'",
    )

    @Test fun `game venue must belong to same group`() {
        val firstGroup = completeGroup("game-group-first")
        val secondGroup = completeGroup("game-group-second")

        assertFails { game(firstGroup, venue(secondGroup)) }
    }

    @Test
    fun `bounded series occurrence stores stable identity once`() {
        val group = completeGroup("occurrence")
        val venue = venue(group)
        val series = series(group)
        val slot = slot(group, series.revision, venue)
        game(group, venue, series, slot)

        assertFails { game(group, venue, series, slot) }
    }

    @Test
    fun `detached override retains bounded occurrence identity as tombstone`() {
        val group = completeGroup("detached")
        val venue = venue(group)
        val series = series(group)
        val slot = slot(group, series.revision, venue)
        val game = game(group, venue, series, slot)

        execute("UPDATE games SET detached_from_series = true WHERE id = '$game'")

        assertEquals(1, int("SELECT count(*) FROM games WHERE id = '$game' AND detached_from_series AND series_id IS NOT NULL"))
        assertFails { game(group, venue, series, slot) }
    }

    @Test fun `game rejects partial series identity`() {
        val group = completeGroup("partial-series")
        val venue = venue(group)
        assertFails { game(group, venue, seriesIdOnly = UUID.randomUUID()) }
    }

    @Test fun `game series revision must belong to same group`() {
        val firstGroup = completeGroup("game-series-first")
        val secondGroup = completeGroup("game-series-second")
        val firstVenue = venue(firstGroup)
        val secondVenue = venue(secondGroup)
        val series = series(firstGroup)
        val slot = slot(firstGroup, series.revision, firstVenue)

        assertFails { game(secondGroup, secondVenue, series, slot) }
    }

    private fun invalidSlot(column: String, value: String) {
        val group = completeGroup("slot-$column")
        val revision = series(group).revision
        val venue = venue(group)
        assertFails { slot(group, revision, venue, "$column = $value") }
    }

    private fun invalidGame(column: String, value: String) {
        val group = completeGroup("game-$column")
        assertFails { game(group, venue(group), override = "$column = $value") }
    }

    private fun completeGroup(subject: String): UUID {
        val owner = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$owner', '$subject-${UUID.randomUUID()}', true, 'Owner', now(), now())",
        )
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Complete Group', 'America/Sao_Paulo', " +
                "'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return group
    }

    private fun venue(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO group_venues (id, group_id, name, address, court, created_at, updated_at) " +
                "VALUES ('$id', '$group', 'Arena Central', 'Rua das Flores 100', 'Quadra 2', now(), now())",
        )
        return id
    }

    private fun series(
        group: UUID,
        lineage: UUID = UUID.randomUUID(),
        previous: UUID? = null,
        revision: Int = 1,
        start: String = "2026-08-01",
        end: String? = "2026-12-31",
        activeThrough: String? = null,
    ): SeriesIds {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO game_series (id, lineage_id, group_id, previous_revision_id, revision_number, zone_id, " +
                "local_start_date, local_end_date, active_through_date, created_at, updated_at) VALUES " +
                "('$id', '$lineage', '$group', ${previous.sqlUuid()}, $revision, 'America/Sao_Paulo', " +
                "DATE '$start', ${end.sqlDate()}, ${activeThrough.sqlDate()}, now(), now())",
        )
        return SeriesIds(lineage, id)
    }

    private fun slot(group: UUID, revision: UUID, venue: UUID, override: String? = null): UUID {
        val values = linkedMapOf(
            "series_revision_id" to "'$revision'",
            "group_id" to "'$group'",
            "slot_key" to "'${UUID.randomUUID()}'",
            "title" to "'Treino semanal'",
            "weekday" to "3",
            "local_time" to "TIME '19:30'",
            "duration_minutes" to "90",
            "venue_id" to "'$venue'",
            "venue_name" to "'Arena Central'",
            "venue_address" to "'Rua das Flores 100'",
            "venue_court" to "'Quadra 2'",
            "capacity" to "24",
            "confirmation_lead_minutes" to "1440",
            "game_fee_cents" to "2500",
            "created_at" to "now()",
        )
        override?.let { values[it.substringBefore('=').trim()] = it.substringAfter('=').trim() }
        execute("INSERT INTO game_series_slots (${values.keys.joinToString()}) VALUES (${values.values.joinToString()})")
        return UUID.fromString(values.getValue("slot_key").trim('\''))
    }

    private fun game(
        group: UUID,
        venue: UUID,
        series: SeriesIds? = null,
        slot: UUID? = null,
        seriesIdOnly: UUID? = null,
        override: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        val values = linkedMapOf(
            "id" to "'$id'",
            "group_id" to "'$group'",
            "series_id" to (series?.lineage ?: seriesIdOnly).sqlUuid(),
            "series_revision_id" to series?.revision.sqlUuid(),
            "slot_key" to slot.sqlUuid(),
            "title" to "'Treino de terça'",
            "local_date" to "DATE '2026-08-12'",
            "local_time" to "TIME '19:30'",
            "zone_id" to "'America/Sao_Paulo'",
            "starts_at" to "TIMESTAMPTZ '2026-08-12 22:30:00+00'",
            "duration_minutes" to "90",
            "confirmation_deadline" to "TIMESTAMPTZ '2026-08-11 22:30:00+00'",
            "venue_id" to "'$venue'",
            "venue_name" to "'Arena Central'",
            "venue_address" to "'Rua das Flores 100'",
            "venue_court" to "'Quadra 2'",
            "capacity" to "24",
            "game_fee_cents" to "2500",
            "notes" to "'Levar bola'",
            "status" to "'PUBLISHED'",
            "created_at" to "now()",
            "updated_at" to "now()",
        )
        override?.let { values[it.substringBefore('=').trim()] = it.substringAfter('=').trim() }
        execute("INSERT INTO games (${values.keys.joinToString()}) VALUES (${values.values.joinToString()})")
        return id
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun uuid(sql: String): UUID = query(sql) { it.getObject(1, UUID::class.java) }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
    private fun assertFails(block: () -> Unit) { assertFailsWith<Exception> { block() } }
    private fun UUID?.sqlUuid(): String = this?.let { "'$it'" } ?: "NULL"
    private fun String?.sqlDate(): String = this?.let { "DATE '$it'" } ?: "NULL"

    private data class SeriesIds(val lineage: UUID, val revision: UUID)
}
