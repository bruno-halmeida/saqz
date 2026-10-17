package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.application.attendance.GameGuests
import br.com.saqz.groups.application.attendance.ReleaseMemberAttendance
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.attendance.SettleWaitlistAtDeadline
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.GroupAccessPolicy
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
 * DEB-021: a diária (group_charges.kind = 'GAME') é só do avulso. Cada caminho de entrada no jogo roda duas vezes,
 * com a pessoa avulsa (gera diária) e mensalista (não gera). Convidado cobra do anfitrião, seja qual for o vínculo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiariaSoAvulsoIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    private class World(val owner: UUID, val group: UUID, val game: UUID, val subject: UUID)

    private val vinculos = mapOf("AVULSO" to 1, "MENSALISTA" to 0)

    private fun cadaVinculo(body: (vinculo: String, esperado: Int) -> Unit) = vinculos.forEach { (vinculo, esperado) -> reset(); body(vinculo, esperado) }

    @Test fun `Vou com vaga gera diaria so para o avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo); fillers(w, 3, "CONFIRMED")
        assertIs<AttendanceCommandResult.Success>(respond().execute(w.subject, w.group, w.game, intent = AttendanceIntent.CONFIRM))
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `subida por desistencia gera diaria so para o avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo); val ids = fillers(w, 4, "CONFIRMED"); waitlist(w)
        respond().execute(ids[0], w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `subida por aumento de vagas gera diaria so para o avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo); fillers(w, 4, "CONFIRMED"); waitlist(w)
        assertIs<br.com.saqz.groups.application.attendance.CapacityCommandResult.Success>(capacity().execute(w.owner, w.group, w.game, 1, 5))
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `subida pelo job do prazo gera diaria so para o avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo, deadline = "now() - interval '5 minutes'"); fillers(w, 3, "CONFIRMED"); waitlist(w)
        val settle = SettleWaitlistAtDeadline(JdbcTransactionRunner(dataSource), JdbcDeadlineWaitlistRepository(dataSource), JdbcAttendanceCommandRepository(dataSource), chargePort(), { _, _ -> }, Instant::now)
        assertEquals(1, settle.run())
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `Colocar no jogo gera diaria so para o avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo); execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${w.group}'")
        fillers(w, 3, "CONFIRMED"); waitlist(w)
        assertIs<AttendanceCommandResult.Success>(respond().promote(w.owner, w.group, w.game, w.subject, UUID.randomUUID(), "Promovido manualmente"))
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `override do organizador gera diaria so para o avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo); fillers(w, 3, "CONFIRMED")
        assertIs<AttendanceCommandResult.Success>(respond().execute(w.owner, w.group, w.game, w.subject, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Autorizado pelo organizador"))
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `saida de outra pessoa do grupo gera diaria so para quem sobe avulso`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo); val ids = fillers(w, 4, "CONFIRMED"); waitlist(w)
        remove().execute(w.owner, w.group, ids[0])
        assertEquals("CONFIRMED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `convidado de anfitriao gera diaria no nome dele mesmo com anfitriao mensalista`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo)
        respond().execute(w.subject, w.group, w.game, intent = AttendanceIntent.CONFIRM)
        val guests = GameGuests(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), respond(), { Instant.now() })
        assertIs<AttendanceCommandResult.Success>(guests.add(w.subject, w.group, w.game, "Rafa Moreira", UUID.randomUUID()))
        // Convidado espera na fila; o organizador o coloca no jogo (a entrada é que gera a diária).
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${w.group}'")
        assertIs<AttendanceCommandResult.Success>(respond().promote(w.owner, w.group, w.game, w.subject, UUID.randomUUID(), "Promovido manualmente", guestSeq = 1))
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE kind='GAME' AND member_user_id='${w.subject}' AND guest_seq=1"), vinculo)
        assertEquals(esperado, diarias(w, w.subject), vinculo)
    }

    @Test fun `avulso que desiste mantem a diaria e mensalista que desiste nao tem o que cancelar`() = cadaVinculo { vinculo, esperado ->
        val w = world(vinculo)
        respond().execute(w.subject, w.group, w.game, intent = AttendanceIntent.CONFIRM)
        respond().execute(w.subject, w.group, w.game, intent = AttendanceIntent.DECLINE)
        assertEquals("DECLINED", status(w, w.subject)); assertEquals(esperado, diarias(w, w.subject), vinculo)
        assertEquals(0, int("SELECT count(*) FROM group_charges WHERE kind='GAME' AND status='CANCELLED'"))
    }

    @Test fun `remover convidado cancela a diaria do convidado do anfitriao mensalista`() {
        val w = world("MENSALISTA")
        respond().execute(w.subject, w.group, w.game, intent = AttendanceIntent.CONFIRM)
        val guests = GameGuests(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), respond(), { Instant.now() })
        guests.add(w.subject, w.group, w.game, "Rafa Moreira", UUID.randomUUID())
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${w.group}'")
        respond().promote(w.owner, w.group, w.game, w.subject, UUID.randomUUID(), "Promovido manualmente", guestSeq = 1)
        execute("UPDATE access_groups SET promotion_mode='FIFO' WHERE id='${w.group}'")
        guests.remove(w.owner, w.group, w.game, w.subject, 1)
        assertEquals("CANCELLED", string("SELECT status FROM group_charges WHERE kind='GAME' AND member_user_id='${w.subject}' AND guest_seq=1"))
    }

    private fun chargePort() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { Instant.now() })
    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)
    private fun respond() = RespondAttendance(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), chargePort(), { now() })
    private fun capacity() = AdjustGameCapacity(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), chargePort(), { now() })
    private fun release() = ReleaseMemberAttendance(JdbcAttendanceCommandRepository(dataSource), chargePort(), Instant::now)
    private fun remove() = RemoveAthlete(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcAthleteRepository(dataSource), GroupAccessPolicy(), attendanceRelease = release())

    /** Grupo sem prioridade de mensalista (a espera sobe em ordem de chegada) e jogo com valor, capacidade 4. */
    private fun world(vinculo: String, deadline: String = "now() + interval '1 day'"): World {
        val owner = user("owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,mensalista_priority,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Grupo','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',false,now(),now())")
        val subject = user("subject"); membership(group, subject, vinculo)
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,published_at,created_at,updated_at) VALUES ('$game','$group','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,$deadline,'Arena','Rua Central 100',4,2500,'PUBLISHED',now(),now(),now())")
        return World(owner, group, game, subject)
    }

    /** Outras pessoas avulsas já no jogo (inseridas direto, sem diária). */
    private fun fillers(w: World, count: Int, status: String): List<UUID> = (1..count).map {
        val id = user("filler-$it"); membership(w.group, id, "AVULSO")
        execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at,member_display_name,guest_seq) VALUES ('${w.game}','${w.group}','$id','$status',now() - interval '1 hour',now(),'User',0)")
        id
    }

    private fun waitlist(w: World) = execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name,guest_seq) VALUES ('${w.game}','${w.group}','${w.subject}','WAITLISTED',1,now(),now(),'User',0)")

    private fun diarias(w: World, member: UUID) = int("SELECT count(*) FROM group_charges WHERE kind='GAME' AND game_id='${w.game}' AND member_user_id='$member' AND guest_seq=0")
    private fun status(w: World, member: UUID) = string("SELECT status::text FROM game_attendance WHERE game_id='${w.game}' AND member_user_id='$member' AND guest_seq=0")
    private fun membership(group: UUID, user: UUID, vinculo: String) = execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$user','ATHLETE',now(),now(),'$vinculo')")
    private fun user(subject: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    private fun execute(sql: String) { dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
}
