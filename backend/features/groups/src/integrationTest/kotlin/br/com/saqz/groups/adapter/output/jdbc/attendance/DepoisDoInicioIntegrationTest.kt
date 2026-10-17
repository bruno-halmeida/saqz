package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcAttendanceChangeNotice
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.application.attendance.CapacityCommandResult
import br.com.saqz.groups.application.attendance.ReleaseMemberAttendance
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.attendance.SettleWaitlistAtDeadline
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * DEB-018 e DEB-020: depois do início do jogo ninguém sobe da espera (promoção automática nem "Colocar no jogo")
 * e as vagas não mudam. Cada caminho tem o controle antes do início.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DepoisDoInicioIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    private class World(val owner: UUID, val group: UUID, val game: UUID, val going: UUID, val waiting: UUID, val filler: UUID)

    @Test fun `controle - desistencia antes do inicio promove da espera`() {
        val w = world()
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals("CONFIRMED", status(w, w.waiting))
        assertEquals(1, notices(w.waiting))
    }

    @Test fun `desistencia da propria pessoa depois do inicio e recusada como encerrada e nao promove`() {
        val w = world()
        started(w)
        val result = responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals(AttendanceDenial.FROZEN, assertIs<AttendanceCommandResult.Denied>(result).reason)
        assertEquals("CONFIRMED", status(w, w.going))
        assertEquals("WAITLISTED", status(w, w.waiting))
    }

    @Test fun `organizador tirar alguem depois do inicio funciona mas a vaga fica livre sem promocao nem aviso`() {
        val w = world()
        started(w)
        val result = responses().execute(w.owner, w.group, w.game, memberId = w.going, intent = AttendanceIntent.DECLINE, source = AttendanceSource.ORGANIZER, reason = "Saiu da quadra")
        assertIs<AttendanceCommandResult.Success>(result)
        assertEquals("DECLINED", status(w, w.going))
        assertEquals("WAITLISTED", status(w, w.waiting))
        assertEquals(0, notices(w.waiting))
        assertEquals(0, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${w.waiting}'"))
    }

    @Test fun `organizador confirmar direto quem nao esta na espera depois do inicio continua funcionando`() {
        val w = world(capacity = 3, withWaiting = false)
        started(w)
        val outsider = member(w.group, "fora")
        val result = responses().execute(w.owner, w.group, w.game, memberId = outsider, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Chegou na quadra")
        assertIs<AttendanceCommandResult.Success>(result)
        assertEquals("CONFIRMED", status(w, outsider))
    }

    @Test fun `controle - Colocar no jogo antes do inicio promove`() {
        val w = world(promotion = "MANUAL", capacity = 3)
        val result = responses().promote(w.owner, w.group, w.game, w.waiting, UUID.randomUUID(), "Faltou gente")
        assertIs<AttendanceCommandResult.Success>(result)
        assertEquals("CONFIRMED", status(w, w.waiting))
    }

    @Test fun `Colocar no jogo depois do inicio e recusado como encerrado e nao promove nem avisa`() {
        val w = world(promotion = "MANUAL", capacity = 3)
        started(w)
        val result = responses().promote(w.owner, w.group, w.game, w.waiting, UUID.randomUUID(), "Faltou gente")
        assertEquals(AttendanceDenial.FROZEN, assertIs<AttendanceCommandResult.Denied>(result).reason)
        assertEquals("WAITLISTED", status(w, w.waiting))
        assertEquals(0, notices(w.waiting))
    }

    @Test fun `controle - sair do grupo antes do inicio libera a vaga e promove`() {
        val w = world()
        JdbcTransactionRunner(dataSource).inTransaction { release().release(w.group, w.going, w.going) }
        assertEquals("CONFIRMED", status(w, w.waiting))
    }

    @Test fun `sair do grupo depois do inicio nao promove ninguem`() {
        val w = world()
        started(w)
        JdbcTransactionRunner(dataSource).inTransaction { release().release(w.group, w.going, w.going) }
        assertEquals("WAITLISTED", status(w, w.waiting))
        assertEquals(0, notices(w.waiting))
    }

    @Test fun `controle - aumento de vagas antes do inicio promove`() {
        val w = world()
        val result = capacity().execute(w.owner, w.group, w.game, version(w), 4)
        assertIs<CapacityCommandResult.Success>(result)
        assertEquals("CONFIRMED", status(w, w.waiting))
    }

    @Test fun `ajustar vagas depois do inicio e recusado como encerrado e nao muda nada`() {
        val w = world()
        started(w)
        val before = version(w)
        assertIs<CapacityCommandResult.Frozen>(capacity().execute(w.owner, w.group, w.game, before, 4))
        assertIs<CapacityCommandResult.Frozen>(capacity().execute(w.owner, w.group, w.game, before, 5))
        assertEquals(2, int("SELECT capacity FROM games WHERE id='${w.game}'"))
        assertEquals(before, version(w))
        assertEquals("WAITLISTED", status(w, w.waiting))
        assertEquals(0, notices(w.waiting))
    }

    @Test fun `controle - job do prazo antes do inicio promove a fila nas vagas que sobraram`() {
        val w = world(capacity = 3)
        execute("UPDATE games SET confirmation_deadline = now() - interval '5 minutes' WHERE id='${w.game}'")
        assertEquals(1, job().run())
        assertEquals("CONFIRMED", status(w, w.waiting))
    }

    @Test fun `job do prazo depois do inicio nao promove ninguem`() {
        val w = world(capacity = 3)
        started(w)
        assertEquals(0, job().run())
        assertEquals("WAITLISTED", status(w, w.waiting))
        assertEquals(0, notices(w.waiting))
    }

    private fun started(w: World) =
        execute("UPDATE games SET confirmation_deadline = now() - interval '2 hours', starts_at = now() - interval '1 hour' WHERE id='${w.game}'")

    private fun version(w: World) = int("SELECT version FROM games WHERE id='${w.game}'").toLong()

    private fun repo() = JdbcAttendanceCommandRepository(dataSource, JdbcAttendanceChangeNotice(dataSource))
    private fun charges() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { now() })
    private fun responses() = RespondAttendance(JdbcTransactionRunner(dataSource), repo(), charges(), { now() })
    private fun capacity() = AdjustGameCapacity(JdbcTransactionRunner(dataSource), repo(), charges(), { now() })
    private fun release() = ReleaseMemberAttendance(repo(), charges(), { now() })
    private fun job() = SettleWaitlistAtDeadline(
        JdbcTransactionRunner(dataSource), JdbcDeadlineWaitlistRepository(dataSource), repo(), charges(), { _, _ -> }, { now() },
    )
    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    /** Jogo cheio: [going] e [filler] confirmados (capacidade 2) e [waiting] na espera. */
    private fun world(capacity: Int = 2, promotion: String = "FIFO", withWaiting: Boolean = true): World {
        val owner = user("owner", "Dona")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,promotion_mode,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Grupo','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED','$promotion',now(),now())")
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$owner','ADMIN',now(),now(),'AVULSO')")
        val going = member(group, "going"); val filler = member(group, "filler"); val waiting = member(group, "waiting")
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,now() + interval '1 day','Arena','Rua Central 100',$capacity,NULL,'PUBLISHED',now(),now())")
        attendance(game, group, going, "CONFIRMED", null); attendance(game, group, filler, "CONFIRMED", null)
        if (withWaiting) attendance(game, group, waiting, "WAITLISTED", 1)
        return World(owner, group, game, going, waiting, filler)
    }

    private fun attendance(game: UUID, group: UUID, member: UUID, status: String, sequence: Long?) =
        execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name) VALUES ('$game','$group','$member','$status',${sequence ?: "NULL"},now(),now(),'User')")

    private fun member(group: UUID, subject: String, type: String = "AVULSO"): UUID {
        val id = user(subject, "User")
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$id','ATHLETE',now(),now(),'$type')")
        return id
    }
    private fun user(subject: String, name: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'$name',now(),now())"); return id }
    private fun status(w: World, member: UUID) = string("SELECT status FROM game_attendance WHERE game_id='${w.game}' AND member_user_id='$member' AND guest_seq=0")
    private fun notices(recipient: UUID) = int("SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id=n.message_id WHERE n.recipient_id='$recipient' AND m.channel='AUTO_CONFIRMATION'")
    private fun execute(sql: String) { dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
}
