package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.attendance.ReleaseMemberAttendance
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseMemberAttendanceIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    private class World(val owner: UUID, val group: UUID, val game: UUID, val leaver: UUID, val waiting: UUID)

    @Test fun `leaving the group frees the confirmed spot and promotes the waitlist`() {
        val w = world()
        remove().leave(w.leaver, w.group)
        assertEquals("DECLINED", status(w.game, w.leaver))
        assertEquals("CONFIRMED", status(w.game, w.waiting))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${w.leaver}' AND source='SYSTEM' AND old_status='CONFIRMED' AND new_status='DECLINED'"))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${w.waiting}' AND source='SYSTEM' AND new_status='CONFIRMED'"))
        assertEquals(0, int("SELECT count(*) FROM group_memberships WHERE user_id='${w.leaver}'"))
    }

    @Test fun `leaving before the deadline with mensalista priority leaves a waiting avulso waiting`() {
        val w = world(priority = true)
        remove().leave(w.leaver, w.group)
        assertEquals("WAITLISTED", status(w.game, w.waiting))
    }

    @Test fun `organizer removal frees the spot and records the organizer as actor`() {
        val w = world()
        remove().execute(w.owner, w.group, w.leaver)
        assertEquals("DECLINED", status(w.game, w.leaver))
        assertEquals("CONFIRMED", status(w.game, w.waiting))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${w.leaver}' AND actor_user_id='${w.owner}' AND source='SYSTEM'"))
    }

    @Test fun `account deletion path releases through the same use case`() {
        val w = world()
        val release = release()
        JdbcTransactionRunner(dataSource).inTransaction {
            release.release(w.group, w.leaver, w.leaver)
            JdbcAthleteRepository(dataSource).remove(w.group, w.leaver)
        }
        assertEquals("DECLINED", status(w.game, w.leaver))
        assertEquals("CONFIRMED", status(w.game, w.waiting))
    }

    @Test fun `past and cancelled games keep the attendance untouched`() {
        val w = world()
        val past = game(w.group, "COMPLETED", "now() - interval '3 days'", 4)
        val cancelled = game(w.group, "CANCELLED", "now() + interval '5 days'", 5)
        listOf(past, cancelled).forEach { insert(it, w.group, w.leaver, "CONFIRMED", null) }
        remove().leave(w.leaver, w.group)
        assertEquals("CONFIRMED", status(past, w.leaver))
        assertEquals("CONFIRMED", status(cancelled, w.leaver))
    }

    @Test fun `draft games are released too`() {
        val w = world()
        val draft = game(w.group, "DRAFT", "now() + interval '6 days'", 1)
        insert(draft, w.group, w.leaver, "CONFIRMED", null)
        remove().leave(w.leaver, w.group)
        assertEquals("DECLINED", status(draft, w.leaver))
    }

    @Test fun `guests of the leaver leave as well and free their spots for the queue`() {
        val w = world(capacity = 3)
        insert(w.game, w.group, w.leaver, "CONFIRMED", null, guest = 1, name = "Convidado")
        val second = user("waiting-2"); membership(w.group, second); insert(w.game, w.group, second, "WAITLISTED", 2)
        remove().leave(w.leaver, w.group)
        assertEquals("DECLINED", string("SELECT status FROM game_attendance WHERE member_user_id='${w.leaver}' AND guest_seq=1"))
        assertEquals("CONFIRMED", status(w.game, w.waiting))
        assertEquals("CONFIRMED", status(w.game, second))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${w.leaver}' AND guest_seq=1 AND source='SYSTEM'"))
    }

    @Test fun `manual promotion mode frees the spot without promoting`() {
        val w = world()
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${w.group}'")
        remove().leave(w.leaver, w.group)
        assertEquals("DECLINED", status(w.game, w.leaver))
        assertEquals("WAITLISTED", status(w.game, w.waiting))
    }

    @Test fun `a waitlisted leaver just leaves the queue`() {
        val w = world()
        remove().leave(w.waiting, w.group)
        assertEquals("DECLINED", status(w.game, w.waiting))
        assertEquals("CONFIRMED", status(w.game, w.leaver))
    }

    private fun release() = ReleaseMemberAttendance(
        JdbcAttendanceCommandRepository(dataSource),
        AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { Instant.now() }),
        Instant::now,
    )

    private fun remove() = RemoveAthlete(
        JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcAthleteRepository(dataSource),
        GroupAccessPolicy(), attendanceRelease = release(),
    )

    /** Game with the leaver CONFIRMED (filling it) and one person WAITLISTED. */
    private fun world(capacity: Int = 2, priority: Boolean = false): World {
        val owner = user("owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Grupo','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        execute("UPDATE access_groups SET mensalista_priority = $priority WHERE id = '$group'")
        val leaver = user("leaver"); membership(group, leaver)
        val waiting = user("waiting"); membership(group, waiting)
        val g = game(group, "PUBLISHED", "now() + interval '2 days'", 2, capacity)
        insert(g, group, leaver, "CONFIRMED", null)
        val filler = user("filler"); membership(group, filler); insert(g, group, filler, "CONFIRMED", null)
        insert(g, group, waiting, "WAITLISTED", 1)
        return World(owner, group, g, leaver, waiting)
    }

    private fun game(group: UUID, status: String, startsAt: String, days: Int, capacity: Int = 2): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$id','$group','Treino',(CURRENT_DATE + $days),TIME '19:30','America/Sao_Paulo',$startsAt,90,($startsAt) - interval '1 hour','Arena','Rua Central 100',$capacity,NULL,'$status',now(),now())")
        return id
    }

    private fun insert(game: UUID, group: UUID, member: UUID, status: String, sequence: Long?, guest: Int = 0, name: String = "User") {
        execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name,guest_seq) VALUES ('$game','$group','$member','$status',${sequence ?: "NULL"},now(),now(),'$name',$guest)")
    }

    private fun membership(group: UUID, user: UUID) = execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$user','ATHLETE',now(),now(),'AVULSO')")
    private fun user(subject: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    private fun status(game: UUID, member: UUID) = string("SELECT status FROM game_attendance WHERE game_id='$game' AND member_user_id='$member' AND guest_seq=0")
    private fun execute(sql: String) { dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
}
