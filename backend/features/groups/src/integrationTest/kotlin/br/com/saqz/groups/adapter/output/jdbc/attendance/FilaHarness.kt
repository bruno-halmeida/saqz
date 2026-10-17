package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcAttendanceChangeNotice
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.attendance.GameGuests
import br.com.saqz.groups.application.attendance.ReleaseMemberAttendance
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.attendance.SettleWaitlistAtDeadline
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Cenário comum dos testes da fila de presença (DEB-003 a DEB-006): grupo, jogo com diária, pessoas e os casos de uso
 * ligados ao Postgres, com o aviso direto do PRES-013 ligado. Cada instância migra um banco novo.
 */
internal class FilaHarness(owner: Any, private val clock: () -> Instant = { Instant.now().truncatedTo(ChronoUnit.MICROS) }) {
    val dataSource: DriverManagerDataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = owner).dataSource

    val dono: UUID = user()
    lateinit var group: UUID
    lateinit var game: UUID

    /** Grupo (o dono é ADMIN) e um jogo publicado daqui a 2 dias, com diária de R$ 25. */
    fun cenario(capacity: Int, priority: Boolean = false, deadline: String = "now() + interval '1 day'") {
        group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,mensalista_priority,created_at,updated_at) VALUES ('$group','$dono','${UUID.randomUUID()}','Grupo','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',$priority,now(),now())")
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$dono','ADMIN',now(),now(),'AVULSO')")
        game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,published_at,created_at,updated_at) VALUES ('$game','$group','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,$deadline,'Arena','Rua Central 100',$capacity,2500,'PUBLISHED',now(),now(),now())")
    }

    fun atleta(vinculo: String = "AVULSO"): UUID {
        val id = user()
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$id','ATHLETE',now(),now(),'$vinculo')")
        return id
    }

    /** Linha de presença inserida direto (sem diária); a sequência da espera é a do alocador do jogo. */
    fun presenca(member: UUID, status: String, sequence: Long? = null) =
        execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name,guest_seq) VALUES ('$game','$group','$member','$status',${sequence ?: "NULL"},now(),now(),'User',0)")

    /** Convidado entra sempre pela espera; o organizador o coloca no jogo (escolha manual) e o grupo volta à ordem de chegada. */
    fun convidadoConfirmado(anfitriao: UUID, nome: String): Int {
        val seq = int("SELECT coalesce(max(guest_seq),0) + 1 FROM game_attendance WHERE game_id='$game' AND member_user_id='$anfitriao'")
        convidados().add(anfitriao, group, game, nome, UUID.randomUUID())
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='$group'")
        respostas().promote(dono, group, game, anfitriao, UUID.randomUUID(), "Colocado no jogo", guestSeq = seq)
        execute("UPDATE access_groups SET promotion_mode='FIFO' WHERE id='$group'")
        check(status(anfitriao, seq) == "CONFIRMED")
        return seq
    }

    fun encerrarPrazo() = execute("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id='$game'")

    fun repo() = JdbcAttendanceCommandRepository(dataSource, JdbcAttendanceChangeNotice(dataSource))
    fun cobrancas() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { clock() })
    fun respostas() = RespondAttendance(JdbcTransactionRunner(dataSource), repo(), cobrancas(), clock)
    fun convidados() = GameGuests(JdbcTransactionRunner(dataSource), repo(), respostas(), clock)
    fun liberar() = ReleaseMemberAttendance(repo(), cobrancas(), clock)
    fun remover() = RemoveAthlete(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcAthleteRepository(dataSource), GroupAccessPolicy(), attendanceRelease = liberar())
    fun job() = SettleWaitlistAtDeadline(JdbcTransactionRunner(dataSource), JdbcDeadlineWaitlistRepository(dataSource), repo(), cobrancas(), { _, _ -> }, clock)

    fun status(member: UUID, guest: Int = 0) = string("SELECT status::text FROM game_attendance WHERE game_id='$game' AND member_user_id='$member' AND guest_seq=$guest")
    fun diarias(member: UUID, guest: Int = 0) = int("SELECT count(*) FROM group_charges WHERE kind='GAME' AND game_id='$game' AND member_user_id='$member' AND guest_seq=$guest AND status <> 'CANCELLED'")
    fun statusDaDiaria(member: UUID, guest: Int = 0) = string("SELECT status FROM group_charges WHERE kind='GAME' AND game_id='$game' AND member_user_id='$member' AND guest_seq=$guest")
    fun avisos(recipient: UUID) = int("SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id=n.message_id WHERE n.recipient_id='$recipient' AND m.channel='AUTO_CONFIRMATION'")
    fun eventos(member: UUID, source: String, novo: String) = int("SELECT count(*) FROM attendance_events WHERE game_id='$game' AND member_user_id='$member' AND source='$source' AND new_status='$novo'")

    private fun user(): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','u-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    fun execute(sql: String) { dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } } }
    fun int(sql: String) = query(sql) { it.getInt(1) }
    fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
}
