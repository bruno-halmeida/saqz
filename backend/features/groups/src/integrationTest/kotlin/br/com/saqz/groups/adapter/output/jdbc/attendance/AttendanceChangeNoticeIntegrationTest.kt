package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcAttendanceChangeNotice
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.attendance.AttendanceChangeKind
import br.com.saqz.groups.application.attendance.AttendanceEvent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.application.attendance.GameGuests
import br.com.saqz.groups.application.attendance.ReleaseMemberAttendance
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
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

/** PRES-013: aviso direto (central + push) quando outra parte confirma, promove ou tira a pessoa do jogo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceChangeNoticeIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    private class World(val owner: UUID, val group: UUID, val game: UUID, val going: UUID, val waiting: UUID, val filler: UUID)

    private val promoted = "Sua vaga está confirmada no jogo de "

    @Test fun `desistencia de outra pessoa promove da espera e avisa so quem subiu`() {
        val w = world()
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals("CONFIRMED", status(w, w.waiting))
        assertEquals(1, notices(w.waiting))
        assertEquals(0, notices(w.going))
        assertEquals(true, body(w.waiting).startsWith(promoted))
        assertEquals(true, body(w.waiting).endsWith(", em Arena."))
    }

    @Test fun `aumento de vagas promove e avisa`() {
        val w = world()
        raise(w, 3)
        assertEquals("CONFIRMED", status(w, w.waiting))
        assertEquals(1, notices(w.waiting))
        assertEquals(0, notices(w.going))
    }

    @Test fun `organizador colocar no jogo promove da espera e avisa`() {
        val w = world(promotion = "MANUAL", capacity = 3)
        responses().promote(w.owner, w.group, w.game, w.waiting, UUID.randomUUID(), "Faltou gente")
        assertEquals("CONFIRMED", status(w, w.waiting))
        assertEquals(1, notices(w.waiting))
    }

    @Test fun `organizador confirma alguem pela rota override e avisa`() {
        val w = world(capacity = 3, withWaiting = false)
        val outsider = member(w.group, "fora")
        responses().execute(w.owner, w.group, w.game, memberId = outsider, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Pediu pelo grupo")
        assertEquals(1, notices(outsider))
        assertEquals(true, body(outsider).startsWith(promoted))
    }

    @Test fun `organizador tira alguem e avisa sem repetir o motivo`() {
        val w = world()
        responses().execute(w.owner, w.group, w.game, memberId = w.going, intent = AttendanceIntent.DECLINE, source = AttendanceSource.ORGANIZER, reason = "Motivo interno do organizador")
        assertEquals(1, notices(w.going))
        assertEquals(true, body(w.going).contains("foi removida pela organização"))
        assertEquals(false, body(w.going).contains("Motivo interno"))
    }

    @Test fun `tirar duas vezes ou repetir o mesmo request nao gera segundo aviso`() {
        val w = world()
        val request = UUID.randomUUID()
        repeat(2) {
            responses().execute(w.owner, w.group, w.game, memberId = w.going, intent = AttendanceIntent.DECLINE, source = AttendanceSource.ORGANIZER, reason = "Motivo", requestId = request)
        }
        assertEquals(1, notices(w.going))
    }

    @Test fun `a propria pessoa tocar Vou ou Nao vou nao avisa ninguem alem da fila`() {
        val w = world(capacity = 3, withWaiting = false)
        val joiner = member(w.group, "novo", "MENSALISTA")
        responses().execute(joiner, w.group, w.game, intent = AttendanceIntent.CONFIRM)
        assertEquals("CONFIRMED", status(w, joiner))
        assertEquals(0, notices(joiner))
        responses().execute(joiner, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals(0, notices(joiner))
    }

    @Test fun `Vou depois do prazo que a fila confirma na hora nao avisa`() {
        val w = world(capacity = 3, withWaiting = false)
        execute("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id='${w.game}'")
        val late = member(w.group, "atrasado")
        responses().execute(late, w.group, w.game, intent = AttendanceIntent.CONFIRM)
        assertEquals("CONFIRMED", status(w, late))
        assertEquals(0, notices(late))
    }

    @Test fun `convidado promovido da espera avisa o anfitriao citando o nome`() {
        val w = world(capacity = 3)
        guests().add(w.going, w.group, w.game, "Rafa Moreira", UUID.randomUUID())
        // lotado: o convidado entra na espera, atras de quem ja esperava
        assertEquals("WAITLISTED", string("SELECT status FROM game_attendance WHERE member_user_id='${w.going}' AND guest_seq=1"))
        raise(w, 5)
        val text = body(w.going)
        assertEquals(1, notices(w.going))
        assertEquals(1, notices(w.waiting))
        assertEquals(true, text.startsWith("A vaga de Rafa Moreira está confirmada"))
    }

    @Test fun `organizador confirma ou tira o convidado e o anfitriao recebe com o nome`() {
        val w = world(capacity = 5)
        guests().add(w.going, w.group, w.game, "Bia Souza", UUID.randomUUID())
        assertEquals(0, notices(w.going))
        guests().remove(w.owner, w.group, w.game, w.going, 1)
        assertEquals(1, notices(w.going))
        assertEquals(true, body(w.going).startsWith("A vaga de Bia Souza no jogo de "))
        assertEquals(true, body(w.going).endsWith("foi removida pela organização."))
    }

    @Test fun `anfitriao que desiste derruba os convidados e ninguem recebe aviso dele`() {
        val w = world(capacity = 5)
        guests().add(w.going, w.group, w.game, "Bia Souza", UUID.randomUUID())
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals(0, notices(w.going))
    }

    @Test fun `sair do grupo libera a vaga e avisa so quem subiu`() {
        val w = world()
        JdbcTransactionRunner(dataSource).inTransaction { release().release(w.group, w.going, w.going) }
        assertEquals("CONFIRMED", status(w, w.waiting))
        assertEquals(1, notices(w.waiting))
        assertEquals(0, notices(w.going))
    }

    @Test fun `preferencia de push desligada mantem o aviso na central e nao enfileira push`() {
        val w = world()
        execute("INSERT INTO group_notification_preferences (user_id, push_notices) VALUES ('${w.waiting}', false)")
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals(1, notices(w.waiting))
        assertEquals(0, int("SELECT count(*) FROM notification_push_queue q JOIN group_notifications n ON n.sequence=q.notification_id WHERE n.recipient_id='${w.waiting}'"))
    }

    @Test fun `com a preferencia padrao o aviso entra na fila de push e nao na do WhatsApp`() {
        val w = world()
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals(1, int("SELECT count(*) FROM notification_push_queue q JOIN group_notifications n ON n.sequence=q.notification_id WHERE n.recipient_id='${w.waiting}'"))
        assertEquals(0, int("SELECT count(*) FROM notification_whatsapp_queue"))
        assertEquals(0, int("SELECT count(*) FROM notification_whatsapp_group_queue"))
    }

    @Test fun `o aviso nao aparece no mural do grupo`() {
        val w = world()
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals(0, int("SELECT count(*) FROM group_messages WHERE channel IN ('NOTICE','CHAT')"))
        assertEquals("AUTO_CONFIRMATION", string("SELECT channel::text FROM group_messages WHERE group_id='${w.group}'"))
    }

    @Test fun `se o aviso falhar a mudanca de presenca e a promocao continuam gravadas`() {
        val w = world()
        execute("""
            CREATE FUNCTION fail_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'aviso quebrado'; END; $$;
            CREATE TRIGGER fail_notice BEFORE INSERT ON group_messages FOR EACH ROW EXECUTE FUNCTION fail_notice();
        """.trimIndent())
        responses().execute(w.going, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals("DECLINED", status(w, w.going))
        assertEquals("CONFIRMED", status(w, w.waiting))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${w.waiting}' AND new_status='CONFIRMED'"))
        assertEquals(0, notices(w.waiting))
    }

    @Test fun `controle - jogo publicado e futuro com membro ativo gera o aviso`() {
        val w = world()
        assertEquals(1, direct(w, w.waiting))
    }

    @Test fun `jogo que ja comecou ou passou nao avisa`() {
        val w = world()
        execute("UPDATE games SET confirmation_deadline = now() - interval '2 hours', starts_at = now() - interval '1 hour' WHERE id='${w.game}'")
        assertEquals(0, direct(w, w.waiting))
    }

    @Test fun `jogo em rascunho nao avisa`() {
        val w = world()
        execute("UPDATE games SET status = 'DRAFT' WHERE id='${w.game}'")
        assertEquals(0, direct(w, w.waiting))
    }

    @Test fun `jogo cancelado nao avisa`() {
        val w = world()
        execute("UPDATE games SET status = 'CANCELLED' WHERE id='${w.game}'")
        assertEquals(0, direct(w, w.waiting))
    }

    @Test fun `pessoa que nao e mais membro ativa nao recebe`() {
        val w = world()
        execute("UPDATE group_memberships SET active = false WHERE group_id='${w.group}' AND user_id='${w.waiting}'")
        assertEquals(0, direct(w, w.waiting))
    }

    /** Chama o aviso direto, dentro de transacao (o SAVEPOINT exige), e devolve quantos avisos a pessoa tem. */
    private fun direct(w: World, member: UUID): Int {
        val event = AttendanceEvent(UUID.randomUUID(), w.game, w.group, member, w.owner, AttendanceSource.ORGANIZER, AttendanceStatus.WAITLISTED, AttendanceStatus.CONFIRMED, null, now())
        JdbcTransactionRunner(dataSource).inTransaction { JdbcAttendanceChangeNotice(dataSource).changed(event, AttendanceChangeKind.CONFIRMED) }
        return notices(member)
    }

    private fun raise(w: World, capacity: Int) {
        val version = int("SELECT version FROM games WHERE id='${w.game}'").toLong()
        capacity().execute(w.owner, w.group, w.game, version, capacity)
    }

    private fun repo() = JdbcAttendanceCommandRepository(dataSource, JdbcAttendanceChangeNotice(dataSource))
    private fun charges() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { now() })
    private fun responses() = RespondAttendance(JdbcTransactionRunner(dataSource), repo(), charges(), { now() })
    private fun guests() = GameGuests(JdbcTransactionRunner(dataSource), repo(), responses()) { now() }
    private fun capacity() = AdjustGameCapacity(JdbcTransactionRunner(dataSource), repo(), charges(), { now() })
    private fun release() = ReleaseMemberAttendance(repo(), charges(), { now() })
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
    private fun body(recipient: UUID) = string("SELECT m.body FROM group_notifications n JOIN group_messages m ON m.id=n.message_id WHERE n.recipient_id='$recipient' AND m.channel='AUTO_CONFIRMATION' ORDER BY n.sequence DESC LIMIT 1")
    private fun execute(sql: String) { dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
}
