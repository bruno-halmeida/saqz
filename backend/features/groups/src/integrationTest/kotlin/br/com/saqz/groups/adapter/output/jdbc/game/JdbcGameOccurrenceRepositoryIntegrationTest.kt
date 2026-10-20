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
import br.com.saqz.groups.domain.game.GameDefaultSnapshotFactory
import br.com.saqz.groups.domain.game.GameDraftValidation
import br.com.saqz.groups.domain.game.GameDraftValidator
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.group.PromotionMode as GroupPromotionMode
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGameOccurrenceRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun resetDatabase() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

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
        assertEquals(true, context?.defaults?.mensalistaPriority)
        assertEquals(GroupPromotionMode.FIFO, context?.defaults?.promotionMode)
    }

    @Test fun `creation context reads back custom game config fields`() {
        val fixture = fixture()
        execute(
            "UPDATE access_groups SET mensalista_priority = false, promotion_mode = 'MANUAL' WHERE id = '${fixture.group}'",
        )

        val context = fixture.repository.creationContext(fixture.owner, fixture.group)

        assertEquals(false, context?.defaults?.mensalistaPriority)
        assertEquals(GroupPromotionMode.MANUAL, context?.defaults?.promotionMode)
    }

    @Test fun `deleted group rejects game reads and creation`() {
        val fixture = fixture()
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '${fixture.group}'")

        assertNull(fixture.repository.creationContext(fixture.owner, fixture.group))
        assertEquals(
            GameCommandResult.GroupNotFound,
            CreateGame(transaction(), fixture.repository, CLOCK).execute(
                fixture.owner, fixture.group, UUID.randomUUID(), createInput(),
            ),
        )
        assertEquals(0, int("SELECT count(*) FROM games"))
    }

    @Test fun `organizer create persists exactly one draft snapshot`() {
        val fixture = fixture()
        val result = CreateGame(transaction(), fixture.repository, CLOCK).execute(
            fixture.owner, fixture.group, UUID.randomUUID(), createInput(),
        )

        assertTrue(result is GameCommandResult.Success)
        assertEquals(1, int("SELECT count(*) FROM games"))
        assertEquals("DRAFT", string("SELECT status FROM games"))
        assertNull(nullableUuid("SELECT series_id FROM games"))
    }

    @Test fun `DEB-102 creation in a group with positions copies the slots and forces capacity to their sum`() {
        val fixture = fixture()
        execute("INSERT INTO group_position_slots (group_id,position,slots) VALUES ('${fixture.group}','PONTA',2),('${fixture.group}','CENTRAL',2),('${fixture.group}','LEVANTADOR',1)")
        val result = CreateGame(transaction(), fixture.repository, CLOCK).execute(fixture.owner, fixture.group, UUID.randomUUID(), createInput())

        assertTrue(result is GameCommandResult.Success)
        assertEquals(5, int("SELECT capacity FROM games"))
        assertEquals(3, int("SELECT count(*) FROM game_position_slots"))
        assertEquals(2, int("SELECT slots FROM game_position_slots WHERE position='PONTA'"))
    }

    @Test fun `DEB-102 creation in a group without positions changes nothing`() {
        val fixture = fixture()
        val result = CreateGame(transaction(), fixture.repository, CLOCK).execute(fixture.owner, fixture.group, UUID.randomUUID(), createInput())

        assertTrue(result is GameCommandResult.Success)
        assertEquals(24, int("SELECT capacity FROM games"))
        assertEquals(0, int("SELECT count(*) FROM game_position_slots"))
    }

    @Test fun `game created from a default venue without address stores and reads back empty address`() {
        val fixture = fixture()
        execute("UPDATE group_venues SET address = ''")

        val game = create(fixture)

        assertEquals("", string("SELECT venue_address FROM games"))
        assertEquals("", fixture.repository.find(fixture.group, game.id)?.snapshot?.venue?.address)
    }

    @Test fun `athlete create is forbidden without game write`() {
        val fixture = fixture()
        assertSame(
            GameCommandResult.AccessForbidden,
            CreateGame(transaction(), fixture.repository, CLOCK).execute(
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
            CreateGame(transaction(), fixture.repository, CLOCK).execute(fixture.owner, fixture.group, UUID.randomUUID(), createInput()),
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

    @Test fun `game reads persisted finance review flag`() {
        val fixture = fixture()
        val game = create(fixture)

        assertFalse(fixture.repository.find(fixture.group, game.id)!!.financeReviewRequired)
        execute("UPDATE games SET status = 'CANCELLED', finance_review_required = true WHERE id = '${game.id}'")

        assertTrue(fixture.repository.find(fixture.group, game.id)!!.financeReviewRequired)
    }

    @Test fun `completed game does not block a new draft at the same start`() {
        val fixture = fixture()
        val completed = create(fixture)
        assertIs<GameWriteResult.Saved>(fixture.repository.update(completed.copy(status = GameStatus.COMPLETED), 1))

        val fresh = create(fixture, UUID.randomUUID())

        assertEquals(GameStatus.COMPLETED, fixture.repository.find(fixture.group, completed.id)?.status)
        assertEquals(GameStatus.DRAFT, fresh.status)
        assertEquals(2, int("SELECT count(*) FROM games WHERE group_id = '${fixture.group}'"))
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
            java.time.Clock.fixed(java.time.Instant.parse("2026-08-01T12:00:00Z"), java.time.ZoneOffset.UTC),
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

    @Test fun `list ordering is resolved by start`() {
        val fixture = fixture()
        val later = create(fixture, UUID.fromString("00000000-0000-0000-0000-000000000002"), START.plusSeconds(4 * 3600))
        val first = create(fixture, UUID.fromString("00000000-0000-0000-0000-000000000003"), START)
        val second = create(fixture, UUID.fromString("00000000-0000-0000-0000-000000000004"), START.plusSeconds(2 * 3600))

        assertEquals(listOf(first.id, second.id, later.id), fixture.repository.list(fixture.group).map(Game::id))
    }

    @Test fun `concurrent inserts at one start return one schedule conflict`() {
        val fixture = fixture()
        val snapshot = assertIs<GameDraftValidation.Valid>(
            GameDraftValidator.validate(
                GameDefaultSnapshotFactory.copy(
                    requireNotNull(fixture.repository.creationContext(fixture.owner, fixture.group)).defaults,
                    createInput(),
                ),
            ),
        ).snapshot
        val games = listOf(
            Game(UUID.randomUUID(), fixture.group, snapshot),
            Game(UUID.randomUUID(), fixture.group, snapshot),
        )
        val barrier = CyclicBarrier(games.size)
        val executor = Executors.newFixedThreadPool(games.size)
        try {
            val results = games.map { game ->
                executor.submit<GameWriteResult> {
                    barrier.await()
                    fixture.repository.create(game)
                }
            }.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it is GameWriteResult.Saved })
            val conflict = assertIs<GameWriteResult.ScheduleConflict>(results.single { it is GameWriteResult.ScheduleConflict })
            assertTrue(games.any { it.id == conflict.gameId })
            assertEquals(1, int("SELECT count(*) FROM games"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `game overlapping an active one is refused with the conflicting game id`() {
        val fixture = fixture()
        val existing = create(fixture)

        val overlapping = CreateGame(transaction(), fixture.repository, CLOCK).execute(
            fixture.owner, fixture.group, UUID.randomUUID(), createInput(START.plusSeconds(60 * 60)),
        )
        val earlier = CreateGame(transaction(), fixture.repository, CLOCK).execute(
            fixture.owner, fixture.group, UUID.randomUUID(), createInput(START.minusSeconds(30 * 60)),
        )

        assertEquals(GameCommandResult.ScheduleConflict(existing.id), overlapping)
        assertEquals(GameCommandResult.ScheduleConflict(existing.id), earlier)
        assertEquals(1, int("SELECT count(*) FROM games"))
    }

    @Test fun `game touching another one is accepted on both sides`() {
        val fixture = fixture()
        create(fixture)

        val after = create(fixture, start = START.plusSeconds(90 * 60))
        val before = create(fixture, start = START.minusSeconds(90 * 60))

        assertEquals(GameStatus.DRAFT, after.status)
        assertEquals(GameStatus.DRAFT, before.status)
        assertEquals(3, int("SELECT count(*) FROM games"))
    }

    @Test fun `cancelled and completed games release their time`() {
        val fixture = fixture()
        val cancelled = create(fixture)
        val completed = create(fixture, start = START.plusSeconds(3 * 3600))
        execute("UPDATE games SET status = 'CANCELLED' WHERE id = '${cancelled.id}'")
        execute("UPDATE games SET status = 'COMPLETED' WHERE id = '${completed.id}'")

        val fresh = create(fixture, start = START.plusSeconds(30 * 60))
        val freshOverCompleted = create(fixture, start = START.plusSeconds(3 * 3600 + 30 * 60))

        assertEquals(GameStatus.DRAFT, fresh.status)
        assertEquals(GameStatus.DRAFT, freshOverCompleted.status)
    }

    @Test fun `editing a game into an overlap is refused and keeps the old time`() {
        val fixture = fixture()
        val first = create(fixture)
        val second = create(fixture, start = START.plusSeconds(3 * 3600))
        val edit = EditGame(transaction(), fixture.repository, GameSideEffectPort { _, _, _ -> }, CLOCK)

        val result = edit.execute(
            fixture.owner, fixture.group, second.id, second.version,
            GameDraftInput(
                title = "Treino",
                venue = br.com.saqz.groups.domain.game.GameVenueInput(name = "Arena Central", address = "Rua das Flores 100"),
                localDate = START.plusSeconds(3600).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate(),
                localTime = START.plusSeconds(3600).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalTime(),
                zoneId = "America/Sao_Paulo",
                startsAt = START.plusSeconds(3600),
                durationMinutes = 90,
                capacity = 24,
                confirmationDeadline = START.minusSeconds(3600),
            ),
        )

        assertEquals(GameCommandResult.ScheduleConflict(first.id), result)
        assertEquals(START.plusSeconds(3 * 3600), fixture.repository.find(fixture.group, second.id)?.snapshot?.startsAt)
    }

    @Test fun `database constraint refuses overlapping insert even without the application check`() {
        val fixture = fixture()
        create(fixture)

        val failure = runCatching { insertRawGame(fixture, START.plusSeconds(45 * 60), 90, "DRAFT") }.exceptionOrNull()

        assertTrue(failure?.message?.contains("games_schedule_no_overlap") == true, failure?.message)
        insertRawGame(fixture, START.plusSeconds(90 * 60), 90, "PUBLISHED")
        insertRawGame(fixture, START.plusSeconds(45 * 60), 15, "CANCELLED")
        assertEquals(3, int("SELECT count(*) FROM games"))
    }

    @Test fun `longer game overlaps a later start`() {
        val fixture = fixture()
        insertRawGame(fixture, START, 240, "PUBLISHED")

        assertEquals(
            true,
            fixture.repository.recurringConflict(fixture.group, START.plusSeconds(3 * 3600), 60, excludingGameId = null) != null,
        )
        assertNull(fixture.repository.recurringConflict(fixture.group, START.plusSeconds(4 * 3600), 60, excludingGameId = null))
    }

    private fun insertRawGame(fixture: Fixture, start: Instant, minutes: Int, status: String) {
        val local = start.atZone(java.time.ZoneId.of("America/Sao_Paulo"))
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline," +
                "venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('${UUID.randomUUID()}','${fixture.group}'," +
                "'Treino','${local.toLocalDate()}',TIME '${local.toLocalTime()}','America/Sao_Paulo',TIMESTAMPTZ '$start',$minutes," +
                "TIMESTAMPTZ '${start.minusSeconds(3600)}','Arena','Rua Central 100',12,2500,'$status',now(),now())",
        )
    }

    private fun create(fixture: Fixture, id: UUID = UUID.randomUUID(), start: Instant = START): Game {
        val input = createInput(start)
        return assertIs<GameCommandResult.Success>(
            CreateGame(transaction(), fixture.repository, CLOCK).execute(fixture.owner, fixture.group, id, input),
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

    private fun createInput(start: Instant = START): CreateGameInput {
        val local = start.atZone(java.time.ZoneId.of("America/Sao_Paulo"))
        return CreateGameInput(
            localDate = local.toLocalDate(),
            localTime = local.toLocalTime(),
            zoneId = "America/Sao_Paulo",
            startsAt = start,
        )
    }

    private fun transaction() = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
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

    private companion object {
        val START: Instant = Instant.parse("2026-08-12T22:30:00Z")
        val CLOCK: java.time.Clock = java.time.Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), java.time.ZoneOffset.UTC)
    }
}
