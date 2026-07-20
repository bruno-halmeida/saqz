package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.GameAttendanceCountSource
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameListResult
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.application.game.GameWriteResult
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.CreateGameInput
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameDraftInput
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGameOccurrenceRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach fun resetDatabase() { flyway().clean(); flyway().migrate() }
    @AfterAll fun stopDatabase() = postgres.stop()

    @Test fun `creation context copies group venue slot and scalar defaults`() {
        val fixture = fixture()
        val context = fixture.repository.creationContext(fixture.owner, fixture.group)

        assertEquals(GroupRole.OWNER, context?.role)
        assertEquals("Training Group", context?.defaults?.title)
        assertEquals("Arena Central", context?.defaults?.venue?.name)
        assertEquals(90, context?.defaults?.durationMinutes)
        assertEquals(24, context?.defaults?.capacity)
        assertEquals(180, context?.defaults?.confirmationLeadMinutes)
        assertEquals(2500, context?.defaults?.gameFeeCents)
    }

    @Test fun `organizer create persists exactly one draft snapshot`() {
        val fixture = fixture()
        val result = CreateGame(transaction(), fixture.repository).execute(
            fixture.owner, fixture.group, UUID.randomUUID(), createInput(),
        )

        assertTrue(result is GameCommandResult.Success)
        assertEquals(1, int("SELECT count(*) FROM games"))
        assertEquals("DRAFT", string("SELECT status FROM games"))
        assertNull(nullableUuid("SELECT series_id FROM games"))
    }

    @Test fun `athlete create is forbidden without game write`() {
        val fixture = fixture()
        assertSame(
            GameCommandResult.AccessForbidden,
            CreateGame(transaction(), fixture.repository).execute(
                fixture.athlete, fixture.group, UUID.randomUUID(), createInput(),
            ),
        )
        assertEquals(0, int("SELECT count(*) FROM games"))
    }

    @Test fun `nonmember and missing group have no creation context`() {
        val fixture = fixture()
        assertNull(fixture.repository.creationContext(UUID.randomUUID(), fixture.group))
        assertNull(fixture.repository.creationContext(fixture.owner, UUID.randomUUID()))
    }

    @Test fun `organizer list includes draft while athlete list hides it`() {
        val fixture = fixture()
        val created = assertIs<GameCommandResult.Success>(
            CreateGame(transaction(), fixture.repository).execute(fixture.owner, fixture.group, UUID.randomUUID(), createInput()),
        ).game
        val noCounts = GameAttendanceCountSource { emptyMap() }

        assertEquals(1, assertIs<GameListResult.Success>(ListGames(fixture.repository, noCounts).execute(fixture.owner, fixture.group)).games.size)
        assertTrue(assertIs<GameListResult.Success>(ListGames(fixture.repository, noCounts).execute(fixture.athlete, fixture.group)).games.isEmpty())

        fixture.repository.update(created.copy(status = GameStatus.PUBLISHED), 1)
        assertEquals(1, assertIs<GameListResult.Success>(ListGames(fixture.repository, noCounts).execute(fixture.athlete, fixture.group)).games.size)
    }

    @Test fun `nonmember role lookup is privacy preserving`() {
        val fixture = fixture()
        assertNull(fixture.repository.role(UUID.randomUUID(), fixture.group))
        assertNull(fixture.repository.role(fixture.owner, UUID.randomUUID()))
    }

    @Test fun `versioned update changes snapshot and increments version`() {
        val fixture = fixture()
        val game = create(fixture)
        val changed = game.copy(snapshot = game.snapshot.copy(title = "Final semanal"))

        val saved = assertIs<GameWriteResult.Saved>(fixture.repository.update(changed, 1)).game
        assertEquals(2, saved.version)
        assertEquals("Final semanal", fixture.repository.find(fixture.group, game.id)?.snapshot?.title)
    }

    @Test fun `stale update retains authoritative row unchanged`() {
        val fixture = fixture()
        val game = create(fixture)
        fixture.repository.update(game.copy(snapshot = game.snapshot.copy(title = "Version two")), 1)

        assertSame(
            GameWriteResult.VersionConflict,
            fixture.repository.update(game.copy(snapshot = game.snapshot.copy(title = "Stale title")), 1),
        )
        assertEquals("Version two", fixture.repository.find(fixture.group, game.id)?.snapshot?.title)
        assertEquals(2, fixture.repository.find(fixture.group, game.id)?.version)
    }

    @Test fun `invalid versioned edit retains old row and emits no side effect`() {
        val fixture = fixture()
        val game = create(fixture)
        var effectCalls = 0
        val result = EditGame(
            transaction(),
            fixture.repository,
            GameSideEffectPort { _, _, _ -> effectCalls++ },
        ).execute(
            fixture.owner,
            fixture.group,
            game.id,
            1,
            GameDraftInput(null, null, null, null, null, null, null, null, null),
        )

        assertTrue(result is GameCommandResult.Invalid)
        assertEquals("Training Group", fixture.repository.find(fixture.group, game.id)?.snapshot?.title)
        assertEquals(1, fixture.repository.find(fixture.group, game.id)?.version)
        assertEquals(0, effectCalls)
    }

    @Test fun `venue and group default changes cannot rewrite stored snapshot`() {
        val fixture = fixture()
        val game = create(fixture)
        execute("UPDATE group_venues SET name = 'Renamed Arena' WHERE id = '${fixture.venue}'")
        execute("UPDATE access_groups SET default_capacity = 50, default_game_fee_cents = 9000 WHERE id = '${fixture.group}'")

        val stored = fixture.repository.find(fixture.group, game.id)
        assertEquals("Arena Central", stored?.snapshot?.venue?.name)
        assertEquals(24, stored?.snapshot?.capacity)
        assertEquals(2500, stored?.snapshot?.gameFeeCents)
    }

    @Test fun `list ordering is resolved start then id`() {
        val fixture = fixture()
        val later = create(fixture, UUID.fromString("00000000-0000-0000-0000-000000000002"), START.plusSeconds(3600))
        val first = create(fixture, UUID.fromString("00000000-0000-0000-0000-000000000003"), START)
        val second = create(fixture, UUID.fromString("00000000-0000-0000-0000-000000000004"), START)

        assertEquals(listOf(first.id, second.id, later.id), fixture.repository.list(fixture.group).map(Game::id))
    }

    private fun create(fixture: Fixture, id: UUID = UUID.randomUUID(), start: Instant = START): Game {
        val input = createInput(start)
        return assertIs<GameCommandResult.Success>(
            CreateGame(transaction(), fixture.repository).execute(fixture.owner, fixture.group, id, input),
        ).game
    }

    private fun fixture(): Fixture {
        val owner = user("owner")
        val athlete = user("athlete")
        val group = UUID.randomUUID()
        val venue = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, " +
                "default_capacity, default_confirmation_lead_minutes, default_game_fee_cents, created_at, updated_at) VALUES " +
                "('$group', '$owner', '${UUID.randomUUID()}', 'Training Group', 'America/Sao_Paulo', 'COMPLETE', " +
                "'COURT_VOLLEYBALL', 'MIXED', 24, 180, 2500, now(), now())",
        )
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$athlete', 'ATHLETE', now(), now())",
        )
        execute(
            "INSERT INTO group_venues (id, group_id, name, address, court, created_at, updated_at) " +
                "VALUES ('$venue', '$group', 'Arena Central', 'Rua das Flores 100', 'Quadra 2', now(), now())",
        )
        execute("UPDATE access_groups SET default_venue_id = '$venue' WHERE id = '$group'")
        execute(
            "INSERT INTO group_regular_slots (id, group_id, venue_id, weekday, start_time, duration_minutes, created_at, updated_at) " +
                "VALUES ('${UUID.randomUUID()}', '$group', '$venue', 3, TIME '19:30', 90, now(), now())",
        )
        return Fixture(JdbcGameOccurrenceRepository(dataSource), owner, athlete, group, venue)
    }

    private fun user(prefix: String): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$prefix-$id', true, 'User', now(), now())",
        )
    }

    private fun createInput(start: Instant = START) = CreateGameInput(
        localDate = LocalDate.of(2026, 8, 12),
        localTime = LocalTime.of(19, 30),
        zoneId = "America/Sao_Paulo",
        startsAt = start,
    )

    private fun transaction() = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
    private fun flyway() = Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun nullableUuid(sql: String): UUID? = query(sql) { it.getObject(1, UUID::class.java) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> check(result.next()); read(result) } }
    }
    private fun connection(): Connection = dataSource.connection

    private data class Fixture(
        val repository: JdbcGameOccurrenceRepository,
        val owner: UUID,
        val athlete: UUID,
        val group: UUID,
        val venue: UUID,
    )

    private companion object { val START: Instant = Instant.parse("2026-08-12T22:30:00Z") }
}
