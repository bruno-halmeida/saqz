package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceMigrationIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll fun start() { postgres.startAndAwaitJdbc(); dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password) }
    @BeforeEach fun reset() { flyway().clean(); flyway().migrate() }
    @AfterAll fun stop() = postgres.stop()

    @Test fun `v6 creates current attendance and event tables`() = assertEquals(2, int("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('game_attendance','attendance_events')"))
    @Test fun `one current row stores status response update and version`() { val f = fixture(); attendance(f, "CONFIRMED"); assertEquals("CONFIRMED", string("SELECT status FROM game_attendance")); assertEquals(1, int("SELECT version FROM game_attendance")) }
    @Test fun `one row per game and member is unique`() { val f = fixture(); attendance(f, "DECLINED"); fails { attendance(f, "CONFIRMED") } }
    @Test fun `waitlisted attendance requires sequence`() { val f = fixture(); fails { attendance(f, "WAITLISTED") } }
    @Test fun `non waitlisted attendance rejects sequence`() { val f = fixture(); fails { attendance(f, "CONFIRMED", 1) } }
    @Test fun `unknown attendance status is rejected`() { val f = fixture(); fails { attendance(f, "MAYBE") } }
    @Test fun `waitlist allocation advances game monotonic allocator`() { val f = fixture(); attendance(f, "WAITLISTED", 1); assertEquals(1, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'")) }
    @Test fun `waitlist sequence cannot skip allocator`() { val f = fixture(); fails { attendance(f, "WAITLISTED", 2) } }
    @Test fun `departed waitlist sequence cannot be reused`() { val f = fixture(); attendance(f, "WAITLISTED", 1); execute("UPDATE game_attendance SET status='DECLINED',waitlist_sequence=NULL,version=2,updated_at=now() WHERE game_id='${f.game}' AND member_user_id='${f.member}'"); val second = member(f.group, "second"); fails { attendance(f.copy(member = second), "WAITLISTED", 1) }; attendance(f.copy(member = second), "WAITLISTED", 2); assertEquals(2, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'")) }
    @Test fun `attendance member fk only requires an access user, not a group membership`() { val f = fixture(); val outsider = user("outsider"); attendance(f.copy(member = outsider), "DECLINED") }
    @Test fun `attendance member fk still rejects an unknown access user`() { val f = fixture(); fails { attendance(f.copy(member = UUID.randomUUID()), "DECLINED") } }
    @Test fun `attendance game must belong to same group`() { val f = fixture(); val other = fixture("other"); fails { attendance(f.copy(game = other.game), "DECLINED") } }
    @Test fun `attendance rejects invalid version and time ordering`() { val f = fixture(); fails { attendance(f, "DECLINED", version = 0) }; fails { attendance(f, "DECLINED", responded = "now()", updated = "now() - interval '1 second'") } }
    @Test fun `event preserves actor source states reason and server time`() { val f = fixture(); val event = event(f, "ORGANIZER", null, "CONFIRMED", "Entrou após prazo"); assertEquals("ORGANIZER|CONFIRMED|Entrou após prazo", string("SELECT source||'|'||new_status||'|'||reason FROM attendance_events WHERE id='$event'")) }
    @Test fun `organizer event requires a reason`() { val f = fixture(); fails { event(f, "ORGANIZER", null, "CONFIRMED", null) } }
    @Test fun `event rejects unknown source and contradictory transition`() { val f = fixture(); fails { event(f, "CLIENT", null, "CONFIRMED", null) }; fails { event(f, "SELF", "CONFIRMED", "CONFIRMED", null) } }
    @Test fun `attendance event cannot be updated`() { val f = fixture(); val event = event(f, "SELF", null, "DECLINED", null); fails { execute("UPDATE attendance_events SET new_status='CONFIRMED' WHERE id='$event'") } }
    @Test fun `attendance event cannot be deleted`() { val f = fixture(); val event = event(f, "SELF", null, "DECLINED", null); fails { execute("DELETE FROM attendance_events WHERE id='$event'") } }

    private fun fixture(subject: String = "attendance"): Fixture {
        val owner = user("$subject-owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Attendance Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        val member = member(group, "$subject-member")
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',2,'PUBLISHED',now(),now())")
        return Fixture(owner, group, member, game)
    }
    private fun member(group: UUID, subject: String): UUID { val id = user(subject); execute("INSERT INTO group_memberships VALUES ('$group','$id','ATHLETE',now(),now())"); return id }
    private fun user(subject: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    private fun attendance(f: Fixture, status: String, sequence: Long? = null, version: Long = 1, responded: String = "now()", updated: String = "now()"): UUID { execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,version,member_display_name) VALUES ('${f.game}','${f.group}','${f.member}','$status',${sequence ?: "NULL"},$responded,$updated,$version,'Member')"); return f.member }
    private fun event(f: Fixture, source: String, old: String?, new: String, reason: String?): UUID { val id = UUID.randomUUID(); execute("INSERT INTO attendance_events (id,game_id,group_id,member_user_id,actor_user_id,source,old_status,new_status,reason,occurred_at) VALUES ('$id','${f.game}','${f.group}','${f.member}','${f.owner}','$source',${old.q()},'$new',${reason.q()},now())"); return id }
    private fun flyway() = Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private fun execute(sql: String) { connection().use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
    private fun connection(): Connection = dataSource.connection
    private fun fails(block: () -> Unit) { assertFailsWith<Exception> { block() } }
    private fun Any?.q() = this?.let { "'$it'" } ?: "NULL"
    private data class Fixture(val owner: UUID, val group: UUID, val member: UUID, val game: UUID)
}
