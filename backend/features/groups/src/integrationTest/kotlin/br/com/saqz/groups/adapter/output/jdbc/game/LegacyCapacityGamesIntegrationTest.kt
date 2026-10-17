package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.AttendanceChargeAdapter
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameSideEffects
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeriesResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * DEB-019: o mínimo de 4 vagas vale só ao criar ou salvar. Jogo e horário antigos com 2 ou 3 vagas
 * seguem vivos: o banco continua aceitando 2 e nenhum UPDATE da rotina normal pode falhar por isso.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegacyCapacityGamesIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var owner: UUID
    private lateinit var member: UUID
    private lateinit var group: UUID

    @BeforeEach fun reset() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        owner = user("owner")
        member = user("member")
        group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Grupo', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at, membership_type) VALUES ('$group', '$owner', 'ADMIN', now(), now(), 'AVULSO')")
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at, membership_type) VALUES ('$group', '$member', 'ATHLETE', now(), now(), 'AVULSO')")
    }

    @Test fun `a legacy draft with 2 spots still publishes without being edited`() {
        val draft = game("DRAFT", capacity = 2)

        assertIs<GameCommandResult.Success>(lifecycle().execute(owner, group, draft, 1, GameMutation.PUBLISH))

        assertEquals("PUBLISHED", string("SELECT status::text FROM games WHERE id = '$draft'"))
        assertEquals(2, int("SELECT capacity FROM games WHERE id = '$draft'"))
    }

    @Test fun `a legacy published game with 3 spots receives attendance`() {
        val published = game("PUBLISHED", capacity = 3)
        // Avulso espera o prazo quando a prioridade de mensalista está ligada; aqui o que importa é a vaga.
        execute("UPDATE access_groups SET mensalista_priority = false WHERE id = '$group'")
        val respond = RespondAttendance(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), charges(), { Instant.now() })

        assertIs<AttendanceCommandResult.Success>(respond.execute(member, group, published, intent = AttendanceIntent.CONFIRM))

        assertEquals("CONFIRMED", string("SELECT status::text FROM game_attendance WHERE game_id = '$published'"))
    }

    @Test fun `a legacy game with 2 spots can be cancelled`() {
        val published = game("PUBLISHED", capacity = 2)

        assertIs<GameCommandResult.Success>(lifecycle().execute(owner, group, published, 1, GameMutation.CANCEL))

        assertEquals("CANCELLED", string("SELECT status::text FROM games WHERE id = '$published'"))
    }

    @Test fun `the finish job completes a legacy game with 2 spots and flags it for finance review without failing`() {
        val ended = game("PUBLISHED", capacity = 2, startsAt = NOW.minus(Duration.ofHours(3)))

        assertEquals(1, JdbcFinishedGamesRepository(dataSource).completeFinished(NOW).size)

        assertEquals("COMPLETED", string("SELECT status::text FROM games WHERE id = '$ended'"))
        execute("UPDATE games SET finance_review_required = true WHERE id = '$ended'")
        assertEquals(2, int("SELECT capacity FROM games WHERE id = '$ended'"))
    }

    @Test fun `a stopped series slot with 2 spots keeps generating games with the same capacity`() {
        val venue = UUID.randomUUID()
        val lineage = UUID.randomUUID()
        val revision = UUID.randomUUID()
        val slotKey = UUID.randomUUID()
        execute("INSERT INTO group_venues (id, group_id, name, address, court, created_at, updated_at) VALUES ('$venue', '$group', 'Arena Central', 'Rua das Flores 100', 'Quadra 2', now(), now())")
        execute("INSERT INTO game_series (id, lineage_id, group_id, revision_number, zone_id, local_start_date, created_at, updated_at) VALUES ('$revision', '$lineage', '$group', 1, 'America/Sao_Paulo', DATE '2026-01-01', now(), now())")
        execute(
            "INSERT INTO game_series_slots (series_revision_id, group_id, slot_key, title, weekday, local_time, duration_minutes, venue_id, venue_name, venue_address, venue_court, capacity, confirmation_lead_minutes, game_fee_cents, created_at) " +
                "VALUES ('$revision', '$group', '$slotKey', 'Treino semanal', 3, TIME '19:30', 90, '$venue', 'Arena Central', 'Rua das Flores 100', 'Quadra 2', 2, 180, 2500, now())",
        )
        val rule = WeeklySeriesRule(
            group, lineage, revision, "America/Sao_Paulo", LocalDate.of(2026, 1, 1),
            slots = listOf(
                WeeklySlotRule(slotKey, DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90, GameVenueSnapshot(venue, "Arena Central", "Rua das Flores 100", "Quadra 2"), 2, 180, 2500, "Treino semanal"),
            ),
        )
        val materializer = MaterializeWeeklySeries(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
            JdbcOccurrenceMaterializationRepository(dataSource),
            GameIdFactory(UUID::randomUUID),
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
        )

        val result = assertIs<MaterializeWeeklySeriesResult.Success>(materializer.execute(rule, LocalDate.of(2026, 1, 1)))

        assertEquals(12, result.inserted)
        assertEquals(12, int("SELECT count(*) FROM games WHERE series_id = '$lineage' AND capacity = 2"))
    }

    private fun lifecycle() = ChangeGameLifecycle(JdbcTransactionRunner(dataSource), JdbcGameOccurrenceRepository(dataSource), GameSideEffects(emptyList()))
    private fun charges() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { Instant.now() })

    private fun user(subject: String): UUID = UUID.randomUUID().also { id ->
        execute("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES ('$id', '$subject-$id', true, 'User', now(), now())")
    }

    private fun game(status: String, capacity: Int, startsAt: Instant? = null): UUID = UUID.randomUUID().also { id ->
        val start = startsAt?.let { "TIMESTAMPTZ '$it'" } ?: "now() + interval '2 days'"
        val deadline = startsAt?.let { "TIMESTAMPTZ '${it.minus(Duration.ofHours(1))}'" } ?: "now() + interval '1 day'"
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', (CURRENT_DATE + 2), TIME '19:30', 'America/Sao_Paulo', $start, 90, $deadline, 'Arena', 'Rua Central 100', $capacity, '$status', now(), now())",
        )
    }

    private fun execute(sql: String) { dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } }
    }

    private companion object { val NOW: Instant = Instant.parse("2026-10-07T23:00:00Z") }
}
