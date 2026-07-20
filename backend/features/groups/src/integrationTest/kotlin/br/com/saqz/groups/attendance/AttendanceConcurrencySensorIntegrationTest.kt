package br.com.saqz.groups.attendance

import br.com.saqz.groups.adapter.output.jdbc.attendance.*
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.testing.*
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
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceConcurrencySensorIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll fun start() { postgres.startAndAwaitJdbc(); dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password) }
    @BeforeEach fun reset() { flyway().clean(); flyway().migrate() }
    @AfterAll fun stop() = postgres.stop()

    @Test fun `sensor final spot never overbooks`() { val f = fixture(2); confirmed(f, member(f.group, "occupied")); val racers = listOf(f.member, member(f.group, "racer")); race(racers.map { id -> { f.responses.execute(id, f.group, f.game, intent = AttendanceIntent.CONFIRM) } }); assertEquals(2, countStatus("CONFIRMED")); assertEquals(1, racers.count { status(it) == "CONFIRMED" }); assertEquals(1, racers.count { status(it) == "WAITLISTED" }) }
    @Test fun `sensor full confirms allocate unique contiguous fifo`() { val f = fixture(2); repeat(2) { confirmed(f, member(f.group, "occupied-$it")) }; val racers = (1..3).map { member(f.group, "racer-$it") }; race(racers.map { id -> { f.responses.execute(id, f.group, f.game, intent = AttendanceIntent.CONFIRM) } }); assertEquals(listOf(1, 2, 3), queryInts("SELECT waitlist_sequence FROM game_attendance WHERE status='WAITLISTED' ORDER BY waitlist_sequence")); assertEquals(3, count("attendance_events")) }
    @Test fun `sensor confirm and withdraw preserve capacity and fifo`() { val f = fullWithWaiter(); val newcomer = member(f.group, "newcomer"); race(listOf({ f.responses.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE) }, { f.responses.execute(newcomer, f.group, f.game, intent = AttendanceIntent.CONFIRM) })); assertEquals(2, countStatus("CONFIRMED")); assertEquals("CONFIRMED", status(f.waiters.single())); assertEquals("WAITLISTED", status(newcomer)) }
    @Test fun `sensor two withdrawals promote two earliest members once`() { val f = fullWithWaiter(waiters = 2); val otherConfirmed = queryUuid("SELECT member_user_id FROM game_attendance WHERE status='CONFIRMED' AND member_user_id<>'${f.member}' LIMIT 1"); race(listOf({ f.responses.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE) }, { f.responses.execute(otherConfirmed, f.group, f.game, intent = AttendanceIntent.DECLINE) })); assertEquals(f.waiters.toSet(), queryUuids("SELECT member_user_id FROM game_attendance WHERE status='CONFIRMED'").toSet()); assertEquals(2, countStatus("CONFIRMED")) }
    @Test fun `sensor capacity increase and confirm preserve earliest fifo`() { val f = fullWithWaiter(); val newcomer = member(f.group, "newcomer"); race(listOf({ f.capacities.execute(f.owner, f.group, f.game, 1, 3) }, { f.responses.execute(newcomer, f.group, f.game, intent = AttendanceIntent.CONFIRM) })); assertEquals(3, countStatus("CONFIRMED")); assertEquals("CONFIRMED", status(f.waiters.single())); assertEquals("WAITLISTED", status(newcomer)) }
    @Test fun `sensor capacity decrease never demotes and blocks confirm`() { val f = fixture(4); repeat(4) { confirmed(f, if (it == 0) f.member else member(f.group, "confirmed-$it")) }; val newcomer = member(f.group, "newcomer"); race(listOf({ f.capacities.execute(f.owner, f.group, f.game, 1, 2) }, { f.responses.execute(newcomer, f.group, f.game, intent = AttendanceIntent.CONFIRM) })); assertEquals(4, countStatus("CONFIRMED")); assertEquals("WAITLISTED", status(newcomer)); assertEquals(2, int("SELECT capacity FROM games")) }
    @Test fun `sensor duplicate confirmation creates one row charge and audit`() { val f = fixture(2); race(List(3) { { f.responses.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) } }); assertEquals(1, count("game_attendance")); assertEquals(1, count("attendance_events")); assertEquals(1, count("group_charges")); assertEquals(1, count("group_charge_events")) }
    @Test fun `sensor duplicate organizer override creates one audit`() { val f = fixture(2); race(List(2) { { f.responses.execute(f.owner, f.group, f.game, f.member, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Ajuste concorrente") } }); assertEquals(1, count("game_attendance")); assertEquals(1, count("attendance_events")); assertEquals("ORGANIZER", string("SELECT source FROM attendance_events")) }
    @Test fun `sensor cancellation race freezes final history and reconciles charge`() { val f = fixture(2); val charges = ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { NOW }; val lifecycle = ChangeGameLifecycle(JdbcTransactionRunner(dataSource), JdbcGameOccurrenceRepository(dataSource), br.com.saqz.groups.application.finance.charge.GameFinanceSideEffects(charges)); race(listOf({ lifecycle.execute(f.owner, f.group, f.game, 1, GameMutation.CANCEL) }, { f.responses.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) })); assertEquals("CANCELLED", string("SELECT status FROM games")); assertEquals(count("game_attendance"), count("attendance_events")); assertEquals(0, int("SELECT count(*) FROM group_charges WHERE status='PENDING'")); assertEquals(count("group_charges") * 2, count("group_charge_events")) }
    @Test fun `sensor competing rollback consumes no spot sequence charge or audit`() { val f = fixture(2); val failingMember = f.member; val successful = member(f.group, "successful"); val real = chargePort(); val failingCharges = AttendanceChargePort { aggregate, actor -> real.confirmed(aggregate, actor); error("injected") }; val failing = RespondAttendance(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), failingCharges, { NOW }); val barrier = CyclicBarrier(3); val executor = Executors.newFixedThreadPool(2); try { val failed = executor.submit { barrier.await(); assertFailsWith<IllegalStateException> { failing.execute(failingMember, f.group, f.game, intent = AttendanceIntent.CONFIRM) } }; val passed = executor.submit { barrier.await(); f.responses.execute(successful, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; barrier.await(); failed.get(); passed.get() } finally { executor.shutdownNow() }; assertEquals(1, countStatus("CONFIRMED")); assertEquals("CONFIRMED", status(successful)); assertEquals(0, int("SELECT count(*) FROM game_attendance WHERE member_user_id='$failingMember'")); assertEquals(1, count("attendance_events")); assertEquals(1, count("group_charges")) }

    private fun race(commands: List<() -> Any>) {
        val barrier = CyclicBarrier(commands.size + 1); val executor = Executors.newFixedThreadPool(commands.size)
        try { val futures = commands.map { command -> executor.submit<Any> { barrier.await(); command() } }; barrier.await(); futures.forEach { it.get() } } finally { executor.shutdownNow() }
    }

    private fun fullWithWaiter(waiters: Int = 1): Fixture {
        val f = fixture(2); confirmed(f, f.member); confirmed(f, member(f.group, "confirmed"))
        val waiting = (1..waiters).map { member(f.group, "waiting-$it").also { id -> waitlisted(f, id, it) } }
        return f.copy(waiters = waiting)
    }

    private fun fixture(capacity: Int): Fixture {
        val owner = user("owner"); val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Sensor Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        val member = member(group, "member"); val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',$capacity,2500,'PUBLISHED',now(),now())")
        val repository = JdbcAttendanceCommandRepository(dataSource); val charges = chargePort()
        return Fixture(owner, member, group, game, RespondAttendance(JdbcTransactionRunner(dataSource), repository, charges, { NOW }), AdjustGameCapacity(JdbcTransactionRunner(dataSource), repository, charges, { NOW }))
    }

    private fun chargePort() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { NOW })
    private fun confirmed(f: Fixture, member: UUID) = execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at) VALUES ('${f.game}','${f.group}','$member','CONFIRMED',now(),now())")
    private fun waitlisted(f: Fixture, member: UUID, sequence: Int) = execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at) VALUES ('${f.game}','${f.group}','$member','WAITLISTED',$sequence,now(),now())")
    private fun member(group: UUID, subject: String): UUID { val id = user(subject); execute("INSERT INTO group_memberships VALUES ('$group','$id','ATHLETE',now(),now())"); return id }
    private fun user(subject: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    private fun status(member: UUID) = string("SELECT status FROM game_attendance WHERE member_user_id='$member'")
    private fun countStatus(status: String) = int("SELECT count(*) FROM game_attendance WHERE status='$status'")
    private fun count(table: String) = int("SELECT count(*) FROM $table")
    private fun queryUuid(sql: String) = UUID.fromString(string(sql))
    private fun queryUuids(sql: String) = queryStrings(sql).map(UUID::fromString)
    private fun queryInts(sql: String) = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> buildList { while (r.next()) add(r.getInt(1)) } } } }
    private fun queryStrings(sql: String) = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> buildList { while (r.next()) add(r.getString(1)) } } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
    private fun execute(sql: String) { connection().use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun connection(): Connection = dataSource.connection
    private fun flyway() = Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID, val responses: RespondAttendance, val capacities: AdjustGameCapacity, val waiters: List<UUID> = emptyList())
    private companion object { val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z") }
}
