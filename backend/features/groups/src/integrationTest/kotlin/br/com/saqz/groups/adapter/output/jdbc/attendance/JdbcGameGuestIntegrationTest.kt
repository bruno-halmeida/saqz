package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.testing.*
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGameGuestIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    @Test
    fun removingAConfirmedGuestCancelsThePendingChargeAndPromotesTheNext() {
        val f = fixture(capacity = 2, fee = 2500)
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        val added = success(f.guests.add(f.member, f.group, f.game, "Rafa Moreira", UUID.randomUUID()))
        assertEquals(AttendanceStatus.WAITLISTED, added.status)
        val waiting = member(f.group, "waiting")
        waitlist(f, waiting, 2)

        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${f.group}'")
        val promoted = success(f.service.promote(f.owner, f.group, f.game, f.member, UUID.randomUUID(), "Promovido manualmente", guestSeq = 1))
        assertEquals(AttendanceStatus.CONFIRMED, promoted.status)
        assertEquals("PENDING", string("SELECT status FROM group_charges WHERE member_user_id='${f.member}' AND guest_seq=1"))
        assertEquals("Rafa Moreira", string("SELECT guest_display_name FROM group_charges WHERE member_user_id='${f.member}' AND guest_seq=1"))

        execute("UPDATE access_groups SET promotion_mode='FIFO' WHERE id='${f.group}'")
        val removed = success(f.guests.remove(f.owner, f.group, f.game, f.member, 1))
        assertEquals(AttendanceStatus.DECLINED, removed.status)
        assertEquals("CANCELLED", string("SELECT status FROM group_charges WHERE member_user_id='${f.member}' AND guest_seq=1"))
        assertEquals("CONFIRMED", status(waiting))
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='$waiting'"))
    }

    @Test
    fun hostDecliningDropsEveryGuestBeforePromoting() {
        val f = fixture(capacity = 2, fee = 2500)
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        success(f.guests.add(f.member, f.group, f.game, "Rafa Moreira", UUID.randomUUID()))
        val waiting = member(f.group, "waiting")
        waitlist(f, waiting, 2)

        val result = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE))

        assertEquals(AttendanceStatus.DECLINED, result.status)
        assertEquals("DECLINED", string("SELECT status FROM game_attendance WHERE member_user_id='${f.member}' AND guest_seq=1"))
        assertEquals("CONFIRMED", status(waiting))
        assertEquals(0, int("SELECT count(*) FROM group_charges WHERE guest_seq>0"))
    }

    @Test
    fun rosterExposesGuestSeqAndHostName() {
        val f = fixture()
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        success(f.guests.add(f.member, f.group, f.game, "Rafa Moreira", UUID.randomUUID()))

        val roster = requireNotNull(JdbcAttendanceCommandRepository(dataSource).roster(f.member, f.group, f.game))
        val guest = roster.waitlisted.single()

        assertEquals(1, guest.guestSeq)
        assertEquals("Rafa Moreira", guest.displayName)
        assertEquals("User", guest.hostDisplayName)
    }

    @Test
    fun guestNeverCountsAsDeclinedOrPending() {
        val f = fixture()
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        success(f.guests.add(f.member, f.group, f.game, "Rafa Moreira", UUID.randomUUID()))
        val before = requireNotNull(JdbcAttendanceCommandRepository(dataSource).find(f.member, f.group, f.game))

        success(f.guests.remove(f.member, f.group, f.game, f.member, 1))
        val after = requireNotNull(JdbcAttendanceCommandRepository(dataSource).find(f.member, f.group, f.game))

        assertEquals(before.declinedCount, after.declinedCount)
        assertEquals(before.pendingCount, after.pendingCount)
    }

    @Test
    fun capacityIncreasePromotesAGuestAndChargesTheGuestRow() {
        val f = fixture(capacity = 2, fee = 2500)
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        success(f.guests.add(f.member, f.group, f.game, "Rafa Moreira", UUID.randomUUID()))

        val result = assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 1, 2))

        assertEquals(1, result.promoted.size)
        assertEquals(AttendanceStatus.CONFIRMED, result.promoted.single().status)
        assertEquals(1, result.promoted.single().guestSeq)
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='${f.member}' AND guest_seq=1"))
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='${f.member}' AND guest_seq=0"))
    }

    private fun fixture(capacity: Int = 2, fee: Long? = 2500): Fixture {
        val owner = user("guest-owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Guest Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        val member = member(group, "guest-member")
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,now() + interval '1 day','Arena','Rua Central 100',$capacity,${fee ?: "NULL"},'PUBLISHED',now(),now())")
        val repository = JdbcAttendanceCommandRepository(dataSource)
        val service = RespondAttendance(JdbcTransactionRunner(dataSource), repository, chargePort(), { now() })
        val guests = GameGuests(JdbcTransactionRunner(dataSource), repository, service, { now() })
        return Fixture(owner, member, group, game, service, guests)
    }

    private fun capacity(f: Fixture) = AdjustGameCapacity(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), chargePort(), { now() })
    private fun chargePort() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { now() })
    // timestamptz guarda microssegundos; sem truncar, o objeto devolvido na primeira chamada
    // (nanos da JVM em Linux) nunca bate com o relido do banco no replay.
    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)
    private fun waitlist(f: Fixture, member: UUID, sequence: Long, name: String = "Member") { execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name) VALUES ('${f.game}','${f.group}','$member','WAITLISTED',$sequence,now(),now(),'$name')") }
    private fun member(group: UUID, subject: String, membership: String = "MENSALISTA", name: String = "User"): UUID { val id = user(subject, name); execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$id','ATHLETE',now(),now(),'$membership')"); return id }
    private fun user(subject: String, name: String = "User"): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'$name',now(),now())"); return id }
    private fun success(result: AttendanceCommandResult) = assertIs<AttendanceCommandResult.Success>(result).attendance
    private fun execute(sql: String) { connection().use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun status(member: UUID) = string("SELECT status FROM game_attendance WHERE member_user_id='$member' AND guest_seq=0")
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
    private fun connection(): Connection = dataSource.connection

    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID, val service: RespondAttendance, val guests: GameGuests)
}
