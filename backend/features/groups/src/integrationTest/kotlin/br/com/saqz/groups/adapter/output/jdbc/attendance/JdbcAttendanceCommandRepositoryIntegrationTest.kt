package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.attendance.*
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
import kotlin.test.assertIs
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAttendanceCommandRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll fun start() { postgres.startAndAwaitJdbc(); dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password) }
    @BeforeEach fun reset() { flyway().clean(); flyway().migrate() }
    @AfterAll fun stop() = postgres.stop()

    @Test fun `self confirmation below capacity persists current response and audit`() { val f = fixture(); val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.CONFIRMED, saved.status); assertEquals(1, count("game_attendance")); assertEquals(1, count("attendance_events")) }
    @Test fun `confirmation at capacity receives first monotonic waitlist position`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }; val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.WAITLISTED, saved.status); assertEquals(1, saved.waitlistSequence) }
    @Test fun `successive full confirmations receive unique fifo positions`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }; val second = member(f.group, "second"); val firstSaved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val secondSaved = success(f.service.execute(second, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(listOf(1L, 2L), listOf(firstSaved.waitlistSequence, secondSaved.waitlistSequence)) }
    @Test fun `confirmation retry returns same row without another audit`() { val f = fixture(); val first = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val retry = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(first, retry); assertEquals(1, count("game_attendance")); assertEquals(1, count("attendance_events")) }
    @Test fun `paid confirmation creates exactly one pending charge`() { val f = fixture(fee = 2500); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM); assertEquals(1, count("group_charges")); assertEquals("PENDING", string("SELECT status FROM group_charges")) }
    @Test fun `paid confirmation retry creates no duplicate charge or charge audit`() { val f = fixture(fee = 2500); repeat(2) { f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; assertEquals(1, count("group_charges")); assertEquals(1, count("group_charge_events")) }
    @Test fun `free game confirmation creates no charge`() { val f = fixture(fee = null); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM); assertEquals(0, count("group_charges")) }
    @Test fun `waitlisted paid game response creates no charge`() { val f = fixture(capacity = 2, fee = 2500); repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }; f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM); assertEquals(0, count("group_charges")) }
    @Test fun `declined member becoming confirmed receives one charge`() { val f = fixture(fee = 2500); attendance(f, f.member, "DECLINED"); val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.CONFIRMED, saved.status); assertEquals(2, saved.version); assertEquals(1, count("group_charges")) }
    @Test fun `nonmember self response is privacy hidden`() { val f = fixture(); val outsider = user("outsider"); assertSame(AttendanceCommandResult.Hidden, f.service.execute(outsider, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(0, count("game_attendance")) }
    @Test fun `cross group game is privacy hidden`() { val f = fixture(); val other = fixture("other"); assertSame(AttendanceCommandResult.Hidden, f.service.execute(f.member, f.group, other.game, intent = AttendanceIntent.CONFIRM)) }
    @Test fun `simultaneous final spot confirmations produce one confirmed and one waitlisted`() { val f = fixture(capacity = 2); attendance(f, member(f.group, "occupied"), "CONFIRMED"); val second = member(f.group, "second"); val results = concurrent(f, listOf(f.member, second)); assertEquals(1, results.count { it.status == AttendanceStatus.CONFIRMED }); assertEquals(1, results.count { it.status == AttendanceStatus.WAITLISTED }); assertEquals(2, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")) }
    @Test fun `simultaneous full confirmations allocate unique ordered waitlist sequences`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "occupied-$it"), "CONFIRMED") }; val second = member(f.group, "second"); val third = member(f.group, "third"); val results = concurrent(f, listOf(f.member, second, third)); assertEquals(listOf(1L, 2L, 3L), results.mapNotNull { it.waitlistSequence }.sorted()); assertEquals(3, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'")) }
    @Test fun `injected audit failure rolls back attendance and allocator`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "occupied-$it"), "CONFIRMED") }; val delegate = JdbcAttendanceCommandRepository(dataSource); val failing = object : AttendanceCommandRepository by delegate { override fun append(event: AttendanceEvent) { delegate.append(event); error("injected audit") } }; val service = service(failing); assertFailsWith<IllegalStateException> { service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; assertEquals(2, count("game_attendance")); assertEquals(0, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'")); assertEquals(0, count("attendance_events")) }
    @Test fun `injected charge failure rolls back attendance audit allocator and charge`() { val f = fixture(fee = 2500); val real = chargePort(); val failing = AttendanceChargePort { aggregate, actor -> real.confirmed(aggregate, actor); error("injected charge") }; val service = RespondAttendance(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), failing, { NOW }); assertFailsWith<IllegalStateException> { service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; listOf("game_attendance", "attendance_events", "group_charges", "group_charge_events").forEach { assertEquals(0, count(it), it) } }

    private fun concurrent(f: Fixture, members: List<UUID>): List<AttendanceRecord> {
        val barrier = CyclicBarrier(members.size + 1)
        val executor = Executors.newFixedThreadPool(members.size)
        return try {
            val futures = members.map { member -> executor.submit<AttendanceRecord> { barrier.await(); success(f.service.execute(member, f.group, f.game, intent = AttendanceIntent.CONFIRM)) } }
            barrier.await()
            futures.map { it.get() }
        } finally { executor.shutdownNow() }
    }

    private fun fixture(subject: String = "attendance", capacity: Int = 2, fee: Long? = 2500): Fixture {
        val owner = user("$subject-owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Attendance Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        val member = member(group, "$subject-member")
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',$capacity,${fee ?: "NULL"},'PUBLISHED',now(),now())")
        return Fixture(owner, member, group, game, service())
    }
    private fun service(repository: AttendanceCommandRepository = JdbcAttendanceCommandRepository(dataSource)) = RespondAttendance(JdbcTransactionRunner(dataSource), repository, chargePort(), { NOW })
    private fun chargePort() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { NOW })
    private fun attendance(f: Fixture, member: UUID, status: String) { execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at) VALUES ('${f.game}','${f.group}','$member','$status',now(),now())") }
    private fun member(group: UUID, subject: String): UUID { val id = user(subject); execute("INSERT INTO group_memberships VALUES ('$group','$id','ATHLETE',now(),now())"); return id }
    private fun user(subject: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    private fun success(result: AttendanceCommandResult) = assertIs<AttendanceCommandResult.Success>(result).attendance
    private fun flyway() = Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private fun execute(sql: String) { connection().use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun count(table: String) = int("SELECT count(*) FROM $table")
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
    private fun connection(): Connection = dataSource.connection
    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID, val service: RespondAttendance)
    private companion object { val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z") }
}
